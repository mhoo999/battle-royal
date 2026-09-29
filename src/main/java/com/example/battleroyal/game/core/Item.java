package com.example.battleroyal.game.core;

/**
 * One item instance, identified for its whole life.
 *
 * <p>The id matters for scoring: pickup pays out once per instance, so dropping and
 * re-taking the same pistol earns nothing.
 *
 * <p>Ammo lives on the instance rather than the holder, so a pistol dropped with two
 * rounds left is still a pistol with two rounds when the next player takes it. Swaps
 * never destroy the outgoing item.
 *
 * <p>Magazine size is supplied by the caller. This class deliberately does not read
 * {@code game.rule}, which would invert the dependency between the two pure packages.
 */
public final class Item {

    private final String id;
    private final ItemKind kind;
    private int ammo;

    public Item(String id, ItemKind kind, int ammo) {
        this.id = id;
        this.kind = kind;
        this.ammo = kind.usesAmmo() ? ammo : 0;
    }

    public String id() {
        return id;
    }

    public ItemKind kind() {
        return kind;
    }

    public int ammo() {
        return ammo;
    }

    public boolean hasAmmo() {
        return ammo > 0;
    }

    public void spendAmmo() {
        if (ammo <= 0) {
            throw new IllegalStateException("No ammo to spend on " + id);
        }
        ammo--;
    }
}
