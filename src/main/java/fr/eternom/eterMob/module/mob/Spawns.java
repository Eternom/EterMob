package fr.eternom.eterMob.module.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conditions d'apparition communes aux variants (MobVariant#canSpawn) : groupes de biomes, grotte, profondeur. Plusieurs
 * variants d'un même mob peuvent partager un biome ; le mob de base de chaque type apparaît partout.
 */
public final class Spawns {

    /**
     * Groupe de biomes : les biomes de Minecraft par leur nom exact ; les autres (Terralith, datapacks) d'après les mots
     * de leur nom (terralith:sakura_grove : forêts ; terralith:frozen_cliffs : neige et montagnes). Le résultat est
     * gardé par biome : calculé une seule fois.
     */
    public static final class Group {

        private final Set<Biome> vanilla;
        private final List<String> words;
        private final Map<Biome, Boolean> known = new HashMap<>();

        private Group(Set<Biome> vanilla, String... words) {
            this.vanilla = vanilla;
            this.words = List.of(words);
        }

        boolean contains(Biome biome) {
            return known.computeIfAbsent(biome, this::match);
        }

        private boolean match(Biome biome) {
            NamespacedKey key = biome.getKey();
            if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
                return vanilla.contains(biome);
            }
            String name = key.getKey();
            for (String word : words) {
                if (name.contains(word)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final Group PLAINS = new Group(Set.of(Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW, Biome.SAVANNA,
            Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA, Biome.BEACH),
            "plain", "meadow", "steppe", "prairie", "savanna", "shrub", "field", "valley", "clearing", "plateau", "brush", "lowland");
    public static final Group FORESTS = new Group(Set.of(Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST,
            Biome.OLD_GROWTH_BIRCH_FOREST, Biome.TAIGA, Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA,
            Biome.WINDSWEPT_FOREST, Biome.CHERRY_GROVE),
            "forest", "grove", "woods", "taiga", "orchard", "shield", "skylands");
    public static final Group DARK = new Group(Set.of(Biome.DARK_FOREST, Biome.PALE_GARDEN, Biome.DEEP_DARK),
            "dark", "shadow", "pale", "moonlight", "haunted");
    public static final Group SWAMPS = new Group(Set.of(Biome.SWAMP, Biome.MANGROVE_SWAMP),
            "swamp", "marsh", "bog", "mire", "wetland");
    public static final Group JUNGLES = new Group(Set.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE, Biome.LUSH_CAVES),
            "jungle", "tropical", "rainforest", "bamboo");
    public static final Group MOUNTAINS = new Group(Set.of(Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS,
            Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES, Biome.GROVE, Biome.STONY_SHORE),
            "mountain", "peak", "alpine", "highland", "cliff", "crag", "summit", "rocky", "spire", "crater", "caldera", "chasm");
    public static final Group DRY = new Group(Set.of(Biome.DESERT, Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS),
            "desert", "badlands", "canyon", "mesa", "dune", "arid", "sand", "oasis", "yellowstone");
    public static final Group COLD = new Group(Set.of(Biome.SNOWY_PLAINS, Biome.ICE_SPIKES, Biome.SNOWY_TAIGA,
            Biome.SNOWY_BEACH, Biome.FROZEN_RIVER, Biome.GROVE, Biome.SNOWY_SLOPES, Biome.FROZEN_PEAKS),
            "snow", "frozen", "ice", "glaci", "wintry", "tundra");
    public static final Group RIVERS = new Group(Set.of(Biome.RIVER, Biome.FROZEN_RIVER, Biome.BEACH, Biome.SNOWY_BEACH,
            Biome.STONY_SHORE),
            "river", "beach", "shore", "coast", "isle", "island");

    private Spawns() {
    }

    /** Le mob apparaît dans un de ces groupes de biomes (biome du bloc à ses pieds, en 3D : vaut aussi sous terre). */
    public static boolean biome(LivingEntity entity, Group... groups) {
        Biome biome = entity.getLocation().getBlock().getBiome();
        for (Group group : groups) {
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
