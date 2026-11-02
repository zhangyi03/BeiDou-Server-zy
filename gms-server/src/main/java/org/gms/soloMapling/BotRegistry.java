package org.gms.soloMapling;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.gms.client.Character;

/**
 * 当前在线的机器人登记表。
 *
 * <p>为什么需要它：服务端的很多入口（组队邀请、聊天、交易请求）拿到的是 {@code Character}，
 * 它们需要判断「这个角色是不是机器人」。判断方式不能看 {@code client} —— 机器人也有一个
 * 共用的无头 {@code BotClient}，真实的玩家客户端反而更难区分。
 *
 * <p>由 {@code BotService} 在上线/下线时维护，其它地方只读。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
public final class BotRegistry {

    private static final Map<Integer, Character> BOTS = new ConcurrentHashMap<>();

    private BotRegistry() {
    }

    public static void register(Character bot) {
        if (bot != null) {
            BOTS.put(bot.getId(), bot);
        }
    }

    public static void unregister(int botId) {
        BOTS.remove(botId);
    }

    public static boolean isBot(int characterId) {
        return BOTS.containsKey(characterId);
    }

    public static Character get(int characterId) {
        return BOTS.get(characterId);
    }

    public static Collection<Character> all() {
        return BOTS.values();
    }

    public static int size() {
        return BOTS.size();
    }

    public static void clear() {
        BOTS.clear();
    }
}
