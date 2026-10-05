package com.example.battleroyal.bot;

import com.example.battleroyal.game.core.Command;
import com.example.battleroyal.game.core.Player;
import com.example.battleroyal.game.core.Room;
import com.example.battleroyal.game.loop.RoomRegistry;
import com.example.battleroyal.game.loop.TickDriver;
import com.example.battleroyal.game.rule.GameConstants;
import com.example.battleroyal.web.GameSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Keeps company on the island (docs/GAME_RULES.md §10b): while at least one person is
 * out there, server-run players arrive one at a time until people and bots together
 * make {@code game.bots.target}; when the last person leaves, the bots go too.
 *
 * <p>A bot is a guest-named {@link Player} with an ordinary player id, no account and no
 * socket. Nothing an opponent receives tells it from a person; it never reaches the
 * ranking, the stash or the results (guest names are unranked). Off unless the target
 * is set: production sets it, tests and CI do not.
 *
 * <p>Runs on the loop thread ({@link TickDriver}).
 */
@Component
public class BotDirector implements TickDriver {

    /** A new bot every 3–12 seconds while short of the target, as people trickle in. */
    static final int ARRIVAL_MIN_TICKS = 3 * GameConstants.TICKS_PER_SECOND;
    static final int ARRIVAL_SPREAD_TICKS = 9 * GameConstants.TICKS_PER_SECOND;

    /** Names a classmate might go by; shown only as a killer's name, with the ~ prefix. */
    static final List<String> NAMES = List.of(
            "민수", "지훈", "서연", "하은", "도윤", "지우", "예준", "수아", "현우", "유진",
            "은지", "태민", "소연", "준호", "다은", "성민", "혜진", "동현", "나연", "재원");

    private final int target;
    private final GameSessionService sessions;
    private final Random random;
    private final Map<String, BotBrain.Memory> minds = new LinkedHashMap<>();
    private long nextArrivalTick = -1;

    @Autowired
    public BotDirector(@Value("${game.bots.target:0}") int target, GameSessionService sessions) {
        this(target, sessions, new Random());
    }

    BotDirector(int target, GameSessionService sessions, Random random) {
        this.target = target;
        this.sessions = sessions;
        this.random = random;
    }

    @Override
    public void drive(RoomRegistry registry, long tick) {
        if (target <= 0) {
            return;
        }
        // Forget the bots that died, got out, or never found a room.
        minds.keySet().removeIf(id -> registry.player(id) == null
                && !registry.isBot(id) && minds.get(id).arrivedBy < tick);

        int people = registry.people();
        if (people == 0) {
            for (String id : minds.keySet()) {
                registry.requestLeave(id);
            }
            minds.clear();
            nextArrivalTick = -1;
            return;
        }

        if (nextArrivalTick < 0) {
            nextArrivalTick = tick + nextGap();
        }
        if (people + minds.size() < target && tick >= nextArrivalTick) {
            String id = sessions.nextPlayerId();
            String name = GameConstants.UNRANKED_PREFIX + NAMES.get(random.nextInt(NAMES.size()));
            registry.requestBotJoin(id, name);
            minds.put(id, new BotBrain.Memory(tick, random, tick + 2));
            nextArrivalTick = tick + nextGap();
        }

        for (Map.Entry<String, BotBrain.Memory> mind : minds.entrySet()) {
            Room room = registry.roomOf(mind.getKey());
            Player self = room == null ? null : room.player(mind.getKey());
            if (self == null) {
                continue;
            }
            Command command = BotBrain.think(room, self, mind.getValue(), tick, random);
            if (command != null) {
                registry.submit(command);
            }
        }
    }

    private int nextGap() {
        return ARRIVAL_MIN_TICKS + random.nextInt(ARRIVAL_SPREAD_TICKS);
    }

    /** Bots this director is running now, arrived or on their way. */
    int running() {
        return minds.size();
    }
}
