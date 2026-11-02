package org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ;

import java.awt.*;
import java.util.List;

/**
 * Map / item / platform constants for the Orbis Party Quest rush bot system.
 *
 * NOTE: Many of these IDs are placeholders taken from the OPQ spec document and
 * the common GMS v83 data set. They MUST be verified against this server's WZ
 * export before relying on them in live runs.
 * that are most likely to drift.
 */
public final class OPQConstants {

    private OPQConstants() {}

    // ---- Map IDs ------------------------------------------------------------
    public static final int OPQ_LOBBY      = 200080101; // recruitment lobby (Orbis Ticketing Booth area)
    public static final int OPQ_STAGE_1    = 920010000; // Stage 1 Cloud Collection Entrance - GOOD
    public static final int OPQ_TOWER      = 920010100; // intermediate tower hub
    public static final int OPQ_STAGE_2    = 920010400; // music record stage
    public static final int OPQ_EXIT_LOBBY = 920011200; // Exit Lobby On the Way Out - GOOD

    // Convenience grouping
    public static final List<Integer> OPQ_INSIDE_MAPS = List.of(
            OPQ_STAGE_1, OPQ_TOWER, OPQ_STAGE_2, OPQ_EXIT_LOBBY
    );

    // ---- 招募大厅站桩 -------------------------------------------------------
    // 机器人进大厅后不巡逻、不闲逛，就站在 web 端「机器人列表」里给每台配的
    // spawn_x / spawn_y 上；只有被撞开、被传送带偏时才走回原位。
    // 档案里坐标是 (0,0)（没配）时落到这个兜底点，也就是原来硬编码的那个位置。
    public static final Point OPQ_LOBBY_FALLBACK_POS = new Point(-233, 174);

    /**
     * 站桩横向容差：偏离站位不超过这个距离就算「站稳了」，不发任何移动包。
     *
     * <p>只看 x，<b>不看 y</b>：出生和传送会把坐标吸到当前平台的地面上，档案里填的 y
     * 与真实落脚点天然有几十像素误差；再拿 y 去比较、又允许跳，会让机器人在原地无限起跳。
     * 纵向一律沿用当前脚下的 y。
     */
    public static final int LOBBY_STATION_TOL_X_PX = 40;

    // ---- NPC IDs -----------------------------------------------------------
    public static final int CHAMBERLAIN_EAK = 2013001; // Stage 1 Cloud NPC - Click on him to teleport to tower

    // ---- Item IDs -----------------------------------------------------------
    public static final int CLOUD_PIECE    = 4001063; // Stage 1 Cloud Pieces - GOOD

    // Stage 2: 7 distinct LP record items dropped by boxes
    public static final int RECORD_LP_FIRST = 4001056;
    public static final int RECORD_LP_LAST  = 4001062;
    public static final List<Integer> STAGE_2_ITEMS = List.of(
            4001056, 4001057, 4001058, 4001059, 4001060, 4001061, 4001062
    );

    public static final List<Integer> STAGE_1_ITEMS = List.of(CLOUD_PIECE);

    // ---- Stage 1 reactor data ids -------------------------------------------
    // Reactor data (template) ids on map 920010000, sourced from
    // wz/Map.wz/Map/Map9/920010000.img.xml:
    //   2006000 -> "eak" altar reactor where cloud pieces are dropped (skip).
    //   2002001 -> the 20 breakable cloud reactors (cloud1..cloud20).
    // Note: the 1000000003 / 1000000004-23 values seen in the !reactor list
    // command are runtime object ids (oid), which are reassigned per map
    // instance — never use those for filtering. Filter by data id instead.
    public static final int STAGE_1_ALTAR_REACTOR_ID = 2006000;
    public static final int STAGE_1_CLOUD_REACTOR_ID = 2002001;

    // ---- Stage 2 reactor data ids -------------------------------------------
    // 7 breakable box reactors on map 920010400 (one per platform m3–m9).
    // Data IDs confirmed from runtime dump: 2002004 through 2002010.
    public static final int STAGE_2_BOX_REACTOR_FIRST = 2002004;
    public static final int STAGE_2_BOX_REACTOR_LAST  = 2002010;
    // Central music box reactor (dataId=2008006, pos ~x=-1706,y=-240).
    public static final int STAGE_2_MUSIC_BOX_REACTOR_ID = 2008006;

    // 喊话里用的箱子序号：index 0 = reactor 1000000005，index 6 = reactor 1000000011
    // 只用于聊天/日志展示，不带空格，配合「我去开 2号箱子！」这类句式
    public static final String[] STAGE_2_BOX_ORDINALS = {
            "1号", "2号", "3号", "4号", "5号", "6号", "7号"
    };

    // ---- Stage 1 top-platform (post-handin) ---------------------------------
    // After the leader hands all cloud pieces to the NPC, they get teleported
    // to a small platform near the top of the map. Bots watch the leader's
    // position and treat stage 1 as complete the moment they observe the
    // leader inside the tolerance box around this anchor.
    //
    // MapleStory uses screen-style coords (down is +y) so the top of a map
    // lives at very negative y values — the sign on Y here is correct.
    public static final Point STAGE_1_ENTRY_TP = new Point(266, 143);
    public static final Point STAGE_1_COMPLETE_TP = new Point(165, -1270);
    public static final int STAGE_1_TOP_PLATFORM_X        =   165;
    public static final int STAGE_1_TOP_PLATFORM_Y        = -1270;
    public static final int STAGE_1_TOP_PLATFORM_TOL_X_PX =   300;
    public static final int STAGE_1_TOP_PLATFORM_TOL_Y_PX =   150;

    // ---- Stage 1 loot scan tuning -------------------------------------------
    // 反应堆碎掉后云片可能穿过几层地板掉到下面：先在自己脚下扫，扫不到就沿云朵的 x
    // 往下按固定步长探测，命中即停（与上游 SoloMapling 完全一致）。
    //
    // 半径只有 150px，不能放大：云片的落点由 MapleMap.calcDropPos 吸附到云朵正下方的
    // 地面，机器人也正好站在那一层，横向几十像素内就是自己那一片。放大到上千 px 就会
    // 把隔壁机器人刚打碎的、隔着一两个平台的云片也吸过来（5 台同时打，等于抢别人战果，
    // 交件数量也会虚高）。
    public static final int    STAGE_1_LOOT_SCAN_RANGE_PX    =  150;
    // 往下探测的步长要小于「两倍半径」，否则相邻两次探测之间会漏掉平台：
    // 步长 120、半径 150，覆盖是连续的；30 步 = 往下 3600px，够覆盖整座塔。
    public static final int    STAGE_1_LOOT_FALLBACK_STEPS   =   30;
    public static final int    STAGE_1_LOOT_FALLBACK_STEP_PX =  120;

    // ---- Platform / coordinate targets --------------------------------------
    // Stage 1 is reactor-driven, not platform-driven: bots scan the map for
    // cloud reactors and walk to the reactor's coordinate directly. The
    // orchestrator assigns each bot a unique reactor oid (closest-unclaimed).

    // Stage 1 交付点 —— 上游原版坐标，祭坛右侧的地面。
    //
    // 分工照上游：机器人只把云片整叠丢在这个交付点，由真人玩家自己捡起来送去祭坛。
    // 祭坛（反应堆 2006000）在 (377,66)，判定框 x∈[277,477]、y∈[-34,166]，只认「一叠恰好 20 个」——
    // 那是真人要处理的事，机器人不代交、不凑数（多台各丢一点永远凑不出一整叠）。
    public static final Point STAGE_1_DROP_POINT      = new Point(497, 143);
    /** 判定「已经站到交付点了」的容差（px）。 */
    public static final int   STAGE_1_DROP_TOL_PX     =   80;
    /** 往回走的补走次数上限，超了就传送到交付点，避免卡死在 RETURN。 */
    public static final int   STAGE_1_RETURN_MAX_TRIES =   6;

    // Stage 2: boxes are on platforms m3–m9 (m1/m2 are base floor and entry).
    public static final List<String> STAGE_2_BOX_PLATFORMS = List.of(
            "m3", "m4", "m5", "m6", "m7", "m8", "m9"
    );

    // Stage 2 drop zone — 音乐盒正下方的地面。
    //
    // 反应堆 2008006（music box）在 920010400 里位于 (-1706, -240)，Reactor.wz 给的判定框是
    // lt=(-52,-64) / rb=(40,79)，换算成世界坐标就是 x∈[-1758,-1666]、y∈[-304,-161]。
    // MapleMap.activateItemReactors 只认 react.getArea().contains(dropPos)，而 dropPos 会被
    // calcDropPos 往下吸到地板（-1706 这一列的地板是 y=-172 的那道台），所以只有站在
    // x∈[-1758,-1666] 这条带子上丢，唱片才落进判定框、第二阶段才会推进。
    //
    // 上游写的 (-1588,-127) 在判定框右侧 78px 之外，照抄过来唱片永远触发不了音乐盒。
    public static final Point STAGE_2_DROP_POINT       = new Point(-1706, -127);
    /** 判定「已经站到音乐盒下面了」的容差（px）。m1 录制里最近的落点是 x=-1694，差 12px。 */
    public static final int   STAGE_2_DROP_TOL_PX      =  100;
    /** 往回走的补走次数上限，超了就直接在音乐盒坐标交件，避免卡死在 RETURN。 */
    public static final int   STAGE_2_RETURN_MAX_TRIES =    6;

    public static final String STAGE_2_DROP_PLATFORM = "m1";

    // ---- Tuning -------------------------------------------------------------
    /**
     * 组队任务机器人的 tick 周期。
     *
     * <p>默认的 500ms 对「摆摊」够用，但跑图时每 500ms 才发一条移动包，客户端只能看到
     * 一顿一顿的跳跃。这里降到 250ms，配合 {@code BotNavigator.WALK_STEP_PX = 55}
     * 与 240ms 的步行动画时长，走出来是连续的。
     */
    public static final long TICK_PERIOD_MS               =  250;
    public static final long STAGE_WAIT_TIMEOUT_MS        = 120_000; // 2 min per stage wait
    public static final long RECRUIT_MESSAGE_INTERVAL_MS  =  12_000; // chat every ~12s in lobby
    public static final long NAVIGATE_SETTLE_MS           =   1_500; // pause after arriving at platform
    public static final long ASSIGNMENT_POLL_MS           =     500; // how often the bot re-checks a null assignment
    public static final int  MAX_REACTOR_HITS             =       4; // OPQ cloud reactors break after 4 hits
    public static final long SWING_INTERVAL_MS            =     700; // delay between bot swings (~typical 1H attack speed)
    public static final int  REACTOR_HIT_RANGE_PX         =      200; // bot must be within this many px of reactor.x to hit

    /**
     * 看到队长进第一阶段后，等多久再跟进去。
     *
     * <p>原来这里是 {@code blockingSleep(3000)}：整台机器人卡 3 秒不动，5 台同时上线就像集体呆立。
     * 改成跨帧计时，同样的 3 秒等待，但机器人的帧照跑（不会互相拖累），也不会漏掉队长换图。
     */
    public static final long STAGE_1_ENTRY_DELAY_MS       =   3_000;

}
