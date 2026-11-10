package fr.eternom.eterMob.module.mob;

import org.bukkit.Material;

/**
 * isBlock / isItem passent par les registres du serveur : sans serveur (tests), on ne peut pas vérifier, la matière est
 * acceptée telle quelle.
 */
public final class Materials {

    private Materials() {
    }

    public static boolean block(Material material) {
        try {
            return material.isBlock();
        } catch (LinkageError | RuntimeException e) {
            return true;
        }
    }

    public static boolean item(Material material) {
        try {
            return material.isItem();
        } catch (LinkageError | RuntimeException e) {
            return true;
        }
    }
}
