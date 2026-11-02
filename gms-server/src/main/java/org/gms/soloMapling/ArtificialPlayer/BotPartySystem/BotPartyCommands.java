package org.gms.soloMapling.ArtificialPlayer.BotPartySystem;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.net.server.coordinator.world.InviteCoordinator;
import org.gms.net.server.coordinator.world.InviteCoordinator.InviteResult;
import org.gms.net.server.coordinator.world.InviteCoordinator.InviteResultType;
import org.gms.net.server.coordinator.world.InviteCoordinator.InviteType;
import org.gms.net.server.world.Party;
import org.gms.util.PacketCreator;

/**
 * 机器人侧真正「接受/拒绝组队邀请」的动作。
 *
 * <p>顺序和真人客户端完全一致：先在 {@link InviteCoordinator} 里把这条邀请应答掉
 * （否则玩家会看到「正在处理另一个邀请」卡三分钟），再 {@link Party#joinParty} 入队。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotPartyCommands {

    private BotPartyCommands() {
    }

    /**
     * 接受队列里的那条邀请。
     *
     * @return 是否成功入队
     */
    public static boolean botAcceptPartyInvite(Character bot) {
        if (bot == null) {
            return false;
        }
        BotPartyQueue.PartyInviteEntry entry = BotPartyQueue.getPartyInvite(bot);
        if (entry == null) {
            return false;
        }
        int partyId = entry.getPartyId();
        InviteResult res = InviteCoordinator.answerInvite(InviteType.PARTY, bot.getId(), partyId, true);
        BotPartyQueue.removePartyInvite(bot);

        if (res.result != InviteResultType.ACCEPTED) {
            log.info("[BotPartyCommands] 机器人 {} 的组队邀请已失效（{}），不再入队",
                    bot.getName(), res.result);
            return false;
        }

        boolean joined = Party.joinParty(bot, partyId, false);
        if (!joined) {
            // joinParty 失败对邀请人是静默的，这里补一句提示，好让人知道该重新邀请
            Character inviter = entry.getInviter();
            if (inviter != null) {
                inviter.sendPacket(PacketCreator.serverNotice(5, bot.getName()
                        + " 没能加入你的队伍（队伍已满或已解散），请重新邀请。"));
            }
            log.info("[BotPartyCommands] 机器人 {} 入队失败（partyId={}）", bot.getName(), partyId);
        }
        return joined;
    }

    /**
     * 拒绝队列里的那条邀请。
     */
    public static boolean botRejectPartyInvite(Character bot) {
        if (bot == null) {
            return false;
        }
        BotPartyQueue.PartyInviteEntry entry = BotPartyQueue.getPartyInvite(bot);
        if (entry == null) {
            return false;
        }
        InviteResult res = InviteCoordinator.answerInvite(InviteType.PARTY, bot.getId(), entry.getPartyId(), false);
        BotPartyQueue.removePartyInvite(bot);

        Character inviter = entry.getInviter();
        if (inviter != null && res.result == InviteResultType.DENIED) {
            inviter.sendPacket(PacketCreator.serverNotice(5, bot.getName() + " 拒绝了你的组队请求。"));
        }
        return true;
    }
}
