package fr.eternom.eterMob.module.variant.hollow;

import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.variant.book.BookFamily;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** La famille d'Orveth : identifiants uniques (avec ceux du Livre), kit qui s'ouvre, mobs courants couverts. */
class HollowFamilyTest {

    @Test
    void uniqueIdsAcrossFamilies() {
        Set<String> ids = new HashSet<>();
        Stream.concat(BookFamily.variants().stream(), HollowFamily.variants().stream())
                .forEach(variant -> assertTrue(ids.add(variant.id()), variant.id()));
    }

    @Test
    void ratioUnlocksTheKit() {
        for (MobVariant variant : HollowFamily.variants()) {
            assertTrue(variant.abilitiesFor(1).size() + variant.statsFor(1).size() >= 1, variant.id());
            assertTrue(variant.abilitiesFor(5).size() + variant.statsFor(5).size() >= 5, variant.id() + " : 5 paliers");
        }
    }

    @Test
    void commonMobsAreCovered() {
        Set<EntityType> types = new HashSet<>();
        Stream.concat(BookFamily.variants().stream(), HollowFamily.variants().stream()).forEach(variant -> types.add(variant.type()));
        assertTrue(types.containsAll(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
                EntityType.HUSK, EntityType.DROWNED, EntityType.SLIME, EntityType.WITCH)));
    }
}
