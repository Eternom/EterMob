package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Toute sa force est dans l'explosion : rayon de base 6 (un creeper chargé), environ 13 au ratio le plus fort, et des
 * dégâts multipliés eux aussi par le ratio (en plus de ceux que donne déjà le rayon).
 */
public class BoomCreeper extends MobVariant {

    private static final double RADIUS = 6;
    /** Dégâts de base de l'explosion, en fois ceux de Minecraft pour ce rayon. */
    private static final double DAMAGE = 1;

    public BoomCreeper() {
        super("creeper_boom", EntityType.CREEPER, Rarity.RARE, "Creeper Boom");
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        ((Creeper) mob.entity()).setExplosionRadius(mob.scaleInt(RADIUS));
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setDamage(event.getDamage() * mob.scale(DAMAGE));
        }
    }
}
