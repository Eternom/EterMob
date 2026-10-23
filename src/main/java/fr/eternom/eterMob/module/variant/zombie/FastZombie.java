package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Harceleur : très rapide mais fragile. Il frappe et recule : après chaque coup, il bondit en arrière de quelques
 * blocs puis revient, et le joueur touché a Lenteur (1 s au ratio 1, environ 2,2 s au ratio 5) : il ne le rattrape
 * pas, et les autres zombies le rattrapent, lui. Plaines, savanes, prairies, déserts,
 * badlands, rivières et plages.
 */
public class FastZombie extends ZombieVariant {

    private static final double SLOWNESS_TICKS = 20;
    /** Bond en arrière après un coup (blocs par tick). */
    private static final double RETREAT = 0.8;
    private static final double RETREAT_LIFT = 0.35;

    public FastZombie() {
        super("zombie_fast", Rarity.PEU_COMMUN, "Celer");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.PLAINS, Spawns.DRY, Spawns.RIVERS);
    }

    @Override
    public double health() {
        return 0.6;
    }

    @Override
    public double damage() {
        return 0.8;
    }

    @Override
    public double speed() {
        return 1.45;
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() != mob.entity() || !(victim instanceof Player)) {
            return;
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, mob.scaleInt(SLOWNESS_TICKS), 0));
        LivingEntity zombie = mob.entity();
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(FastZombie.class), () -> {
            if (!zombie.isValid() || zombie.getWorld() != victim.getWorld()) {
                return;
            }
            Vector back = zombie.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (back.lengthSquared() > 0.01) {
                zombie.setVelocity(back.normalize().multiply(RETREAT).setY(RETREAT_LIFT));
                zombie.getWorld().spawnParticle(Particle.CLOUD, zombie.getLocation(), 6, 0.3, 0.1, 0.3, 0.02);
            }
        });
    }

    /** Fléau : nuage de poussière à ses pieds. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.CLOUD, 2, 0.1, 0.2);
    }
}
