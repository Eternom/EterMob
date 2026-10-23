package fr.eternom.eterMob.module.boss;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
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
            if (IMMUNE.contains(event.getCause()) || fight.invulnerable()) {
                event.setCancelled(true);
                return;
            }
            event.setDamage(event.getDamage() / fight.toughness());
            fight.boss().onDamaged(fight, event);
            return;
        }
        BossFight owner = bosses.ownerOf(event.getEntity());
        if (owner != null) {
            owner.boss().onOwnedDamaged(owner, event.getEntity(), event);
        }
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
