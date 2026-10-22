package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;

/** Toute sa force est dans l'explosion : rayon de base 6 (un creeper chargé), environ 13 au ratio le plus fort. */
public class BoomCreeper extends MobVariant {

    private static final double RADIUS = 6;

    public BoomCreeper() {
        super("creeper_boom", EntityType.CREEPER, Rarity.RARE, "Creeper Boom");
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        ((Creeper) mob.entity()).setExplosionRadius(mob.scaleInt(RADIUS));
    }
}
