package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Revenant, rare. À sa mort il ne lâche rien : ses os restent au sol (un crâne et des os, visibles), et 3 s plus tard il
 * se relève avec la moitié de sa vie. Pour l'en empêcher, il faut rester sur ses os (à 1,5 bloc) : ils se brisent et
 * lâchent son butin.
 * <ul>
 *   <li>Ratio 1 et 2 : il revient une fois.</li>
 *   <li>Ratio 3 et 4 : pendant l'attente, un bébé zombie faible sort de ses os pour distraire (6 PV, coups minuscules,
 *   disparaît à la fin) ; en se relevant, il projette des éclats d'os (petits dégâts et recul à moins de 3 blocs).</li>
 *   <li>Ratio 5 : deux bébés zombies, les éclats, et il revient deux fois.</li>
 * </ul>
 * Déserts, badlands, montagnes et profondeurs (sous Y = 0).
 */
public class ReviveSkeleton extends SkeletonVariant {

    private static final int REVIVE_TICKS = 60;
    private static final double CRUSH_DISTANCE = 1.5;
    /** À partir de ce ratio : bébés zombies pendant l'attente et éclats d'os au retour. */
    private static final int AWAKENED_RATIO = 3;
    private static final double BABY_HEALTH = 6;
    private static final double BABY_DAMAGE = 1;
    /** Éclats d'os : rayon (blocs) et dégâts de base, multipliés par le ratio. */
    private static final double SHARDS_RADIUS = 3;
    private static final double SHARDS_DAMAGE = 2;

    private NamespacedKey revivesKey;

    public ReviveSkeleton() {
        super("skeleton_revive", Rarity.RARE, "Resurgens");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.DRY, Spawns.MOUNTAINS) || Spawns.below(entity, 0);
    }

    private NamespacedKey revivesKey() {
        if (revivesKey == null) {
            revivesKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(ReviveSkeleton.class), "revives");
        }
        return revivesKey;
    }

    private int allowedRevives(int ratio) {
        return ratio >= service().ratios().max() ? 2 : 1;
    }

    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        int revives = mob.entity().getPersistentDataContainer().getOrDefault(revivesKey(), PersistentDataType.INTEGER, 0);
        if (revives >= allowedRevives(mob.ratio())) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        int experience = event.getDroppedExp();
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location bones = mob.entity().getLocation();
        crumble(bones);
        List<Entity> remains = bonesOnGround(bones);
        if (mob.ratio() >= AWAKENED_RATIO) {
            int babies = mob.ratio() >= service().ratios().max() ? 2 : 1;
            for (int i = 0; i < babies; i++) {
                remains.add(baby(bones, mob.entity().getKiller()));
            }
        }
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(ReviveSkeleton.class), () -> {
            remains.forEach(Entity::remove);
            if (!bones.isChunkLoaded()) {
                return;
            }
            if (!bones.getNearbyPlayers(CRUSH_DISTANCE).isEmpty()) {
                // Os brisés : il ne reviendra pas, son butin tombe
                crumble(bones);
                bones.getWorld().playSound(bones, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.8f);
                drops.forEach(item -> bones.getWorld().dropItemNaturally(bones, item));
                if (experience > 0) {
                    bones.getWorld().spawn(bones, ExperienceOrb.class, orb -> orb.setExperience(experience));
                }
                return;
            }
            revive(mob, bones, revives + 1);
        }, REVIVE_TICKS);
    }

    /** Un crâne et trois os couchés au sol (affichages, jamais sauvegardés). */
    private static List<Entity> bonesOnGround(Location at) {
        List<Entity> remains = new ArrayList<>();
        Location ground = at.getBlock().getLocation().add(0.5, 0.05, 0.5);
        remains.add(display(ground.clone().add(0, 0.2, 0), Material.SKELETON_SKULL, 0, 0.55f));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Location spot = ground.clone().add(random.nextDouble(-0.5, 0.5), 0, random.nextDouble(-0.5, 0.5));
            remains.add(display(spot, Material.BONE, random.nextFloat() * (float) Math.PI * 2, 0.6f));
        }
        return remains;
    }

    /** Objet posé à plat (couché sur le sol), tourné de yaw. */
    private static ItemDisplay display(Location at, Material material, float yaw, float size) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(material));
            display.setPersistent(false);
            boolean flat = material == Material.BONE;
            AxisAngle4f lay = flat ? new AxisAngle4f((float) Math.PI / 2, 1, 0, 0) : new AxisAngle4f();
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(yaw, 0, 1, 0),
                    new Vector3f(size, size, size), lay));
        });
    }

    /** Bébé zombie faible qui sort des os pour distraire. */
    private static Zombie baby(Location at, Player killer) {
        return Minions.spawn(at, Zombie.class, baby -> {
            baby.setBaby();
            AttributeInstance max = baby.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(BABY_HEALTH);
                baby.setHealth(BABY_HEALTH);
            }
            AttributeInstance damage = baby.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(BABY_DAMAGE);
            }
            if (killer != null) {
                baby.setTarget(killer);
            }
        });
    }

    private void revive(ActiveMob mob, Location bones, int revives) {
        crumble(bones);
        bones.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bones.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        Skeleton risen = bones.getWorld().spawn(bones, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, skeleton -> {
            service().apply(skeleton, this, mob.ratio());
            skeleton.getPersistentDataContainer().set(revivesKey(), PersistentDataType.INTEGER, revives);
        });
        AttributeInstance max = risen.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            risen.setHealth(max.getValue() / 2);
        }
        if (mob.ratio() >= AWAKENED_RATIO) {
            bones.getWorld().playSound(bones, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.6f);
            shockwave(risen, bones, SHARDS_RADIUS, mob.scale(SHARDS_DAMAGE), 0.6, 0.3);
        }
    }

    /** Os qui volent. */
    private static void crumble(Location location) {
        location.getWorld().spawnParticle(Particle.BLOCK, location.clone().add(0, 0.3, 0), 30, 0.4, 0.2, 0.4, 0.1,
                Material.BONE_BLOCK.createBlockData());
    }

    /** Fléau : flammes d'âme autour de lui. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.SOUL_FIRE_FLAME, 2, 1, 0.35);
    }
}
