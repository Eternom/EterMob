package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Vampire : chaque coup qu'il porte le soigne de la moitié des dégâts infligés (après armure). Le ratio fait grandir ses
 * coups, donc ses soins. Il faut le tuer vite, ou ne pas se laisser toucher.
 */
public class VampireZombie extends ZombieVariant {

    /** Part des dégâts infligés rendue en vie. */
    private static final double LIFESTEAL = 0.5;
    private static final Color BLOOD = Color.fromRGB(0x8A0303);

    public VampireZombie() {
        super("zombie_vampire", Rarity.RARE, "Sanguis");
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() != mob.entity()) {
            return;
        }
        LivingEntity zombie = mob.entity();
        AttributeInstance max = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || zombie.isDead()) {
            return;
        }
        double healed = Math.min(max.getValue(), zombie.getHealth() + event.getFinalDamage() * LIFESTEAL);
        zombie.setHealth(healed);
        puff(mob, Particle.DUST, 6, 1.2, 0.3, new Particle.DustOptions(BLOOD, 1.2f));
    }

    /** Fléau : gouttes de sang autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.DUST, 3, 1.2, 0.35, new Particle.DustOptions(BLOOD, 1f));
    }
}
