package org.gms.soloMapling.movement.NavigationSystem;

import org.gms.client.Character;
import org.gms.server.maps.MapleMap;
import org.gms.soloMapling.movement.MovementStructures.MovementRecording;
import org.gms.soloMapling.movement.MovementStructures.MovementRecordingRaw;
import org.gms.soloMapling.movement.MovementStructures.StartEndPair;
import org.gms.soloMapling.movement.MovementStructures.TimeCoordinatePair;
import org.gms.soloMapling.ArtificialPlayer.BotHelpers;

import org.gms.soloMapling.ArtificialPlayer.BotLogic;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.gms.soloMapling.movement.InPacketReader.getMovementRecordingRaw;
import static org.gms.soloMapling.movement.MovementCommands.executeMovement;
import static org.gms.soloMapling.movement.NavigationSystem.NavigationElement.parseNavigationType;
import static org.gms.soloMapling.server.DebugUtil.debugprint;


public class PathFinder {


    private final Character character;
    private final MapleMap map;
    private final Point endPt;

    private static final Random random = new Random();
    private static final Map<PathType, Double> WEIGHTS = new EnumMap<>(PathType.class);


    public PathFinder(Character fakechar, Point endPt) {
        this.character = fakechar;
        this.map = fakechar.getMap();
        this.endPt = endPt;
    }

    public static List<TimeCoordinatePair> coordPathBuilder(Character fakechar, Point endPt, PathType pathType) {
        int mapId = fakechar.getMapId();
        Point start = fakechar.getPosition();
        PathFinder pf = new PathFinder(fakechar, endPt);
        List<String> path = createPath(mapId, start, endPt, pathType);
        List<NavigationElement> elements = getNavElements(mapId, path);
        List<TimeCoordinatePair> tcp = pf.buildPointPath(mapId, elements);
        return tcp;
    }

    public static List<TimeCoordinatePair> coordPathBuilder(Character fakechar, Point endPt) {
//        int mapId = fakechar.getMapId();
//        Point start = fakechar.getPosition();
//        PathFinder pf = new PathFinder(fakechar, endPt);
//        List<String> path = createPath(mapId, start, endPt, getRandomPathType());
//        List<NavigationElement> elements = getNavElements(mapId, path);
//        List<TimeCoordinatePair> tcp = pf.buildPointPath(mapId, elements);
//        return tcp;
        return coordPathBuilder(fakechar, endPt, getRandomPathType());
    }

    public static List<TimeCoordinatePair> coordPathSplitter(List<TimeCoordinatePair> tcp, double percentageInterval) {
        // Validate inputs
        if (tcp == null || tcp.size() < 2) {
            return tcp;
        }
        if (percentageInterval <= 0 || percentageInterval >= 100) {
            throw new IllegalArgumentException("Percentage interval must be between 0 and 100");
        }

        List<TimeCoordinatePair> result = new ArrayList<>();
        int totalPoints = tcp.size();

        // Calculate number of intervals
        int numIntervals = (int) Math.ceil(100 / percentageInterval);

        // Add points at each interval
        for (int i = 1; i <= numIntervals; i++) {  // Start from 1 to skip 0%
            double percentage = i * percentageInterval / 100.0;

            // If percentage exceeds 100%, use the last point
            int index = percentage >= 1.0 ?
                    totalPoints - 1 :
                    (int) Math.round((totalPoints - 1) * percentage);

            // Ensure index is within bounds and avoid duplicates
            if (index > 0 && index < totalPoints &&
                    (result.isEmpty() || !result.get(result.size() - 1).equals(tcp.get(index)))) {
                result.add(tcp.get(index));
            }
        }

        // Ensure the last point is included if it wasn't added by the intervals
        if (result.isEmpty() || !result.get(result.size() - 1).equals(tcp.get(totalPoints - 1))) {
            result.add(tcp.get(totalPoints - 1));
        }

        return result;
    }

    public enum PathType {
        SHORTEST,
        RANDOM
    }

    static {
        // Define weights (probabilities should sum to 1.0)
        WEIGHTS.put(PathType.SHORTEST, 0.65);
        WEIGHTS.put(PathType.RANDOM, 0.35);
        // Add more types with their weights as needed:
        // WEIGHTS.put(PathType.SCENIC, 0.2);
        // WEIGHTS.put(PathType.SAFEST, 0.1);
    }

    public static PathType getRandomPathType() {
        double value = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<PathType, Double> entry : WEIGHTS.entrySet()) {
            cumulative += entry.getValue();
            if (value < cumulative) {
                return entry.getKey();
            }
        }

        // Fallback to first enum value in case of rounding errors
        return PathType.values()[0];
    }

    public static List<String> getRandomPath(MapGraph gr, String startNode, String endNode) {
        return MapGraph.getPathChain(gr.getRandomPath(startNode, endNode));
    }

    public static List<String> getShortestPath(MapGraph gr, String startNode, String endNode) {
        return MapGraph.getPathChain(gr.getShortestPath(startNode, endNode));
    }

    public static List<String> createPath(int mapId, Point startPoint, Point endPoint, PathType pathType) {
        MapGraph gr = new MapGraph(mapId);

        // 关键：getMainAreaOfPoint 用的是「y 容差 5px」的精确匹配。机器人被传送到入口、
        // 或者被别的脚本挪过位置时，y 和录制地面能差几十像素（920010000 入口 y=96 vs
        // 录制地面 y=143），于是「落在哪段录制里」直接返回空 → 拿到 null 顶点 →
        // Dijkstra 抛 IllegalArgumentException: Graph must contain the source vertex!
        // 这里先把起终点吸附到最近的真实录制点上。
        Point start = snapIfNotInside(gr, mapId, startPoint);
        Point end = snapIfNotInside(gr, mapId, endPoint);
        String mainArear = pickArea(gr, start);
        String endArea = pickArea(gr, end);
        if (mainArear == null || endArea == null) {
            debugprint("createPath: 找不到起点/终点所在的录制段，本帧放弃。start=", start, "end=", end);
            return new ArrayList<>();
        }

        List<String> path;
        switch (pathType) {
            case SHORTEST:
                path = getShortestPath(gr, mainArear, endArea);
                break;
            case RANDOM:
                path = getRandomPath(gr, mainArear, endArea);
                break;
            default:
                throw new IllegalArgumentException("Invalid path type: " + pathType);
        }
        // getShortestPath/getRandomPath 在「图里没有这条路径」时会返回 null，
        // 上层是 for-each 消费的，这里统一收敛成空列表 = 这一帧不走。
        return path == null ? new ArrayList<>() : path;
    }

    /**
     * 点已经在某段录制里就原样返回；否则按放宽的 y 容差吸附到最近录制点。
     */
    private static Point snapIfNotInside(MapGraph gr, int mapId, Point pt) {
        if (pt == null) {
            return null;
        }
        if (!gr.getMainAreaOfPoint(pt).isEmpty()) {
            return pt;
        }
        // 逐级放宽：先试「贴地」的 40，再放到 120 / 400 / 1200（越界说明数据缺失）
        int[] tolerances = {40, 120, 400, 1200};
        for (int tol : tolerances) {
            Point snapped = snapToGround(gr, mapId, pt, tol);
            if (snapped != null) {
                debugprint("createPath: 吸附坐标 ", pt, " -> ", snapped, " (|dy|<=", tol, ")");
                return snapped;
            }
        }
        return null;
    }

    private static String pickArea(MapGraph gr, Point pt) {
        if (pt == null) {
            return null;
        }
        List<String> areas = gr.getMainAreaOfPoint(pt);
        if (areas.isEmpty()) {
            return null;
        }
        return BotHelpers.randomElement(areas);
    }

//    public static void executePath(int mapId, List<NavigationElement> elements) {
//        for (int i = 0; i < elements.size(); i++) {
//            debugprint("Element index: " + i);
//            if (elements.get(i) instanceof MainArea) {
//                if (i == elements.size() - 1) {
//                    // if last, walk to end point
//                    MainArea mainArea = (MainArea) elements.get(i);
//                    MovementRecording finalPath = mainArea.getMovementRecording();
//                    // executeMovement(fakechar, finalPath); // todo
//                } else if (elements.get(i + 1) instanceof Connector) {
//                    MainArea mainArea = (MainArea) elements.get(i);
//                    String recName = mainArea.getRecordingName(); //
//
//                    Connector connector = (Connector) elements.get(i + 1);
//                    Point endpt = connector.getStartPosition();
////                    Point currPos = start; // char's current pos
//                    Point currPos = mainArea.getMovementRecordingRaw().getStartPoint(); // test only
//
//                    MovementRecording finalPath = getPathBetweenTwoPointsInMainArea(mapId, recName, currPos, endpt);
////                    executeMovement(fakechar, finalPath); // todo
//                }
//            } else if (elements.get(i) instanceof Connector) {
//                Connector connector = (Connector) elements.get(i);
//                MovementRecording finalPath = connector.getMovementRecording();
////                executeMovement(fakechar, finalPath); // todo
//            }
//        }
//
//    }

    public List<TimeCoordinatePair> buildPointPath(int mapId, List<NavigationElement> elements) {
        if (elements == null || elements.isEmpty()) {
            debugprint("No navigation elements provided");
            return null;
        }

        List<TimeCoordinatePair> tcp = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            NavigationElement currentElement = elements.get(i);
            try {
                if (currentElement instanceof MainArea) {
                    tcp.addAll(currentElement.getMovementRecordingRaw().getTimeCoordPath());
                }
            } catch (Exception e) {
                debugprint(e);
                debugprint("Failed to execute navigation element at index current: " + currentElement.getRecordingName());
            }
        }
        return tcp;

    }

    public void executePath(int mapId, List<NavigationElement> elements) {
        if (elements == null || elements.isEmpty()) {
//            debugprint("No navigation elements provided");
            return;
        }

        for (int i = 0; i < elements.size(); i++) {
            NavigationElement currentElement = elements.get(i);
            NavigationElement nextElement = (i < elements.size() - 1) ? elements.get(i + 1) : null;

            try {
                boolean interrupted = executeElement(mapId, currentElement, nextElement);
                if (interrupted) {
                    break;
                }
            } catch (Exception e) {
                debugprint(e);
                debugprint("Failed to execute navigation element at index current: " + currentElement.getRecordingName());
            }
        }
    }

    private boolean executeElement(int mapId, NavigationElement currentElement, NavigationElement nextElement) {
        if (currentElement instanceof MainArea) {
            return executeMainArea(mapId, (MainArea) currentElement, nextElement);
        } else if (currentElement instanceof Connector) {
            return executeConnector((Connector) currentElement);
        } else {
            debugprint("Unknown navigation element type: {}");
            return false;
        }
    }

    private boolean executeMainArea(int mapId, MainArea mainArea, NavigationElement nextElement) {
        MovementRecording finalPath;

        if (nextElement == null) {
            finalPath = getPathBetweenTwoPointsInMainArea(
                    mapId,
                    mainArea.getRecordingName(),
                    this.character.getPosition(),
                    this.endPt
            );


        } else if (nextElement instanceof Connector) {
            // Calculate path to connector
            Connector connector = (Connector) nextElement;
            Point startPoint = this.character.getPosition();
            Point endPoint = connector.getStartPosition();

            finalPath = getPathBetweenTwoPointsInMainArea(
                    mapId,
                    mainArea.getRecordingName(),
                    startPoint,
                    endPoint
            );
        } else {
            debugprint("Unexpected element type after MainArea: {}");
            return false;
        }

        if (finalPath == null) {
            // 起终点吸附后仍然取不到这层的一段录制：本帧不走，下一帧再试
            debugprint("executeMainArea: 取不到 ", mainArea.getRecordingName(), " 的子路径，跳过");
            return false;
        }
        return executeMovement(this.character, finalPath);
    }

    private boolean executeConnector(Connector connector) {
        MovementRecording finalPath = connector.getMovementRecording();
        if (finalPath == null) {
            return false;
        }
        return executeMovement(this.character, finalPath);
    }

    public static List<NavigationElement> getNavElements(int mapId, List<String> path) {
        List<NavigationElement> elements = new ArrayList<>();
        for (String ele : path) {
            elements.add(parseNavigationType(mapId, ele));
        }
        return elements;
    }

    public static void main(String[] args) {
//        PathFinder pf = new PathFinder();
        int mapId = 100040000; // char
        Point start = new Point(-1272, 1993); // char curr position

        Point end = new Point(352, 456);

        List<String> path = createPath(mapId, start, end, PathType.RANDOM);

        List<NavigationElement> elements = getNavElements(mapId, path);

//        executePath(mapId, elements);
    }

    // todo move out
    public static MovementRecording getPathBetweenTwoPointsInMainArea(int mapId, String recName, Point start, Point end) {
        MovementRecordingRaw mvRaw = getMovementRecordingRaw(mapId, recName);
        if (mvRaw == null || start == null || end == null) {
            return null;
        }
        StartEndPair startEndPair = mvRaw.getStartEndTimeCoordPairs(start, end);
        if (startEndPair == null) {
            // getTimeCoordPairsAtPoint 的 y 容差只有 5px，机器人被传到入口/被撞下平台后
            // 会和录制地面差几十像素 → 取不到子段。先吸附到这段录制里最近的点再取一次。
            Point snappedStart = mvRaw.nearestPoint(start);
            Point snappedEnd = mvRaw.nearestPoint(end);
            if (snappedStart == null || snappedEnd == null) {
                return null;
            }
            debugprint("getPathBetweenTwoPointsInMainArea: ", recName, " 吸附 ",
                    start, "->", snappedStart, " / ", end, "->", snappedEnd);
            startEndPair = mvRaw.getStartEndTimeCoordPairs(snappedStart, snappedEnd);
            if (startEndPair == null) {
                return null;
            }
        }
        return mvRaw.trimRecording2(startEndPair.getStartTimestamp(), startEndPair.getEndTimestamp());
    }

    // ----- Aerial pathing (OPQ cloud reactors etc.) -----
    // Targets sitting "in the air" above platforms have a Y that doesn't match any
    // recorded ground path point. We project the airborne target onto the closest
    // recorded ground point (smallest |dx| within a Y tolerance), then delegate to
    // the normal ground-based path builder. The bot walks under the target; whatever
    // vertical interaction the caller needs (jump/attack) is handled separately.

    public static final int DEFAULT_AERIAL_Y_TOLERANCE = 100;

    public static class AerialPathResult {
        private final Point snappedPoint;
        private final List<TimeCoordinatePair> groundedPath;
        private final int yDelta;

        public AerialPathResult(Point snappedPoint, List<TimeCoordinatePair> groundedPath, int yDelta) {
            this.snappedPoint = snappedPoint;
            this.groundedPath = groundedPath;
            this.yDelta = yDelta;
        }

        public Point getSnappedPoint() { return snappedPoint; }
        public List<TimeCoordinatePair> getGroundedPath() { return groundedPath; }
        public int getYDelta() { return yDelta; }
    }

    public static AerialPathResult coordPathBuilderAerial(Character fakechar, Point airTarget) {
        return coordPathBuilderAerial(fakechar, airTarget, getRandomPathType(), DEFAULT_AERIAL_Y_TOLERANCE);
    }

    public static AerialPathResult coordPathBuilderAerial(Character fakechar, Point airTarget, PathType pathType) {
        return coordPathBuilderAerial(fakechar, airTarget, pathType, DEFAULT_AERIAL_Y_TOLERANCE);
    }

    public static AerialPathResult coordPathBuilderAerial(Character fakechar, Point airTarget, PathType pathType, int yTolerance) {
        int mapId = fakechar.getMapId();
        Point snapped = snapToGround(mapId, airTarget, yTolerance);
        if (snapped == null) {
            // 有些云朵/箱子悬得比默认容差更高（实测 OPQ 塔里最高差 150+px），
            // 逐级放宽再试，不然会直接返回 null、机器人一帧都不动。
            for (int tol : new int[]{yTolerance * 2, yTolerance * 4, yTolerance * 8}) {
                snapped = snapToGround(mapId, airTarget, tol);
                if (snapped != null) {
                    debugprint("coordPathBuilderAerial: 放宽 y 容差到 ", tol, " 才吸附到 ", snapped);
                    break;
                }
            }
        }
        if (snapped == null) {
            debugprint("coordPathBuilderAerial: no ground point within yTolerance=" + yTolerance + " for target " + airTarget);
            return null;
        }
        List<TimeCoordinatePair> tcp = coordPathBuilder(fakechar, snapped, pathType);
        return new AerialPathResult(snapped, tcp, airTarget.y - snapped.y);
    }

    public static Point snapToGround(int mapId, Point airTarget) {
        return snapToGround(mapId, airTarget, DEFAULT_AERIAL_Y_TOLERANCE);
    }

    // Scans every MainArea recording and returns the recorded Point with the
    // smallest horizontal distance to airTarget, gated by |dy| <= yTolerance.
    // Math.abs handles MapleStory's negative-coordinate maps without special casing.
    public static Point snapToGround(int mapId, Point airTarget, int yTolerance) {
        return snapToGround(new MapGraph(mapId), mapId, airTarget, yTolerance);
    }

    /**
     * 同上，但复用调用方已经建好的 {@link MapGraph}，避免每帧重复扫目录。
     */
    private static Point snapToGround(MapGraph gr, int mapId, Point airTarget, int yTolerance) {
        Point best = null;
        int bestDx = Integer.MAX_VALUE;

        for (String area : gr.getMainAreas()) {
            try {
                MovementRecordingRaw raw = getMovementRecordingRaw(mapId, area);
                if (raw == null) continue;
                for (TimeCoordinatePair pair : raw.getTimeCoordPath()) {
                    for (Point candidate : pair.getPoints()) {
                        int dy = Math.abs(airTarget.y - candidate.y);
                        if (dy > yTolerance) continue;
                        int dx = Math.abs(airTarget.x - candidate.x);
                        if (dx < bestDx) {
                            bestDx = dx;
                            best = candidate;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("snapToGround: error reading area " + area + ": " + e);
            }
        }
        return best;
    }

}

