package org.gms.soloMapling;

import org.gms.client.Client;
import org.gms.net.packet.Packet;

/**
 * 机器人专用的共享无头客户端。
 *
 * <p>一个进程里所有机器人共用一个实例：它们没有 socket，也不需要 socket。
 * 只有 {@code world} / {@code channel} 是真正有用的（决定 {@link #getChannelServer()}
 * 路由到哪个频道），其余元数据全部传空。
 *
 * <p>四个覆写都是「防止基类踩空」：
 * <ul>
 *   <li>{@link #sendPacket} —— 基类直接 {@code ioChannel.writeAndFlush}，机器人没有
 *       ioChannel，不覆盖必 NPE（所有 announce* 帮助方法最终都会走到这里）；</li>
 *   <li>{@link #isLoggedIn} —— 一堆玩家代码路径以它为门禁，机器人必须恒为 true；</li>
 *   <li>{@link #updateLoginState} —— 基类会 UPDATE accounts 并把会话注册进
 *       SessionCoordinator，机器人不该产生这两类副作用；</li>
 *   <li>{@link #disconnectSession}/{@link #closeSession} —— 基类会 close 真正的 ioChannel。</li>
 * </ul>
 *
 * <p>⚠️ {@code remoteAddress} 必须与任何真实账号、其他机器人都不重名
 * （SessionCoordinator 拿它当 hwid 缓存的键），这里固定用 {@code bot-headless}。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
public class BotClient extends Client {

    /**
     * 无头客户端的固定远端标识，切勿用于真实连接。
     */
    public static final String REMOTE_ADDRESS = "bot-headless";

    public BotClient(int world, int channel) {
        // (Type, sessionId, remoteAddress, PacketProcessor, world, channel)
        super(null, -1L, REMOTE_ADDRESS, null, world, channel);
    }

    @Override
    public void sendPacket(Packet packet) {
        // 机器人没有 socket，发给它的封包直接丢弃
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public void updateLoginState(int newState) {
        // 无头客户端：不写 accounts.loggedin，也不注册在线会话
    }

    @Override
    public void disconnectSession() {
        // 没有 socket 可断
    }

    @Override
    public void closeSession() {
        // 没有会话可关
    }
}
