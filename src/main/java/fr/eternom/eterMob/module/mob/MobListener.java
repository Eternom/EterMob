package fr.eternom.eterMob.module.mob;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import fr.eternom.eterLib.module.quest.QuestBridge;
import fr.eternom.eterLib.module.state.States;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.kind.Volley;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.loot.KillStats;
import fr.eternom.eterMob.module.spawn.SpawnDirector;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

import java.util.Locale;
import java.util.Map;

/**
 * Apparitions naturelles (variants, ratios), suivi des monstres chargés, et tout ce qui nourrit leur IA : coups reçus
 * (menace, part des joueurs), coups donnés (capacités au coup ; perce-armure et plafond : combat d'EterLib), cible imposée, mort (capacités,
 * butin, statistiques, quêtes).
 */
public class MobListener implements Listener {

    private final MobService mobs;
    private final SpawnDirector director;
    private final KillStats stats;
    private final boolean creeperBlockDamage;
    /** Part des dégâts qui doit venir des joueurs pour le butin et l'expérience en plus. */
    private final double lootShare;

    public MobListener(MobService mobs, SpawnDirector director, KillStats stats, boolean creeperBlockDamage, double lootShare) {
        this.mobs = mobs;
        this.director = director;
        this.stats = stats;
        this.creeperBlockDamage = creeperBlockDamage;
        this.lootShare = lootShare;
    }

    /** Seulement les apparitions naturelles : jamais les spawners, les œufs, les commandes ni les autres plugins. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            director.onNatural(event.getEntity());
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
        States states = Services.states();
        boolean stateDamage = states != null && states.isStateDamage();
        Entity source = event instanceof EntityDamageByEntityEvent byEntity ? attacker(byEntity.getDamager()) : null;
        ActiveMob damaged = mobs.get(event.getEntity());
        if (damaged != null) {
            if (source instanceof Player player) {
                boolean ranged = ((EntityDamageByEntityEvent) event).getDamager() instanceof Projectile;
                mobs.damagedBy(damaged, player.getUniqueId(), event.getDamage());
                damaged.brain().damagedBy(player, event.getDamage(), ranged);
            } else {
                mobs.otherDamage(damaged, event.getDamage());
            }
            if (!stateDamage) {
                damaged.brain().onDamaged(source instanceof LivingEntity living ? living : null);
            }
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Caster caster = mobs.caster(source);
        if (caster == null || caster.entity() == victim) {
            return;
        }
        // Un vrai coup au corps à corps (pas une capacité, pas un état, pas un projectile) : capacités « au coup »
        if (!stateDamage && !Hits.isAbilityDamage() && byEntity.getDamager() == caster.entity()) {
            caster.brain().onHit(victim);
        }
    }

    /** Qui frappe : le tireur pour un projectile. */
    private static Entity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    /** L'IA de Minecraft ne remplace pas la cible choisie par celle d'EterMob (sauf provocation et cible disparue). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getReason() == EntityTargetEvent.TargetReason.CUSTOM) {
            return;
        }
        Caster caster = mobs.caster(event.getEntity());
        if (caster == null) {
            return;
        }
        LivingEntity chosen = caster.brain().target();
        if (chosen != null && chosen.isValid() && !chosen.isDead() && event.getTarget() != chosen) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (Minions.is(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        ActiveMob mob = mobs.get(entity);
        if (mob == null) {
            return;
        }
        Player killer = entity.getKiller();
        mob.brain().onDeath(killer);
        if (mob.minion()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (killer == null || mob.playerShare() < lootShare) {
            return;
        }
        MobVariant variant = mob.variant();
        event.getDrops().addAll(variant.loot().roll(mob.ratio(), mob.level().loot()));
        event.setDroppedExp((int) Math.round((event.getDroppedExp() + variant.xp()) * mob.level().xp()));
        stats.add(killer.getUniqueId(), variant.id(), mob.ratio());
        QuestBridge.signal(killer, "etermob:kill", Map.of("type", entity.getType().name().toLowerCase(Locale.ROOT),
                "variant", variant.id(), "ratio", String.valueOf(mob.ratio()), "tier", variant.tier().id()), 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Volley.onHit(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!creeperBlockDamage && event.getEntity() instanceof Creeper) {
            event.blockList().clear();
        }
    }

    /** Bloc temporaire d'une capacité (pilier...) cassé par un joueur : il ne lâche rien. */
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
