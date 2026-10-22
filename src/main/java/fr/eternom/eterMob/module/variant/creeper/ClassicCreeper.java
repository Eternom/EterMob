package fr.eternom.eterMob.module.variant.creeper;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;

/** Le creeper de base : seul son rayon d'explosion (3 blocs, comme Minecraft) grandit avec le ratio. */
public class ClassicCreeper extends MobVariant {

    private static final double RADIUS = 3;

    public ClassicCreeper() {
        super("creeper", EntityType.CREEPER, Rarity.COMMUN, "Sibilus");
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        ((Creeper) mob.entity()).setExplosionRadius(mob.scaleInt(RADIUS));
    }
}
