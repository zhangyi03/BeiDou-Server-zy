/*
 This file is part of the HeavenMS MapleStory Server
 Copyleft (L) 2016 - 2019 RonanLana

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.scripting.event.scheduler;

import org.gms.config.GameConfig;
import org.gms.net.server.Server;
import org.gms.server.ThreadManager;
import org.gms.server.TimerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Ronan
 */
public class EventScriptScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventScriptScheduler.class);

    private boolean disposed = false;
    private int idleProcs = 0;
    private final Map<Runnable, Long> registeredEntries = new HashMap<>();

    /**
     * 排期代次：每次 {@link #clearEntries()} 自增，用于作废"在途"的旧登记。
     * <p>
     * registerEntry 的写入是异步投递到 ThreadManager 的，若不做代次校验，
     * 一个正在执行的排期任务可能在清空之后才把自己的下一拍登记进来，
     * 形成再也取消不掉的"孤儿"条目 —— 同一事件就会并行跑多条定时链。
     */
    private long generation = 0;

    private ScheduledFuture<?> schedulerTask = null;
    private final Lock schedulerLock = new ReentrantLock(true);

    private void runBaseSchedule() {
        List<Runnable> toRemove;
        Map<Runnable, Long> registeredEntriesCopy;

        schedulerLock.lock();
        try {
            if (registeredEntries.isEmpty()) {
                idleProcs++;

                if (idleProcs >= GameConfig.getServerInt("mob_status_monitor_idle")) {
                    if (schedulerTask != null) {
                        schedulerTask.cancel(false);
                        schedulerTask = null;
                    }
                }

                return;
            }

            idleProcs = 0;
            registeredEntriesCopy = new HashMap<>(registeredEntries);
        } finally {
            schedulerLock.unlock();
        }

        long timeNow = Server.getInstance().getCurrentTime();
        toRemove = new LinkedList<>();
        for (Entry<Runnable, Long> rmd : registeredEntriesCopy.entrySet()) {
            if (rmd.getValue() < timeNow) {
                Runnable r = rmd.getKey();

                try {
                    r.run();  // runs the scheduled action
                } catch (Exception e) {
                    log.error("Exception occurred while running scheduled event task", e);
                }
                toRemove.add(r);
            }
        }

        if (!toRemove.isEmpty()) {
            schedulerLock.lock();
            try {
                for (Runnable r : toRemove) {
                    registeredEntries.remove(r);
                }
            } finally {
                schedulerLock.unlock();
            }
        }
    }

    public long registerEntry(final Runnable scheduledAction, final long duration) {
        long nextScheduledTime = Server.getInstance().getCurrentTime() + duration;
        long entryGeneration = generation;
        ThreadManager.getInstance().newTask(() -> {
            schedulerLock.lock();
            try {
                idleProcs = 0;
                if (schedulerTask == null) {
                    if (disposed) {
                        return;
                    }

                    schedulerTask = TimerManager.getInstance().register(this::runBaseSchedule, GameConfig.getServerLong("mob_status_monitor_proc"), GameConfig.getServerLong("mob_status_monitor_proc"));
                }

                if (entryGeneration != generation) {
                    // 登记期间发生过重建排期，这次登记属于已被清掉的旧链，直接丢弃
                    return;
                }

                registeredEntries.put(scheduledAction, nextScheduledTime);
            } finally {
                schedulerLock.unlock();
            }
        });
        return nextScheduledTime;
    }

    public void cancelEntry(final Runnable scheduledAction) {

        ThreadManager.getInstance().newTask(() -> {
            schedulerLock.lock();
            try {
                registeredEntries.remove(scheduledAction);
            } finally {
                schedulerLock.unlock();
            }
        });
    }

    /**
     * 同步清空当前全部待执行条目，并作废所有在途登记。
     * <p>
     * 与 {@link #cancelEntry(Runnable)} 的区别：后者只能取消调用方明确持有的那一个任务。
     * 重建排期必须用本方法，否则历史上产生过的孤儿条目会残留下来，
     * 表现为"改了刷新间隔却仍按旧节奏刷"。
     * <p>
     * 本方法同步执行（不投递 ThreadManager）：重建排期的调用方需要在它返回后
     * 立即登记新排期，异步执行会导致"清空"晚于"登记"，把刚排好的任务一起清掉。
     */
    public void clearEntries() {
        schedulerLock.lock();
        try {
            generation++;
            registeredEntries.clear();
            idleProcs = 0;
        } finally {
            schedulerLock.unlock();
        }
    }

    /**
     * 当前待执行条目数。仅用于诊断（判断是否存在孤儿排期），
     * 由于登记是异步的，瞬时值可能滞后。
     */
    public int pendingCount() {
        schedulerLock.lock();
        try {
            return registeredEntries.size();
        } finally {
            schedulerLock.unlock();
        }
    }

    public void dispose() {

        ThreadManager.getInstance().newTask(() -> {
            schedulerLock.lock();
            try {
                if (schedulerTask != null) {
                    schedulerTask.cancel(false);
                    schedulerTask = null;
                }

                registeredEntries.clear();
                disposed = true;
            } finally {
                schedulerLock.unlock();
            }
        });
    }
}
