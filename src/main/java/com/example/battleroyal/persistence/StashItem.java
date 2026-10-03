package com.example.battleroyal.persistence;

import com.example.battleroyal.game.core.ItemKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One item an account owns. It is either in the stash, or out on the island with a
 * sortie; it is never in both, which is the whole of the duplication guard.
 *
 * <p>While out, the game holds it as an ordinary {@code Item} whose id is
 * {@link #gameItemId()}, so a pickup score and a crate can tell it apart from loot.
 */
@Entity
@Table(name = "stash_item", indexes = @Index(name = "idx_stash_item_account", columnList = "account_id"))
public class StashItem {

    public enum Location { STASH, OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemKind kind;

    private int ammo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Location location;

    /** The sortie carrying it while {@link Location#OUT}, else null. */
    @Column(name = "sortie_id")
    private Long sortieId;

    /** A second guard behind the account lock: a stale write fails instead of winning. */
    @Version
    private long version;

    protected StashItem() {
        // for JPA
    }

    public StashItem(Long accountId, ItemKind kind, int ammo) {
        this.accountId = accountId;
        this.kind = kind;
        this.ammo = ammo;
        this.location = Location.STASH;
    }

    public Long id() {
        return id;
    }

    public Long accountId() {
        return accountId;
    }

    public ItemKind kind() {
        return kind;
    }

    public int ammo() {
        return ammo;
    }

    public Location location() {
        return location;
    }

    public Long sortieId() {
        return sortieId;
    }

    public void sendOut(Long sortieId) {
        this.location = Location.OUT;
        this.sortieId = sortieId;
    }

    public void bringBack() {
        this.location = Location.STASH;
        this.sortieId = null;
    }

    /** Home through an exit, with however many rounds it has left. */
    public void bringBack(int ammo) {
        bringBack();
        this.ammo = ammo;
    }

    /** The id this item has in the game while it is out. */
    public String gameItemId() {
        return "s-" + id;
    }
}
