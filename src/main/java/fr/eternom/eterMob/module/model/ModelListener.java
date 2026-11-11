package fr.eternom.eterMob.module.model;

import fr.eternom.eterMob.module.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Les animations de combat des monstres modélisés : attack quand ils frappent au corps à corps, hurt quand ils sont touchés. */
public final class ModelListener implements Listener {

    private final MobService mobs;

    public ModelListener(MobService mobs) {
        this.mobs = mobs;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        // Au corps à corps seulement (un projectile a son propre lanceur, et ses capacités jouent leur animation)
        if (!(damager instanceof Player) && mobs.caster(damager) != null) {
            Models.play(damager, "attack");
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof Player) && mobs.caster(victim) != null) {
            Models.play(victim, "hurt");
        }
    }
}
