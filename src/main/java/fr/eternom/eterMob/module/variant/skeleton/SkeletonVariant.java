package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Base des squelettes : le ratio multiplie leur vie et les dégâts de leurs flèches (les flèches ne passent pas par les
 * dégâts d'attaque du mob, d'où onAttack). Chaque variant a ses biomes (spawnsHere) ; Ossis apparaît partout. Un
 * variant qui réécrit onAttack appelle super.onAttack.
 */
public abstract class SkeletonVariant extends MobVariant {

    protected SkeletonVariant(String id, Rarity rarity, String name) {
        super(id, EntityType.SKELETON, rarity, name);
    }

    @Override
    public boolean canSpawn(LivingEntity entity) {
        return spawnsHere(entity);
    }

    /** Biomes et autres conditions du variant (voir Spawns). Par défaut : partout. */
    protected boolean spawnsHere(LivingEntity entity) {
        return true;
    }

    /** Dégâts des flèches, en fois ceux de Minecraft : multipliés ensuite par le ratio. */
    protected double arrowDamage() {
        return 1;
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow) {
            event.setDamage(event.getDamage() * mob.scale(arrowDamage()));
        }
    }
}
