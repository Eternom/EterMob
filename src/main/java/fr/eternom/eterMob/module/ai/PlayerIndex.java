package fr.eternom.eterMob.module.ai;

import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les joueurs en combat (ni créatif, ni spectateur, ni staff invisible), rangés par monde, relus toutes les 5 ticks :
 * l'IA et les apparitions ne refont pas une recherche d'entités par monstre.
 */
public final class PlayerIndex {

    private final Map<UUID, List<Player>> byWorld = new HashMap<>();

    public void refresh() {
        byWorld.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Hits.fighting(player)) {
                byWorld.computeIfAbsent(player.getWorld().getUID(), id -> new ArrayList<>()).add(player);
            }
        }
    }

    public List<Player> in(World world) {
        return byWorld.getOrDefault(world.getUID(), List.of());
    }

    /** Joueurs en combat à moins de range blocs. */
    public List<Player> near(Location at, double range) {
        List<Player> world = in(at.getWorld());
        if (world.isEmpty()) {
            return List.of();
        }
        double max = range * range;
        List<Player> found = new ArrayList<>();
        for (Player player : world) {
            if (player.getLocation().distanceSquared(at) <= max) {
                found.add(player);
            }
        }
        return found;
    }

    public boolean any(Location at, double range) {
        double max = range * range;
        for (Player player : in(at.getWorld())) {
            if (player.getLocation().distanceSquared(at) <= max) {
                return true;
            }
        }
        return false;
    }

    public int count(Location at, double range) {
        double max = range * range;
        int count = 0;
        for (Player player : in(at.getWorld())) {
            if (player.getLocation().distanceSquared(at) <= max) {
                count++;
            }
        }
        return count;
    }
}
