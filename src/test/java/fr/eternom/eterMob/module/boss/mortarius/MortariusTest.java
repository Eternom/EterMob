package fr.eternom.eterMob.module.boss.mortarius;

import fr.eternom.eterMob.module.ability.Ability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le kit de Mortarius se construit, phase par phase, avec les bons réglages. */
class MortariusTest {

    private static Mortarius mortarius() {
        Mortarius boss = new Mortarius();
        boss.configure(null);
        return boss;
    }

    private static Ability find(List<Ability> abilities, String id) {
        return abilities.stream().filter(ability -> ability.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void threePhasesWithTheirKits() {
        Mortarius boss = mortarius();
        assertEquals(List.of("masse", "bond", "lecture-funeste", "levee-des-morts"), boss.abilities(1).stream().map(Ability::id).toList());
        assertEquals(List.of("masse", "bond", "charge", "chaines-d-ames", "levee-des-morts"), boss.abilities(2).stream().map(Ability::id).toList());
        assertEquals(List.of("masse", "bond", "onde-sismique", "tombes", "aspiration"), boss.abilities(3).stream().map(Ability::id).toList());
        assertEquals(2, boss.phaseThresholds().length);
    }

    @Test
    void maceIsNotInterruptibleButSpellsAre() {
        Mortarius boss = mortarius();
        assertFalse(find(boss.abilities(1), "masse").interruptible());
        assertTrue(find(boss.abilities(1), "lecture-funeste").interruptible());
        assertTrue(find(boss.abilities(3), "aspiration").interruptible());
        assertFalse(find(boss.abilities(2), "charge").interruptible());
    }

    @Test
    void globalCooldownShortensWithPhases() {
        Mortarius boss = mortarius();
        assertTrue(boss.globalCooldown(1) > boss.globalCooldown(2));
        assertTrue(boss.globalCooldown(2) > boss.globalCooldown(3));
        assertEquals(40, boss.leashRadius());
    }
}
