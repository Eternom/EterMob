package fr.eternom.eterMob.module.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La table de menace d'un monstre : ce que chaque joueur lui a fait (coups, tirs, soins de ses alliés, présence au
 * contact), qui s'efface avec le temps. C'est la base du choix de sa cible (Brain). Pure : testée sans serveur.
 */
public final class Threat {

    /** En dessous, un joueur est oublié. */
    static final double FORGET = 0.05;

    private final Map<UUID, Double> values = new HashMap<>();

    public void add(UUID player, double amount) {
        if (amount > 0) {
            values.merge(player, amount, Double::sum);
        }
    }

    public double get(UUID player) {
        return values.getOrDefault(player, 0.0);
    }

    public double max() {
        double max = 0;
        for (double value : values.values()) {
            max = Math.max(max, value);
        }
        return max;
    }

    /** Multiplie toute la menace par factor (0 à 1) et oublie ce qui tombe sous FORGET. */
    public void decay(double factor) {
        values.replaceAll((player, value) -> value * factor);
        values.values().removeIf(value -> value < FORGET);
    }

    public void remove(UUID player) {
        values.remove(player);
    }

    public void clear() {
        values.clear();
    }

    public Map<UUID, Double> view() {
        return Collections.unmodifiableMap(values);
    }

    /** Menace normalisée (0 à 1) : par rapport au plus menaçant, et jamais au-dessus d'1. */
    public double normalized(UUID player) {
        double max = max();
        return max <= 0 ? 0 : get(player) / max;
    }
}
