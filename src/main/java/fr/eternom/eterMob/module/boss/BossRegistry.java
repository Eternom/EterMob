package fr.eternom.eterMob.module.boss;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registre des boss en vie (plugins/EterMob/bosses.yml) : numéro, boss, monde, position. Il sert à les lister et à les
 * supprimer même quand leur chunk n'est pas chargé, et après un redémarrage. Un boss à supprimer qui n'est pas chargé
 * est marqué : il est retiré dès qu'il se charge.
 */
public class BossRegistry {

    public record Entry(int number, UUID uuid, String boss, String world, double x, double y, double z, boolean removing) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public BossRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bosses.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("bosses");
        if (section == null) {
            return entries;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection boss = section.getConfigurationSection(key);
            if (boss == null) {
                continue;
            }
            entries.add(new Entry(Integer.parseInt(key), UUID.fromString(boss.getString("uuid", "")), boss.getString("boss", ""),
                    boss.getString("world", ""), boss.getDouble("x"), boss.getDouble("y"), boss.getDouble("z"),
                    boss.getBoolean("removing")));
        }
        return entries;
    }

    public Entry find(UUID uuid) {
        return entries().stream().filter(entry -> entry.uuid().equals(uuid)).findFirst().orElse(null);
    }

    public Entry find(int number) {
        return entries().stream().filter(entry -> entry.number() == number).findFirst().orElse(null);
    }

    /** Nouveau boss : le plus petit numéro libre. */
    public int add(UUID uuid, String boss, Location location) {
        int number = 1;
        while (data.contains("bosses." + number)) {
            number++;
        }
        data.set("bosses." + number + ".uuid", uuid.toString());
        data.set("bosses." + number + ".boss", boss);
        move(number, location);
        return number;
    }

    /** Dernière position connue (rafraîchie pendant le combat). */
    public void update(UUID uuid, Location location) {
        Entry entry = find(uuid);
        if (entry != null) {
            move(entry.number(), location);
        }
    }

    private void move(int number, Location location) {
        String path = "bosses." + number;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", Math.round(location.getX() * 10) / 10.0);
        data.set(path + ".y", Math.round(location.getY() * 10) / 10.0);
        data.set(path + ".z", Math.round(location.getZ() * 10) / 10.0);
        save();
    }

    public void markRemoving(int number) {
        data.set("bosses." + number + ".removing", true);
        save();
    }

    public void remove(UUID uuid) {
        Entry entry = find(uuid);
        if (entry != null) {
            data.set("bosses." + entry.number(), null);
            save();
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("bosses.yml non enregistré : " + e.getMessage());
        }
    }
}
