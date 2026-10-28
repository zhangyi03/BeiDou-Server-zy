package org.gms.soloMapling.ArtificialPlayer;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.dao.entity.BotProfileDO;
import org.gms.net.server.Server;
import org.gms.net.server.channel.Channel;
import org.gms.net.server.world.World;
import org.gms.server.maps.Foothold;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Portal;
import org.gms.soloMapling.ArtificialPlayer.BotMovementSystem.BotMovement;
import org.gms.soloMapling.BotClient;
import org.gms.soloMapling.itemPool.EquipMetadataCache;

import java.awt.Point;

/**
 * 机器人生成：把一条 {@code bot_profile} 变成地图上站着的一个「真实玩家」。
 *
 * <p>与 SoloMapling 的区别：那边所有 bot 共用一个基础角色、在内存里改 id/名字；
 * 这边每条档案背后都是 {@code characters} 表里的真实行，所以生成 = 走
 * {@link Character#loadCharFromDB} 把角色读出来（连同 {@code inventoryitems} 里的装备一起）、
 * 塞进频道/世界/地图三处容器。外观与装备的随机只发生在首次上线，之后一律以库为准。
 *
 * <p>本轮只做生成（站桩）。原版的下落动画（portal drop-down）依赖移动系统，留到移动系统
 * 移植时再接回；<b>出生这一下不能播</b> —— 客户端会按角色出生坐标算「图层」，先摆在空中
 * 再补 MOVE_PLAYER 落地，人落到地面了图层还留在半空，表现出来就是「被树/前景物件挡住」。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Slf4j
public class BotGeneration {

    /**
     * 无头客户端，进程内所有机器人共用。
     */
    private static volatile BotClient botClient;

    private BotGeneration() {
    }

    /**
     * 初始化无头客户端并预热 WZ 装备元数据缓存。
     *
     * <p>缓存必须在上线第一个机器人之前 ready，否则装饰会退化成「只穿通用装备」。
     * 扫描是同步的（要遍历 Character.wz），所以放在后台线程里跑，不阻塞启动。
     */
    public static void init(int world, int channel) {
        if (botClient == null) {
            synchronized (BotGeneration.class) {
                if (botClient == null) {
                    botClient = new BotClient(world, channel);
                }
            }
        }
        if (!EquipMetadataCache.isInitialized()) {
            Thread t = new Thread(() -> {
                try {
                    EquipMetadataCache.initialize();
                    log.info("[BotGeneration] 装备元数据缓存预热完成");
                } catch (Exception e) {
                    log.error("[BotGeneration] 装备元数据缓存预热失败，机器人只能穿通用装备", e);
                }
            }, "bot-equip-cache-init");
            t.setDaemon(true);
            t.start();
        }
    }

    public static BotClient getBotClient() {
        BotClient client = botClient;
        if (client == null) {
            throw new IllegalStateException("BotGeneration 尚未初始化（先调 init(world, channel)）");
        }
        return client;
    }

    public static boolean isInitialized() {
        return botClient != null;
    }

    /**
     * 让一个机器人上线，返回挂在地图上的角色对象。
     *
     * <p>顺序不能改：先落到地图（{@code addPlayer}）再装饰，因为
     * {@code equipChanged()} 会向地图广播外观更新，map 为空会 NPE。
     *
     * <p>本方法只负责「读库 + 挂地图」。外观与装备的生成 / 落库在
     * {@code BotService#ensureDecorated} 里做 —— 那一步需要 Mapper 与事务上下文，
     * 放在 Spring 层比塞进静态工具类干净。
     */
    public static Character spawnBot(BotProfileDO profile) {
        if (profile == null || profile.getCharacterId() == null) {
            throw new IllegalArgumentException("机器人档案缺少 characterId");
        }
        BotClient client = getBotClient();

        Character bot = Character.loadCharFromDB(profile.getCharacterId(), client, true);
        if (bot == null) {
            throw new IllegalStateException("加载角色 " + profile.getCharacterId() + " 失败（看 CharacterService 的日志）");
        }

        // 关键：跳过「当前等级/属性能不能穿」这道校验。
        //
        // PacketCreator#addCharEquips 走的是 ItemInformationProvider#canWearEquipment，那个方法在
        // Inventory#checked == false 时会把装备逐件按 reqLevel / reqSTR / reqDEX / reqINT / reqLUK
        // 筛一遍，再把剩余的写进 spawn 包，最后把 checked 置 true 缓存住。
        //
        // 机器人的属性是建号时的 4/4/4/4（或 12/5），装备却是按档位随机的 —— 一堆高等级装备
        // 因为「reqSTR 90 > 12」被筛掉，于是上线那一刻广播出去的 spawn 包里几乎是裸的；
        // 而 checked 已经被置成 true，后续任何人进图拿到的都是完整装备。
        // 表现出来就是：刚上线没装备，别人换线 / 重登之后就都有了。
        //
        // 机器人装备是服务端自己给的，不存在「穿不上」的业务语义，这里直接标成已校验。
        bot.getInventory(InventoryType.EQUIPPED).checked(true);

        placeOnMap(bot, profile);

        log.info("[BotGeneration] 机器人 {}（cid={}）已上线，地图 {} 落点 ({}, {})，stance {}",
                bot.getName(), bot.getId(), profile.getMapId(),
                bot.getPosition().x, bot.getPosition().y, bot.getStance());
        return bot;
    }

    /**
     * 把角色挂到频道/世界/地图三处容器上，和真实玩家登录时的顺序一致。
     */
    private static void placeOnMap(Character bot, BotProfileDO profile) {
        int mapId = profile.getMapId() == null ? 910000000 : profile.getMapId();
        MapleMap map = getBotClient().getChannelServer().getMapFactory().getMap(mapId);
        if (map == null) {
            throw new IllegalStateException("地图 " + mapId + " 不存在");
        }

        // 注意：Character 有两个 setMap 重载 —— setMap(int) 只改 mapId，
        // setMap(MapleMap) 才会挂上真正的地图对象（equipChanged 依赖它）。
        // loadCharFromDB(channelServer=true) 已经按 characters.map 挂过一次，
        // 这里以档案里的 mapId 为准重新挂，避免两边不一致。
        MapleMap oldMap = bot.getMap();
        if (oldMap != null && oldMap != map) {
            oldMap.removePlayer(bot);
        }
        bot.setMap(map);
        Point ground = resolveSpawnPoint(map, profile);
        // 出生就直接站在地面上，不要先摆到空中再补 move 包落地。
        // 客户端的「图层」（角色被前景树/物件遮挡的先后顺序）是拿角色坐标算的，
        // 出生点在空中 → 图层也按空中那份算；后面的 MOVE_PLAYER 只会改坐标，
        // 不会把图层重新算回来，于是人明明已经落地，画面上却还压在树后面。
        bot.setPosition(ground);
        bot.setStance(BotMovement.STANCE_IDLE_RIGHT);

        Channel channel = Server.getInstance().getChannel(bot.getWorld(), getBotClient().getChannel());
        World world = Server.getInstance().getWorld(bot.getWorld());
        if (channel == null || world == null) {
            throw new IllegalStateException("世界/频道不存在，机器人无法上线");
        }

        channel.addPlayer(bot);
        world.addPlayer(bot);
        bot.setEnteredChannelWorld();
        // 关键：第三个参数必须走 enteringField = false 的重载。
        //
        // 默认的 addPlayer(chr) 会把这次上线当成「真人自己进图」，广播出去的 spawn 包里
        // 坐标被抬高 42px、姿态被写成 6（跳跃），本意是让客户端自己播一段「从上方落地」的
        // 动画、落地后再用 MOVE_PLAYER 回传真实坐标。真人有客户端做这一步，机器人没有，
        // 于是客户端就把它摆在半空 42px 处、还保持跳跃姿势 —— 之前查了半天「落点是对的、
        // 画面上却卡在半空」，根因就在这里。
        map.addPlayer(bot, false);
    }

    /**
     * 算出机器人真正要站的落点。
     *
     * <p>机器人没有客户端，服务端也不替它跑重力：坐标给在空中就永远悬在那儿，所以上线前
     * 必须自己把坐标吸到 foothold 上。
     *
     * <p>踩过的坑，别再退回去：
     * <ul>
     *   <li>{@code MapleMap#getPointBelow} 内部就是 {@code footholds.findBelow}，只找「起点下方」
     *       的第一块地面。拿档案里默认的 {@code y = 0} 当起点，找到的往往是地图上方某个悬空
     *       平台（甚至是树台），表现出来就是「机器人卡在半空中」。所以起点高度必须有参照物，
     *       而出生传送门的 y 一定贴着地面。</li>
     *   <li>不能用 {@code getGroundBelow}：它在 {@code calcPointBelow} 返回 null 时直接解引用会 NPE。</li>
     *   <li>{@code MapleMap#getPortals()} 在本项目里被注释掉了，取传送门只能用
     *       {@code findClosestPlayerSpawnpoint} / {@code getPortal(id)}。</li>
     * </ul>
     */
    private static Point resolveSpawnPoint(MapleMap map, BotProfileDO profile) {
        int x = profile.getSpawnX() == null ? 0 : profile.getSpawnX();
        int y = profile.getSpawnY() == null ? 0 : profile.getSpawnY();
        boolean hasCoord = x != 0 || y != 0;

        // 出生传送门（pt=0/1 且不连其它地图）一定站在地面上，拿它当「地面高度」的参照
        Portal spawnPortal = hasCoord ? map.findClosestPlayerSpawnpoint(new Point(x, y)) : null;
        if (spawnPortal == null) {
            spawnPortal = map.getPortal(0);
        }
        if (spawnPortal == null) {
            spawnPortal = map.findClosestPlayerSpawnpoint(new Point(x, y));
        }
        Point portalPos = spawnPortal == null ? null : spawnPortal.getPosition();

        // 参照高度：档案里给了 y 就以档案为准（可能想放到某一层平台），
        // 默认的 y = 0 不作数，改用传送门的 y。
        int refY = y != 0 ? y : (portalPos != null ? portalPos.y : y);

        // 1) 档案给了坐标：保留它的 x，把 y 吸到该列的地面上
        if (hasCoord) {
            Point ground = groundBelow(map, new Point(x, refY));
            // 落差过大说明抓到的不是同一层的地面（该 x 下面只剩悬空平台/树台），
            // 这种情况宁可用出生点，也不要让机器人飘在半空。
            if (ground != null && Math.abs(ground.y - refY) <= 400) {
                log.info("[BotGeneration] 地图 {} 落点：档案 x={} + 参照高度 {} -> ({}, {})",
                        map.getId(), x, refY, ground.x, ground.y);
                return ground;
            }
            if (ground != null) {
                log.info("[BotGeneration] 地图 {} 档案 x={} 下方的地面 y={} 离参照高度 {} 太远，改用出生点",
                        map.getId(), x, ground.y, refY);
            }
        }

        // 2) 退回出生传送门：它本身就在地面上，能吸附就吸附，吸不上就直接用它的坐标
        if (portalPos != null) {
            Point ground = groundBelow(map, portalPos);
            Point spot = ground != null ? ground : portalPos;
            log.info("[BotGeneration] 地图 {} 落点：出生传送门（{}）-> ({}, {})",
                    map.getId(), ground != null ? "吸附地面" : "原坐标", spot.x, spot.y);
            return spot;
        }

        log.warn("[BotGeneration] 地图 {} 没有可用的出生点（档案坐标 ({}, {})），机器人会停在空中",
                map.getId(), x, y);
        return new Point(x, y);
    }

    /**
     * 从 {@code from} 往下找第一块 foothold，返回站在上面的坐标；下方没有地面返回 null。
     *
     * <p>起点取 {@code from.y - 14}：{@code findBelow} 的条件是 foothold 的 y >= 起点的 y，
     * 从正好贴着地面的点开始找会漏掉脚下那块，所以先抬高一点。
     */
    private static Point groundBelow(MapleMap map, Point from) {
        Foothold foothold = map.getFootholds().findBelow(new Point(from.x, from.y - 14));
        if (foothold == null) {
            return null;
        }
        return new Point(from.x, foothold.calculateFooting(from.x) - 1);
    }

    /**
     * 下线：从地图 / 频道 / 世界三处容器里摘掉，并清掉舱外存的档位。
     * 不写库、不发断连封包（无头客户端没有 socket）。
     */
    public static void despawnBot(Character bot) {
        if (bot == null) return;

        if (bot.getMap() != null) {
            bot.getMap().removePlayer(bot);
        }
        Channel channel = Server.getInstance().getChannel(bot.getWorld(), getBotClient().getChannel());
        if (channel != null) {
            channel.removePlayer(bot);
        }
        World world = Server.getInstance().getWorld(bot.getWorld());
        if (world != null) {
            world.getPlayerStorage().removePlayer(bot.getId());
        }
        BotTierStore.clear(bot);

        log.info("[BotGeneration] 机器人 {}（cid={}）已下线", bot.getName(), bot.getId());
    }
}
