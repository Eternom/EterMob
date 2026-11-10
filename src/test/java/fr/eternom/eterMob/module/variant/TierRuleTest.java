package fr.eternom.eterMob.module.variant;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.variant.book.BookFamily;
import fr.eternom.eterMob.module.variant.hollow.HollowFamily;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La règle des paliers, pour toutes les familles : Ordinaire 1 capacité de base ; Robuste 1 passif (statistique, crochet
 * ou capacité qui réagit seule : au coup, en étant frappé, périodique, à la mort) ; Dangereux et Élite 1 capacité
 * chacun ; Fléau sa signature (au moins une capacité).
 */
class TierRuleTest {

    private static List<MobVariant> all() {
        return Stream.concat(BookFamily.variants().stream(), HollowFamily.variants().stream()).toList();
    }

    private static List<Ability> added(MobVariant variant, int ratio) {
        return variant.gated().stream().filter(gated -> gated.minRatio() == ratio).map(MobVariant.Gated::ability).toList();
    }

    @Test
    void everyTierFollowsTheRule() {
        for (MobVariant variant : all()) {
            String id = variant.id();
            assertEquals(1, added(variant, 1).size(), id + " : 1 capacité en Ordinaire");
            long stats = variant.stats().stream().filter(stat -> stat.minRatio() == 2).count();
            List<Ability> sturdy = added(variant, 2);
            boolean passive = stats + sturdy.size() == 1
                    && (stats == 1 || sturdy.getFirst().trigger() != Ability.Trigger.ACTIVE);
            assertTrue(passive || hasHook(variant), id + " : 1 passif en Robuste");
            assertEquals(1, added(variant, 3).size(), id + " : 1 capacité en Dangereux");
            assertEquals(1, added(variant, 4).size(), id + " : 1 capacité en Élite");
            assertTrue(!added(variant, 5).isEmpty(), id + " : une signature en Fléau");
            assertTrue(variant.stats().stream().noneMatch(stat -> stat.minRatio() != 2), id + " : les statistiques sont le passif du Robuste");
        }
    }

    private static boolean hasHook(MobVariant variant) {
        return variant.damagedHooksFor(2).size() == 1 && variant.damagedHooksFor(1).isEmpty();
    }
}
