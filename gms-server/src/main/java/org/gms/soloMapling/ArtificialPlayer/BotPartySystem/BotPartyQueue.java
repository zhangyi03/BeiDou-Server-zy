package org.gms.soloMapling.ArtificialPlayer.BotPartySystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.soloMapling.server.BotTickService;

/**
 * 「机器人收到了组队邀请」的暂存队列。
 *
 * <p>真人点邀请时，服务端走的是 {@code InviteCoordinator} + 给被邀请人发封包，
 * 等对方客户端回一个「接受/拒绝」。机器人没有客户端，也就永远不会回包 —— 邀请会在 3 分钟后
 * 过期。所以要在 {@code PartyOperationHandler} 创建邀请成功的那一刻，顺手往这里塞一条，
 * 机器人的状态机下一帧就能自己「接受」。
 *
 * <p>用 {@code Character} 的 id 做键而不是角色对象：机器人下线重上会换新对象，
 * 用对象当键会漏清理。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotPartyQueue {

    /**
     * 一条待处理的邀请。
     */
    public static final class PartyInviteEntry {

        private final Character inviter;

        private final int partyId;

        public PartyInviteEntry(Character inviter, int partyId) {
            this.inviter = inviter;
            this.partyId = partyId;
        }

        public Character getInviter() {
            return inviter;
        }

        public int getPartyId() {
            return partyId;
        }
    }

    private static final Map<Integer, PartyInviteEntry> QUEUES = new ConcurrentHashMap<>();

    private BotPartyQueue() {
    }

    /**
     * 记录一条邀请（后到的覆盖先到的）。
     *
     * <p>覆盖而不是忽略，是因为 {@code InviteCoordinator} 只保证「同时只有一个有效邀请」，
     * 而这里如果不覆盖就会留下过期条目：机器人拿着旧 partyId 去 answerInvite 会拿到
     * NOT_FOUND，玩家侧那条真正的邀请反而被卡住。
     */
    public static void addPartyInvite(Character bot, Character inviter, int partyId) {
        if (bot == null) {
            return;
        }
        QUEUES.put(bot.getId(), new PartyInviteEntry(inviter, partyId));
        log.debug("[BotPartyQueue] 机器人 {} 收到 {} 的组队邀请（partyId={}）",
                bot.getName(), inviter == null ? "?" : inviter.getName(), partyId);
        // 立刻推一帧，不用等它自己的慢周期
        BotTickService.nudge(bot.getId());
    }

    public static PartyInviteEntry getPartyInvite(Character bot) {
        return bot == null ? null : QUEUES.get(bot.getId());
    }

    public static boolean hasPendingInvite(Character bot) {
        return bot != null && QUEUES.containsKey(bot.getId());
    }

    public static void removePartyInvite(Character bot) {
        if (bot != null) {
            QUEUES.remove(bot.getId());
        }
    }

    /**
     * 机器人下线时清干净，避免 id 被复用后误接受。
     */
    public static void clear(int botId) {
        QUEUES.remove(botId);
    }
}
