package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Le zombie de base, la référence des autres. L'appel de la horde : quand il repère un joueur, il grogne et les zombies
 * autour de lui qui ne chassent personne (husks et noyés compris) se mettent à chasser ce joueur. Rayon de 12 blocs,
 * environ 26 au ratio 5 ; au plus un appel toutes les 10 s. Aucun mob en plus : il réveille ceux qui sont là, et les
 * zombies appelés n'appellent pas à leur tour.
 */
public class ClassicZombie extends ZombieVariant {

    private static final double CALL_RANGE = 12;
    private static final long CALL_COOLDOWN_MILLIS = 10_000;

    private final Map<Integer, Long> nextCall = new HashMap<>();

    public ClassicZombie() {
        super("zombie", Rarity.COMMUN, "Putridus");
    }

    @Override
    public void onTarget(ActiveMob mob, EntityTargetLivingEntityEvent event) {
        // CUSTOM : cible donnée par un plugin (dont un autre appel) : pas d'appel en chaîne
        if (!(event.getTarget() instanceof Player player) || event.getReason() == EntityTargetEvent.TargetReason.CUSTOM) {
            return;
        }
        LivingEntity zombie = mob.entity();
        long now = System.currentTimeMillis();
        if (now < nextCall.getOrDefault(zombie.getEntityId(), 0L)) {
            return;
        }
        nextCall.put(zombie.getEntityId(), now + CALL_COOLDOWN_MILLIS);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.6f);
        for (Zombie other : zombie.getLocation().getNearbyEntitiesByType(Zombie.class, mob.scale(CALL_RANGE))) {
            if (other != zombie && other.getTarget() == null && !other.isDead()) {
                other.setTarget(player);
            }
        }
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextCall.remove(mob.entity().getEntityId());
    }
}
