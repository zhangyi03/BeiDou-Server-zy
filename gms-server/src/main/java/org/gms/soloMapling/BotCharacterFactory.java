package org.gms.soloMapling;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.constants.string.CharsetConstants;
import org.gms.client.Job;
import org.gms.client.SkinColor;
import org.gms.client.creator.CharacterFactoryRecipe;
import org.gms.dao.entity.CharactersDO;
import org.gms.dao.mapper.CharactersMapper;
import org.gms.exception.BizException;
import org.gms.model.dto.AddAccountDTO;
import org.gms.service.AccountService;
import org.gms.util.ThreadLocalUtil;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.gms.dao.entity.table.CharactersDOTableDef.CHARACTERS_D_O;

/**
 * 机器人「建号」工厂：按真实玩家的方式落库。
 *
 * <p>与 SoloMapling 的做法（所有机器人共用 cid=2 一个基础角色、只在内存里改 id/名字）
 * 不同，这里每个机器人在 {@code accounts} + {@code characters} 里都有真实行：
 * <ul>
 *   <li>先在 {@code accounts} 建 {@code bot_<角色名>} 专用账号（密码随机、加密存储）；</li>
 *   <li>再用 {@link Character#getDefault} + {@link Character#insertNewChar} 建角色，
 *       和 {@code BeginnerCreator} 走的是同一条 insert 路径，只是不起包（无 socket）；</li>
 *   <li>返回的 id 直接来自自增主键，不需要回查。</li>
 * </ul>
 *
 * <p>机器人账号统一前缀 {@code bot_}，方便运维一眼区分、也方便日后批量清理。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotCharacterFactory {

    /**
     * 机器人账号名前缀
     */
    public static final String ACCOUNT_PREFIX = "bot_";

    /**
     * 角色名的<b>字节</b>上限，不是列宽。
     *
     * <p>{@code characters.name} 是 {@code VARCHAR(13)}（MySQL 按<b>字符</b>计），
     * 所以 7 个汉字也存得进去；但角色名在封包里是<b>定长 13 字节</b>写的
     * （{@code PacketCreator.addCharStats} / {@code addPartyStatus} 都是
     * {@code writeFixedString(getRightPaddedStr(name, '\0', 13))}），
     * 而 {@code ByteBufOutPacket.writeFixedString} 内部是
     * {@code Arrays.copyOf(bytes, 13)} —— 按字节硬砍，不管是不是半个汉字。
     *
     * <p>中文服走 GBK（{@link CharsetConstants}），一个汉字 2 字节，
     * 「天空组队机器人1」= 15 字节，砍完变成「天空组队机器」+ 半个汉字的残字节；
     * 五台机器人砍出来的前缀完全一样，队伍面板里就成了「名字全一样/全乱」。
     */
    private static final int NAME_MAX_BYTES = 13;

    /**
     * 字符数下限（1 个汉字也算 1 个字符，与客户端输入框一致）
     */
    private static final int NAME_MIN_LEN = 4;

    /**
     * 按服务端当前语言对应的字符集算名字的字节数 —— 必须和发包侧用同一套字符集，
     * 否则校验通过、发出去还是被砍。{@code getClientLang()} 在无客户端上下文（web 线程）里
     * 返回 0，{@code Language.fromLang} 会回落到服务端语言，正好是我们要的。
     */
    private static int nameByteLength(String name) {
        return name.getBytes(CharsetConstants.getCharset(ThreadLocalUtil.getClientLang())).length;
    }

    /**
     * 按字符边界截断到不超过 {@code maxBytes} 字节（删掉放不下的整个字符，绝不产生半个汉字）。
     */
    private static String truncateToBytes(String value, int maxBytes) {
        if (nameByteLength(value) <= maxBytes) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < value.length(); i++) {
            String ch = String.valueOf(value.charAt(i));
            int len = nameByteLength(ch);
            if (used + len > maxBytes) {
                break;
            }
            sb.append(ch);
            used += len;
        }
        return sb.toString();
    }

    /**
     * 生成随机角色名用的音节表（够拼出上百种组合，撞名再补随机后缀）
     */
    private static final String[] SYLLABLES = {
            "dark", "blue", "star", "moon", "fire", "ice", "wind", "rain",
            "shadow", "light", "soul", "king", "zero", "nova", "echo", "grey",
            "iron", "leaf", "mist", "arc", "fox", "lyn", "rex", "zen"
    };

    private static final int[] MALE_FACES = {20000, 20001, 20002};
    private static final int[] FEMALE_FACES = {21000, 21001, 21002};
    private static final int[] MALE_HAIRS = {30000, 30020, 30030};
    private static final int[] FEMALE_HAIRS = {31000, 31020, 31030};

    private final AccountService accountService;
    private final CharactersMapper charactersMapper;

    /**
     * 建号结果。
     *
     * @param accountId   新建/复用的机器人账号 id
     * @param characterId 新建角色 id
     * @param name        角色名
     */
    public record Created(int accountId, int characterId, String name) {
    }

    /**
     * 按真实玩家链路建一个机器人角色。
     *
     * @param requestedName 指定角色名，空则随机生成
     * @param level         目标等级（10~200），null 则按档位随机
     * @param jobId         指定职业 id，null 则按等级随机
     * @param gender        0=男 1=女，null 则随机
     * @param mapId         出生地图（同时写入 characters.map）
     */
    public Created createBotCharacter(String requestedName, Integer level, Integer jobId,
                                      Integer gender, int mapId, BotClient botClient) {
        String name = resolveName(requestedName);
        int rollGender = gender == null ? ThreadLocalRandom.current().nextInt(2) : gender;
        int rollLevel = level == null ? ThreadLocalRandom.current().nextInt(10, 81) : level;
        if (rollLevel < 1) {
            rollLevel = 1;
        }
        if (rollLevel > 200) {
            rollLevel = 200;
        }
        int rollJob = jobId == null
                ? org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem.BotDecorate.selectJobByLevel(rollLevel)
                : jobId;

        int accountId = createBotAccount(name);
        try {
            botClient.setAccID(accountId);

            Character bot = Character.getDefault(botClient);
            bot.setName(name);
            bot.setGender(rollGender);
            bot.setFace(pickFace(rollGender));
            bot.setHair(pickHair(rollGender));
            bot.setSkinColor(SkinColor.NORMAL);
            bot.setJob(Job.getById(rollJob));

            CharacterFactoryRecipe recipe = new CharacterFactoryRecipe(
                    Job.getById(rollJob), rollLevel, mapId, 0, 0, 0, 0);
            // 血量/蓝量按等级给个像样的值：真实玩家在这个等级绝不会是 50/5
            recipe.setMaxHp(50 + rollLevel * 20);
            recipe.setMaxMp(5 + rollLevel * 10);

            if (!bot.insertNewChar(recipe)) {
                throw new IllegalStateException("insertNewChar 失败（看日志里的 Error trying to insert chr）");
            }

            log.info("[BotCharacterFactory] 已建号 {}（account={}, character={}, level={}, job={}, gender={}）",
                    name, accountId, bot.getId(), rollLevel, rollJob, rollGender);
            return new Created(accountId, bot.getId(), name);
        } catch (RuntimeException e) {
            // 账号先留着不删：删账号要连带清 character，风险更大，交由运维处理
            log.error("[BotCharacterFactory] 建号失败（账号 {} 已创建，请人工确认是否需要清理）", accountId, e);
            throw e;
        }
    }

    /**
     * 建机器人专用账号，返回账号 id。
     */
    private int createBotAccount(String characterName) {
        String accountName = ACCOUNT_PREFIX + characterName.toLowerCase();
        if (accountName.length() > 34) {
            accountName = accountName.substring(0, 34);
        }
        AddAccountDTO dto = new AddAccountDTO();
        dto.setName(accountName);
        dto.setPassword(randomPassword());
        dto.setBirthday(Date.valueOf(LocalDate.of(1990, 1, 1)));
        dto.setLanguage(3);
        try {
            accountService.addAccount(dto);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("机器人账号密码加密失败", e);
        }
        var account = accountService.findByName(accountName);
        if (account == null) {
            throw new IllegalStateException("机器人账号 " + accountName + " 创建后回查不到");
        }
        return account.getId();
    }

    /**
     * 角色名：指定就校验，没指定就随机拼一个不撞库的。
     */
    private String resolveName(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (!name.isEmpty()) {
            if (name.length() < NAME_MIN_LEN) {
                throw BizException.illegalArgument("角色名至少 " + NAME_MIN_LEN + " 个字符");
            }
            int bytes = nameByteLength(name);
            if (bytes > NAME_MAX_BYTES) {
                throw BizException.illegalArgument("角色名最多 " + NAME_MAX_BYTES
                        + " 字节（中文约 6 个字），「" + name + "」当前 " + bytes + " 字节，"
                        + "否则队伍/好友面板里的名字会被截断成乱码");
            }
            if (isNameTaken(name)) {
                throw BizException.illegalArgument("角色名 " + name + " 已被占用");
            }
            return name;
        }
        for (int i = 0; i < 50; i++) {
            String candidate = randomIgn();
            if (!isNameTaken(candidate)) {
                return candidate;
            }
        }
        throw BizException.illegalArgument("随机角色名连续撞名 50 次，请手动指定名字");
    }

    private boolean isNameTaken(String name) {
        return charactersMapper.selectCountByQuery(
                QueryWrapper.create().where(CHARACTERS_D_O.NAME.eq(name))) > 0;
    }

    private String randomIgn() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String a = SYLLABLES[rnd.nextInt(SYLLABLES.length)];
        String b = SYLLABLES[rnd.nextInt(SYLLABLES.length)];
        String s = capitalize(a) + capitalize(b);
        // 音节表全是 ASCII，按字节截断和按字符截断等价；统一走字节是为了只有一处口径
        s = truncateToBytes(s, NAME_MAX_BYTES);
        if (s.length() < NAME_MIN_LEN) {
            s = s + rnd.nextInt(100);
        }
        return s;
    }

    private static String capitalize(String s) {
        return java.lang.Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int pickFace(int gender) {
        int[] pool = gender == 1 ? FEMALE_FACES : MALE_FACES;
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private static int pickHair(int gender) {
        int[] pool = gender == 1 ? FEMALE_HAIRS : MALE_HAIRS;
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 删除机器人角色行（只用于 web 端「删除机器人」，账号保留以免误删绑定关系）。
     */
    public void deleteCharacter(int characterId) {
        CharactersDO row = charactersMapper.selectOneById(characterId);
        if (row != null) {
            charactersMapper.delete(row);
        }
    }
}
