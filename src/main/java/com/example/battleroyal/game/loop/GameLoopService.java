package com.example.battleroyal.game.loop;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.rule.GameConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
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
    private final List<DepartureListener> departureListeners;
    private final List<TickDriver> drivers;

    private volatile boolean running;
    private Thread thread;
    private volatile long tick;

    public GameLoopService(RoomRegistry registry, RoomBroadcaster broadcaster,
                           List<DepartureListener> departureListeners,
                           List<TickDriver> drivers) {
        this.registry = registry;
        this.broadcaster = broadcaster;
        this.departureListeners = departureListeners;
        this.drivers = drivers;
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
        for (TickDriver driver : drivers) {
            driver.drive(registry, tick);
        }
        registry.applyCommands(tick);
        registry.tickRooms(tick);

        // Before reaping, so a victim still receives the snapshot showing them at zero
        // and the result that goes with it.
        for (Room room : registry.rooms()) {
            List<GameEvent> events = room.drainEvents();
            if (room.dirty() || !events.isEmpty()) {
                broadcaster.broadcast(room, events, tick);
                room.clearDirty();
            }
            for (GameEvent event : events) {
                if (event instanceof GameEvent.Died died) {
                    tell(died.playerId(), listener -> listener.onDeath(died));
                } else if (event instanceof GameEvent.Extracted extracted) {
                    tell(extracted.playerId(), listener -> listener.onExtracted(extracted));
                } else if (event instanceof GameEvent.Ejected ejected) {
                    tell(ejected.playerId(), listener -> listener.onEjected(ejected));
                }
            }
        }

        // Removing the dead and the departed marks their room dirty, so the others see
        // them go on the next tick.
        registry.reapDead();
        registry.fitWorld();
    }

    private void tell(String playerId, Consumer<DepartureListener> call) {
        for (DepartureListener listener : departureListeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException e) {
                // A failed save must not cost the rest of the tick.
                log.error("Departure listener failed for {}", playerId, e);
            }
        }
    }
}
