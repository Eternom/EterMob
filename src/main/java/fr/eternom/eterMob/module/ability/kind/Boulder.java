package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * boulder : des rochers (faux blocs, rien ne touche la map) : mode throw, lancés en cloche depuis lui ; mode fall,
 * tombés du ciel. Leur ombre (cercle rouge) marque l'impact pendant le télégraphe et le vol : on en sort. À l'impact :
 * dégâts, états, et le rocher se brise. count : nombre de rochers (sa cible, puis d'autres joueurs) ; block : matière
 * (MOSSY_COBBLESTONE) ; size : taille (1,4) ; flight : ticks de vol (24).
 */
public final class Boulder extends Ability {

    private final boolean fall;
    private final int count;
    private final Material block;
    private final float size;
    private final int flight;

    public Boulder(ConfigurationSection config) {
        super("boulder", config, new Defaults(Trigger.ACTIVE, 10, 0.6, 5, 30, 1, 12, 3, 0.5, 0.3));
        this.fall = config.getString("mode", "throw").toLowerCase(Locale.ROOT).equals("fall");
        this.count = Math.clamp(config.getInt("count", 1), 1, 12);
        Material material = Material.matchMaterial(config.getString("block", "MOSSY_COBBLESTONE"));
        this.block = material == null ? Material.MOSSY_COBBLESTONE : material;
        this.size = (float) Math.clamp(config.getDouble("size", 1.4), 0.4, 4);
        this.flight = Math.clamp(config.getInt("flight", 24), 6, 80);
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        double far = Math.min(1, situation.distance() / 18);
        return clamp01(0.3 + 0.35 * far + (situation.targetRanged() ? 0.25 : 0) + (situation.unreachable() ? 0.3 : 0));
    }

    @Override
    public void begin(AbilityContext context) {
        List<Location> spots = new ArrayList<>();
        if (context.target() != null) {
            spots.add(context.target().getLocation());
        }
        for (Player player : players(context.self().getLocation(), maxRange)) {
            if (spots.size() >= count) {
                break;
            }
            if (context.target() == null || !player.equals(context.target())) {
                spots.add(player.getLocation());
            }
        }
        context.data(spots);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        List<Location> spots = context.data();
        spots.forEach(spot -> dangerRing(spot, radius, progress));
    }

    @Override
    public void cast(AbilityContext context) {
        List<Location> spots = context.data();
        strike(context, spots);
    }

    /** Lance (ou fait tomber) un rocher sur chaque point (le Dernier éboulement d'un boss s'en sert directement). */
    public void strike(AbilityContext context, List<Location> spots) {
        BlockData data = block.createBlockData();
        for (Location target : spots) {
            Location from = fall ? target.clone().add(0, 14, 0) : context.self().getEyeLocation();
            BlockDisplay rock = from.getWorld().spawn(from, BlockDisplay.class, display -> {
                display.setBlock(data);
                display.setPersistent(false);
                display.setTeleportDuration(2);
                display.setTransformation(new Transformation(new Vector3f(-size / 2, -size / 2, -size / 2), new AxisAngle4f(),
                        new Vector3f(size, size, size), new AxisAngle4f()));
            });
            if (!fall) {
                from.getWorld().playSound(from, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.6f);
            }
            new BukkitRunnable() {
                private int ticks;

                @Override
                public void run() {
                    if (!rock.isValid()) {
                        cancel();
                        return;
                    }
                    ticks += 2;
                    double t = Math.min(1, (double) ticks / flight);
                    Vector line = target.toVector().subtract(from.toVector()).multiply(t);
                    // Cloche : jusqu'à 5 blocs au-dessus de la ligne au milieu du vol (lancer seulement)
                    double arc = fall ? 0 : 4 * 5 * t * (1 - t);
                    rock.teleport(from.clone().add(line).add(0, arc, 0));
                    dangerRing(target, radius, t);
                    if (t >= 1) {
                        cancel();
                        impact(context, target, data);
                        rock.remove();
                    }
                }
            }.runTaskTimer(Services.plugin(), 2, 2);
        }
    }

    private void impact(AbilityContext context, Location at, BlockData data) {
        at.getWorld().playSound(at, Sound.BLOCK_STONE_BREAK, 1.6f, 0.5f);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 40, radius / 2, 0.4, radius / 2, 0.1, data);
        for (Player player : players(at, radius)) {
            hit(context, player, damage, at);
        }
    }

    @Override
    public double value() {
        return super.value() * count;
    }
}
