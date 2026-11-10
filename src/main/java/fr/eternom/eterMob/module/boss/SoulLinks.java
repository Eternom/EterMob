package fr.eternom.eterMob.module.boss;

import fr.eternom.eterLib.module.combat.DamageKind;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.Hits;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lien d'âmes (la Gardienne) : des joueurs liés partagent leurs dégâts : chaque coup reçu par l'un n'est gardé qu'à
 * moitié, l'autre moitié est répartie en dégâts bruts entre les autres liés. Le lien casse quand ils s'éloignent de plus
 * de BREAK blocs, ou à la fin. Un crochet du combat d'EterLib (Main) appelle share ; état soullink pour l'icône.
 */
public final class SoulLinks {

    private static final double BREAK = 8;

    private record Link(List<UUID> players, long until, LivingEntity source) {
    }

    private static final Map<UUID, Link> LINKS = new HashMap<>();
    private static boolean sharing;

    private SoulLinks() {
    }

    /** Lie ces joueurs pendant seconds. */
    public static void link(List<Player> players, double seconds, LivingEntity source) {
        if (players.size() < 2) {
            return;
        }
        List<UUID> ids = players.stream().map(Player::getUniqueId).toList();
        Link link = new Link(ids, System.currentTimeMillis() + Math.round(seconds * 1000), source);
        for (Player player : players) {
            LINKS.put(player.getUniqueId(), link);
            if (Services.states() != null) {
                Services.states().apply(player, "soullink", 1, seconds, source);
            }
        }
    }

    /** Crochet du combat : la victime ne garde que la moitié, le reste va aux autres liés (un tick plus tard). */
    public static double share(LivingEntity victim, double damage) {
        if (sharing || damage <= 0 || !(victim instanceof Player)) {
            return damage;
        }
        Link link = LINKS.get(victim.getUniqueId());
        if (link == null || System.currentTimeMillis() > link.until()) {
            return damage;
        }
        List<Player> others = new ArrayList<>();
        for (UUID id : link.players()) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && !id.equals(victim.getUniqueId()) && Hits.fighting(other)) {
                others.add(other);
            }
        }
        if (others.isEmpty()) {
            return damage;
        }
        double each = damage / 2 / others.size();
        Bukkit.getScheduler().runTask(Services.plugin(), () -> {
            sharing = true;
            try {
                for (Player other : others) {
                    Hits.abilityDamage(other, each, link.source() != null && link.source().isValid() ? link.source() : null, DamageKind.TRUE);
                }
            } finally {
                sharing = false;
            }
        });
        return damage / 2;
    }

    /** Toutes les 5 ticks : liens trop étirés cassés, fils de particules entre les liés. */
    public static void tick() {
        long now = System.currentTimeMillis();
        LINKS.values().removeIf(link -> now > link.until());
        for (Link link : new ArrayList<>(new java.util.HashSet<>(LINKS.values()))) {
            List<Player> players = new ArrayList<>();
            link.players().forEach(id -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    players.add(player);
                }
            });
            boolean broken = false;
            for (int i = 0; i + 1 < players.size(); i++) {
                Location a = players.get(i).getLocation();
                Location b = players.get(i + 1).getLocation();
                if (!a.getWorld().equals(b.getWorld()) || a.distanceSquared(b) > BREAK * BREAK) {
                    broken = true;
                    break;
                }
                thread(a.clone().add(0, 1.2, 0), b.clone().add(0, 1.2, 0));
            }
            if (broken) {
                link.players().forEach(LINKS::remove);
                players.forEach(player -> {
                    if (Services.states() != null) {
                        Services.states().remove(player, "soullink");
                    }
                    player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.2, 0), 8, 0.3, 0.3, 0.3, 0.02);
                });
            }
        }
    }

    private static void thread(Location from, Location to) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x66CCFF), 0.8f);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1) {
            return;
        }
        step.normalize().multiply(0.6);
        Location point = from.clone();
        for (double done = 0; done < length; done += 0.6) {
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            point.add(step);
        }
    }
}
