package fr.eternom.eterMob.module.boss.disciple;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.boss.SubBoss;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les trois disciples : sous-boss, sans rayon de déplacement, avec leur kit complet par phase. */
class DisciplesTest {

    private static List<String> ids(List<Ability> abilities) {
        return abilities.stream().map(Ability::id).toList();
    }

    @Test
    void scribe() {
        Scribe scribe = new Scribe();
        scribe.configure(null);
        assertSubBoss(scribe);
        assertEquals(List.of("trait-d-encre", "lecture-interdite", "page-envolee", "chapitre-de-la-servitude"), ids(scribe.abilities(1)));
        assertTrue(ids(scribe.abilities(2)).containsAll(List.of("copie-conforme", "flaques-d-encre", "rature", "derniere-page")));
    }

    @Test
    void gravedigger() {
        Gravedigger digger = new Gravedigger();
        digger.configure(null);
        assertSubBoss(digger);
        assertEquals(2, digger.phaseThresholds().length);
        assertTrue(ids(digger.abilities(1)).containsAll(List.of("coup-de-pelle", "pic-d-os", "cage-d-os", "enterrement")));
        assertTrue(ids(digger.abilities(2)).containsAll(List.of("pelletee", "fosse-commune")));
    }

    @Test
    void soulWarden() {
        SoulWarden warden = new SoulWarden();
        warden.configure(null);
        assertSubBoss(warden);
        assertTrue(ids(warden.abilities(1)).containsAll(List.of("faux", "lien-d-ames", "moisson", "pas-de-l-ombre")));
        assertTrue(ids(warden.abilities(2)).containsAll(List.of("pacte", "pluie-d-ames", "recolte")));
    }

    private static void assertSubBoss(SubBoss boss) {
        assertTrue(boss.subBoss());
        assertEquals(0, boss.leashRadius());
        assertTrue(boss.despawnMinutes() > 0);
        // Pas de spawn sans config : seulement par bosses.<id>.spawn
        assertEquals(null, boss.naturalSpawn());
    }
}
