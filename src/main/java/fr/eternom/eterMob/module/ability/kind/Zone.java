package fr.eternom.eterMob.module.ability.kind;

import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * zone : count zones au sol (3), sous les joueurs à moins de reach blocs (24) puis au hasard autour d'eux.
 * mode linger : une flaque qui reste seconds (10) : chaque seconde, dégâts et états à ceux dedans (encre, poison...) ;
 * mode impact : un cercle pendant le télégraphe, puis un seul coup (pluie d'âmes, météores). color : RRGGBB.
 */
public final class Zone extends Ability {

    private final boolean linger;
    private final int count;
    private final double seconds;
    private final double reach;
    private final Color color;

    public Zone(ConfigurationSection config) {
        super("zone", config, new Defaults(Trigger.ACTIVE, 18, 1, 0, 30, 1, 2, 2.5, 0, 0));
        this.linger = !config.getString("mode", "linger").toLowerCase(Locale.ROOT).equals("impact");
        this.count = Math.clamp(config.getInt("count", 3), 1, 12);
        this.seconds = Math.clamp(config.getDouble("seconds", 10), 1, 60);
        this.reach = Math.clamp(config.getDouble("reach", 24), 4, 64);
        Color parsed;
        try {
            parsed = Color.fromRGB(Integer.parseInt(config.getString("color", "1A1A2E"), 16));
        } catch (IllegalArgumentException e) {
            parsed = Color.fromRGB(0x1A1A2E);
        }
        this.color = parsed;
    }

    @Override
    protected fr.eternom.eterLib.module.combat.DamageKind defaultKind() {
        return fr.eternom.eterLib.module.combat.DamageKind.MAGIC;
    }

    @Override
    public boolean inRange(double distance, boolean hasSight) {
        return distance <= reach;
    }

    @Override
    public double score(AbilityContext context) {
        return clamp01(0.35 + 0.1 * players(context.self().getLocation(), reach).size());
    }

    @Override
    public void begin(AbilityContext context) {
        List<Location> spots = new ArrayList<>();
        List<Player> near = players(context.self().getLocation(), reach);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location base = near.isEmpty() ? context.self().getLocation() : near.get(i % near.size()).getLocation();
            spots.add(i < near.size() ? base : base.add(random.nextDouble(-5, 5), 0, random.nextDouble(-5, 5)));
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
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        if (!linger) {
            for (Location spot : spots) {
                spot.getWorld().spawnParticle(Particle.SOUL, spot.clone().add(0, 1.5, 0), 25, 0.5, 1, 0.5, 0.05);
                spot.getWorld().playSound(spot, Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 0.6f);
                for (Player player : players(spot, radius)) {
                    hit(context, player, damage, spot);
                }
            }
            return;
        }
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (ticks >= seconds * 20 || !context.self().isValid()) {
                    cancel();
                    return;
                }
                for (Location spot : spots) {
                    for (int i = 0; i < 10; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                        double r = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
                        spot.getWorld().spawnParticle(Particle.DUST, spot.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r), 1, 0, 0, 0, 0, dust);
                    }
                    if (ticks % 20 == 0) {
                        for (Player player : players(spot, radius)) {
                            hit(context, player, damage, spot);
                        }
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(Services.plugin(), 0, 5);
    }

    @Override
    public double value() {
        return super.value() * count * (linger ? seconds / 3 : 1);
    }
}
