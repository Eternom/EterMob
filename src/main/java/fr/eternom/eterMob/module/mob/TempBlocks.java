package fr.eternom.eterMob.module.mob;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocs posés par une capacité pour quelques secondes (piliers d'os...) : toujours dans l'air (jamais à la place d'un
 * bloc), remis en air à temps, sans rien lâcher si un joueur les casse (MobListener), tous retirés à l'arrêt. Notés dans
 * temp-blocks.yml (toutes les 5 s s'il y a du nouveau) : après un plantage, ils sont retirés au démarrage suivant, jamais
 * laissés pour toujours.
 */
public final class TempBlocks {

    /** Bloc posé → ce qu'on a posé (retiré seulement s'il est encore là). */
    private static final Map<Block, Material> PLACED = new HashMap<>();
    private static boolean dirty;
    private static File file;

    private TempBlocks() {
    }

    /** Démarrage : retire ce qu'un arrêt brutal a laissé, puis note régulièrement les blocs en place. */
    public static void start(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "temp-blocks.yml");
        if (file.exists()) {
            YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
            int removed = 0;
            for (String line : saved.getStringList("blocks")) {
                String[] parts = line.split(";");
                World world = parts.length == 5 ? Bukkit.getWorld(parts[0]) : null;
                Material material = parts.length == 5 ? Material.matchMaterial(parts[4]) : null;
                if (world == null || material == null) {
                    continue;
                }
                try {
                    Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    if (block.getType() == material) {
                        block.setType(Material.AIR, false);
                        removed++;
                    }
                } catch (NumberFormatException ignored) {
                    // ligne illisible : ignorée
                }
            }
            if (removed > 0) {
                plugin.getLogger().info(removed + " bloc(s) temporaire(s) laissé(s) par un arrêt brutal retiré(s)");
            }
            file.delete();
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 100, 100);
    }

    /** Pose material dans ce bloc s'il est vide, pour ticks ticks. */
    public static boolean place(Block block, Material material, long ticks) {
        if (!block.getType().isAir() || PLACED.containsKey(block)) {
            return false;
        }
        block.setType(material, false);
        PLACED.put(block, material);
        dirty = true;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(TempBlocks.class), () -> clear(block), ticks);
        return true;
    }

    public static boolean is(Block block) {
        return PLACED.containsKey(block);
    }

    /** Le bloc a été cassé ou remplacé : il n'est plus à retirer. */
    public static void forget(Block block) {
        if (PLACED.remove(block) != null) {
            dirty = true;
        }
    }

    private static void clear(Block block) {
        Material material = PLACED.remove(block);
        if (material != null && block.getType() == material) {
            block.setType(Material.AIR, false);
        }
        dirty = true;
    }

    private static void save() {
        dirty = false;
        if (file == null) {
            return;
        }
        if (PLACED.isEmpty()) {
            file.delete();
            return;
        }
        List<String> lines = new ArrayList<>();
        PLACED.forEach((block, material) -> lines.add(block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";"
                + block.getZ() + ";" + material.name()));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", lines);
        try {
            yaml.save(file);
        } catch (IOException e) {
            JavaPlugin.getProvidingPlugin(TempBlocks.class).getLogger().warning("temp-blocks.yml non écrit : " + e.getMessage());
        }
    }

    /** Arrêt du plugin : retire tout ce qui reste. */
    public static void clearAll() {
        for (Block block : PLACED.keySet().toArray(new Block[0])) {
            clear(block);
        }
        if (file != null) {
            file.delete();
        }
    }
}
