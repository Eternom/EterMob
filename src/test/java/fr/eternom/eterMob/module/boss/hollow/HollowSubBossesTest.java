package fr.eternom.eterMob.module.boss.hollow;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.boss.SubBoss;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les sous-boss d'Orveth : 2 phases, sans rayon, leur kit complet. */
class HollowSubBossesTest {

    private static List<String> ids(List<Ability> abilities) {
        return abilities.stream().map(Ability::id).toList();
    }

    private static void subBoss(SubBoss boss) {
        assertTrue(boss.subBoss());
        assertEquals(0, boss.leashRadius());
        assertEquals(1, boss.phaseThresholds().length);
    }

    @Test
    void foreman() {
        Foreman boss = new Foreman();
        boss.configure(null);
        subBoss(boss);
        assertTrue(ids(boss.abilities(1)).containsAll(List.of("coup-de-pioche", "appel-des-mineurs", "dynamite", "wagonnet")));
        assertTrue(ids(boss.abilities(2)).containsAll(List.of("grisou", "eboulement-de-galerie")));
    }

    @Test
    void dryad() {
        Dryad boss = new Dryad();
        boss.configure(null);
        subBoss(boss);
        assertTrue(ids(boss.abilities(1)).containsAll(List.of("spores", "ronces", "bourgeon", "fuite-dans-les-feuilles")));
        assertTrue(ids(boss.abilities(2)).containsAll(List.of("liane", "pluie-de-pollen", "bourgeons", "etreinte-vegetale")));
    }

    @Test
    void heartThief() {
        HeartThief boss = new HeartThief();
        boss.configure(null);
        subBoss(boss);
        assertTrue(ids(boss.abilities(1)).containsAll(List.of("pas-de-l-ombre", "larcin", "bombe-fumigene")));
        assertTrue(ids(boss.abilities(2)).containsAll(List.of("eclats-d-amethyste", "pieges", "resonance")));
    }
}
