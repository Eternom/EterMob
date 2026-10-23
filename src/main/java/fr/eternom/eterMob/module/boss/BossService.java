package fr.eternom.eterMob.module.boss;

import fr.eternom.eterMob.module.mob.Hits;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
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
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
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
     * Par joueur en plus dans le combat : sa résistance augmente d'autant (les dégâts qu'il reçoit sont divisés par
     * 1 + 0,35 par joueur en plus). Plutôt qu'une vie plus grande : la vie d'un mob est plafonnée à 1 024. Recompté en
     * continu ; un joueur sorti de la zone compte encore 10 s (sortir et revenir ne le fait pas baisser).
     */
    private static final double TOUGHNESS_PER_EXTRA_PLAYER = 0.35;
    private static final long PARTICIPANT_MEMORY_MILLIS = 10_000;
    /** Position du boss enregistrée dans le registre toutes les 30 s. */
    private static final int SAVE_TICKS = 600;
    /** Chunk chargé pour supprimer un boss lointain : relâché après ce délai (ticks). */
    private static final int REMOVAL_TICKET_TICKS = 200;
    /** Personne ne le frappe depuis ce temps alors que des joueurs sont là : il relance le combat (Boss#onStall). */
    private static final long STALL_MILLIS = 60_000;
    /** Rage de fin : ses coups font tant de fois plus mal. */
    public static final double ENRAGE_DAMAGE = 1.5;
    /** Effondrement avant la mort (ticks), et profondeur dont il s'enfonce. */
    private static final int DEATH_TICKS = 60;
    private static final double DEATH_SINK = 2.5;

    private final JavaPlugin plugin;
    private final BossRegistry registry;
    private final Map<String, Boss> bosses = new LinkedHashMap<>();
    private final Map<UUID, BossFight> fights = new HashMap<>();
    private final Map<UUID, BossFight> owners = new HashMap<>();
    private final NamespacedKey bossKey;
    private final NamespacedKey homeKey;
    private long ticks;

    public BossService(JavaPlugin plugin, BossRegistry registry, List<Boss> bosses) {
        this.plugin = plugin;
        this.registry = registry;
        bosses.forEach(boss -> {
            this.bosses.put(boss.id(), boss);
            boss.bind(this);
            boss.configure(plugin.getConfig().getConfigurationSection("bosses." + boss.id()));
        });
        this.bossKey = new NamespacedKey(plugin, "boss");
        this.homeKey = new NamespacedKey(plugin, "boss_home");
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
        double health = boss.baseHealth();
        LivingEntity entity = at.getWorld().spawn(at, boss.type(), CreatureSpawnEvent.SpawnReason.CUSTOM, mob -> {
            PersistentDataContainer data = mob.getPersistentDataContainer();
            data.set(bossKey, PersistentDataType.STRING, boss.id());
            data.set(homeKey, PersistentDataType.STRING, at.getX() + ";" + at.getY() + ";" + at.getZ());
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
            fight.invulnerableFor(boss.intro(fight) * 50L);
            title(fight, "<red><b>" + boss.name() + "</b>", "<gray>" + boss.title());
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
        BossFight fight = new BossFight(boss, living, home(living), BossBar.bossBar(Component.empty(), 1f, boss.color(1),
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
            if (!entity.isValid() || entity.isDead() || fight.dying()) {
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
            countParticipants(fight);
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
            capHealth(fight);
            refreshBar(fight);
            if (near && !fight.invulnerable()) {
                fight.boss().tick(fight);
                markTarget(entity);
                enrageAndStall(fight);
            }
            if (ticks % SAVE_TICKS == 0) {
                registry.update(fight.id(), entity.getLocation());
            }
        }
    }

    /** Joueurs du combat (zone de la barre, ou sortis depuis moins de 10 s) : la résistance suit leur nombre. */
    private void countParticipants(BossFight fight) {
        long now = System.currentTimeMillis();
        for (Player player : fight.entity().getLocation().getNearbyPlayers(BAR_RANGE)) {
            if (Hits.fighting(player)) {
                fight.participants().put(player.getUniqueId(), now);
            }
        }
        fight.participants().values().removeIf(seen -> now - seen > PARTICIPANT_MEMORY_MILLIS);
        fight.toughness(1 + TOUGHNESS_PER_EXTRA_PLAYER * Math.max(0, fight.participants().size() - 1));
    }

    /** Vie maximale autorisée dans la phase en cours : il ne remonte jamais au-dessus du seuil de sa phase. */
    public double healthCap(BossFight fight) {
        AttributeInstance max = fight.entity().getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? fight.entity().getHealth() : max.getValue();
        double[] thresholds = fight.boss().phaseThresholds();
        int phase = fight.phase();
        return phase <= 1 || phase - 2 >= thresholds.length ? (phase <= 1 ? full : full * thresholds[thresholds.length - 1])
                : full * thresholds[phase - 2];
    }

    /** Soins (aura, drain...) plafonnés au seuil de la phase : sinon le combat pourrait durer à l'infini. */
    private void capHealth(BossFight fight) {
        double cap = healthCap(fight);
        if (fight.entity().getHealth() > cap) {
            fight.entity().setHealth(cap);
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
        title(fight, "<dark_red>Phase " + phase, "<gray>" + fight.boss().phaseName(phase));
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
        if (fight.combatStart() > 0) {
            plugin.getLogger().info("Combat " + fight.boss().name() + " abandonné après " + duration(fight) + " (remis à zéro).");
        }
        fight.resetCombat();
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

    /** Grand titre à l'écran des joueurs de la zone du combat. */
    private void title(BossFight fight, String title, String subtitle) {
        Title shown = Title.title(MiniMessage.miniMessage().deserialize(title), MiniMessage.miniMessage().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700)));
        fight.players().forEach(player -> player.showTitle(shown));
    }

    /** Petit marqueur rouge au-dessus du joueur qu'il vise, pour que le groupe sache qui il poursuit. */
    private static void markTarget(LivingEntity boss) {
        if (boss instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player target) {
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() + 0.6, 0), 3,
                    0.08, 0.08, 0.08, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(0xD01010), 1f));
        }
    }

    /** Rage de fin après enrageMinutes de combat, et relance si personne ne le frappe depuis 60 s. */
    private void enrageAndStall(BossFight fight) {
        if (fight.combatStart() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!fight.enraged() && now - fight.combatStart() > fight.boss().enrageMinutes() * 60_000) {
            fight.enraged(true);
            LivingEntity entity = fight.entity();
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.4f);
            title(fight, "<dark_red><b>Rage</b>", "<gray>" + fight.boss().name() + " frappe désormais bien plus fort");
        }
        if (now - fight.lastPlayerDamage() > STALL_MILLIS) {
            fight.lastPlayerDamage(now);
            fight.boss().onStall(fight);
        }
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

    /**
     * Coup fatal : au lieu de mourir d'un coup, il s'effondre 3 s (intouchable, s'enfonce dans le sol), puis meurt pour
     * de bon (died).
     */
    public void beginDeath(BossFight fight) {
        LivingEntity entity = fight.entity();
        fight.dying(true);
        entity.setAI(false);
        entity.setGravity(false);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.3f);
        Location start = entity.getLocation();
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 2;
            if (!entity.isValid()) {
                task[0].cancel();
                return;
            }
            entity.teleport(start.clone().subtract(0, DEATH_SINK * elapsed[0] / DEATH_TICKS, 0));
            entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, entity.getLocation().add(0, 1, 0), 15, 1, 1, 1, 0.02);
            entity.getWorld().spawnParticle(Particle.BLOCK, start, 20, 1.2, 0.1, 1.2, 0.1,
                    start.getBlock().getRelative(0, -1, 0).getBlockData());
            if (elapsed[0] % 20 == 0) {
                entity.getWorld().playSound(start, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
            }
            if (elapsed[0] >= DEATH_TICKS) {
                task[0].cancel();
                entity.setInvulnerable(false);
                entity.setHealth(0);
            }
        }, 2, 2);
    }

    /** Le boss est mort : réplique, bilan, registre, fin du combat (aucun butin). */
    public void died(BossFight fight) {
        say(fight, fight.boss().deathLine());
        fight.boss().onDeath(fight);
        summary(fight);
        registry.remove(fight.id());
        end(fight);
    }

    /** Un joueur est mort : compté dans chaque combat dont il fait partie. */
    public void playerDied(Player player) {
        for (BossFight fight : fights.values()) {
            if (fight.participants().containsKey(player.getUniqueId())) {
                fight.stats(player.getUniqueId()).deaths++;
            }
        }
    }

    /** Le combat auquel appartient cette entité (le boss, une de ses entités, ou le tireur d'un projectile), ou null. */
    public BossFight fightOf(Entity entity) {
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            entity = shooter;
        }
        BossFight fight = fight(entity);
        return fight != null ? fight : ownerOf(entity);
    }

    /** Bilan de fin aux joueurs du combat : durée, MVP, et pour chacun dégâts, dégâts reçus, morts, lignes du boss. */
    private void summary(BossFight fight) {
        Map<UUID, BossFight.Stats> stats = fight.allStats();
        double total = stats.values().stream().mapToDouble(stat -> stat.dealt).sum();
        int deaths = stats.values().stream().mapToInt(stat -> stat.deaths).sum();
        plugin.getLogger().info("Combat " + fight.boss().name() + " gagné en " + duration(fight) + ", " + stats.size()
                + " joueur(s), " + deaths + " mort(s).");
        if (stats.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, BossFight.Stats>> ranking = new ArrayList<>(stats.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue().dealt, a.getValue().dealt));
        MiniMessage mini = MiniMessage.miniMessage();
        List<Component> lines = new ArrayList<>();
        lines.add(mini.deserialize("<gold><b>━━ " + fight.boss().name() + " vaincu ━━</b>"));
        lines.add(mini.deserialize("<gray>Durée <white>" + duration(fight) + "</white> · joueurs <white>" + stats.size()
                + "</white> · morts <white>" + deaths));
        Map.Entry<UUID, BossFight.Stats> mvp = ranking.get(0);
        lines.add(mini.deserialize("<gold>MVP <yellow><b>" + name(mvp.getKey()) + "</b></yellow> <gray>(" + percent(mvp.getValue().dealt, total)
                + " des dégâts)"));
        for (Map.Entry<UUID, BossFight.Stats> entry : ranking) {
            BossFight.Stats stat = entry.getValue();
            StringBuilder line = new StringBuilder("<white>" + name(entry.getKey()) + " <dark_gray>»</dark_gray> <gray>dégâts <red>"
                    + Math.round(stat.dealt) + "</red> (" + percent(stat.dealt, total) + ") · reçus <white>" + Math.round(stat.taken)
                    + "</white> · morts <white>" + stat.deaths + "</white>");
            stat.extra.forEach((label, count) -> line.append(" · ").append(label).append(" <white>").append(count).append("</white>"));
            lines.add(mini.deserialize(line.toString()));
        }
        for (UUID id : stats.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                lines.forEach(player::sendMessage);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
    }

    private static String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "?" : name;
    }

    private static String percent(double part, double total) {
        return total <= 0 ? "0 %" : Math.round(part * 100 / total) + " %";
    }

    private static String duration(BossFight fight) {
        long seconds = fight.combatStart() == 0 ? 0 : (System.currentTimeMillis() - fight.combatStart()) / 1000;
        return seconds / 60 + " min " + String.format("%02d", seconds % 60) + " s";
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
