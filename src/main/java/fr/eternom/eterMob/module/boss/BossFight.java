package fr.eternom.eterMob.module.boss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un combat en cours : le boss chargé, sa phase, sa barre, son point d'apparition, ses entités, ses délais, et les
 * statistiques de chaque joueur pour le bilan de fin (jamais enregistrées).
 */
public class BossFight {

    /** Ce qu'un joueur a fait pendant le combat. */
    public static final class Stats {
        double dealt;
        double taken;
        int deaths;
        final Map<String, Integer> extra = new LinkedHashMap<>();
    }

    private final Boss boss;
    private final LivingEntity entity;
    private final Location home;
    private double toughness = 1;
    /** Joueurs du combat : dernière fois vus dans la zone. */
    private final Map<UUID, Long> participants = new HashMap<>();
    private final BossBar bar;
    private final Set<Entity> owned = new HashSet<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private int phase = 1;
    private long invulnerableUntil;
    private long lastPlayerNear = System.currentTimeMillis();
    private final Map<UUID, Stats> stats = new LinkedHashMap<>();
    private long combatStart;
    private long lastPlayerDamage;
    private boolean enraged;
    private boolean dying;
    /** Vie la plus basse atteinte dans la phase en cours (limite de regain). */
    private double lowest = Double.MAX_VALUE;

    BossFight(Boss boss, LivingEntity entity, Location home, BossBar bar) {
        this.boss = boss;
        this.entity = entity;
        this.home = home;
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

    /** Résistance selon les joueurs du combat (recomptés en continu) : les dégâts reçus (boss et piliers) en sont divisés. */
    public double toughness() {
        return toughness;
    }

    void toughness(double toughness) {
        this.toughness = toughness;
    }

    Map<UUID, Long> participants() {
        return participants;
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

    /** Intouchable pendant ce temps (transition, entrée...). */
    public void invulnerableFor(long millis) {
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

    // ---- Statistiques (bilan de fin) ----

    Stats stats(UUID player) {
        return stats.computeIfAbsent(player, id -> new Stats());
    }

    Map<UUID, Stats> allStats() {
        return stats;
    }

    /** Dégâts reçus par un joueur venant du boss (à appeler pour ceux qui ne passent pas par un événement : aura, drain...). */
    public void taken(Player player, double amount) {
        stats(player.getUniqueId()).taken += amount;
    }

    /** Ligne de bilan propre au boss (piliers cassés...) : +1 pour ce joueur. */
    public void count(Player player, String label) {
        stats(player.getUniqueId()).extra.merge(label, 1, Integer::sum);
    }

    void dealt(Player player, double amount) {
        long now = System.currentTimeMillis();
        if (combatStart == 0) {
            combatStart = now;
        }
        lastPlayerDamage = now;
        stats(player.getUniqueId()).dealt += amount;
    }

    /** Début du combat (premier coup d'un joueur), ou 0. */
    public long combatStart() {
        return combatStart;
    }

    long lastPlayerDamage() {
        return lastPlayerDamage;
    }

    void lastPlayerDamage(long when) {
        lastPlayerDamage = when;
    }

    /** Rage de fin (combat trop long) : ses coups font plus mal. */
    public boolean enraged() {
        return enraged;
    }

    void enraged(boolean enraged) {
        this.enraged = enraged;
    }

    /** En train de mourir (effondrement) : plus d'attaques, intouchable. */
    public boolean dying() {
        return dying;
    }

    void dying(boolean dying) {
        this.dying = dying;
    }

    double lowest() {
        return lowest;
    }

    void lowest(double lowest) {
        this.lowest = lowest;
    }

    void resetCombat() {
        lowest = Double.MAX_VALUE;
        stats.clear();
        combatStart = 0;
        lastPlayerDamage = 0;
        enraged = false;
    }

    /** Joueurs dans la zone du combat (BossService.BAR_RANGE). */
    public Collection<Player> players() {
        return entity.getLocation().getNearbyPlayers(BossService.BAR_RANGE);
    }
}
