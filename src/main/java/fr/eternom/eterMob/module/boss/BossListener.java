package fr.eternom.eterMob.module.boss;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;

import java.util.EnumSet;
import java.util.Set;

/** Suivi des boss, protections communes (chute, étouffement, feu, montures...), et dégâts vers le boss et ses entités. */
public class BossListener implements Listener {

    /** Ce qui ne blesse jamais un boss : on ne le tue pas avec le décor. */
    private static final Set<EntityDamageEvent.DamageCause> IMMUNE = EnumSet.of(
            EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.DROWNING, EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.CRAMMING,
            EntityDamageEvent.DamageCause.FREEZE, EntityDamageEvent.DamageCause.FLY_INTO_WALL,
            EntityDamageEvent.DamageCause.CONTACT);

    private final BossService bosses;

    public BossListener(BossService bosses) {
        this.bosses = bosses;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        bosses.attach(event.getEntity());
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        bosses.detach(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        BossFight fight = bosses.fight(event.getEntity());
        if (fight != null) {
            if (IMMUNE.contains(event.getCause()) || fight.invulnerable() || fight.dying()) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(event.getDamage() / (fight.toughness() * fight.boss().toughness()));
            fight.boss().onDamaged(fight, event);
            if (event.isCancelled()) {
                return;
            }
            LivingEntity boss = fight.entity();
            double dealt = Math.min(event.getFinalDamage(), boss.getHealth());
            Player attacker = attacker(event);
            if (attacker != null) {
                fight.dealt(attacker, dealt);
            }
            if (event.getFinalDamage() >= boss.getHealth()) {
                // Coup fatal : il s'effondre 3 s avant de mourir
                event.setCancelled(true);
                bosses.beginDeath(fight);
            }
            return;
        }
        if (event.getEntity() instanceof Player player && event instanceof EntityDamageByEntityEvent byEntity) {
            BossFight source = bosses.fightOf(byEntity.getDamager());
            if (source != null && source.enraged()) {
                event.setDamage(event.getDamage() * BossService.ENRAGE_DAMAGE);
            }
            return;
        }
        BossFight owner = bosses.ownerOf(event.getEntity());
        if (owner != null) {
            owner.boss().onOwnedDamaged(owner, event.getEntity(), event);
        }
    }

    /** Dégâts reçus d'un boss ou de ses entités, pour le bilan (après tous les autres plugins). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHurt(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            BossFight source = bosses.fightOf(event.getDamager());
            if (source != null) {
                source.taken(player, event.getFinalDamage());
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        bosses.playerDied(event.getPlayer());
    }

    /** Le joueur qui a porté ce coup (de près ou avec un projectile), ou null. */
    private static Player attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        return byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    /** Aucun butin pour l'instant. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        BossFight fight = bosses.fight(event.getEntity());
        if (fight != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            bosses.died(fight);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (bosses.fight(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Pas de bateau ni de wagonnet. */
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (bosses.fight(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    /** Les supports invisibles d'un boss (piliers...) ne se manipulent pas. */
    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (bosses.ownerOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }
}
