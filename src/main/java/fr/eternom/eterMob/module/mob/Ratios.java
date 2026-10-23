package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Les ratios de config.yml (multiplicateur, chance, statut, perce-armure) et leur tirage, favorisé par la profondeur. */
public class Ratios {

    /** armorPiercing : part (0 à 1) de l'armure et des enchantements de protection d'un joueur que ses coups ignorent. */
    public record Level(double multiplier, double weight, String status, double armorPiercing) {
    }

    /** Perce-armure quand config.yml ne le donne pas (config d'avant EterMob 1.2.3), par rang de ratio. */
    private static final double[] DEFAULT_PIERCING = {0, 0.1, 0.2, 0.35, 0.5};

    private final List<Level> levels;
    private final double depthBonus;

    public Ratios(List<Level> levels, double depthBonus) {
        if (levels.isEmpty()) {
            levels = List.of(new Level(1, 1, "<gray>Ordinaire", 0));
        }
        this.levels = List.copyOf(levels);
        this.depthBonus = Math.max(0, depthBonus);
    }

    public static Ratios load(ConfigurationSection config) {
        List<Level> levels = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("ratios")) {
            double piercing = DEFAULT_PIERCING[Math.min(levels.size(), DEFAULT_PIERCING.length - 1)];
            levels.add(new Level(number(map.get("multiplier"), 1), Math.max(0, number(map.get("weight"), 1)),
                    String.valueOf(map.get("status") == null ? "" : map.get("status")),
                    Math.clamp(number(map.get("armor-piercing"), piercing), 0, 1)));
        }
        return new Ratios(levels, config.getDouble("depth-bonus", 0.01));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /** Ratio le plus fort (= nombre de ratios). */
    public int max() {
        return levels.size();
    }

    /** Ratio ramené entre 1 et max. */
    public int clamp(int ratio) {
        return Math.clamp(ratio, 1, levels.size());
    }

    public double multiplier(int ratio) {
        return levels.get(clamp(ratio) - 1).multiplier();
    }

    public double armorPiercing(int ratio) {
        return levels.get(clamp(ratio) - 1).armorPiercing();
    }

    public String status(int ratio) {
        return levels.get(clamp(ratio) - 1).status();
    }

    /** Tire un ratio : sous le niveau de la mer, chaque bloc augmente la chance du ratio n de (n - 1) × depthBonus. */
    public int roll(Location location) {
        double depth = Math.max(0, location.getWorld().getSeaLevel() - location.getY());
        double total = 0;
        double[] weights = new double[levels.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = levels.get(i).weight() * (1 + depth * depthBonus * i);
            total += weights[i];
        }
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i + 1;
            }
        }
        return 1;
    }
}
