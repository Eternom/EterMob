package fr.eternom.eterMob.module.variant.spider;

import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/**
 * Base des araignées (pas les araignées venimeuses : elles ne viennent que des spawners). Le ratio multiplie leur vie
 * et leurs dégâts d'attaque. Chaque variant a ses biomes (spawnsHere) ; Aranea apparaît partout.
 */
public abstract class SpiderVariant extends MobVariant {

    protected SpiderVariant(String id, Rarity rarity, String name) {
        super(id, EntityType.SPIDER, rarity, name);
    }

    @Override
    public boolean canSpawn(LivingEntity entity) {
        return spawnsHere(entity);
    }

    /** Biomes et autres conditions du variant (voir Spawns). Par défaut : partout. */
    protected boolean spawnsHere(LivingEntity entity) {
        return true;
    }
}
