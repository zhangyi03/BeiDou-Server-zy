package org.gms.soloMapling.ArtificialPlayer.BotCommandsPack;

import org.gms.client.Character;
import org.gms.util.PacketCreator;

/**
 * 机器人说话。上游对应 {@code SocialCommands}（189 行，还带着指令表、表情、黑板等），
 * 这里只留 OPQ 用得到的两个：
 *
 * <ul>
 *   <li>{@link #speak} —— 普通聊天（地图里所有人都看得到聊天框那一行）；</li>
 *   <li>{@link #bubble} —— 只冒气泡、不进聊天框，用来做“自言自语”的效果。</li>
 * </ul>
 *
 * <p>两个都走主线现成的聊天封包，不需要客户端回包 —— 机器人没有 socket，只能这样广播。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
public final class BotActions {

    private BotActions() {
    }

    /**
     * 普通聊天。{@code show=0} 表示正常显示在聊天框。
     */
    public static void speak(Character bot, String message) {
        if (bot == null || bot.getMap() == null || message == null || message.isBlank()) {
            return;
        }
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), message, false, 0));
    }

    /**
     * 只冒气泡。{@code show=1} 是客户端约定的「气泡模式」。
     */
    public static void bubble(Character bot, String message) {
        if (bot == null || bot.getMap() == null || message == null || message.isBlank()) {
            return;
        }
        bot.getMap().broadcastMessage(PacketCreator.getChatText(bot.getId(), message, false, 1));
    }

    /**
     * 逐个字符地「打」出一句话（气泡），用于表现打字节奏。
     *
     * <p>会阻塞调用线程，只允许在 {@code BotTiming.chain()} 的虚拟线程里调。
     */
    public static void bubbleTyping(Character bot, String message, long perCharMs) {
        if (bot == null || message == null || message.isEmpty()) {
            return;
        }
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            shown.append(message.charAt(i));
            bubble(bot, shown.toString());
            if (perCharMs > 0) {
                try {
                    Thread.sleep(perCharMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
