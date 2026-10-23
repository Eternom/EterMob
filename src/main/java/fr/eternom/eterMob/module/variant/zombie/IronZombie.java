package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Tank : beaucoup de vie, lent, presque insensible au recul, en casque et plastron de fer (qui ne tombent jamais ; pas
 * d'armure complète : avec sa vie, il deviendrait un sac à PV). Les flèches et autres projectiles ne lui font que la moitié des dégâts.
 * Toutes les 8 s, il charge en ligne droite sur le joueur qu'il chasse (entre 4 et 12 blocs) : s'il le touche pendant
 * la charge, coup plus fort et gros recul. Son casque le protège du soleil. Montagnes, neige, profondeurs.
 */
public class IronZombie extends ZombieVariant {

    /** Résistance au recul (0 à 1), pas multipliée par le ratio. */
    private static final double KNOCKBACK_RESISTANCE = 0.6;
    private static final double PROJECTILE_DAMAGE = 0.5;

    private static final int CHECK_TICKS = 10;
    private static final long CHARGE_COOLDOWN_MILLIS = 8_000;
    /** Une charge dure ce temps : un coup porté pendant ce temps est un coup de charge. */
    private static final long CHARGE_MILLIS = 1_200;
    private static final double MIN_DISTANCE = 4;
    private static final double MAX_DISTANCE = 12;
    private static final double CHARGE_SPEED = 1.3;
    private static final double CHARGE_DAMAGE = 1.3;
    private static final double CHARGE_KNOCKBACK = 1.4;

    private final Map<Integer, Long> nextCharge = new HashMap<>();
    private final Map<Integer, Long> chargingUntil = new HashMap<>();

    public IronZombie() {
        super("zombie_iron", Rarity.PEU_COMMUN, "Ferreus");
    }

    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.MOUNTAINS, Spawns.COLD) || Spawns.below(entity, 0);
    }

    @Override
    public double health() {
        return 1.6;
    }

    @Override
    public double damage() {
        return 1.1;
    }

    @Override
    public double speed() {
        return 0.8;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        AttributeInstance knockback = mob.entity().getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(KNOCKBACK_RESISTANCE);
        }
        EntityEquipment equipment = mob.entity().getEquipment();
        if (equipment == null) {
            return;
        }
        wear(equipment, EquipmentSlot.HEAD, Material.IRON_HELMET);
        wear(equipment, EquipmentSlot.CHEST, Material.IRON_CHESTPLATE);
    }

    private static void wear(EntityEquipment equipment, EquipmentSlot slot, Material material) {
        equipment.setItem(slot, new ItemStack(material));
        equipment.setDropChance(slot, 0);
    }

    @Override
    public void onDamaged(ActiveMob mob, EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile) {
            event.setDamage(event.getDamage() * PROJECTILE_DAMAGE);
        }
    }

    @Override
    public int tickInterval() {
        return CHECK_TICKS;
    }

    @Override
    public void tick(ActiveMob mob) {
        Zombie zombie = (Zombie) mob.entity();
        if (!zombie.isOnGround() || !(zombie.getTarget() instanceof Player target) || target.getWorld() != zombie.getWorld()) {
            return;
        }
        Vector toward = target.getLocation().toVector().subtract(zombie.getLocation().toVector()).setY(0);
        double distance = toward.length();
        long now = System.currentTimeMillis();
        int id = zombie.getEntityId();
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || now < nextCharge.getOrDefault(id, 0L) || !zombie.hasLineOfSight(target)) {
            return;
        }
        nextCharge.put(id, now + CHARGE_COOLDOWN_MILLIS);
        chargingUntil.put(id, now + CHARGE_MILLIS);
        zombie.setVelocity(toward.normalize().multiply(CHARGE_SPEED).setY(0.2));
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
        zombie.getWorld().spawnParticle(Particle.CLOUD, zombie.getLocation(), 12, 0.4, 0.1, 0.4, 0.05);
    }

    @Override
    public void onAttack(ActiveMob mob, LivingEntity victim, EntityDamageByEntityEvent event) {
        LivingEntity zombie = mob.entity();
        Long until = chargingUntil.get(zombie.getEntityId());
        if (event.getDamager() != zombie || until == null || System.currentTimeMillis() > until) {
            return;
        }
        chargingUntil.remove(zombie.getEntityId());
        event.setDamage(event.getDamage() * CHARGE_DAMAGE);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(IronZombie.class), () -> {
            if (!victim.isValid() || victim.getWorld() != zombie.getWorld()) {
                return;
            }
            Vector away = victim.getLocation().toVector().subtract(zombie.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                victim.setVelocity(away.normalize().multiply(CHARGE_KNOCKBACK).setY(0.5));
            }
        });
    }

    @Override
    public void onRemove(ActiveMob mob) {
        nextCharge.remove(mob.entity().getEntityId());
        chargingUntil.remove(mob.entity().getEntityId());
    }

    /** Fléau : reflets métalliques. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.WAX_OFF, 3, 1.2, 0.4);
    }
}
