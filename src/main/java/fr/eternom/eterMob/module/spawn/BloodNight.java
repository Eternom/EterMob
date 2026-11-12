package fr.eternom.eterMob.module.spawn;

import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La Nuit de sang : certaines nuits (chance au coucher du soleil du monde principal), ou par /etermob event : plus de
 * monstres (density) et plus forts (ratio-shift rangs de plus).
 * <ul>
 *   <li>avant : le tirage se fait au coucher du soleil, annoncé ~50 s avant la nuit (blood-night.soon) ;</li>
 *   <li>pendant : une barre rouge avec le temps restant, pour tous les joueurs connectés ici ;</li>
 *   <li>après : un résumé (ses monstres tués pendant la nuit, le meilleur chasseur).</li>
 * </ul>
 */
public final class BloodNight {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final double chance;
    private final double densityFactor;
    private final int shift;
    private long until;
    private long length;
    private boolean wasNight;
    private boolean wasDusk;
    /** Tirée au coucher du soleil : commence à la nuit. */
    private boolean pending;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private BukkitTask barTask;

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
        wasDusk = main != null && dusk(main);
    }

    private static boolean night(World world) {
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    private static boolean dusk(World world) {
        long time = world.getTime();
        return time >= 12000 && time < 13000;
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

    /** Appelé par le directeur : coucher du soleil (tirage, annonce), tombée de la nuit (début), fin. */
    void check() {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (main == null || main.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        boolean night = night(main);
        boolean dusk = dusk(main);
        if (dusk && !wasDusk && !active() && !pending && ThreadLocalRandom.current().nextDouble() < chance) {
            pending = true;
            for (Player player : Bukkit.getOnlinePlayers()) {
                messages.send(player, "blood-night.soon");
                player.playSound(player, Sound.AMBIENT_CAVE, 0.6f, 0.5f);
            }
        }
        if (night && !wasNight && !active()) {
            // Tirée au coucher du soleil ; un serveur démarré pendant le coucher tire ici
            if (pending || !wasDusk && ThreadLocalRandom.current().nextDouble() < chance) {
                // Jusqu'au lever du jour (10 000 ticks de nuit = 8 min 20)
                begin((23000 - main.getTime()) * 50L);
            }
            pending = false;
        }
        if (!night && wasNight && active()) {
            end();
        }
        wasNight = night;
        wasDusk = dusk;
    }

    /** Commence pour millis millisecondes (commande ou tirage). */
    public void begin(long millis) {
        boolean was = active();
        until = System.currentTimeMillis() + Math.max(1000, millis);
        length = Math.max(1000, millis);
        if (!was) {
            kills.clear();
            names.clear();
            for (Player player : Bukkit.getOnlinePlayers()) {
                messages.send(player, "blood-night.start");
                player.playSound(player, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.6f);
            }
            barTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBar, 0, 20);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active() && until != 0) {
                    end();
                }
            }, Math.max(20, millis / 50) + 20);
        }
    }

    /** La barre : temps restant, montrée à tous les joueurs connectés ici. */
    private void updateBar() {
        if (!active()) {
            return;
        }
        long remaining = remainingMillis();
        bar.progress((float) Math.clamp(remaining / (double) Math.max(1, length), 0, 1));
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.name(messages.get(player, "blood-night.bar", "time", fr.eternom.eterLib.EterLib.get().formatDuration(player, remaining / 1000)));
            player.showBossBar(bar);
        }
    }

    /** Un monstre tué pendant la Nuit de sang (pour le résumé). */
    public void record(Player killer) {
        if (active()) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            names.put(killer.getUniqueId(), killer.getName());
        }
    }

    public void end() {
        if (until == 0) {
            return;
        }
        until = 0;
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
        }
        var best = kills.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bar);
            messages.send(player, "blood-night.end");
            int mine = kills.getOrDefault(player.getUniqueId(), 0);
            if (mine > 0) {
                messages.send(player, "blood-night.summary-mine", "count", String.valueOf(mine));
            }
            if (best != null) {
                messages.send(player, "blood-night.summary-best", "player", names.getOrDefault(best.getKey(), "?"),
                        "count", String.valueOf(best.getValue()));
            }
        }
        kills.clear();
        names.clear();
    }

    public long remainingMillis() {
        return Math.max(0, until - System.currentTimeMillis());
    }
}
