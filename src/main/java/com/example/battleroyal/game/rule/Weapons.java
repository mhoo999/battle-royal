package com.example.battleroyal.game.rule;

import com.example.battleroyal.game.core.ItemKind;

/**
 * How each thing you can hold hits, bare hands included. The numbers themselves are in
 * {@link GameConstants}; this only says which belong to what.
 */
public final class Weapons {

    private Weapons() {
    }

    /**
     * One attack's reach and effect.
     *
     * @param shot   true for a trail along the line (SHOT), false for a blow on the tile
     *               in front (SWING)
     */
    public record Strike(int range, int damage, int cooldownTicks, boolean shot) {
    }

    private static final Strike FIST = new Strike(GameConstants.FIST_RANGE,
            GameConstants.FIST_DAMAGE, GameConstants.FIST_COOLDOWN_TICKS, false);

    /**
     * @param held what is in hand, or null for bare hands
     * @return how it strikes, or null for an item whose A is not an attack (medkit)
     */
    public static Strike strikeOf(ItemKind held) {
        if (held == null) {
            return FIST;
        }
        return switch (held) {
            case KNIFE -> new Strike(GameConstants.KNIFE_RANGE, GameConstants.KNIFE_DAMAGE,
                    GameConstants.KNIFE_COOLDOWN_TICKS, false);
            case BAT -> new Strike(GameConstants.BAT_RANGE, GameConstants.BAT_DAMAGE,
                    GameConstants.BAT_COOLDOWN_TICKS, false);
            case PAN -> new Strike(GameConstants.PAN_RANGE, GameConstants.PAN_DAMAGE,
                    GameConstants.PAN_COOLDOWN_TICKS, false);
            case PISTOL -> new Strike(GameConstants.PISTOL_RANGE, GameConstants.PISTOL_DAMAGE,
                    GameConstants.PISTOL_COOLDOWN_TICKS, true);
            case CROSSBOW -> new Strike(GameConstants.CROSSBOW_RANGE,
                    GameConstants.CROSSBOW_DAMAGE, GameConstants.CROSSBOW_COOLDOWN_TICKS, true);
            case SPOON -> junk(GameConstants.SPOON_DAMAGE);
            case DOLL -> junk(GameConstants.DOLL_DAMAGE);
            case CUP -> junk(GameConstants.CUP_DAMAGE);
            case RECORDER -> junk(GameConstants.RECORDER_DAMAGE);
            case REGISTER -> junk(GameConstants.REGISTER_DAMAGE);
            // A bundle of ammunition swung is a fist with something in it.
            case ROUNDS, BOLTS -> junk(GameConstants.FIST_DAMAGE);
            case MEDKIT -> null;
        };
    }

    /** What loads this gun, or null for anything that is not one. */
    public static ItemKind ammunitionFor(ItemKind gun) {
        return switch (gun) {
            case PISTOL -> ItemKind.ROUNDS;
            case CROSSBOW -> ItemKind.BOLTS;
            default -> null;
        };
    }

    /** How many rounds this gun holds when full. */
    public static int capacity(ItemKind gun) {
        return switch (gun) {
            case PISTOL -> GameConstants.PISTOL_MAGAZINE;
            case CROSSBOW -> GameConstants.CROSSBOW_BOLTS;
            default -> 0;
        };
    }

    /**
     * Ammunition a freshly spawned item comes with: a gun found on the island is loaded,
     * a bundle is full. Zero for anything that needs none.
     */
    public static int startingAmmo(ItemKind kind) {
        return switch (kind) {
            case PISTOL, ROUNDS -> GameConstants.PISTOL_MAGAZINE;
            case CROSSBOW, BOLTS -> GameConstants.CROSSBOW_BOLTS;
            default -> 0;
        };
    }

    private static Strike junk(int damage) {
        return new Strike(GameConstants.JUNK_RANGE, damage,
                GameConstants.JUNK_COOLDOWN_TICKS, false);
    }
}
