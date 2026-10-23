package fr.eternom.eterMob.module.mob;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocs posés par un variant pour quelques secondes (toiles...) : remis en air à temps, sans rien lâcher si un joueur
 * les casse (MobListener), et tous retirés à l'arrêt du plugin. Seulement dans l'air : jamais à la place d'un bloc.
 */
public final class TempBlocks {

    /** Bloc posé → ce qu'on a posé (retiré seulement s'il est encore là). */
    private static final Map<Block, Material> PLACED = new HashMap<>();

    private TempBlocks() {
    }

    /** Pose material dans ce bloc s'il est vide, pour ticks ticks. */
    public static boolean place(Block block, Material material, long ticks) {
        if (!block.getType().isAir() || PLACED.containsKey(block)) {
            return false;
        }
        block.setType(material, false);
        PLACED.put(block, material);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(TempBlocks.class), () -> clear(block), ticks);
        return true;
    }

    public static boolean is(Block block) {
        return PLACED.containsKey(block);
    }

    /** Le bloc a été cassé ou remplacé : il n'est plus à retirer. */
    public static void forget(Block block) {
        PLACED.remove(block);
    }

    private static void clear(Block block) {
        Material material = PLACED.remove(block);
        if (material != null && block.getType() == material) {
            block.setType(Material.AIR, false);
        }
    }

    /** Arrêt du plugin : retire tout ce qui reste. */
    public static void clearAll() {
        for (Block block : PLACED.keySet().toArray(new Block[0])) {
            clear(block);
        }
    }
}
