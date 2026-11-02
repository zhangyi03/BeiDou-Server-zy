package org.gms.soloMapling.ArtificialPlayer.BotPartySystem;

import org.gms.client.Character;

/**
 * 状态机每帧调一下：有邀请就接受。
 *
 * <p>单独一层是因为「什么时候接受」以后可能要加策略（只接受队长邀请、只接受白名单、
 * 打怪中的机器人不接受……），现在先无条件接受。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
public final class BotPartyLogic {

    private BotPartyLogic() {
    }

    /**
     * @return 这一帧是否真的入队成功
     */
    public static boolean checkPartyQueue(Character bot) {
        if (bot == null || !BotPartyQueue.hasPendingInvite(bot)) {
            return false;
        }
        return BotPartyCommands.botAcceptPartyInvite(bot);
    }

    public static void clearBotInvite(Character bot) {
        BotPartyQueue.removePartyInvite(bot);
    }
}
