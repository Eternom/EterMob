package fr.eternom.eterMob.module.boss;

import fr.eternom.eterMob.module.mob.Hits;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le socle des boss : apparition (place libre, résistance selon les joueurs), barre de boss, phases selon la vie (avec une
 * transition intouchable de 2 s), laisse (ramené à son point d'apparition au-delà de 40 blocs), remise à zéro (plus de
 * joueur à 64 blocs pendant 30 s : soigné, phase 1), registre persistant et suppression. Le boss est sauvegardé ; ce
 * qu'il crée (piliers, sbires, affichages) ne l'est jamais. Thread principal.
 */
public class BossService {

    public static final int TICK_PERIOD = 5;
    public static final double BAR_RANGE = 48;
    public static final String TAG = "etermob_boss";
    private static final double LEASH = 40;
    private static final double RESET_RANGE = 64;
    private static final long RESET_MILLIS = 30_000;
    private static final long TRANSITION_MILLIS = 2_000;
    /**
     * Par joueur en plus présent à l'apparition : sa résistance augmente d'autant (les dégâts qu'il reçoit sont divisés
     * par 1 + 0,35 par joueur en plus). Plutôt qu'une vie plus grande : la vie d'un mob est plafonnée à 1 024.
     */
    private static final double TOUGHNESS_PER_EXTRA_PLAYER = 0.35;
    /** Position du boss enregistrée dans le registre toutes les 30 s. */
    private static final int SAVE_TICKS = 600;
    /** Chunk chargé pour supprimer un boss lointain : relâché après ce délai (ticks). */
    private static final int REMOVAL_TICKET_TICKS = 200;

    private final JavaPlugin plugin;
    private final BossRegistry registry;
    private final Map<String, Boss> bosses = new LinkedHashMap<>();
    private final Map<UUID, BossFight> fights = new HashMap<>();
    private final Map<UUID, BossFight> owners = new HashMap<>();
    private final NamespacedKey bossKey;
    private final NamespacedKey homeKey;
    private final NamespacedKey toughnessKey;
    private long ticks;

    public BossService(JavaPlugin plugin, BossRegistry registry, List<Boss> bosses) {
        this.plugin = plugin;
        this.registry = registry;
        bosses.forEach(boss -> {
            this.bosses.put(boss.id(), boss);
            boss.bind(this);
        });
        this.bossKey = new NamespacedKey(plugin, "boss");
        this.homeKey = new NamespacedKey(plugin, "boss_home");
        this.toughnessKey = new NamespacedKey(plugin, "boss_toughness");
    }

    public Collection<Boss> bosses() {
        return bosses.values();
    }

    public Boss boss(String id) {
        return bosses.get(id);
    }

    public Collection<BossFight> fights() {
        return fights.values();
    }

    public BossRegistry registry() {
        return registry;
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                attach(entity);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    /** Arrêt du plugin : barres cachées, entités du boss retirées (elles ne sont jamais sauvegardées). */
    public void stop() {
        for (BossFight fight : new ArrayList<>(fights.values())) {
            end(fight);
        }
    }

    // ---- Apparition ----

    /** Fait apparaître ce boss ici ; renvoie null, ou la raison du refus. */
    public String spawn(Boss boss, Location location) {
        Location at = location.getBlock().getLocation().add(0.5, 0, 0.5);
        for (int y = 0; y < Math.ceil(boss.height()); y++) {
            if (!at.clone().add(0, y, 0).getBlock().isPassable()) {
                return "Pas assez de place : il faut " + (int) Math.ceil(boss.height()) + " blocs libres en hauteur.";
            }
        }
        long players = at.getNearbyPlayers(BAR_RANGE).stream().filter(Hits::fighting).count();
        double health = boss.baseHealth();
        double toughness = 1 + TOUGHNESS_PER_EXTRA_PLAYER * Math.max(0, players - 1);
        LivingEntity entity = at.getWorld().spawn(at, boss.type(), CreatureSpawnEvent.SpawnReason.CUSTOM, mob -> {
            PersistentDataContainer data = mob.getPersistentDataContainer();
            data.set(bossKey, PersistentDataType.STRING, boss.id());
            data.set(homeKey, PersistentDataType.STRING, at.getX() + ";" + at.getY() + ";" + at.getZ());
            data.set(toughnessKey, PersistentDataType.DOUBLE, toughness);
            mob.addScoreboardTag(TAG);
            // Avant son entrée dans le monde : attach le trouve déjà dans le registre
            registry.add(mob.getUniqueId(), boss.id(), at);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            boss.setup(mob);
            AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(health);
                mob.setHealth(health);
            }
        });
        BossFight fight = fights.get(entity.getUniqueId());
        if (fight != null) {
            say(fight, boss.spawnLine());
        }
        return null;
    }

    // ---- Suivi ----

    /** Entité entrée dans le monde : si c'est un boss, son combat reprend (ou il est retiré s'il était à supprimer). */
    public void attach(Entity entity) {
        if (!(entity instanceof LivingEntity living) || fights.containsKey(entity.getUniqueId())) {
            return;
        }
        PersistentDataContainer data = living.getPersistentDataContainer();
        Boss boss = bosses.get(data.get(bossKey, PersistentDataType.STRING));
        if (boss == null) {
            return;
        }
        BossRegistry.Entry entry = registry.find(living.getUniqueId());
        if (entry != null && entry.removing()) {
            registry.remove(living.getUniqueId());
            living.getLocation().getChunk().removePluginChunkTicket(plugin);
            living.remove();
            return;
        }
        if (entry == null) {
            registry.add(living.getUniqueId(), boss.id(), living.getLocation());
        }
        double toughness = Math.max(1, data.getOrDefault(toughnessKey, PersistentDataType.DOUBLE, 1.0));
        BossFight fight = new BossFight(boss, living, home(living), toughness, BossBar.bossBar(Component.empty(), 1f, boss.color(1),
                BossBar.Overlay.NOTCHED_10));
        fights.put(living.getUniqueId(), fight);
        int phase = phaseFor(fight);
        fight.phase(phase);
        refreshBar(fight);
        boss.onLoad(fight);
        if (phase > 1) {
            boss.onPhase(fight, phase, true);
        }
    }

    private Location home(LivingEntity entity) {
        String home = entity.getPersistentDataContainer().get(homeKey, PersistentDataType.STRING);
        if (home != null) {
            String[] parts = home.split(";");
            if (parts.length == 3) {
                return new Location(entity.getWorld(), Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]));
            }
        }
        return entity.getLocation();
    }

    /** Entité sortie du monde : si c'est un boss, son combat s'arrête (il reprendra quand il se rechargera). */
    public void detach(Entity entity) {
        BossFight fight = fights.get(entity.getUniqueId());
        if (fight != null) {
            end(fight);
        }
    }

    /** Fin d'un combat : barre cachée, entités du boss retirées. */
    private void end(BossFight fight) {
        fights.remove(fight.id());
        for (UUID viewer : fight.viewers()) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                player.hideBossBar(fight.bar());
            }
        }
        fight.viewers().clear();
        clearOwned(fight);
        fight.boss().onEnd(fight);
    }

    public BossFight fight(Entity entity) {
        return entity == null ? null : fights.get(entity.getUniqueId());
    }

    /** Le boss qui possède cette entité (pilier, sbire...), ou null. */
    public BossFight ownerOf(Entity entity) {
        return entity == null ? null : owners.get(entity.getUniqueId());
    }

    /** Rattache une entité au combat : retirée à la fin du combat ou à la remise à zéro. */
    public void own(BossFight fight, Entity entity) {
        fight.owned().add(entity);
        owners.put(entity.getUniqueId(), fight);
    }

    private void clearOwned(BossFight fight) {
        for (Entity owned : fight.owned()) {
            owners.remove(owned.getUniqueId());
            if (owned.isValid()) {
                owned.remove();
            }
        }
        fight.owned().clear();
    }

    // ---- Combat ----

    private void tick() {
        ticks += TICK_PERIOD;
        for (BossFight fight : new ArrayList<>(fights.values())) {
            LivingEntity entity = fight.entity();
            if (!entity.isValid() || entity.isDead()) {
                continue;
            }
            fight.owned().removeIf(owned -> {
                boolean gone = !owned.isValid() || owned.isDead();
                if (gone) {
                    owners.remove(owned.getUniqueId());
                }
                return gone;
            });
            boolean near = entity.getLocation().getNearbyPlayers(RESET_RANGE).stream().anyMatch(Hits::fighting);
            if (near) {
                fight.playerNear();
            } else if (System.currentTimeMillis() - fight.lastPlayerNear() > RESET_MILLIS) {
                reset(fight);
            }
            leash(fight);
            int phase = phaseFor(fight);
            if (phase > fight.phase()) {
                transition(fight, phase);
            }
            refreshBar(fight);
            if (near && !fight.invulnerable()) {
                fight.boss().tick(fight);
            }
            if (ticks % SAVE_TICKS == 0) {
                registry.update(fight.id(), entity.getLocation());
            }
        }
    }

    private int phaseFor(BossFight fight) {
        AttributeInstance max = fight.entity().getAttribute(Attribute.MAX_HEALTH);
        double part = max == null ? 1 : fight.entity().getHealth() / max.getValue();
        int phase = 1;
        for (double threshold : fight.boss().phaseThresholds()) {
            if (part <= threshold) {
                phase++;
            }
        }
        return phase;
    }

    /** Transition : intouchable 2 s, cri, réplique, puis la phase prend ses capacités. */
    private void transition(BossFight fight, int phase) {
        fight.phase(phase);
        fight.invulnerableFor(TRANSITION_MILLIS);
        LivingEntity entity = fight.entity();
        Location at = entity.getLocation().add(0, entity.getHeight() / 2, 0);
        entity.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
        entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 60, 1, entity.getHeight() / 3, 1, 0.05);
        say(fight, fight.boss().phaseLine(phase));
        fight.boss().onPhase(fight, phase, false);
    }

    /** Ramené à son point d'apparition s'il en est trop loin. */
    private void leash(BossFight fight) {
        LivingEntity entity = fight.entity();
        Location home = fight.home();
        if (home.getWorld() != entity.getWorld() || home.distanceSquared(entity.getLocation()) > LEASH * LEASH) {
            entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.5);
            entity.teleport(home);
            entity.getWorld().spawnParticle(Particle.PORTAL, home.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.5);
        }
    }

    /** Plus personne : soigné, phase 1, entités retirées, ramené chez lui. */
    private void reset(BossFight fight) {
        LivingEntity entity = fight.entity();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (fight.phase() == 1 && (max == null || entity.getHealth() >= max.getValue())) {
            return;
        }
        clearOwned(fight);
        if (max != null) {
            entity.setHealth(max.getValue());
        }
        fight.phase(1);
        fight.clearCooldowns();
        entity.teleport(fight.home());
        fight.boss().onReset(fight);
        fight.playerNear();
    }

    private void refreshBar(BossFight fight) {
        LivingEntity entity = fight.entity();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        float progress = max == null ? 1 : (float) Math.clamp(entity.getHealth() / max.getValue(), 0, 1);
        BossBar bar = fight.bar();
        Boss boss = fight.boss();
        bar.progress(progress);
        bar.color(boss.color(fight.phase()));
        bar.name(MiniMessage.miniMessage().deserialize("<red><b><name></b></red> <gray><title></gray> <dark_gray>·</dark_gray> <white><phase>",
                Placeholder.unparsed("name", boss.name()), Placeholder.unparsed("title", boss.title()),
                Placeholder.unparsed("phase", boss.phaseName(fight.phase()))));
        List<UUID> inRange = new ArrayList<>();
        for (Player player : entity.getLocation().getNearbyPlayers(BAR_RANGE)) {
            inRange.add(player.getUniqueId());
            if (fight.viewers().add(player.getUniqueId())) {
                player.showBossBar(bar);
            }
        }
        fight.viewers().removeIf(viewer -> {
            if (inRange.contains(viewer)) {
                return false;
            }
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                player.hideBossBar(bar);
            }
            return true;
        });
    }

    /** Réplique du boss aux joueurs de la zone du combat. */
    public void say(BossFight fight, String line) {
        if (line == null) {
            return;
        }
        Component message = MiniMessage.miniMessage().deserialize("<dark_red><b><name></b></dark_red> <dark_gray>»</dark_gray> <gray><i><line>",
                Placeholder.unparsed("name", fight.boss().name()), Placeholder.unparsed("line", line));
        fight.players().forEach(player -> player.sendMessage(message));
    }

    // ---- Mort et suppression ----

    /** Le boss est mort : réplique, registre, fin du combat (aucun butin). */
    public void died(BossFight fight) {
        say(fight, fight.boss().deathLine());
        fight.boss().onDeath(fight);
        registry.remove(fight.id());
        end(fight);
    }

    /** Supprime un boss chargé : il disparaît sans mourir, avec tout ce qu'il a créé. */
    public void remove(BossFight fight) {
        registry.remove(fight.id());
        LivingEntity entity = fight.entity();
        end(fight);
        entity.remove();
    }

    /**
     * Supprime un boss du registre, chargé ou non. Non chargé : il est marqué, son chunk est chargé un instant, et il est
     * retiré dès qu'il se charge (ou plus tard, à son prochain chargement).
     */
    public boolean remove(BossRegistry.Entry entry) {
        Entity loaded = Bukkit.getEntity(entry.uuid());
        BossFight fight = fight(loaded);
        if (fight != null) {
            remove(fight);
            return true;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            registry.remove(entry.uuid());
            return false;
        }
        registry.markRemoving(entry.number());
        Chunk chunk = world.getChunkAt((int) Math.floor(entry.x()) >> 4, (int) Math.floor(entry.z()) >> 4);
        chunk.addPluginChunkTicket(plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> chunk.removePluginChunkTicket(plugin), REMOVAL_TICKET_TICKS);
        return true;
    }

    /** Boss chargé le plus proche, à moins de range blocs, ou null. */
    public BossFight nearest(Location location, double range) {
        BossFight nearest = null;
        double best = range * range;
        for (BossFight fight : fights.values()) {
            if (fight.entity().getWorld() != location.getWorld()) {
                continue;
            }
            double distance = fight.entity().getLocation().distanceSquared(location);
            if (distance <= best) {
                best = distance;
                nearest = fight;
            }
        }
        return nearest;
    }
}
