package fr.eternom.eterMob.module.boss.golem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le kit d'Orveth par phase, et sa défense : faible à la magie. */
class OrvethTest {

    @Test
    void kitByPhase() {
        Orveth orveth = new Orveth();
        orveth.configure(null);
        assertEquals(1, orveth.phaseOf("ronces"));
        assertEquals(2, orveth.phaseOf("rouleau"));
        assertEquals(2, orveth.phaseOf("eboulement"));
        assertEquals(3, orveth.phaseOf("poigne"));
        assertEquals(3, orveth.phaseOf("onde-du-sceau"));
        assertEquals(2, orveth.phaseThresholds().length);
    }

    @Test
    void weakToMagic() {
        Orveth orveth = new Orveth();
        orveth.configure(null);
        assertTrue(orveth.armor() > orveth.magicResist() * 5);
        assertTrue(orveth.leashRadius() > 0);
    }
}
