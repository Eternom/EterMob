package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le squelette de base, la référence des autres. Le tir qui s'affine : chaque flèche qui touche le même joueur d'affilée
 * fait 10 % de dégâts en plus (jusqu'à +40 % à la 5e) ; une flèche ratée, ou qui touche autre chose, remet le compteur à
 * zéro. Rester à découvert devant lui coûte de plus en plus cher.
 */
public class ClassicSkeleton extends SkeletonVariant {

    private static final double BONUS_PER_HIT = 0.1;
    private static final int MAX_STREAK = 5;

    /** Série en cours par squelette : le joueur visé et le nombre de flèches d'affilée. */
    private record Streak(UUID player, int hits) {
    }

    private final Map<Integer, Streak> streaks = new HashMap<>();

    public ClassicSkeleton() {
        super("skeleton", Rarity.COMMUN, "Ossis");
    }

    @Override
    public List<String> lore() {
        return List.of(
                "Tir qui s'affine : +10 % par flèche d'affilée.",
                "Biomes : partout.");
    }

    /** Arrive avant les dégâts : la série est à jour quand onAttack calcule le bonus. */
    @Override
    public void onProjectileHit(ActiveMob mob, ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow)) {
            return;
        }
        int id = mob.entity().getEntityId();
        if (!(event.getHitEntity() instanceof Player player)) {
            streaks.remove(id);
            return;
        }
        Streak streak = streaks.get(id);
        int hits = streak != null && streak.player().equals(player.getUniqueId()) ? Math.min(MAX_STREAK, streak.hits() + 1) : 1;
        streaks.put(id, new Streak(player.getUniqueId(), hits));
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        super.onAttack(mob, victim, event);
        Streak streak = streaks.get(mob.entity().getEntityId());
        if (!(event.getDamager() instanceof AbstractArrow) || streak == null || streak.hits() < 2
                || !streak.player().equals(victim.getUniqueId())) {
            return;
        }
        event.setDamage(event.getDamage() * (1 + BONUS_PER_HIT * (streak.hits() - 1)));
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 4 * streak.hits(), 0.3, 0.5, 0.3, 0.1);
    }

    @Override
    public void onRemove(ActiveMob mob) {
        streaks.remove(mob.entity().getEntityId());
    }
}
