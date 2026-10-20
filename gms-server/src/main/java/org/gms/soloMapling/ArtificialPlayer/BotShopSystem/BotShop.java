package org.gms.soloMapling.ArtificialPlayer.BotShopSystem;

import org.gms.client.Character;
import org.gms.client.inventory.Item;
import org.gms.constants.game.GameConstants;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.PlayerShop;
import org.gms.server.maps.PlayerShopItem;
import org.gms.util.PacketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 摆摊出售机器人（{@code SELLING_MERCHANT_BOT}）的摊子。
 *
 * <p>上游 SoloMapling 的 {@code SellingMerchantBot} 是「站在自由市场喊 S&gt; 物品名，
 * 等玩家点它发起交易，再走 {@code BotTradeSM} 交易状态机」，那条路在 v83 里需要玩家
 * 点两次确认，移植量极大。本版换成<b>直接开个人商店（{@code PlayerShop}）</b>：
 * 机器人站在自由市场房间里铺一个摊子，货架物品从 {@link SellItemPool} 抽，
 * 玩家点开摊子就能按标价直接买 —— 买卖全部复用主线的 {@code PlayerShop#buy}。
 *
 * <p>生命周期：
 * <ul>
 *   <li>上线（{@code BotService#spawnOne}）→ {@link #openStall(Character)}；</li>
 *   <li>下线/删除 → {@link #closeStall(Character)}；</li>
 *   <li>卖光 → 主线自己关摊并清空 {@code chr.playerShop}，{@link #tick()} 下一轮补货重开。</li>
 * </ul>
 *
 * <p>必须在自由市场<b>房间</b>（910000001~910000022）里摆摊：
 * 入口 910000000 没有摊位空间，主线 {@code PlayerInteractionHandler} 同样拒绝在那里开摊。
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class BotShop {

    private static final Logger log = LoggerFactory.getLogger(BotShop.class);

    /**
     * 个人商店兑换券（现金道具），只取 {@code itemId % 100} 当摊子的外观编号
     */
    private static final int PLAYER_SHOP_ITEM_ID = 5140000;

    /**
     * 一次上架的格数。{@code PlayerShop} 上限 16 格，8 格足够让货看起来不空。
     */
    private static final int SLOT_COUNT = 8;

    private static final String SHOP_DESCRIPTION = "卷轴 / 药水 / 飞镖 便宜卖";

    private static final String[] AD_LINES = {
            "卷轴、药水、飞镖，走过路过别错过~",
            "新货上架，价格公道，老板来看看？",
            "收摊前最后一波特价，手慢无！",
            "强化卷轴齐全，欢迎选购~",
    };

    /**
     * 摊子登记表：characterId -> 摊位状态。只有<b>成功开摊</b>的机器人才会进来，
     * 卖光后仍然保留（下一轮补货重开），下线时移除。
     */
    private static final Map<Integer, Stall> stalls = new ConcurrentHashMap<>();

    /**
     * 已经提示过「地图不对，不能摆摊」的角色，避免每 5 秒刷一条 warn
     */
    private static final Map<Integer, Boolean> warnedMap = new ConcurrentHashMap<>();

    private BotShop() {
    }

    private static class Stall {
        final Character bot;
        /** 下一次喊话的时间戳（毫秒） */
        volatile long nextChatAt;
        /** 最近一次开的摊子：卖光后主线只 broadcast 移除框、不摘地图对象，补货时要自己摘 */
        volatile PlayerShop lastShop;

        Stall(Character bot) {
            this.bot = bot;
            this.nextChatAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(8000, 16000);
        }
    }

    /**
     * 给机器人开摊。已经开着就直接返回 true。
     *
     * @return 是否处于「摊位已开」状态
     */
    public static boolean openStall(Character bot) {
        if (bot == null) {
            return false;
        }
        Stall existing = stalls.get(bot.getId());
        if (bot.getPlayerShop() != null) {
            if (existing == null) {
                stalls.put(bot.getId(), new Stall(bot));
            }
            return true;
        }

        MapleMap map = bot.getMap();
        if (map == null) {
            return false;
        }
        if (!GameConstants.isFreeMarketRoom(map.getId())) {
            if (warnedMap.putIfAbsent(bot.getId(), Boolean.TRUE) == null) {
                log.warn("[BotShop] 机器人 {}（cid={}）所在地图 {} 不是自由市场房间，无法摆摊；"
                                + "请把档案的 mapId 改成 910000001~910000022 之一",
                        bot.getName(), bot.getId(), map.getId());
            }
            return false;
        }

        SellItemPool.load();
        PlayerShop shop = new PlayerShop(bot, SHOP_DESCRIPTION, PLAYER_SHOP_ITEM_ID);
        int stocked = stock(shop);
        if (stocked <= 0) {
            log.warn("[BotShop] 机器人 {} 开摊失败：出售物品池为空，请检查 bot_sell_item_pool", bot.getName());
            return false;
        }

        // 顺序照抄 PlayerInteractionHandler：先挂到角色，再加进地图（加进地图才会分配 objectId），
        // 最后广播摊位框，否则客户端拿到的 objectId 是 0，点不开。
        bot.setPlayerShop(shop);
        map.addMapObject(shop);
        shop.setOpen(true);
        map.broadcastMessage(PacketCreator.updatePlayerShopBox(shop));

        Stall stall = stalls.computeIfAbsent(bot.getId(), k -> new Stall(bot));
        // 卖光时主线自己关的摊子，对象还留在地图对象表里，这里顺手摘掉，避免每卖空一次泄漏一个对象
        PlayerShop previous = stall.lastShop;
        if (previous != null && previous != shop) {
            map.removeMapObject(previous);
        }
        stall.lastShop = shop;
        stall.nextChatAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(8000, 16000);
        warnedMap.remove(bot.getId());
        log.info("[BotShop] 机器人 {}（cid={}）在 {} 开摊，上架 {} 格", bot.getName(), bot.getId(), map.getId(), stocked);
        return true;
    }

    /**
     * 收摊：广播移除摊位框，并把摊子从地图对象表里摘掉。
     */
    public static void closeStall(Character bot) {
        if (bot == null) {
            return;
        }
        stalls.remove(bot.getId());
        warnedMap.remove(bot.getId());

        PlayerShop shop = bot.getPlayerShop();
        if (shop == null) {
            return;
        }
        bot.setPlayerShop(null);
        shop.setOpen(false);
        MapleMap map = bot.getMap();
        if (map != null) {
            shop.closeShop();
            map.removeMapObject(shop);
        } else {
            // 角色已经不在图上（下线流程里地图可能已置空），只做本地清理，避免 NPE
            shop.removeVisitors();
        }
        log.info("[BotShop] 机器人 {}（cid={}）已收摊", bot.getName(), bot.getId());
    }

    /**
     * 定时调度入口（{@code BotShopTicker} 每 5 秒调一次）：
     * ① 摊子没了（卖光被主线关掉）就补货重开；② 到点喊一句广告。
     */
    public static void tick() {
        if (stalls.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Stall stall : stalls.values()) {
            Character bot = stall.bot;
            if (bot == null || bot.getMap() == null) {
                continue;
            }
            try {
                if (bot.getPlayerShop() == null) {
                    openStall(bot);
                    continue;
                }
                if (now >= stall.nextChatAt) {
                    shout(bot);
                    stall.nextChatAt = now + ThreadLocalRandom.current().nextInt(12000, 20000);
                }
            } catch (Exception e) {
                log.warn("[BotShop] 机器人 {} 摊位维护失败：{}", bot.getName(), e.getMessage());
            }
        }
    }

    /**
     * 当前由本模块维护的摊子数（监控/日志用）。
     */
    public static int stallCount() {
        return stalls.size();
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 按池抽 {@link #SLOT_COUNT} 件货，逐格加到摊子上。
     *
     * @return 实际上架的格数
     */
    private static int stock(PlayerShop shop) {
        List<SellItemPool.PoolItem> picked = SellItemPool.randomItems(SLOT_COUNT);
        int added = 0;
        for (SellItemPool.PoolItem poolItem : picked) {
            int qty = poolItem.qtyMax > poolItem.qtyMin
                    ? ThreadLocalRandom.current().nextInt(poolItem.qtyMin, poolItem.qtyMax + 1)
                    : poolItem.qtyMin;
            Item item = new Item(poolItem.itemId, (short) 0, (short) qty);
            // bundles=1：一格就是一组，买家买 1 组付 price、拿 qty 个
            if (shop.addItem(new PlayerShopItem(item, (short) 1, poolItem.price))) {
                added++;
            }
        }
        return added;
    }

    /**
     * 机器人喊一句广告。用主线聊天包原地广播，不需要客户端。
     */
    private static void shout(Character bot) {
        String line = AD_LINES[ThreadLocalRandom.current().nextInt(AD_LINES.length)];
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), line, false, 0));
    }
}
