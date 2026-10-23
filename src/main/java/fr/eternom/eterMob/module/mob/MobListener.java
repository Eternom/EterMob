package fr.eternom.eterMob.module.mob;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.block.BlockBreakEvent;
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
        MobVariant variant = mobs.variants().pick(entity);
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
                if (victim instanceof Player && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    pierce(event, mobs.ratios().armorPiercing(attacker.ratio()));
                }
            }
        }
    }

    /**
     * Perce-armure : le coup ignore cette part de ce que l'armure et les enchantements de protection du joueur lui
     * retirent. Pas les explosions de creepers (déjà calées pour que la Protection IV suffise, sauf face au Fléau).
     */
    @SuppressWarnings({"deprecation", "removal"})
    private static void pierce(EntityDamageEvent event, double piercing) {
        if (piercing <= 0) {
            return;
        }
        for (EntityDamageEvent.DamageModifier modifier : new EntityDamageEvent.DamageModifier[]{
                EntityDamageEvent.DamageModifier.ARMOR, EntityDamageEvent.DamageModifier.MAGIC}) {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, event.getDamage(modifier) * (1 - piercing));
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
        if (Minions.is(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null) {
            mob.variant().onDeath(mob, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter) {
            ActiveMob mob = mobs.get(shooter);
            if (mob != null) {
                mob.variant().onProjectileHit(mob, event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null && event.getTarget() != null) {
            mob.variant().onTarget(mob, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        ActiveMob mob = mobs.get(event.getEntity());
        if (mob != null) {
            mob.variant().onShoot(mob, event);
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

    /** Bloc temporaire d'un variant (toile...) cassé par un joueur : il ne lâche rien. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (TempBlocks.is(event.getBlock())) {
            event.setDropItems(false);
            TempBlocks.forget(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!creeperBlockDamage && event.getRemover() instanceof Creeper) {
            event.setCancelled(true);
        }
    }
}
