package com.example.battleroyal.ws;

import com.example.battleroyal.game.core.GameEvent;
import com.example.battleroyal.game.core.Pos;
import com.example.battleroyal.game.rule.GameConstants;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The event wire shapes are a visibility contract, the same way Snapshot.Other is. */
class OutboundTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hitNamesNoTarget() {
        assertEquals(List.of("attackerId"), names(GameEvent.Hit.class.getRecordComponents()),
                "the event knows only who to tell");
        assertEquals("{\"type\":\"EVENT\",\"event\":\"HIT\"}",
                mapper.writeValueAsString(Outbound.hit()));
    }

    @Test
    void aShotIsSentAsCoordinatePairsFromTheShooter() {
        GameEvent.Shot shot = new GameEvent.Shot(List.of(new Pos(10, 7), new Pos(10, 6)));

        assertEquals("{\"type\":\"EVENT\",\"event\":\"SHOT\",\"path\":[[10,7],[10,6]]}",
                mapper.writeValueAsString(Outbound.shot(shot)));
    }

    @Test
    void aSwingNamesItsTwoTiles() {
        GameEvent.Swing swing = new GameEvent.Swing(new Pos(3, 4), new Pos(4, 4));

        assertEquals("{\"type\":\"EVENT\",\"event\":\"SWING\",\"from\":[3,4],\"to\":[4,4]}",
                mapper.writeValueAsString(Outbound.swing(swing)));
    }

    @Test
    void theResultReportsSurvivalInSeconds() {
        GameEvent.Died died = new GameEvent.Died("p1", 420, 2,
                95L * GameConstants.TICKS_PER_SECOND + 7);

        assertEquals(
                "{\"type\":\"YOU_DIED\",\"score\":420,\"kills\":2,\"survivedSeconds\":95}",
                mapper.writeValueAsString(Outbound.youDied(died)));
    }

    private static List<String> names(RecordComponent[] components) {
        return Arrays.stream(components).map(RecordComponent::getName).toList();
    }
}
