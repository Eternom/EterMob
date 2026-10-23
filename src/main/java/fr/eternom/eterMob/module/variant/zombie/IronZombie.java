package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Rarity;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Tank : beaucoup de vie, lent, presque insensible au recul, en armure de fer (casque et plastron, armure complète à
 * partir du ratio 3). L'armure ne tombe jamais à sa mort. Son casque le protège du soleil : il survit le jour.
 */
public class IronZombie extends ZombieVariant {

    /** Résistance au recul (0 à 1), pas multipliée par le ratio. */
    private static final double KNOCKBACK_RESISTANCE = 0.6;
    /** Armure complète à partir de ce ratio. */
    private static final int FULL_ARMOR_RATIO = 3;

    public IronZombie() {
        super("zombie_iron", Rarity.PEU_COMMUN, "Ferreus");
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
        if (mob.ratio() >= FULL_ARMOR_RATIO) {
            wear(equipment, EquipmentSlot.LEGS, Material.IRON_LEGGINGS);
            wear(equipment, EquipmentSlot.FEET, Material.IRON_BOOTS);
        }
    }

    private static void wear(EntityEquipment equipment, EquipmentSlot slot, Material material) {
        equipment.setItem(slot, new ItemStack(material));
        equipment.setDropChance(slot, 0);
    }

    /** Fléau : reflets métalliques. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.WAX_OFF, 3, 1.2, 0.4);
    }
}
