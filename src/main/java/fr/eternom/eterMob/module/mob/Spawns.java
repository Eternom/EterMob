package fr.eternom.eterMob.module.mob;

import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

/**
 * Conditions d'apparition communes aux variants (MobVariant#canSpawn) : groupes de biomes, grotte, profondeur. Plusieurs
 * variants d'un même mob peuvent partager un biome ; le mob de base de chaque type apparaît partout.
 */
public final class Spawns {

    public static final Set<Biome> PLAINS = Set.of(Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW, Biome.SAVANNA,
            Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA, Biome.BEACH);
    public static final Set<Biome> FORESTS = Set.of(Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST,
            Biome.OLD_GROWTH_BIRCH_FOREST, Biome.TAIGA, Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA,
            Biome.WINDSWEPT_FOREST, Biome.CHERRY_GROVE);
    public static final Set<Biome> DARK = Set.of(Biome.DARK_FOREST, Biome.PALE_GARDEN, Biome.DEEP_DARK);
    public static final Set<Biome> SWAMPS = Set.of(Biome.SWAMP, Biome.MANGROVE_SWAMP);
    public static final Set<Biome> JUNGLES = Set.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE, Biome.LUSH_CAVES);
    public static final Set<Biome> MOUNTAINS = Set.of(Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS,
            Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES, Biome.GROVE, Biome.STONY_SHORE);
    public static final Set<Biome> DRY = Set.of(Biome.DESERT, Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS);
    public static final Set<Biome> COLD = Set.of(Biome.SNOWY_PLAINS, Biome.ICE_SPIKES, Biome.SNOWY_TAIGA, Biome.SNOWY_BEACH,
            Biome.FROZEN_RIVER, Biome.GROVE, Biome.SNOWY_SLOPES, Biome.FROZEN_PEAKS);
    public static final Set<Biome> RIVERS = Set.of(Biome.RIVER, Biome.FROZEN_RIVER, Biome.BEACH, Biome.SNOWY_BEACH,
            Biome.STONY_SHORE);

    private Spawns() {
    }

    /** Le mob apparaît dans un de ces biomes (biome du bloc à ses pieds, en 3D : vaut aussi sous terre). */
    @SafeVarargs
    public static boolean biome(LivingEntity entity, Set<Biome>... groups) {
        Biome biome = entity.getLocation().getBlock().getBiome();
        for (Set<Biome> group : groups) {
            if (group.contains(biome)) {
                return true;
            }
        }
        return false;
    }

    /** Dans une grotte : aucune lumière du ciel n'arrive jusqu'à lui. */
    public static boolean cave(LivingEntity entity) {
        Block block = entity.getLocation().getBlock();
        return block.getLightFromSky() == 0;
    }

    /** Sous cette hauteur. */
    public static boolean below(LivingEntity entity, double y) {
        return entity.getLocation().getY() < y;
    }
}
