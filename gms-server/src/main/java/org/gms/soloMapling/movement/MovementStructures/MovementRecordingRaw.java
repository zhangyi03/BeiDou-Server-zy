package org.gms.soloMapling.movement.MovementStructures;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.gms.soloMapling.ArtificialPlayer.BotLogic.isPointNear;
import static org.gms.soloMapling.ArtificialPlayer.BotLogic.isPointNearSameY;
import static org.gms.soloMapling.movement.InPacketReader.getMovementRecording;
import static org.gms.soloMapling.movement.MovementStructures.SingleMoveCommand.endsInJumpingStance;
import static org.gms.soloMapling.movement.MovementStructures.SingleMoveCommand.endsInStandingStance;
import static org.gms.soloMapling.server.DebugUtil.debugprint;

/*
    Full Raw Recording of a movement packet list.
    Can be created by using "startrecording" movement command
    Useful for determining the raw trajectory of player's x/y coordinates without needing to parse data from hex
 */

public class MovementRecordingRaw {

    private final int mapId;
    private final String recordingName;
    private final List<MovementPacketRaw> movementPacketList;
    /**
     * 懒构建的时间-坐标路径。
     *
     * <p><b>必须是 volatile + 一次性发布。</b>本类实例由 {@code InPacketReader.RAW_CACHE}
     * 共享，5 台机器人各自跑在独立虚拟线程上，会同时调用 {@link #getTimeCoordPath()}。
     * 原实现是「先把空 ArrayList 赋给字段，再逐个 add」，另一个线程此刻拿到的就是
     * 这个正在被填充的列表 → 边遍历边 add →
     * {@code ConcurrentModificationException}（日志里的 {@code Error processing element: m1}）。
     * 改成「本地构建完再整体发布」，任何线程看到的要么是 null、要么是完整列表。
     */
    private volatile List<TimeCoordinatePair> timeCoordPath = null;

    public MovementRecordingRaw(int mapId, String recordingName, List<MovementPacketRaw> movementPacketList) {
        this.mapId = mapId;
        this.recordingName = recordingName;
        this.movementPacketList = movementPacketList;
    }

    public int getMapId() {
        return this.mapId;
    }

    public String getRecordingName() {
        return this.recordingName;
    }

    public List<MovementPacketRaw> getMovementPacketList() {
        return this.movementPacketList;
    }

    /*
    Some Movement Packets are recorded in a way where stand still stances are considered "Intersection" points
    This method can be used to quickly get those intersection point for Navigation Route calculations
     */
    public List<TimeCoordinatePair> getPacketsWithSelectStances(List<Byte> stances) {
        List<TimeCoordinatePair> packetsWithStances = new ArrayList<>();
        for (MovementPacketRaw rawPacket : getMovementPacketList()) {
            for (SingleMoveCommand comm : rawPacket.getRecordList()) {
                if (stances.contains(comm.getNewstate())) {
                    packetsWithStances.add(rawPacket.getTimeCoordinatePair());
                }
            }
        }
        return packetsWithStances;
    }

    public MovementPacketRaw getLastPacket() {
        return getMovementPacketList().getLast();
    }

    public MovementPacketRaw getFirstPacket() {
        return getMovementPacketList().getFirst();
    }

    public Point getEndPoint() {
        return getLastPacket().getRecordList().getLast().getPoint();
    }

    public Point getStartPoint() {
        return getFirstPacket().getRecordList().getFirst().getPoint();
    }

    public Long getEndPointTimestamp() {
        return getLastPacket().getTimestamp();
    }

    public List<TimeCoordinatePair> getTimeCoordPath() {
        List<TimeCoordinatePair> local = timeCoordPath;
        if (local == null) {
            synchronized (this) {
                local = timeCoordPath;
                if (local == null) {
                    local = buildTimeCoordPath();
                    timeCoordPath = local; // 整体发布，构建过程中别线程看不到半成品
                }
            }
        }
        return local;
    }

    public void setTimeCoordPath() {
        timeCoordPath = buildTimeCoordPath();
    }

    /** 在本地列表里建好再返回，调用方负责发布（不要在字段上边建边加）。 */
    private List<TimeCoordinatePair> buildTimeCoordPath() {
        List<MovementPacketRaw> packets = getMovementPacketList();
        List<TimeCoordinatePair> built = new ArrayList<>(packets.size());
        for (MovementPacketRaw rawPacket : packets) {
            built.add(rawPacket.getTimeCoordinatePair());
        }
        return built;
    }

    public long getRecordingDuration() {
        return getMovementPacketList().getLast().getTimestamp() -
                getMovementPacketList().getFirst().getTimestamp();
    }

//    public List<TimeCoordinatePair> getTimeCoordPairsAtPoint(Point myPoint) {
//        List<TimeCoordinatePair> tcp = getTimeCoordPairsAtPoint(myPoint, 15);
//        if (tcp != null) {
//            return tcp;
//        } else {
//            tcp = getTimeCoordPairsAtPoint(myPoint, 25);
//            if (tcp != null) {
//                return tcp;
//            } else {
//                tcp = getTimeCoordPairsAtPoint(myPoint, 35);
//                if (tcp != null) {
//                    return tcp;
//                }
//            }
//        }
//        return new ArrayList<>();
//    }

    public List<TimeCoordinatePair> getTimeCoordPairsAtPoint(Point myPoint) {
        // Define search distances in ascending order
        int[] searchDistances = {40}; // {15,25,35} // if smaller, coordinates need to be tighter, might skip over

        // Try each distance until we find a result
        for (int distance : searchDistances) {
            List<TimeCoordinatePair> pairs = getTimeCoordPairsAtPoint(myPoint, distance);
            if (pairs != null && !pairs.isEmpty()) {
                return pairs;
            }
        }

        // Return empty list if no results found at any distance
//        debugprint("ERROR: getTimeCoordPairsAtPoint not found: " + myPoint);
        return new ArrayList<>();
    }

    /*
    Helper function to prevent bot movement ending in a non-standing state (such as jumping or walking)
    Unused
     */
//    public List<TimeCoordinatePair> trimTCPairsForNonStandingStances(List<TimeCoordinatePair> tcPair) {
//        // Iterate backwards to find the last index with standing stance
//        for (int i = tcPair.size() - 1; i >= 0; i--) {
//            if (endsInJumpingStance(tcPair.get(i).getStances())) {
//                // Found a standing stance, return everything up to and including this index
//                debugprint("Trimmed list ends in idle stance");
//                return tcPair.subList(0, i + 1);
//            }
//        }
//        // If no standing stance found at all, return default
//        debugprint("no standing stance found. returning default");
//        return tcPair;
//    }

    // Old getTimeCoordPairs
    // Modified to try and prevent stopping mid-jump.
    // Need to just make the recordings stop at platform edges
    public List<TimeCoordinatePair> getTimeCoordPairsAtPoint(Point myPoint, int maxDistance) {
        int seekCount = 0;
        final int MAX_SEEK = 3; // limit how far behind we look

        List<TimeCoordinatePair> timePointsInList = new ArrayList<>();
        // 只取一次快照：这里每帧被调用十几次，反复进 getTimeCoordPath() 既慢也容易踩并发
        List<TimeCoordinatePair> path = getTimeCoordPath();
        for (TimeCoordinatePair pair : path) {
            for (Point pts : pair.getPoints()) {
                if (isPointNearSameY(myPoint, pts, maxDistance, 5)) {
                    if (endsInJumpingStance(pair.getStances())) {
                        // check behind for a suitable packet if it ends in jump
                        for (int x = 1; x < MAX_SEEK; x++) {
                            int seekIndex = seekCount - x;
                            // seekCount 很小时（录制开头就是跳跃 stance）会取到负下标，
                            // 上游这里直接 .get(-1) 抛 IndexOutOfBounds，被上层 catch 后
                            // 打印成「Error processing element: mX」。
                            if (seekIndex < 0) {
                                continue;
                            }
                            TimeCoordinatePair tcp = path.get(seekIndex);
                            if (!endsInJumpingStance(tcp.getStances())) {
                                timePointsInList.add(pair);
                                break;
                            }
                        }
                    } else {
                        timePointsInList.add(pair);
                    }
                }
            }
            seekCount += 1;
        }
//        for (TimeCoordinatePair tcp : timePointsInList) {
////            debugprint("tcp: ", tcp.getTimestamp(), tcp.getPoints(), tcp.getStances());
//        }
        if (timePointsInList.isEmpty()) {
            return null;
        }
        return timePointsInList;
    }


    /**
     * 找录制里离 {@code pt} 最近的坐标点。
     *
     * <p>为什么需要它：{@link #getTimeCoordPairsAtPoint(Point, int)} 用的是
     * {@code isPointNearSameY(pt, pts, 40, 5)} —— <b>y 容差只有 5px</b>。机器人被游戏
     * 传送到入口时，落点 y 经常和录制里的地面 y 差几十像素（例如 920010000 的入口在
     * y=96，录制地面在 y=143），这时所有录制段都「不包含」这个点，上层就会拿到 null
     * 顶点去跑最短路，抛 {@code Graph must contain the source vertex!}。
     * <p>这里退化成「最近点吸附」：以纵向误差为主、横向误差为辅打分，保证总能拿到一个
     * 录制里真实存在的坐标。
     *
     * @param pt 目标坐标（可以是半空中的、也可以和录制差几十像素的）
     * @return 录制里最近的坐标；录制为空时返回 {@code null}
     */
    public Point nearestPoint(Point pt) {
        if (pt == null) {
            return null;
        }
        Point best = null;
        long bestScore = Long.MAX_VALUE;
        for (TimeCoordinatePair pair : getTimeCoordPath()) {
            for (Point pts : pair.getPoints()) {
                // 权重 8：优先「同一层平台」，再比横向距离
                long score = Math.abs((long) pts.x - pt.x) + 8L * Math.abs((long) pts.y - pt.y);
                if (score < bestScore) {
                    bestScore = score;
                    best = pts;
                }
            }
        }
        return best;
    }

    public boolean arePointsWithinRecording(Point startPt, Point endPt) {
        return !getTimeCoordPairsAtPoint(startPt).isEmpty() &&
                !getTimeCoordPairsAtPoint(endPt).isEmpty();
    }

    public boolean isPointWithinRecording(Point pt) {
        return !getTimeCoordPairsAtPoint(pt).isEmpty();
    }

    public StartEndPair getStartEndTimeCoordPairs(Point startPt, Point endPt) {
        List<TimeCoordinatePair> startPairs = getTimeCoordPairsAtPoint(startPt);
        List<TimeCoordinatePair> endPairs = getTimeCoordPairsAtPoint(endPt);
//        endPairs = trimTCPairsForNonStandingStances(endPairs); //
        if (startPairs.isEmpty() && endPairs.isEmpty()) {
            debugprint("ERROR startPairs && endPairs Empty");
            return null;
        }
        if (startPairs.isEmpty() || endPairs.isEmpty()) {
            debugprint("ERROR startPairs OR endPairs Empty");
            return null;
        }

        TimeCoordinatePair latestValidStart = null;
        TimeCoordinatePair validEnd = null;

        // Iterate through all end pairs to find the valid pairs
        for (TimeCoordinatePair endPair : endPairs) {
            for (TimeCoordinatePair startPair : startPairs) {
                if (startPair.getTimestamp() <= endPair.getTimestamp()) {
                    // Update the latest valid start pair if necessary
                    if (latestValidStart == null || startPair.getTimestamp() > latestValidStart.getTimestamp()) {
                        latestValidStart = startPair;
                        validEnd = endPair; // Corresponding end pair
                    }
                }
            }
        }

        if (latestValidStart == null) {
            debugprint("ERROR no valid pair found");
            return null; // No valid pair found
        }
        return new StartEndPair(latestValidStart, validEnd);
    }

    public MovementRecordingRaw trimRecordingRaw(long startTimestamp, long endTimestamp) {
        List<MovementPacketRaw> trimmedList = new ArrayList<>();

        for (MovementPacketRaw packet : getMovementPacketList()) {
            long packetTimestamp = packet.getTimestamp();
            if (packetTimestamp >= startTimestamp && packetTimestamp <= endTimestamp) {
                trimmedList.add(packet);
            }
        }
        return new MovementRecordingRaw(getMapId(), getRecordingName(), trimmedList);
    }

    public MovementRecording trimRecording2(long startTimestamp, long endTimestamp) {
        MovementRecording mvr = getMovementRecording(getMapId(), getRecordingName());
        List<MovementPacket> trimmedList = new ArrayList<>();

        for (MovementPacket packet : mvr.getMovementPacketList()) {
            long packetTimestamp = packet.getTimestamp();
            if (packetTimestamp >= startTimestamp && packetTimestamp <= endTimestamp) {
                trimmedList.add(packet);
            }
        }
        return new MovementRecording(getMapId(), getRecordingName(), trimmedList);
    }

}
