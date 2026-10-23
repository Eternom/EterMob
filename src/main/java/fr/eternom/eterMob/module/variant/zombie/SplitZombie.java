package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Piège : à sa mort, il se divise en bébés zombies (rapides) qui attaquent son tueur : 2 au ratio 1, 4 au ratio 5. Les
 * bébés ne lâchent rien et ne sont pas sauvegardés.
 */
public class SplitZombie extends ZombieVariant {

    private static final double BABIES = 2;

    public SplitZombie() {
        super("zombie_split", Rarity.PEU_COMMUN, "Divisus");
    }

    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        Location location = mob.entity().getLocation();
        Player killer = mob.entity().getKiller();
        location.getWorld().spawnParticle(Particle.ITEM_SLIME, location.clone().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
        int babies = mob.scaleInt(BABIES);
        for (int i = 0; i < babies; i++) {
            Minions.spawn(location, Zombie.class, baby -> {
                baby.setBaby();
                if (killer != null) {
                    baby.setTarget(killer);
                }
            });
        }
    }

    /** Fléau : il suinte. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ITEM_SLIME, 3, 1, 0.35);
    }
}
