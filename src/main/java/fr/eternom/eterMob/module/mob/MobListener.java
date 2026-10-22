package fr.eternom.eterMob.module.mob;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

/** Apparitions naturelles transformées en variants, suivi des variants chargés, et capacités déclenchées par les événements. */
public class MobListener implements Listener {

    private final MobService mobs;
    /** false : les explosions de creepers (variants ou non) ne cassent ni blocs, ni cadres, ni tableaux. */
    private final boolean creeperBlockDamage;

    public MobListener(MobService mobs, boolean creeperBlockDamage) {
        this.mobs = mobs;
        this.creeperBlockDamage = creeperBlockDamage;
    }

    /** Seulement les apparitions naturelles : jamais les spawners, les œufs, les commandes ni les autres plugins. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!mobs.variants().has(entity.getType())) {
            return;
        }
        MobVariant variant = mobs.variants().pick(entity.getType(), event.getLocation());
        if (variant != null) {
            mobs.apply(entity, variant, mobs.ratios().roll(event.getLocation()));
        }
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        mobs.track(event.getEntity());
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        mobs.untrack(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        ActiveMob damaged = mobs.get(event.getEntity());
        if (damaged != null) {
            damaged.variant().onDamaged(damaged, event);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getEntity() instanceof LivingEntity victim) {
            ActiveMob attacker = mobs.get(attacker(byEntity.getDamager()));
            if (attacker != null) {
                attacker.variant().onAttack(attacker, victim, byEntity);
            }
        }
    }

    /** Qui frappe : le tireur pour un projectile. */
    private static Entity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null) {
            mob.variant().onDeath(mob, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null) {
            mob.variant().onExplode(mob, event);
        }
        if (!creeperBlockDamage && event.getEntity() instanceof Creeper) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!creeperBlockDamage && event.getRemover() instanceof Creeper) {
            event.setCancelled(true);
        }
    }
}
