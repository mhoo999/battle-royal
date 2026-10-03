package com.example.battleroyal.game.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A crate on the floor: what looting opens. Holds the items, in order; the order is
 * what the loot window shows and what {@code TAKE} indexes into.
 *
 * <p>Everyone sees that a crate lies on a tile; only a player who has opened it learns
 * what is inside. A room's loot roll leaves a crate of one item, and a death leaves a
 * crate of everything the player carried.
 *
 * <p>Capacity is the caller's to enforce, like magazine size on {@link Item}: this
 * class does not read {@code game.rule}.
 */
public final class Crate {

    private final String id;
    private final List<Item> items;

    public Crate(String id, List<Item> items) {
        this.id = id;
        this.items = new ArrayList<>(items);
    }

    public String id() {
        return id;
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Item get(int index) {
        return items.get(index);
    }

    public Item remove(int index) {
        return items.remove(index);
    }

    /** Puts {@code item} where the one at {@code index} was, and hands that one back. */
    public Item replace(int index, Item item) {
        return items.set(index, item);
    }

    public void add(Item item) {
        items.add(item);
    }
}
