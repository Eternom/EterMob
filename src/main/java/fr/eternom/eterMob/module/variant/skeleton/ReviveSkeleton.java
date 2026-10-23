package fr.eternom.eterMob.module.variant.skeleton;

import fr.eternom.eterMob.module.mob.ActiveMob;
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
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Revenant : à sa première mort il ne lâche rien et ses os restent au sol ; 3 s plus tard il se relève avec la moitié de
 * sa vie. Pour l'en empêcher, il faut rester sur ses os (à 1,5 bloc) : ils se brisent et lâchent son butin. Il ne se
 * relève qu'une fois. Déserts, badlands, montagnes et profondeurs (sous Y = 0).
 */
public class ReviveSkeleton extends SkeletonVariant {

    private static final int REVIVE_TICKS = 60;
    private static final double CRUSH_DISTANCE = 1.5;

    private NamespacedKey revivedKey;

    public ReviveSkeleton() {
        super("skeleton_revive", Rarity.RARE, "Resurgens");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.DRY, Spawns.MOUNTAINS) || Spawns.below(entity, 0);
    }

    private NamespacedKey revivedKey() {
        if (revivedKey == null) {
            revivedKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(ReviveSkeleton.class), "revived");
        }
        return revivedKey;
    }

    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        if (mob.entity().getPersistentDataContainer().has(revivedKey(), PersistentDataType.BYTE)) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        int experience = event.getDroppedExp();
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location bones = mob.entity().getLocation();
        crumble(bones);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(ReviveSkeleton.class), () -> {
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
            revive(mob, bones);
        }, REVIVE_TICKS);
    }

    private void revive(ActiveMob mob, Location bones) {
        crumble(bones);
        bones.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bones.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        Skeleton risen = bones.getWorld().spawn(bones, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, skeleton -> {
            service().apply(skeleton, this, mob.ratio());
            skeleton.getPersistentDataContainer().set(revivedKey(), PersistentDataType.BYTE, (byte) 1);
        });
        AttributeInstance max = risen.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            risen.setHealth(max.getValue() / 2);
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
