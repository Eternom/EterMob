package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le zombie de base, la référence des autres. L'appel de la horde : quand il repère un joueur, il grogne et les zombies
 * autour de lui qui ne chassent personne (husks et noyés compris) se mettent à chasser ce joueur. Rayon de 12 blocs,
 * environ 26 au ratio 5. Un seul appel par zone : si un Putridus a déjà appelé à portée dans les 10 dernières secondes,
 * les autres se taisent (pas de chorale). Aucun mob en plus : il réveille ceux qui sont là, et les zombies appelés
 * n'appellent pas à leur tour.
 */
public class ClassicZombie extends ZombieVariant {

    private static final double CALL_RANGE = 12;
    private static final long CALL_COOLDOWN_MILLIS = 10_000;

    /** Appel récent : où, jusqu'à quand il fait taire les autres, et sa portée. */
    private record Call(Location where, long until, double range) {
    }

    private final List<Call> calls = new ArrayList<>();

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
        Location here = zombie.getLocation();
        calls.removeIf(call -> now > call.until());
        for (Call call : calls) {
            if (call.where().getWorld() == here.getWorld() && call.where().distanceSquared(here) <= call.range() * call.range()) {
                return;
            }
        }
        double range = mob.scale(CALL_RANGE);
        calls.add(new Call(here, now + CALL_COOLDOWN_MILLIS, range));
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.6f);
        for (Zombie other : here.getNearbyEntitiesByType(Zombie.class, range)) {
            if (other != zombie && other.getTarget() == null && !other.isDead()) {
                other.setTarget(player);
            }
        }
    }
}
