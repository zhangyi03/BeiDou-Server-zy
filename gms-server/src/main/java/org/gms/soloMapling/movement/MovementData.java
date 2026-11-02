package org.gms.soloMapling.movement;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

/**
 * 录制移动数据（{@code movementDataPackets/map<mapId>/}）的目录解析。
 *
 * <p>上游把这个目录硬编码成相对 {@code src/main/java} 的路径，只有「在仓库根目录起服务」时才找得到。
 * 本移植版放三个候选，按顺序取第一个存在的：
 * <ol>
 *   <li>{@code -Dsolomapling.movementData=<绝对路径>} 指定的目录；</li>
 *   <li>{@code ./movementDataPackets}（工作目录就是 gms-server 时，IDEA 默认跑法）；</li>
 *   <li>{@code ./gms-server/movementDataPackets}（工作目录是仓库根时）。</li>
 * </ol>
 *
 * <p>目录结构、文件名规则与上游完全一致（{@code m1.bin / m1.csv / c1-2a.bin ...}），
 * 直接从上游仓库整目录拷过来即可。
 *
 * @author WorkBuddy ported from SoloMapling
 * @since 2026-11-03
 */
@Slf4j
public final class MovementData {

    private static final String DEFAULT_DIR_NAME = "movementDataPackets";

    private static volatile File cachedBase;

    private MovementData() {
    }

    /**
     * 录制数据的根目录（不含 map 号）。
     */
    public static File baseDir() {
        File cached = cachedBase;
        if (cached != null) {
            return cached;
        }
        synchronized (MovementData.class) {
            if (cachedBase != null) {
                return cachedBase;
            }
            File resolved = resolveBaseDir();
            cachedBase = resolved;
            log.debug("[MovementData] 录制移动数据目录：{}（存在={}）", resolved.getAbsolutePath(), resolved.isDirectory());
            return resolved;
        }
    }

    public static File mapDir(int mapId) {
        return new File(baseDir(), "map" + mapId);
    }

    /**
     * 拼成绝对路径供 {@code FileInputStream} / {@code FileReader} 直接使用。
     */
    public static String basePath() {
        String path = baseDir().getPath();
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private static File resolveBaseDir() {
        String override = System.getProperty("solomapling.movementData");
        if (override != null && !override.isBlank()) {
            return new File(override);
        }
        File cwd = new File(DEFAULT_DIR_NAME);
        if (cwd.isDirectory()) {
            return cwd;
        }
        File repoRoot = new File("gms-server/" + DEFAULT_DIR_NAME);
        if (repoRoot.isDirectory()) {
            return repoRoot;
        }
        return cwd;
    }
}
