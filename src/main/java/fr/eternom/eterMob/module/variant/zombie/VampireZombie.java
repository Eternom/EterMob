package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Bat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vampire, rare.
 * <ul>
 *   <li>Saignement : chaque coup fait saigner le joueur 3 s, 1 point de vie par seconde (2,2 au ratio 5) qui ignore
 *   l'armure et les enchantements ; chaque goutte soigne le vampire d'autant. Un nouveau coup relance le saignement,
 *   sans le cumuler.</li>
 *   <li>La nuée : quand il tombe sous 30 % de vie, il se dissout en chauves-souris (4, 6 au ratio 5) qui tournent autour
 *   du joueur 3 s, pendant que lui est invisible et intouchable. Puis il se reforme derrière le joueur : chaque
 *   chauve-souris encore en vie lui rend 15 % de sa vie. Une fois par vie, deux pour un Fléau. Les chauves-souris ne
 *   lâchent rien et ne sont pas sauvegardées.</li>
 * </ul>
 * Forêts sombres, jardin pâle, marais, jungles.
 */
public class VampireZombie extends ZombieVariant {

    private static final int BLEED_SECONDS = 3;
    private static final double BLEED_DAMAGE = 1;

    /** Seuil de vie de la nuée, durée (ticks), chauves-souris, et soin par chauve-souris survivante (part de la vie). */
    private static final double SWARM_THRESHOLD = 0.3;
    private static final int SWARM_TICKS = 60;
    private static final int BATS = 4;
    private static final int BATS_SCOURGE = 6;
    private static final double HEAL_PER_BAT = 0.15;
    /** Rayon et hauteur du cercle des chauves-souris autour du joueur, mise à jour toutes les 2 ticks. */
    private static final double SWARM_RADIUS = 2;
    private static final double SWARM_HEIGHT = 1.5;
    private static final int SWARM_STEP = 2;
    private static final Color BLOOD = Color.fromRGB(0x8A0303);

    /** Saignement en cours par joueur. */
    private final Map<UUID, BukkitTask> bleeding = new HashMap<>();
    /** Nuées utilisées par vampire chargé, et vampires en nuée (par identifiant d'entité). */
    private final Map<Integer, Integer> swarmsUsed = new HashMap<>();
    private final Set<Integer> swarming = new HashSet<>();

    public VampireZombie() {
        super("zombie_vampire", Rarity.RARE, "Sanguis");
    }

    /** Forêts sombres, jardin pâle, marais, jungles. */
    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.DARK, Spawns.SWAMPS, Spawns.JUNGLES);
    }

    @Override
    public void onLoad(ActiveMob mob) {
        // Déchargé pendant une nuée : il se reforme
        LivingEntity zombie = mob.entity();
        zombie.setInvisible(false);
        zombie.setInvulnerable(false);
        zombie.setAI(true);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (event.getDamager() != mob.entity() || !(victim instanceof Player player)
                || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        bleed(mob, player);
    }

    private void bleed(ActiveMob mob, Player player) {
        BukkitTask previous = bleeding.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        LivingEntity zombie = mob.entity();
        double damage = mob.scale(BLEED_DAMAGE);
        DamageSource fatal = DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(zombie).withDirectEntity(zombie).build();
        int[] left = {BLEED_SECONDS};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(VampireZombie.class), () -> {
            if (left[0]-- <= 0 || !player.isOnline() || player.isDead()) {
                task[0].cancel();
                bleeding.remove(player.getUniqueId(), task[0]);
                return;
            }
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(BLOOD, 1.2f));
            trueDamage(player, damage, fatal);
            heal(zombie, damage);
        }, 20, 20);
        bleeding.put(player.getUniqueId(), task[0]);
    }

    private static void heal(LivingEntity zombie, double amount) {
        AttributeInstance max = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && zombie.isValid() && !zombie.isDead()) {
            zombie.setHealth(Math.min(max.getValue(), zombie.getHealth() + amount));
        }
    }

    /** Un coup qui le ferait passer sous 30 % (même fatal) : il le prend, garde au moins 1 PV, et part en nuée. */
    @Override
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
        LivingEntity zombie = mob.entity();
        int id = zombie.getEntityId();
        AttributeInstance max = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || swarming.contains(id)) {
            return;
        }
        int allowed = mob.ratio() >= service().ratios().max() ? 2 : 1;
        double after = zombie.getHealth() - event.getFinalDamage();
        if (after > max.getValue() * SWARM_THRESHOLD || swarmsUsed.getOrDefault(id, 0) >= allowed) {
            return;
        }
        event.setCancelled(true);
        zombie.setHealth(Math.max(1, after));
        swarmsUsed.merge(id, 1, Integer::sum);
        swarm(mob);
    }

    private void swarm(ActiveMob mob) {
        Zombie zombie = (Zombie) mob.entity();
        int id = zombie.getEntityId();
        LivingEntity anchor = zombie.getTarget() instanceof Player player ? player : zombie;
        swarming.add(id);
        Location start = zombie.getLocation().add(0, 1, 0);
        zombie.getWorld().spawnParticle(Particle.LARGE_SMOKE, start, 25, 0.4, 0.6, 0.4, 0.02);
        zombie.getWorld().playSound(start, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.7f);
        zombie.setInvisible(true);
        zombie.setInvulnerable(true);
        zombie.setAI(false);
        int count = mob.ratio() >= service().ratios().max() ? BATS_SCOURGE : BATS;
        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bats.add(Minions.spawn(start, Bat.class, bat -> {
                bat.setAwake(true);
                bat.setAI(false);
                bat.setGravity(false);
            }));
        }
        int[] elapsed = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(VampireZombie.class), () -> {
            elapsed[0] += SWARM_STEP;
            Location center = anchor.isValid() ? anchor.getLocation() : start;
            if (elapsed[0] < SWARM_TICKS && zombie.isValid()) {
                for (int i = 0; i < bats.size(); i++) {
                    Bat bat = bats.get(i);
                    if (bat.isValid()) {
                        double angle = elapsed[0] * 0.15 + i * Math.PI * 2 / bats.size();
                        bat.teleport(center.clone().add(Math.cos(angle) * SWARM_RADIUS, SWARM_HEIGHT, Math.sin(angle) * SWARM_RADIUS));
                    }
                }
                return;
            }
            task[0].cancel();
            reform(mob, bats, anchor);
        }, SWARM_STEP, SWARM_STEP);
    }

    /** Fin de la nuée : les chauves-souris disparaissent, il réapparaît derrière le joueur, soigné par les survivantes. */
    private void reform(ActiveMob mob, List<Bat> bats, LivingEntity anchor) {
        Zombie zombie = (Zombie) mob.entity();
        swarming.remove(zombie.getEntityId());
        int alive = 0;
        for (Bat bat : bats) {
            if (bat.isValid() && !bat.isDead()) {
                alive++;
                bat.getWorld().spawnParticle(Particle.POOF, bat.getLocation(), 5, 0.2, 0.2, 0.2, 0.02);
                bat.remove();
            }
        }
        if (!zombie.isValid()) {
            return;
        }
        if (anchor instanceof Player player && player.isOnline() && player.getWorld() == zombie.getWorld()) {
            zombie.teleport(behind(player));
            zombie.setTarget(player);
        }
        zombie.setInvisible(false);
        zombie.setInvulnerable(false);
        zombie.setAI(true);
        AttributeInstance max = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            heal(zombie, alive * HEAL_PER_BAT * max.getValue());
        }
        Location at = zombie.getLocation().add(0, 1, 0);
        zombie.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 25, 0.4, 0.6, 0.4, 0.02);
        zombie.getWorld().spawnParticle(Particle.DUST, at, 20, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(BLOOD, 1.4f));
        zombie.getWorld().playSound(at, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
    }

    /** 1,5 bloc derrière le joueur s'il y a la place ; sinon sa position. */
    private static Location behind(Player player) {
        Vector back = player.getLocation().getDirection().setY(0);
        if (back.lengthSquared() > 0.01) {
            Block feet = player.getLocation().subtract(back.normalize().multiply(1.5)).getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return player.getLocation();
    }

    @Override
    public void onRemove(ActiveMob mob) {
        swarmsUsed.remove(mob.entity().getEntityId());
        swarming.remove(mob.entity().getEntityId());
    }

    /** Fléau : gouttes de sang autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        if (!swarming.contains(mob.entity().getEntityId())) {
            puff(mob, Particle.DUST, 3, 1.2, 0.35, new Particle.DustOptions(BLOOD, 1f));
        }
    }
}
