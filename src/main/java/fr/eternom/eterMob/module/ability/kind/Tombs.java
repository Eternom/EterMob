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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * tombs : une tombe sous chaque joueur à moins de reach blocs (24) : un cercle au sol pendant le télégraphe (fixé au
 * début : on s'en écarte), puis une pierre tombale (affichage, aucun vrai bloc) jaillit : dégâts et états à ceux encore
 * dans le cercle (radius 1,4). L'IA la lance quand on est dispersé.
 */
public final class Tombs extends Ability {

    private final double reach;

    public Tombs(ConfigurationSection config) {
        super("tombs", config, new Defaults(Trigger.ACTIVE, 16, 1.2, 0, 30, 1, 10, 1.4, 0, 0.6));
        this.reach = Math.clamp(config.getDouble("reach", 24), 4, 64);
    }

    @Override
    public boolean inRange(double distance, boolean hasSight) {
        return distance <= reach;
    }

    @Override
    public double score(AbilityContext context) {
        var situation = context.situation();
        // Dispersés : peu de monde autour de lui, la cible loin
        return clamp01(0.3 + (situation.playersNearSelf() <= 1 ? 0.3 : 0) + Math.min(0.3, situation.distance() / 30));
    }

    @Override
    public void begin(AbilityContext context) {
        List<Location> spots = new ArrayList<>();
        for (Player player : players(context.self().getLocation(), reach)) {
            spots.add(player.getLocation());
        }
        context.data(spots);
        context.self().getWorld().playSound(context.self().getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.6f);
    }

    @Override
    public void telegraph(AbilityContext context, double progress) {
        super.telegraph(context, progress);
        List<Location> spots = context.data();
        for (Location spot : spots) {
            dangerRing(spot, radius, progress);
        }
    }

    @Override
    public void cast(AbilityContext context) {
        List<Location> spots = context.data();
        BlockData stone = Material.CHISELED_STONE_BRICKS.createBlockData();
        for (Location spot : spots) {
            spot.getWorld().playSound(spot, Sound.BLOCK_STONE_BREAK, 1.2f, 0.5f);
            spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0.1, stone);
            BlockDisplay tomb = spot.getWorld().spawn(spot.clone().add(0, 0, 0), BlockDisplay.class, display -> {
                display.setBlock(stone);
                display.setPersistent(false);
                display.setTransformation(new Transformation(new Vector3f(-0.4f, 0, -0.15f), new AxisAngle4f(),
                        new Vector3f(0.8f, 1.4f, 0.3f), new AxisAngle4f()));
            });
            org.bukkit.Bukkit.getScheduler().runTaskLater(Services.plugin(), tomb::remove, 60);
            for (Player player : players(spot, radius)) {
                hit(context, player, damage, spot);
            }
        }
    }
}
