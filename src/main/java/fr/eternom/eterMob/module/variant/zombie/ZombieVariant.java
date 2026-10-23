package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/**
 * Base des zombies : le ratio multiplie leur vie et leurs dégâts d'attaque (MobVariant#health, #damage). Seuls les
 * adultes deviennent des variants : les bébés zombies restent ceux de Minecraft. Chaque variant a ses biomes
 * (spawnsHere) ; Putridus apparaît partout.
 */
public abstract class ZombieVariant extends MobVariant {

    protected ZombieVariant(String id, Rarity rarity, String name) {
        super(id, EntityType.ZOMBIE, rarity, name);
    }

    @Override
    public boolean canSpawn(LivingEntity entity) {
        return (!(entity instanceof Ageable ageable) || ageable.isAdult()) && spawnsHere(entity);
    }

    /** Biomes et autres conditions du variant (voir Spawns). Par défaut : partout. */
    protected boolean spawnsHere(LivingEntity entity) {
        return true;
    }
}
