package fr.eternom.eterMob.module.ai;

import fr.eternom.eterMob.module.mob.Tier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusTest {

    private static final AiProfile VARIANT = AiProfile.defaults(Tier.VARIANT);
    private static final AiProfile BOSS = AiProfile.defaults(Tier.BOSS);

    private static Focus.Criteria player(double threat, double proximity, double finish, boolean ranged, boolean healer) {
        return new Focus.Criteria(threat, proximity, finish, ranged, healer, false, 1, 1);
    }

    @Test
    void theOneWhoHitsTheMostIsPreferred() {
        double hitter = Focus.score(VARIANT, player(1, 0.5, 0, false, false));
        double bystander = Focus.score(VARIANT, player(0.1, 0.5, 0, false, false));
        assertTrue(hitter > bystander);
    }

    @Test
    void aBossGoesForTheHealerOverAnEqualDamageDealer() {
        double healer = Focus.score(BOSS, player(0.5, 0.3, 0, false, true));
        double dealer = Focus.score(BOSS, player(0.5, 0.3, 0, false, false));
        assertTrue(healer > dealer);
    }

    @Test
    void aNearlyDeadPlayerIsAttractive() {
        double dying = Focus.score(VARIANT, player(0.3, 0.5, Focus.finish(0.15), false, false));
        double healthy = Focus.score(VARIANT, player(0.3, 0.5, Focus.finish(1), false, false));
        assertTrue(dying > healthy);
        assertEquals(0, Focus.finish(1));
        assertEquals(0.64, Focus.finish(0.2), 1e-9);
    }

    @Test
    void notSeenAndUnreachableLowerTheScore() {
        Focus.Criteria seen = player(1, 1, 0, false, false);
        Focus.Criteria hidden = new Focus.Criteria(1, 1, 0, false, false, false, 0.5, 1);
        Focus.Criteria stuck = new Focus.Criteria(1, 1, 0, false, false, false, 1, 0.4);
        assertTrue(Focus.score(VARIANT, seen) > Focus.score(VARIANT, hidden));
        assertTrue(Focus.score(VARIANT, seen) > Focus.score(VARIANT, stuck));
    }

    @Test
    void stickinessPreventsPingPong() {
        // Juste un peu mieux : on garde la cible actuelle
        assertTrue(Focus.keep(1, 1.2, 1.3, 10_000, 1500));
        // Nettement mieux, et le dernier changement est ancien : on change
        assertFalse(Focus.keep(1, 1.5, 1.3, 10_000, 1500));
        // Nettement mieux mais changement trop récent : on garde
        assertTrue(Focus.keep(1, 3, 1.3, 500, 1500));
        // Cible actuelle plus valable : on change
        assertFalse(Focus.keep(-1, 0.2, 1.3, 0, 1500));
    }

    @Test
    void proximityAndVariety() {
        assertEquals(1, Focus.proximity(1, 20));
        assertEquals(0, Focus.proximity(25, 20));
        assertEquals(1, Focus.variety(0));
        assertTrue(Focus.variety(2) < Focus.variety(1));
    }

    @Test
    void threatDecaysAndForgets() {
        Threat threat = new Threat();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        threat.add(a, 10);
        threat.add(b, 0.06);
        assertEquals(1, threat.normalized(a));
        threat.decay(0.5);
        assertEquals(5, threat.get(a), 1e-9);
        assertEquals(0, threat.get(b));
        threat.add(a, -3);
        assertEquals(5, threat.get(a), 1e-9);
    }
}
