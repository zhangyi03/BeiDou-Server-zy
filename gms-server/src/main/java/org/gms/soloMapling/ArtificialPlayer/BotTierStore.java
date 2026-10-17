package org.gms.soloMapling.ArtificialPlayer;

import org.gms.client.Character;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bot 档位（{@link BotTier}）的舱外存储。
 *
 * <p>上游 SoloMapling 直接在 {@code client.Character} 里加了 {@code private BotTier tier}
 * 字段和 getter/setter，那会把 bot-only 的类型拖进主线。这里改成按 Character 身份索引的
 * 弱引用表：
 * <ul>
 *   <li>{@code Character} 没有重写 equals/hashCode，弱引用表的键就是对象身份，不会误合并；</li>
 *   <li>键是弱引用，bot 被回收后记录自动消失，不泄漏；</li>
 *   <li>空值兜底复用 {@link BotTier.TierManager#getSafeTier(BotTier)}，语义与上游一致。</li>
 * </ul>
 *
 * <p>调用点写法：{@code bot.setTier(t)} → {@code BotTierStore.setTier(bot, t)}；
 * {@code bot.getTier()} → {@code BotTierStore.getTier(bot)}。
 */
public final class BotTierStore {

    private static final Map<Character, BotTier> TIERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BotTierStore() {
    }

    public static void setTier(Character bot, BotTier tier) {
        if (bot == null) {
            return;
        }
        TIERS.put(bot, BotTier.TierManager.safeTierSet(TIERS.get(bot), tier));
    }

    public static BotTier getTier(Character bot) {
        if (bot == null) {
            return BotTier.getDefaultTier();
        }
        return BotTier.TierManager.getSafeTier(TIERS.get(bot));
    }

    public static void clear(Character bot) {
        if (bot != null) {
            TIERS.remove(bot);
        }
    }
}
