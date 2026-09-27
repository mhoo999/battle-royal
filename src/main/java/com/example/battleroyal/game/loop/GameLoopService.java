package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.GameConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.LockSupport;

/**
 * The single thread that advances the whole world, 20 times a second.
 *
 * <p>All room mutation happens here. Nothing else may write to a {@link Room}, which
 * is what lets the simulation run without a single lock. Inbound work arrives through
 * {@link RoomRegistry}'s concurrent queues.
 *
 * <p>Snapshots go out only for rooms that actually changed. A tile-based game at 20Hz
 * is idle most ticks, so gating on the dirty flag removes almost all traffic without
 * any delta-encoding complexity.
 */
@Service
public class GameLoopService {

    private static final Logger log = LoggerFactory.getLogger(GameLoopService.class);
    private static final long TICK_NANOS = GameConstants.TICK_MS * 1_000_000L;

    private final RoomRegistry registry;
    private final RoomBroadcaster broadcaster;

    private volatile boolean running;
    private Thread thread;
    private volatile long tick;

    public GameLoopService(RoomRegistry registry, RoomBroadcaster broadcaster) {
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    public long tick() {
        return tick;
    }

    @PostConstruct
    public void start() {
        running = true;
        thread = new Thread(this::run, "game-loop");
        thread.setDaemon(true);
        thread.start();
        log.info("Game loop started at {}Hz ({}ms per tick)",
                GameConstants.TICKS_PER_SECOND, GameConstants.TICK_MS);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Game loop stopped after {} ticks", tick);
    }

    private void run() {
        long deadline = System.nanoTime();
        while (running) {
            deadline += TICK_NANOS;
            try {
                step();
            } catch (RuntimeException e) {
                // One bad tick must not take the world down with it.
                log.error("Tick {} failed", tick, e);
            }
            tick++;

            long remaining = deadline - System.nanoTime();
            if (remaining > 0) {
                LockSupport.parkNanos(remaining);
            } else {
                // Behind schedule. Resync rather than sprint to catch up, which would
                // burn a burst of ticks and make movement cooldowns feel wrong.
                deadline = System.nanoTime();
            }
        }
    }

    private void step() {
        registry.processPending(tick);
        registry.applyCommands(tick);
        registry.tickRooms(tick);
        registry.collectRooms();
        registry.connectIslands();

        for (Room room : registry.rooms()) {
            if (room.dirty()) {
                broadcaster.broadcast(room, tick);
                room.clearDirty();
            }
        }
    }
}
