package fr.eternom.eterMob.module.spawn;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Tier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les apparitions d'EterMob (config.yml > spawning et spawns) :
 * <ul>
 *   <li>remplacement : une apparition naturelle devient un variant (règles replace), sinon un mob de natural-ratios
 *   reçoit juste un ratio ;</li>
 *   <li>directeur : toutes les interval-seconds, autour de chaque joueur qui a de la place (per-player monstres d'EterMob
 *   à moins de 48 blocs, × la densité du serveur et de la Nuit de sang), un groupe d'une règle director, parfois mené ;
 *   plafonds par chunk et par monde ; jamais près du point d'apparition du monde ni sur un terrain de clan ;</li>
 *   <li>ratios : bonus la nuit, dans le Nether, dans l'End ; un seul Fléau dans un rayon ; les débutants (moins de
 *   beginner.hours de jeu) ne croisent pas plus fort que beginner.max-ratio.</li>
 * </ul>
 */
public final class SpawnDirector {

    private final JavaPlugin plugin;
    private final MobService mobs;
    private final SpawnGuard guard;
    private final Messages messages;
    private final boolean enabled;
    private final double density;
    private final int interval;
    private final int perPlayer;
    private final int perChunk;
    private final int perWorld;
    private final double distanceMin;
    private final double distanceMax;
    private final int attempts;
    private final Set<EntityType> naturalRatios = EnumSet.noneOf(EntityType.class);
    private final double beginnerHours;
    private final int beginnerMax;
    private final double topRadius;
    private final double nightBonus;
    private final double netherBonus;
    private final double endBonus;
    private final BloodNight bloodNight;
    private List<SpawnRule> rules;
    /** Sous-boss qui apparaissent d'eux-mêmes en survie (posés par Main), leur apparition et leur nombre par monde. */
    public record SubBoss(String id, String name, SpawnRule where, double chance, double cooldownMinutes) {
    }

    private List<SubBoss> subBosses = List.of();
    private java.util.function.BiFunction<String, Location, String> subBossSpawner;
    private java.util.function.ToIntFunction<World> subBossesAlive;
    private final Map<UUID, Long> subBossCooldown = new HashMap<>();
    private final int subBossesPerWorld;
    private final Map<String, Integer> counters = new HashMap<>();

    public SpawnDirector(JavaPlugin plugin, MobService mobs, SpawnGuard guard, Messages messages, ConfigurationSection config,
                         String serverName) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.guard = guard;
        this.messages = messages;
        ConfigurationSection spawning = config.getConfigurationSection("spawning");
        ConfigurationSection server = server(spawning, serverName);
        this.enabled = (spawning == null || spawning.getBoolean("enabled", true)) && (server == null || server.getBoolean("enabled", true));
        this.density = Math.max(0, (spawning == null ? 1 : spawning.getDouble("density", 1)) * (server == null ? 1 : server.getDouble("density", 1)));
        ConfigurationSection director = spawning == null ? null : spawning.getConfigurationSection("director");
        this.interval = (int) Math.round(Math.clamp(director == null ? 10 : director.getDouble("interval-seconds", 10), 2, 120) * 20);
        this.perPlayer = Math.max(0, director == null ? 6 : director.getInt("per-player", 6));
        this.perChunk = Math.max(1, director == null ? 4 : director.getInt("per-chunk", 4));
        this.perWorld = Math.max(1, director == null ? 200 : director.getInt("per-world", 200));
        List<Double> distance = director == null ? List.of() : director.getDoubleList("distance");
        this.distanceMin = distance.size() == 2 ? Math.max(8, distance.get(0)) : 24;
        this.distanceMax = distance.size() == 2 ? Math.max(distanceMin + 4, distance.get(1)) : 44;
        this.attempts = Math.clamp(director == null ? 6 : director.getInt("attempts", 6), 1, 20);
        if (spawning != null) {
            for (String type : spawning.getStringList("natural-ratios")) {
                try {
                    naturalRatios.add(EntityType.valueOf(type.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("natural-ratios : mob inconnu « " + type + " »");
                }
            }
        }
        ConfigurationSection beginner = spawning == null ? null : spawning.getConfigurationSection("beginner");
        this.beginnerHours = beginner == null ? 5 : Math.max(0, beginner.getDouble("hours", 5));
        this.beginnerMax = beginner == null ? 3 : Math.max(1, beginner.getInt("max-ratio", 3));
        this.topRadius = spawning == null ? 64 : Math.max(0, spawning.getDouble("top-ratio-radius", 64));
        ConfigurationSection bonus = spawning == null ? null : spawning.getConfigurationSection("ratio-bonus");
        this.nightBonus = bonus == null ? 0.3 : bonus.getDouble("night", 0.3);
        this.netherBonus = bonus == null ? 0.5 : bonus.getDouble("nether", 0.5);
        this.endBonus = bonus == null ? 0.8 : bonus.getDouble("end", 0.8);
        this.bloodNight = new BloodNight(plugin, messages, config.getConfigurationSection("blood-night"));
        this.subBossesPerWorld = spawning == null ? 1 : Math.max(0, spawning.getInt("sub-bosses-per-world", 1));
        this.rules = SpawnRule.load(config.getConfigurationSection("spawns"), plugin.getLogger());
    }

    /** Les sous-boss à faire apparaître en survie ; spawner : BossService#spawn (null si c'est fait) ; alive : en vie par monde. */
    public void setSubBosses(List<SubBoss> list, java.util.function.BiFunction<String, Location, String> spawner,
                             java.util.function.ToIntFunction<World> alive) {
        this.subBosses = List.copyOf(list);
        this.subBossSpawner = spawner;
        this.subBossesAlive = alive;
    }

    /** Réglages propres à ce serveur : servers.<début du nom> (ex. ressource, lobby). */
    private static ConfigurationSection server(ConfigurationSection spawning, String serverName) {
        ConfigurationSection servers = spawning == null ? null : spawning.getConfigurationSection("servers");
        if (servers == null || serverName == null) {
            return null;
        }
        for (String prefix : servers.getKeys(false)) {
            if (serverName.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return servers.getConfigurationSection(prefix);
            }
        }
        return null;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        bloodNight.start();
    }

    public void reloadRules(ConfigurationSection config) {
        this.rules = SpawnRule.load(config.getConfigurationSection("spawns"), plugin.getLogger());
    }

    public List<SpawnRule> rules() {
        return rules;
    }

    public BloodNight bloodNight() {
        return bloodNight;
    }

    public boolean enabled() {
        return enabled;
    }

    public double density() {
        return density * bloodNight.density();
    }

    /** Apparitions par règle depuis le démarrage (équilibrage). */
    public Map<String, Integer> counters() {
        return counters;
    }

    // ---------- Remplacement des apparitions naturelles ----------

    /** Une apparition naturelle : en variant (règles replace), sinon un ratio (natural-ratios). */
    public void onNatural(LivingEntity entity) {
        if (!enabled) {
            return;
        }
        Location at = entity.getLocation();
        if (!guard.allowsSpawn(at)) {
            return;
        }
        // Les bébés (zombies...) ne deviennent jamais des variants : seuls les adultes sont remplacés
        boolean baby = entity instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult();
        for (SpawnRule rule : baby ? List.<SpawnRule>of() : rules) {
            if (rule.mode() != SpawnRule.Mode.REPLACE || ThreadLocalRandom.current().nextDouble() >= rule.chance() || !rule.matches(at)) {
                continue;
            }
            MobVariant variant = pickOfType(rule, entity.getType());
            if (variant != null) {
                mobs.apply(entity, variant, rollRatio(at, rule.ratioBonus()), null, false);
                counters.merge(rule.id(), 1, Integer::sum);
                if (variant.tier() == Tier.LEADER) {
                    Bukkit.getScheduler().runTask(plugin, () -> followers(entity, variant, rule));
                }
                return;
            }
        }
        if (!baby && naturalRatios.contains(entity.getType())) {
            mobs.apply(entity, mobs.vanilla(entity.getType()), rollRatio(at, 0), null, false);
        }
    }

    /** Un variant de la règle qui a le bon type de mob (plusieurs essais, selon les poids). */
    private MobVariant pickOfType(SpawnRule rule, EntityType type) {
        for (int i = 0; i < 4; i++) {
            MobVariant variant = mobs.variants().get(rule.pick());
            if (variant != null && variant.type() == type && !mobs.variants().disabled(variant.id())) {
                return variant;
            }
        }
        return null;
    }

    // ---------- Ratios ----------

    /** Ratio d'un monstre qui apparaît ici. */
    public int rollRatio(Location at, double ruleBonus) {
        World world = at.getWorld();
        double bonus = ruleBonus;
        switch (world.getEnvironment()) {
            case NETHER -> bonus += netherBonus;
            case THE_END -> bonus += endBonus;
            default -> {
                long time = world.getTime();
                if (time >= 13000 && time <= 23000) {
                    bonus += nightBonus;
                }
            }
        }
        int max = mobs.ratios().max();
        int cap = beginnerNear(at) ? beginnerMax : max;
        int ratio = mobs.ratios().roll(at, bonus, bloodNight.ratioShift(), cap);
        if (ratio == max && topRadius > 0 && mobs.ratioNear(at, topRadius, max)) {
            ratio = max - 1;
        }
        return Math.max(1, ratio);
    }

    /** Débutants (temps de jeu et niveau de métier, Beginners) ; posé par Main. */
    private Beginners beginners;

    public void beginners(Beginners beginners) {
        this.beginners = beginners;
    }

    private boolean beginnerNear(Location at) {
        if (beginners == null && beginnerHours <= 0) {
            return false;
        }
        double limitTicks = beginnerHours * 3600 * 20;
        for (Player player : mobs.players().near(at, 48)) {
            if (beginners != null ? beginners.isBeginner(player) : player.getStatistic(Statistic.PLAY_ONE_MINUTE) < limitTicks) {
                return true;
            }
        }
        return false;
    }

    /** Direction de from vers to, parmi 8 (north, north_east... ; nord = -Z dans Minecraft). */
    static String direction(Location from, Location to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] names = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};
        return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    // ---------- Directeur ----------

    private void tick() {
        if (!enabled || perPlayer <= 0) {
            return;
        }
        bloodNight.check();
        List<SpawnRule> director = rules.stream().filter(rule -> rule.mode() == SpawnRule.Mode.DIRECTOR && rule.weight() > 0).toList();
        int cap = (int) Math.round(perPlayer * density());
        for (World world : Bukkit.getWorlds()) {
            List<Player> players = new ArrayList<>(mobs.players().in(world));
            if (players.isEmpty()) {
                continue;
            }
            subBossAttempt(world, players);
            if (!director.isEmpty() && cap > 0) {
                int inWorld = mobs.countIn(world);
                java.util.Collections.shuffle(players);
                for (Player player : players) {
                    if (inWorld >= perWorld) {
                        break;
                    }
                    if (mobs.countNear(player.getLocation(), 48) < cap) {
                        inWorld += spawnAround(player, director);
                    }
                }
            }
        }
    }

    /** Un groupe d'une règle director autour du joueur ; renvoie le nombre de monstres apparus. */
    private int spawnAround(Player player, List<SpawnRule> director) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            Location spot = spot(player);
            if (spot == null) {
                continue;
            }
            List<SpawnRule> fitting = director.stream().filter(rule -> rule.matches(spot)).toList();
            SpawnRule rule = weighted(fitting, random);
            if (rule == null) {
                continue;
            }
            return spawnGroup(rule, spot);
        }
        return 0;
    }

    private static SpawnRule weighted(List<SpawnRule> rules, ThreadLocalRandom random) {
        double total = rules.stream().mapToDouble(SpawnRule::weight).sum();
        if (total <= 0) {
            return null;
        }
        double pick = random.nextDouble() * total;
        for (SpawnRule rule : rules) {
            pick -= rule.weight();
            if (pick < 0) {
                return rule;
            }
        }
        return rules.getLast();
    }

    /** Fait apparaître le groupe d'une règle ici (meneur d'abord s'il y en a un) ; renvoie le nombre de monstres. */
    public int spawnGroup(SpawnRule rule, Location spot) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = 0;
        UUID leaderId = null;
        MobVariant leader = rule.leader() == null ? null : mobs.variants().get(rule.leader());
        if (leader != null && random.nextDouble() < rule.leaderChance()) {
            LivingEntity spawned = mobs.spawn(leader, rollRatio(spot, rule.ratioBonus() + 0.5), spot, null);
            leaderId = spawned.getUniqueId();
            count++;
            if (!leader.followers().isEmpty()) {
                count += followers(spawned, leader, rule);
                counters.merge(rule.id(), count, Integer::sum);
                return count;
            }
        }
        int size = random.nextInt(rule.groupMin(), rule.groupMax() + 1);
        for (int i = 0; i < size; i++) {
            MobVariant variant = mobs.variants().get(rule.pick());
            if (variant == null || mobs.variants().disabled(variant.id())) {
                continue;
            }
            Location at = near(spot, i == 0 ? 0 : 2.5);
            if (at != null) {
                mobs.spawn(variant, rollRatio(at, rule.ratioBonus()), at, leaderId);
                count++;
            }
        }
        counters.merge(rule.id(), count, Integer::sum);
        return count;
    }

    /** Les suiveurs d'un meneur (followers de son variant, sinon les variants de la règle). */
    private int followers(LivingEntity leaderEntity, MobVariant leader, SpawnRule rule) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int size = leader.followersMax() > 0 ? random.nextInt(leader.followersMin(), leader.followersMax() + 1)
                : random.nextInt(rule.groupMin(), rule.groupMax() + 1);
        int count = 0;
        for (int i = 0; i < size; i++) {
            String id = leader.followers().isEmpty() ? rule.pick() : pick(leader.followers(), random);
            MobVariant variant = mobs.variants().get(id);
            Location at = near(leaderEntity.getLocation(), 3);
            if (variant != null && variant.tier() != Tier.LEADER && at != null) {
                mobs.spawn(variant, rollRatio(at, rule.ratioBonus()), at, leaderEntity.getUniqueId());
                count++;
            }
        }
        return count;
    }

    private static String pick(Map<String, Integer> weights, ThreadLocalRandom random) {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int pick = random.nextInt(Math.max(1, total));
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    /** Un endroit où apparaître autour du joueur : sol, deux blocs libres, chunk chargé, loin de tous les joueurs. */
    private Location spot(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location base = player.getLocation();
        World world = base.getWorld();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = random.nextDouble(distanceMin, distanceMax);
        int x = (int) Math.floor(base.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(base.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        Block feet;
        boolean underground = base.getBlock().getLightFromSky() == 0 && world.getEnvironment() == World.Environment.NORMAL;
        if (underground || world.getEnvironment() == World.Environment.NETHER) {
            feet = floor(world.getBlockAt(x, base.getBlockY(), z), 10);
        } else {
            feet = world.getHighestBlockAt(x, z).getRelative(0, 1, 0);
            feet = standable(feet) ? feet : null;
        }
        if (feet == null) {
            return null;
        }
        Location spot = feet.getLocation().add(0.5, 0, 0.5);
        if (!guard.allowsSpawn(spot) || mobs.players().any(spot, distanceMin * 0.8) || crowded(feet.getChunk())) {
            return null;
        }
        return spot;
    }

    private boolean crowded(Chunk chunk) {
        int count = 0;
        for (var mob : mobs.active()) {
            Location at = mob.entity().getLocation();
            if (at.getWorld().equals(chunk.getWorld()) && at.getBlockX() >> 4 == chunk.getX() && at.getBlockZ() >> 4 == chunk.getZ()
                    && ++count >= perChunk) {
                return true;
            }
        }
        return false;
    }

    /** Le premier endroit où tenir debout à moins de range blocs plus haut ou plus bas. */
    private static Block floor(Block from, int range) {
        for (int dy = 0; dy <= range; dy++) {
            for (int sign : new int[]{-1, 1}) {
                Block feet = from.getRelative(0, dy * sign, 0);
                if (standable(feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static boolean standable(Block feet) {
        return feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, 1, 0).isLiquid()
                && feet.getRelative(0, -1, 0).getType().isSolid();
    }

    private static Location near(Location spot, double spread) {
        if (spread <= 0) {
            return spot;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Block feet = floor(spot.clone().add(random.nextDouble(-spread, spread), 0, random.nextDouble(-spread, spread)).getBlock(), 3);
        return feet == null ? spot : feet.getLocation().add(0.5, 0, 0.5);
    }


    // ---------- Sous-boss ----------

    /** Un sous-boss de temps en temps près d'un joueur, là où ses conditions sont remplies ; annoncé à ceux du monde. */
    private void subBossAttempt(World world, List<Player> players) {
        if (subBosses.isEmpty() || subBossSpawner == null || subBossesPerWorld <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < subBossCooldown.getOrDefault(world.getUID(), 0L) || subBossesAlive.applyAsInt(world) >= subBossesPerWorld) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (SubBoss boss : subBosses) {
            if (random.nextDouble() >= boss.chance()) {
                continue;
            }
            Location spot = spot(players.get(random.nextInt(players.size())));
            if (spot == null || !boss.where().matches(spot) || subBossSpawner.apply(boss.id(), spot) != null) {
                continue;
            }
            subBossCooldown.put(world.getUID(), now + Math.round(boss.cooldownMinutes() * 60_000));
            counters.merge("sous-boss:" + boss.id(), 1, Integer::sum);
            for (Player online : world.getPlayers()) {
                if (online.getLocation().distanceSquared(spot) < 200 * 200) {
                    messages.send(online, "sub-boss.appeared", "name", boss.name(), "x", String.valueOf(spot.getBlockX()),
                            "z", String.valueOf(spot.getBlockZ()), "distance", String.valueOf((int) online.getLocation().distance(spot)),
                            "direction", messages.plain(online, "direction." + direction(online.getLocation(), spot)));
                    online.playSound(online, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.7f);
                }
            }
            return;
        }
    }
}
