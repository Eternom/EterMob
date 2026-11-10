package fr.eternom.eterMob.module.spawn;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * La Nuit de sang : certaines nuits (chance à la tombée de la nuit du monde principal), ou par /etermob event : plus de
 * monstres (density) et plus forts (ratio-shift rangs de plus). Annoncée à son début et à sa fin.
 */
public final class BloodNight {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final double chance;
    private final double densityFactor;
    private final int shift;
    private long until;
    private boolean wasNight;

    BloodNight(JavaPlugin plugin, Messages messages, ConfigurationSection config) {
        this.plugin = plugin;
        this.messages = messages;
        this.chance = config == null ? 0.05 : Math.clamp(config.getDouble("chance", 0.05), 0, 1);
        this.densityFactor = config == null ? 2 : Math.max(1, config.getDouble("density", 2));
        this.shift = config == null ? 1 : Math.max(0, config.getInt("ratio-shift", 1));
    }

    void start() {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        wasNight = main != null && night(main);
    }

    private static boolean night(World world) {
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    public boolean active() {
        return System.currentTimeMillis() < until;
    }

    double density() {
        return active() ? densityFactor : 1;
    }

    int ratioShift() {
        return active() ? shift : 0;
    }

    /** Appelé par le directeur : tombée de la nuit (tirage), fin. */
    void check() {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (main == null || main.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        boolean night = night(main);
        if (night && !wasNight && !active() && ThreadLocalRandom.current().nextDouble() < chance) {
            // Jusqu'au lever du jour (10 000 ticks de nuit = 8 min 20)
            begin((23000 - main.getTime()) * 50L);
        }
        if (!night && wasNight && active()) {
            end();
        }
        wasNight = night;
    }

    /** Commence pour millis millisecondes (commande ou tirage). */
    public void begin(long millis) {
        boolean was = active();
        until = System.currentTimeMillis() + Math.max(1000, millis);
        if (!was) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                messages.send(player, "blood-night.start");
                player.playSound(player, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.6f);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active() && until != 0) {
                    end();
                }
            }, Math.max(20, millis / 50) + 20);
        }
    }

    public void end() {
        if (until == 0) {
            return;
        }
        until = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.send(player, "blood-night.end");
        }
    }

    public long remainingMillis() {
        return Math.max(0, until - System.currentTimeMillis());
    }
}
