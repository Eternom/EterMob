package fr.eternom.eterMob.module.spawn;

import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Une règle d'apparition (config.yml > spawns) : quoi, où, quand, comment.
 * <pre>
 *   mode          replace : une apparition naturelle de ce type de mob devient un des variants (chance) ;
 *                 director : EterMob fait apparaître des groupes autour des joueurs (weight : part des tirages) ;
 *                 boss : un boss apparaît de lui-même, rarement (chance par tentative, cooldown-minutes par monde)
 *   variants      { id: poids }  (boss : boss: id)
 *   worlds, environments (NORMAL, NETHER, THE_END), biomes (groupes : plains, forests, dark, swamps, jungles,
 *   mountains, dry, cold, rivers ; ou un biome : minecraft:plains), y [min, max], light [min, max] (lumière du bloc),
 *   time (day, night, any), weather (clear, rain, thunder, any), cave (true : sans lumière du ciel)
 *   group         { size: [1, 3], leader: id, leader-chance: 0.2 } (director) : un groupe, parfois mené
 *   ratio-bonus   ratios forts plus fréquents ici (0,5 : +50 % par rang)
 * </pre>
 */
public final class SpawnRule {

    public enum Mode { REPLACE, DIRECTOR, BOSS }

    private final String id;
    private final Mode mode;
    private final double chance;
    private final double weight;
    private final Map<String, Integer> variants;
    private final String boss;
    private final double cooldownMinutes;
    private final Set<String> worlds;
    private final Set<World.Environment> environments;
    private final List<Spawns.Group> groups;
    private final Set<NamespacedKey> biomes;
    private final double yMin;
    private final double yMax;
    private final int lightMin;
    private final int lightMax;
    private final String time;
    private final String weather;
    private final Boolean cave;
    private final int groupMin;
    private final int groupMax;
    private final String leader;
    private final double leaderChance;
    private final double ratioBonus;

    private SpawnRule(String id, ConfigurationSection section, Logger logger) {
        this.id = id;
        Mode parsed;
        try {
            parsed = Mode.valueOf(section.getString("mode", "director").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Règle " + id + " : mode inconnu « " + section.getString("mode") + " » (replace, director, boss)");
            parsed = Mode.DIRECTOR;
        }
        this.mode = parsed;
        this.chance = Math.clamp(section.getDouble("chance", parsed == Mode.BOSS ? 0.02 : 0.2), 0, 1);
        this.weight = Math.max(0, section.getDouble("weight", 1));
        Map<String, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection list = section.getConfigurationSection("variants");
        if (list != null) {
            list.getKeys(false).forEach(key -> weights.put(key.toLowerCase(Locale.ROOT), Math.max(1, list.getInt(key, 1))));
        }
        this.variants = weights;
        this.boss = section.getString("boss");
        this.cooldownMinutes = Math.max(0, section.getDouble("cooldown-minutes", 120));
        this.worlds = new HashSet<>(section.getStringList("worlds"));
        Set<World.Environment> envs = new HashSet<>();
        for (String env : section.getStringList("environments")) {
            try {
                envs.add(World.Environment.valueOf(env.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("Règle " + id + " : environnement inconnu « " + env + " »");
            }
        }
        this.environments = envs;
        List<Spawns.Group> groupList = new ArrayList<>();
        Set<NamespacedKey> biomeKeys = new HashSet<>();
        for (String biome : section.getStringList("biomes")) {
            Spawns.Group group = Spawns.group(biome);
            if (group != null) {
                groupList.add(group);
            } else {
                NamespacedKey key = NamespacedKey.fromString(biome.toLowerCase(Locale.ROOT));
                if (key == null) {
                    logger.warning("Règle " + id + " : biome illisible « " + biome + " » (groupes : " + Spawns.groupNames() + ")");
                } else {
                    biomeKeys.add(key);
                }
            }
        }
        this.groups = groupList;
        this.biomes = biomeKeys;
        List<Double> y = section.getDoubleList("y");
        this.yMin = y.size() == 2 ? y.get(0) : -4096;
        this.yMax = y.size() == 2 ? y.get(1) : 4096;
        List<Integer> light = section.getIntegerList("light");
        this.lightMin = light.size() == 2 ? light.get(0) : 0;
        this.lightMax = light.size() == 2 ? light.get(1) : 15;
        this.time = section.getString("time", "any").toLowerCase(Locale.ROOT);
        this.weather = section.getString("weather", "any").toLowerCase(Locale.ROOT);
        this.cave = section.isSet("cave") ? section.getBoolean("cave") : null;
        ConfigurationSection group = section.getConfigurationSection("group");
        List<Integer> size = group == null ? List.of() : group.getIntegerList("size");
        this.groupMin = size.size() == 2 ? Math.max(1, size.get(0)) : 1;
        this.groupMax = size.size() == 2 ? Math.max(groupMin, size.get(1)) : groupMin;
        this.leader = group == null ? null : group.getString("leader");
        this.leaderChance = group == null ? 0 : Math.clamp(group.getDouble("leader-chance", 0.2), 0, 1);
        this.ratioBonus = Math.max(0, section.getDouble("ratio-bonus", 0));
    }

    public static List<SpawnRule> load(ConfigurationSection section, Logger logger) {
        List<SpawnRule> rules = new ArrayList<>();
        if (section == null) {
            return rules;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection rule = section.getConfigurationSection(key);
            if (rule != null && rule.getBoolean("enabled", true)) {
                rules.add(new SpawnRule(key.toLowerCase(Locale.ROOT), rule, logger));
            }
        }
        return rules;
    }

    /** Les conditions de lieu et de moment sont-elles remplies ici ? */
    public boolean matches(Location at) {
        World world = at.getWorld();
        if (!worlds.isEmpty() && !worlds.contains(world.getName())) {
            return false;
        }
        if (!environments.isEmpty() && !environments.contains(world.getEnvironment())) {
            return false;
        }
        if (at.getY() < yMin || at.getY() > yMax) {
            return false;
        }
        Block block = at.getBlock();
        int light = block.getLightLevel();
        if (light < lightMin || light > lightMax) {
            return false;
        }
        if (cave != null && (block.getLightFromSky() == 0) != cave) {
            return false;
        }
        if (!time.equals("any") && world.getEnvironment() == World.Environment.NORMAL) {
            long day = world.getTime();
            boolean night = day >= 13000 && day <= 23000;
            if (time.equals("night") != night) {
                return false;
            }
        }
        if (!weather.equals("any")) {
            boolean thunder = world.isThundering();
            boolean rain = world.hasStorm();
            boolean ok = switch (weather) {
                case "thunder" -> thunder;
                case "rain" -> rain;
                case "clear" -> !rain;
                default -> true;
            };
            if (!ok) {
                return false;
            }
        }
        if (groups.isEmpty() && biomes.isEmpty()) {
            return true;
        }
        Biome biome = block.getBiome();
        for (Spawns.Group group : groups) {
            if (Spawns.in(group, biome)) {
                return true;
            }
        }
        return biomes.contains(biome.getKey());
    }

    /** Un variant de la règle, au hasard selon les poids (null s'il n'y en a pas). */
    public String pick() {
        int total = variants.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return null;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<String, Integer> entry : variants.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String id() {
        return id;
    }

    public Mode mode() {
        return mode;
    }

    public double chance() {
        return chance;
    }

    public double weight() {
        return weight;
    }

    public Map<String, Integer> variants() {
        return variants;
    }

    public String boss() {
        return boss;
    }

    public double cooldownMinutes() {
        return cooldownMinutes;
    }

    public int groupMin() {
        return groupMin;
    }

    public int groupMax() {
        return groupMax;
    }

    public String leader() {
        return leader;
    }

    public double leaderChance() {
        return leaderChance;
    }

    public double ratioBonus() {
        return ratioBonus;
    }
}
