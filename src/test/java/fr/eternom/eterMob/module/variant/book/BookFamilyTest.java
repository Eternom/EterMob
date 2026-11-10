package fr.eternom.eterMob.module.variant.book;

import fr.eternom.eterMob.module.mob.MobVariant;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La famille du Livre : 7 variants, identifiants uniques, kit qui s'ouvre avec le ratio. */
class BookFamilyTest {

    @Test
    void sevenUniqueVariants() {
        List<MobVariant> variants = BookFamily.variants();
        assertEquals(7, variants.size());
        Set<String> ids = new HashSet<>();
        variants.forEach(variant -> assertTrue(ids.add(variant.id()), variant.id()));
        assertTrue(ids.containsAll(Set.of("goule", "page-volante", "ame-enchainee", "garde-os", "archer-os", "acolyte", "zombie-mineur")));
    }

    @Test
    void ratioUnlocksTheKit() {
        for (MobVariant variant : BookFamily.variants()) {
            int ordinary = variant.abilitiesFor(1).size();
            int scourge = variant.abilitiesFor(5).size();
            assertTrue(ordinary >= 1, variant.id() + " : une capacité de base");
            assertTrue(scourge > ordinary, variant.id() + " : un Fléau a plus qu'un Ordinaire");
            for (int ratio = 1; ratio < 5; ratio++) {
                assertTrue(variant.abilitiesFor(ratio).size() <= variant.abilitiesFor(ratio + 1).size());
            }
        }
    }

    @Test
    void passivesAreStats() {
        MobVariant page = BookFamily.page();
        assertTrue(page.statsFor(1).isEmpty());
        assertEquals(1, page.statsFor(2).size());
        MobVariant archer = BookFamily.boneArcher();
        assertEquals(List.of("salve"), archer.abilitiesFor(1).stream().map(a -> a.id()).toList());
        assertEquals(List.of("salve", "recul", "fleche-clouante", "pluie-de-fleches"), archer.abilitiesFor(5).stream().map(a -> a.id()).toList());
    }
}
