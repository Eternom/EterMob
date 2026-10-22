package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tous les variants, rangés par mob une fois pour toutes : à chaque apparition, seuls les variants de ce mob sont
 * regardés (conditions puis tirage selon la rareté).
 */
public class Variants {

    private final Map<String, MobVariant> byId = new LinkedHashMap<>();
    /** Variants qui apparaissent naturellement (hors disabled-variants), par mob. */
    private final Map<EntityType, List<MobVariant>> natural = new EnumMap<>(EntityType.class);
    private final Map<Rarity, Double> rarityWeights;

    public Variants(Collection<MobVariant> variants, Set<String> disabled, Map<Rarity, Double> rarityWeights) {
        this.rarityWeights = rarityWeights;
        for (MobVariant variant : variants) {
            if (byId.putIfAbsent(variant.id(), variant) != null) {
                throw new IllegalArgumentException("Deux variants ont l'identifiant " + variant.id());
            }
            if (!disabled.contains(variant.id())) {
                natural.computeIfAbsent(variant.type(), type -> new ArrayList<>()).add(variant);
            }
        }
    }

    public MobVariant get(String id) {
        return byId.get(id);
    }

    public Collection<MobVariant> all() {
        return byId.values();
    }

    /** Ce mob a-t-il des variants ? Sinon il reste celui de Minecraft. */
    public boolean has(EntityType type) {
        return natural.containsKey(type);
    }

    /** Variant tiré pour ce mob à cet endroit, selon la rareté ; null si aucun ne peut apparaître là. */
    public MobVariant pick(EntityType type, Location location) {
        List<MobVariant> candidates = natural.get(type);
        if (candidates == null) {
            return null;
        }
        MobVariant[] allowed = new MobVariant[candidates.size()];
        double[] weights = new double[candidates.size()];
        int count = 0;
        double total = 0;
        for (MobVariant variant : candidates) {
            double weight = rarityWeights.getOrDefault(variant.rarity(), 0.0);
            if (weight > 0 && variant.canSpawn(location)) {
                allowed[count] = variant;
                weights[count++] = weight;
                total += weight;
            }
        }
        if (count == 0) {
            return null;
        }
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < count; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return allowed[i];
            }
        }
        return allowed[count - 1];
    }
}
