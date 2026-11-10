package fr.eternom.eterMob.module.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Où EterMob n'a pas le droit d'agir sur le monde : autour du point d'apparition du monde (spawn-radius), sur un terrain
 * de clan (EterClan, s'il est là). Ni apparition d'EterMob (les mobs de Minecraft restent ceux de Minecraft), ni bloc
 * temporaire (piliers).
 */
public final class SpawnGuard {

    private final double spawnRadius;
    private final boolean claims;

    public SpawnGuard(ConfigurationSection config) {
        this.spawnRadius = config == null ? 48 : Math.max(0, config.getDouble("spawn-radius", 48));
        this.claims = config == null || config.getBoolean("claims", true);
    }

    public boolean allowsSpawn(Location at) {
        return !nearSpawn(at) && !claimed(at);
    }

    public boolean allowsBlocks(Location at) {
        return !nearSpawn(at) && !claimed(at);
    }

    private boolean nearSpawn(Location at) {
        if (spawnRadius <= 0) {
            return false;
        }
        Location spawn = at.getWorld().getSpawnLocation();
        double dx = spawn.getX() - at.getX();
        double dz = spawn.getZ() - at.getZ();
        return dx * dx + dz * dz <= spawnRadius * spawnRadius;
    }

    private boolean claimed(Location at) {
        if (!claims || Bukkit.getPluginManager().getPlugin("EterClan") == null) {
            return false;
        }
        try {
            return ClanCheck.claimed(at);
        } catch (LinkageError e) {
            return false;
        }
    }

    /** Isolé : la classe d'EterClan n'est chargée que s'il est installé. */
    private static final class ClanCheck {
        static boolean claimed(Location at) {
            return fr.eternom.eterClan.api.ClanApi.get().map(api -> api.isClaimed(at)).orElse(false);
        }
    }
}
