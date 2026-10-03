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
        spendAmmo(1);
    }

    /** Takes {@code count} rounds out: one fired, or several moved into a gun. */
    public void spendAmmo(int count) {
        if (count > ammo) {
            throw new IllegalStateException("Not " + count + " rounds to spend on " + id);
        }
        ammo -= count;
    }

    public void addAmmo(int count) {
        ammo += count;
    }
}
