package org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ;

import org.gms.client.Character;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 组队任务大厅的招募喊话生成器。
 *
 * <p>结构照搬 MerchantBot.MerchantBotMessageCreator：
 * {@code [等级职业] [前缀] [任务名] [可选后缀]}。
 *
 * <p>目标只是「看起来像大厅里挤着一堆人在喊组队」，不追求语义可信度 ——
 * 这些话会和真人聊天混在一起刷在天空之城招募大厅。
 *
 * <p>文案全中文：中文服聊天包走 GBK 变长字符串，没有角色名那种 13 字节定长限制，
 * 但也要控制长度，太长会被客户端截断显示。
 */
public final class OPQRecruitMessages {

    private OPQRecruitMessages() {}

    private static final List<String> PREFIXES = new ArrayList<>(Arrays.asList(
            "组队>", "求组>", "缺人>", "组队++", "来人>", "组队来人>"
    ));

    private static final List<String> PQ_NAMES = new ArrayList<>(Arrays.asList(
            "天空组队", "天空之城组队", "组队任务", "OPQ", "组队任务来人", "OPQ缺人"
    ));

    private static final List<String> FILLERS = new ArrayList<>(Arrays.asList(
            "@@@@@@@@", "！！！", "速度", "在线等"
    ));

    public static String generateRecruitMessage(Character chr) {
        Random random = new Random();

        String prefix = PREFIXES.get(random.nextInt(PREFIXES.size()));
        String pqName = PQ_NAMES.get(random.nextInt(PQ_NAMES.size()));

        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(' ');

        // 35% 概率自报等级职业（「Lv65 祭司 组队> 天空组队」这种）。
        // 职业名走 Job#getName()，由 i18n 出中文，不在这里硬编码职业表。
        if (chr != null && random.nextDouble() < 0.35) {
            try {
                sb.insert(0, "Lv" + chr.getLevel() + " " + chr.getJob().getName() + " ");
            } catch (Exception ignored) {
                // Character 上缺数据（职业未初始化等）就跳过自报家门
            }
        }

        sb.append(pqName);

        // 0~2 个后缀
        int fillerCount = random.nextInt(3);
        for (int i = 0; i < fillerCount; i++) {
            sb.append(' ').append(FILLERS.get(random.nextInt(FILLERS.size())));
        }

        return sb.toString().replaceAll("\\[", "").replaceAll("]", "");
    }
}
