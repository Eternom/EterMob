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
 * Base des creepers : le rayon d'explosion (la zone) et les dégâts sont réglés séparément, tous deux multipliés par le
 * ratio. Dans Minecraft, les dégâts montent avec le rayon (14 × rayon + 1 à bout portant) : ici ils sont ramenés à
 * damage (à bout portant, difficulté Normal, avant armure), en gardant la baisse avec la distance et les murs, et la
 * difficulté du serveur. Une grande zone ne rend donc pas l'explosion plus mortelle.
 */
public abstract class CreeperVariant extends MobVariant {

    private final double radius;
    private final double damage;

    /**
     * @param radius rayon d'explosion de base (3 = creeper de Minecraft)
     * @param damage dégâts de base à bout portant (43 = creeper de Minecraft)
     */
    protected CreeperVariant(String id, Rarity rarity, String name, double radius, double damage) {
        super(id, EntityType.CREEPER, rarity, name);
        this.radius = radius;
        this.damage = damage;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        ((Creeper) mob.entity()).setExplosionRadius(mob.scaleInt(radius));
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        Creeper creeper = (Creeper) mob.entity();
        // Chargé par la foudre : Minecraft double le rayon (la zone), pas les dégâts ici
        double blast = creeper.getExplosionRadius() * (creeper.isPowered() ? 2 : 1);
        double vanillaMax = 14 * blast + 1;
        event.setDamage(event.getDamage() / vanillaMax * mob.scale(damage));
    }
}
