package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les ratios de config.yml (du plus faible au plus fort) et leur tirage. Chaque ratio a ses multiplicateurs séparés :
 * vie, dégâts (coups), capacités (dégâts et puissance des états) ; et son perce-armure, sa ténacité (contrôles plus
 * courts), sa résistance au recul, son butin et son expérience. Le tirage est favorisé par la profondeur.
 */
public class Ratios {

    /**
     * @param health        × la vie
     * @param damage        × les dégâts des coups
     * @param ability       × les dégâts et la puissance des états de ses capacités
     * @param weight        chance d'être tiré, relative aux autres (à la surface)
     * @param status        statut affiché (MiniMessage)
     * @param armorPiercing part (0 à 1) de l'armure et de la protection des joueurs que ses coups ignorent
     * @param tenacity      part (0 à 1) des contrôles qu'il ignore (EterLib : durée réduite)
     * @param knockback     résistance au recul (0 à 1)
     * @param loot          × la chance de son butin
     * @param xp            × son expérience
     */
    public record Level(double health, double damage, double ability, double weight, String status, double armorPiercing,
                        double tenacity, double knockback, double loot, double xp) {
    }

    /** Valeurs par défaut par rang quand config.yml ne les donne pas (configs d'avant 2.0.0). */
    private static final double[] PIERCING = {0, 0.1, 0.2, 0.35, 0.5};
    private static final double[] TENACITY = {0, 0.05, 0.1, 0.2, 0.3};
    private static final double[] KNOCKBACK = {0, 0.1, 0.2, 0.35, 0.5};

    private final List<Level> levels;
    private final double depthBonus;

    public Ratios(List<Level> levels, double depthBonus) {
        if (levels.isEmpty()) {
            levels = List.of(new Level(1, 1, 1, 1, "<gray>Ordinaire", 0, 0, 0, 1, 1));
        }
        this.levels = List.copyOf(levels);
        this.depthBonus = Math.max(0, depthBonus);
    }

    public static Ratios load(ConfigurationSection config) {
        List<Level> levels = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("ratios")) {
            int index = Math.min(levels.size(), PIERCING.length - 1);
            double multiplier = number(map.get("multiplier"), 1);
            levels.add(new Level(
                    Math.max(0.1, number(map.get("health"), multiplier)),
                    Math.max(0.1, number(map.get("damage"), multiplier)),
                    Math.max(0.1, number(map.get("ability"), multiplier)),
                    Math.max(0, number(map.get("weight"), 1)),
                    String.valueOf(map.get("status") == null ? "" : map.get("status")),
                    Math.clamp(number(map.get("armor-piercing"), PIERCING[index]), 0, 1),
                    Math.clamp(number(map.get("tenacity"), TENACITY[index]), 0, 0.9),
                    Math.clamp(number(map.get("knockback-resistance"), KNOCKBACK[index]), 0, 1),
                    Math.max(0, number(map.get("loot"), 1 + index * 0.25)),
                    Math.max(0, number(map.get("xp"), multiplier))));
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

    public Level level(int ratio) {
        return levels.get(clamp(ratio) - 1);
    }

    /** Multiplicateur de vie (menu, compatibilité). */
    public double multiplier(int ratio) {
        return level(ratio).health();
    }

    public double armorPiercing(int ratio) {
        return level(ratio).armorPiercing();
    }

    public String status(int ratio) {
        return level(ratio).status();
    }

    /**
     * Tire un ratio : sous le niveau de la mer, chaque bloc augmente la chance du ratio n de (n - 1) × depthBonus ;
     * bonus : facteur en plus pour les ratios forts (nuit, Nether, thème...), même formule ; shift : ratios de plus
     * (Nuit de sang) ; cap : ratio maximal (protection des débutants, un seul Fléau à la fois...).
     */
    public int roll(Location location, double bonus, int shift, int cap) {
        double depth = Math.max(0, location.getWorld().getSeaLevel() - location.getY());
        return roll(depth, bonus, shift, cap, ThreadLocalRandom.current().nextDouble());
    }

    /** Le tirage sans monde (testé) : random de 0 à 1. */
    int roll(double depth, double bonus, int shift, int cap, double random) {
        double total = 0;
        double[] weights = new double[levels.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = levels.get(i).weight() * (1 + depth * depthBonus * i) * (1 + Math.max(0, bonus) * i);
            total += weights[i];
        }
        double pick = random * total;
        int ratio = levels.size();
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                ratio = i + 1;
                break;
            }
        }
        return Math.clamp(ratio + Math.max(0, shift), 1, Math.clamp(cap, 1, levels.size()));
    }
}
