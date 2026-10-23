package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Particle;

/** Harceleur : beaucoup plus rapide, mais fragile et frappe moins fort. */
public class FastZombie extends ZombieVariant {

    public FastZombie() {
        super("zombie_fast", Rarity.PEU_COMMUN, "Celer");
    }

    @Override
    public double health() {
        return 0.6;
    }

    @Override
    public double damage() {
        return 0.8;
    }

    @Override
    public double speed() {
        return 1.45;
    }

    /** Fléau : nuage de poussière à ses pieds. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.CLOUD, 2, 0.1, 0.2);
    }
}
