package fr.eternom.eterMob.module.boss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Un combat en cours : le boss chargé, sa phase, sa barre, son point d'apparition, ses entités et ses délais. */
public class BossFight {

    private final Boss boss;
    private final LivingEntity entity;
    private final Location home;
    private final double toughness;
    private final BossBar bar;
    private final Set<Entity> owned = new HashSet<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private int phase = 1;
    private long invulnerableUntil;
    private long lastPlayerNear = System.currentTimeMillis();

    BossFight(Boss boss, LivingEntity entity, Location home, double toughness, BossBar bar) {
        this.boss = boss;
        this.entity = entity;
        this.home = home;
        this.toughness = toughness;
        this.bar = bar;
    }

    public Boss boss() {
        return boss;
    }

    public LivingEntity entity() {
        return entity;
    }

    public UUID id() {
        return entity.getUniqueId();
    }

    /** Point d'apparition : la laisse le ramène ici. */
    public Location home() {
        return home.clone();
    }

    /** Résistance selon les joueurs présents à l'apparition : les dégâts reçus (boss et piliers) en sont divisés. */
    public double toughness() {
        return toughness;
    }

    public int phase() {
        return phase;
    }

    void phase(int phase) {
        this.phase = phase;
    }

    BossBar bar() {
        return bar;
    }

    Set<UUID> viewers() {
        return viewers;
    }

    public Set<Entity> owned() {
        return owned;
    }

    /** En transition de phase : intouchable, et ses capacités attendent. */
    public boolean invulnerable() {
        return System.currentTimeMillis() < invulnerableUntil;
    }

    void invulnerableFor(long millis) {
        invulnerableUntil = System.currentTimeMillis() + millis;
    }

    long lastPlayerNear() {
        return lastPlayerNear;
    }

    void playerNear() {
        lastPlayerNear = System.currentTimeMillis();
    }

    /** Capacité prête (key) : true et relance le délai de millis ; false si le délai n'est pas écoulé. */
    public boolean ready(String key, long millis) {
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(key, 0L)) {
            return false;
        }
        cooldowns.put(key, now + millis);
        return true;
    }

    void clearCooldowns() {
        cooldowns.clear();
    }

    /** Joueurs dans la zone du combat (BossService.BAR_RANGE). */
    public Collection<Player> players() {
        return entity.getLocation().getNearbyPlayers(BossService.BAR_RANGE);
    }
}
