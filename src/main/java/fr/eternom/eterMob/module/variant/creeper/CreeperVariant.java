package fr.eternom.eterMob.module.variant.creeper;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Bukkit;
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
 *
 * Déclenchement : Minecraft fait gonfler un creeper à 3 blocs de sa cible. Avec une autre distance, le variant s'allume
 * dès qu'il voit sa cible à cette distance ; une fois allumé, il explose même si elle s'éloigne. Plus près que 3 blocs,
 * le gonflement de Minecraft lui est retiré (à chaque chargement : Minecraft ne sauvegarde pas l'IA).
 */
public abstract class CreeperVariant extends MobVariant {

    /** Distance de déclenchement de Minecraft. */
    private static final double VANILLA_TRIGGER = 3;
    /** Vérification de la distance de déclenchement, en ticks (multiple de MobService.TICK_PERIOD). */
    private static final int TRIGGER_CHECK = 5;

    private final double radius;
    private final double damage;
    private final double trigger;

    /**
     * @param radius rayon d'explosion de base (3 = creeper de Minecraft)
     * @param damage dégâts de base à bout portant en Normal (43 = creeper de Minecraft) ; le serveur est en Difficile : × 1,5
     */
    protected CreeperVariant(String id, Rarity rarity, String name, double radius, double damage) {
        this(id, rarity, name, radius, damage, VANILLA_TRIGGER);
    }

    /** @param trigger distance (blocs) à laquelle il s'allume en voyant sa cible ; 3 = Minecraft */
    protected CreeperVariant(String id, Rarity rarity, String name, double radius, double damage, double trigger) {
        super(id, EntityType.CREEPER, rarity, name);
        this.radius = radius;
        this.damage = damage;
        this.trigger = trigger;
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

    @Override
    public void onLoad(ActiveMob mob) {
        if (trigger < VANILLA_TRIGGER) {
            Bukkit.getMobGoals().removeGoal((Creeper) mob.entity(), VanillaGoal.CREEPER_SWELL);
        }
    }

    /** Seulement si le déclenchement n'est pas celui de Minecraft (sinon Minecraft s'en charge). */
    @Override
    public int tickInterval() {
        return trigger != VANILLA_TRIGGER ? TRIGGER_CHECK : 0;
    }

    @Override
    public void tick(ActiveMob mob) {
        Creeper creeper = (Creeper) mob.entity();
        LivingEntity target = creeper.getTarget();
        if (creeper.isIgnited() || target == null || target.getWorld() != creeper.getWorld()) {
            return;
        }
        if (creeper.getLocation().distanceSquared(target.getLocation()) <= trigger * trigger && creeper.hasLineOfSight(target)) {
            creeper.ignite();
        }
    }
}
