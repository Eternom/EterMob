package fr.eternom.eterMob.module.boss;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un sous-boss : au-dessus d'un Fléau, tout son kit, un combat à phases avec sa barre (une « phase 1 et demie » avant les
 * boss). Il apparaît de lui-même en survie dans ses biomes (bosses.<id>.spawn), sans rayon de déplacement, et disparaît
 * s'il reste seul trop longtemps (despawn-minutes). Ses chiffres : config.yml > bosses.<id>.
 */
public abstract class SubBoss extends BrainBoss {

    protected double health;
    protected double toughness = 1.2;
    protected double armor;
    protected double magicResist;
    protected double piercing;
    protected double despawn = 10;
    protected double enrage = 10;
    protected final Map<String, Double> damage = new HashMap<>();

    protected SubBoss(String id, String name, String title, double health, double armor, double magicResist, double piercing,
                      Map<String, Double> damage) {
        super(id, name, title);
        this.health = health;
        this.armor = armor;
        this.magicResist = magicResist;
        this.piercing = piercing;
        this.damage.putAll(damage);
    }

    @Override
    public final void configure(ConfigurationSection section) {
        if (section != null) {
            health = Math.clamp(section.getDouble("health", health), 20, 1024);
            toughness = section.getDouble("toughness", toughness);
            armor = section.getDouble("armor", armor);
            magicResist = section.getDouble("magic-resist", magicResist);
            piercing = Math.clamp(section.getDouble("piercing", piercing), 0, 0.9);
            despawn = Math.max(0, section.getDouble("despawn-minutes", despawn));
            enrage = section.getDouble("enrage-minutes", enrage);
            for (String key : List.copyOf(damage.keySet())) {
                damage.put(key, section.getDouble("damage." + key, damage.get(key)));
            }
            readSpawn(section.getConfigurationSection("spawn"));
        }
        build();
    }

    /** Construit ses capacités d'après les chiffres (appelé après la config). */
    protected abstract void build();

    protected double damage(String key) {
        return damage.getOrDefault(key, 1.0);
    }

    @Override
    public boolean subBoss() {
        return true;
    }

    @Override
    public double despawnMinutes() {
        return despawn;
    }

    /** Pas de rayon de déplacement : réservé aux boss de donjon. */
    @Override
    public double leashRadius() {
        return 0;
    }

    @Override
    public double baseHealth() {
        return health;
    }

    @Override
    public double toughness() {
        return toughness;
    }

    @Override
    public double enrageMinutes() {
        return enrage;
    }

    @Override
    public double piercing() {
        return piercing;
    }

    @Override
    public double armor() {
        return armor;
    }

    @Override
    public double magicResist() {
        return magicResist;
    }

    protected static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    protected static void wear(LivingEntity entity, EquipmentSlot slot, Material material) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItem(slot, material == null ? null : ItemStack.of(material));
            equipment.setDropChance(slot, 0);
        }
    }

    /** Vie (0 à 1). */
    protected static double healthPart(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 1 : entity.getHealth() / max.getValue();
    }

    /** Soigne de amount, jamais au-dessus du plafond de sa phase. */
    protected void heal(BossFight fight, double amount) {
        LivingEntity entity = fight.entity();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? entity.getHealth() : max.getValue();
        entity.setHealth(Math.min(Math.min(full, service().healthCap(fight)), entity.getHealth() + amount));
    }
}
