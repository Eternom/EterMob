package fr.eternom.eterMob.module.ability;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Réglages d'une capacité écrits dans le code (monstres codés en dur) : Spec.of("damage", 16, "cast", 0.8, ...), comme
 * une section de config. Les listes (states, range...) se donnent en List.of(...).
 */
public final class Spec {

    private Spec() {
    }

    public static ConfigurationSection of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Spec.of : clés et valeurs par paires");
        }
        MemoryConfiguration section = new MemoryConfiguration();
        for (int i = 0; i < keyValues.length; i += 2) {
            section.set(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return section;
    }
}
