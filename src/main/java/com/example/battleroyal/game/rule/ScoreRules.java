package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.Player;

/**
 * Score that comes from time and travel rather than from combat or pickups, each with
 * the guard that stops it being farmed. See docs/GAME_RULES.md section 7.
 */
public final class ScoreRules {

    private ScoreRules() {
    }

    /**
     * One tick of being alive. Every {@link GameConstants#SURVIVAL_INTERVAL_TICKS} of it
     * outside a cabinet pays a point. Time in a cabinet neither counts nor resets the
     * count: hiding there forever would otherwise be the best way to score.
     *
     * @return true when this tick paid out
     */
    public static boolean accrueSurvival(Player player) {
        if (player.inCabinet()) {
            return false;
        }
        int ticks = player.survivalTicks() + 1;
        if (ticks < GameConstants.SURVIVAL_INTERVAL_TICKS) {
            player.setSurvivalTicks(ticks);
            return false;
        }
        player.setSurvivalTicks(0);
        player.addScore(GameConstants.SCORE_SURVIVAL);
        return true;
    }

    /**
     * Arriving in a room. Pays only for a room never visited before, and no more than
     * once per {@link GameConstants#ROOM_SCORE_RATE_CAP_TICKS}. The first-visit check
     * stops walking back and forth; the cap stops cycling through rooms the world keeps
     * rebuilding under new ids. A first visit inside the cap still counts as visited.
     *
     * @return true when the arrival paid out
     */
    public static boolean enterRoom(Player player, String roomId, long nowTick) {
        boolean firstVisit = player.visitRoom(roomId);
        if (!firstVisit || nowTick < player.nextRoomScoreTick()) {
            return false;
        }
        player.addScore(GameConstants.SCORE_ROOM_ENTER);
        player.setNextRoomScoreTick(nowTick + GameConstants.ROOM_SCORE_RATE_CAP_TICKS);
        return true;
    }
}
