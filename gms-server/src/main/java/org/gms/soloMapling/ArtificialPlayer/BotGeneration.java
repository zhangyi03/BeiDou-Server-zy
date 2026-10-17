package org.gms.soloMapling.ArtificialPlayer;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.dao.entity.BotProfileDO;
import org.gms.net.server.Server;
import org.gms.net.server.channel.Channel;
import org.gms.net.server.world.World;
import org.gms.server.maps.MapleMap;
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
 * <p>本轮只做生成（站桩），原版的下落动画（portal drop-down）与转身微动作依赖移动系统，
 * 留到移动系统移植时再接回。
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

        placeOnMap(bot, profile);

        log.info("[BotGeneration] 机器人 {}（cid={}）已上线，地图 {} 坐标 ({}, {})",
                bot.getName(), bot.getId(), profile.getMapId(),
                profile.getSpawnX(), profile.getSpawnY());
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
        bot.setPosition(new Point(
                profile.getSpawnX() == null ? 0 : profile.getSpawnX(),
                profile.getSpawnY() == null ? 0 : profile.getSpawnY()));
        bot.setStance(0);

        Channel channel = Server.getInstance().getChannel(bot.getWorld(), getBotClient().getChannel());
        World world = Server.getInstance().getWorld(bot.getWorld());
        if (channel == null || world == null) {
            throw new IllegalStateException("世界/频道不存在，机器人无法上线");
        }

        channel.addPlayer(bot);
        world.addPlayer(bot);
        bot.setEnteredChannelWorld();
        map.addPlayer(bot);
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
