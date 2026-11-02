package org.gms.soloMapling.server;

import lombok.extern.slf4j.Slf4j;

/**
 * 上游 {@code DebugUtilities.debugprint} 的替身。
 *
 * <p>上游是「写死 {@code printDebug=false} 的 System.out 调试打印」，本移植版改成
 * 走 slf4j 的 debug 级别：{@code log4j2.xml} 的 root 是 INFO，所以默认一条都不输出，
 * 要排查寻路时把 root 调成 DEBUG 即可，不用改代码。
 *
 * @author WorkBuddy ported from SoloMapling
 * @since 2026-11-03
 */
@Slf4j
public final class DebugUtil {

    private DebugUtil() {
    }

    public static void debugprint(Object... variables) {
        if (!log.isDebugEnabled() || variables == null || variables.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(variables.length * 8);
        for (int i = 0; i < variables.length; i++) {
            sb.append(variables[i]);
            if (i < variables.length - 1) {
                sb.append(", ");
            }
        }
        log.debug("[bot] {}", sb);
    }

    /**
     * 强制输出（寻路对不上时用，等价于上游的 System.out 打印）。
     */
    public static void forceprint(Object... variables) {
        if (variables == null || variables.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder(variables.length * 8);
        for (int i = 0; i < variables.length; i++) {
            sb.append(variables[i]);
            if (i < variables.length - 1) {
                sb.append(", ");
            }
        }
        log.info("[bot] {}", sb);
    }
}
