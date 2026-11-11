package fr.eternom.eterMob.module.spawn;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les débutants, qui ne croisent pas de monstres plus forts que beginner.max-ratio :
 * <ul>
 *   <li>moins de beginner.hours heures de jeu (sur ce serveur) ;</li>
 *   <li>ou, jusqu'à beginner.max-hours, tant que leur niveau de métier (EterJob) est sous beginner.job-level : un joueur
 *   qui progresse peu reste protégé plus longtemps.</li>
 * </ul>
 * Le niveau de métier est lu en tâche de fond (base d'EterJob) et gardé en mémoire : la décision à chaque apparition
 * ne touche pas la base. Le joueur apprend son statut à la connexion, puis une fois quand il le perd.
 */
public final class Beginners implements Listener {

    private static final NamespacedKey ENDED = new NamespacedKey("etermob", "beginner_ended");
    private static final double TICKS_PER_HOUR = 3600 * 20;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final double hours;
    private final int jobLevel;
    private final double maxHours;
    private final int maxRatio;
    /** Niveau du métier actuel des joueurs connectés (0 : pas de métier ou EterJob absent). */
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public Beginners(JavaPlugin plugin, Messages messages, ConfigurationSection beginner) {
        this.plugin = plugin;
        this.messages = messages;
        this.hours = beginner == null ? 5 : Math.max(0, beginner.getDouble("hours", 5));
        this.jobLevel = beginner == null ? 5 : Math.max(0, beginner.getInt("job-level", 5));
        this.maxHours = beginner == null ? 15 : Math.max(this.hours, beginner.getDouble("max-hours", 15));
        this.maxRatio = beginner == null ? 3 : Math.max(1, beginner.getInt("max-ratio", 3));
    }

    public int maxRatio() {
        return maxRatio;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                levels.put(online.getUniqueId(), readLevel(online.getUniqueId()));
            }
        }, 20, 20 * 60 * 2);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::checkEnded), 20 * 60, 20 * 60);
    }

    /** Le joueur est débutant (thread principal ; niveau de métier en mémoire). */
    public boolean isBeginner(Player player) {
        if (hours <= 0) {
            return false;
        }
        double played = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
        if (played < hours) {
            return true;
        }
        return jobLevel > 0 && played < maxHours && levels.getOrDefault(player.getUniqueId(), 0) < jobLevel;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            levels.put(player.getUniqueId(), readLevel(player.getUniqueId()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isBeginner(player)) {
                    double played = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
                    long left = Math.max(1, Math.round((played < hours ? hours : maxHours) - played));
                    messages.send(player, played < hours || jobLevel <= 0 ? "beginner.status" : "beginner.status-job",
                            "hours", String.valueOf(left), "level", String.valueOf(jobLevel));
                }
            }, 20 * 8);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        levels.remove(event.getPlayer().getUniqueId());
    }

    /** Une fois : la protection des débutants est finie (retenu sur le joueur). */
    private void checkEnded(Player player) {
        if (hours <= 0 || isBeginner(player) || player.getPersistentDataContainer().has(ENDED, PersistentDataType.BYTE)) {
            return;
        }
        player.getPersistentDataContainer().set(ENDED, PersistentDataType.BYTE, (byte) 1);
        double played = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR;
        if (played < hours + 24) { // pas pour les anciens joueurs à l'arrivée de cette règle
            messages.send(player, "beginner.ended");
        }
    }

    /** Bloquant : niveau du métier actuel (0 sans métier, EterJob absent ou en erreur). */
    private static int readLevel(UUID player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("EterJob")) {
            return 0;
        }
        try {
            return Jobs.level(player);
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** Chargée seulement si EterJob tourne (sinon NoClassDefFoundError). */
    private static final class Jobs {
        static int level(UUID player) {
            return fr.eternom.eterJob.api.JobApi.get()
                    .map(api -> api.job(player).map(job -> api.level(player, job)).orElse(0)).orElse(0);
        }
    }
}
