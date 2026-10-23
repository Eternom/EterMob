package fr.eternom.eterMob.module.variant.zombie;

import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.Minions;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Spawns;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Se divise comme un slime. Il est plus grand qu'un zombie normal ; à sa mort, il se divise en 2 copies plus petites,
 * chacune avec la moitié de sa vie et des coups moins forts, qui foncent sur son tueur. Les copies se divisent à leur
 * tour, sur 1 génération au ratio 1 et 2, 2 aux ratios 3 et 4, 3 au ratio 5 (1 → 2 → 4 → 8). Seul le grand d'origine
 * lâche son butin : les copies ne lâchent rien et ne sont pas sauvegardées. Marais, jungles, rivières et plages.
 */
public class SplitZombie extends ZombieVariant {

    /** Taille du grand d'origine, et taille et coups de chaque génération (en fois ceux de la précédente). */
    private static final double SIZE = 1.2;
    private static final double SHRINK = 0.75;
    private static final int COPIES = 2;

    private NamespacedKey generationKey;

    public SplitZombie() {
        super("zombie_split", Rarity.PEU_COMMUN, "Divisus");
    }

    /** Marais, jungles (grottes luxuriantes comprises), rivières et plages. */
    @Override
    protected boolean spawnsHere(LivingEntity entity) {
        return Spawns.biome(entity, Spawns.SWAMPS, Spawns.JUNGLES, Spawns.RIVERS);
    }

    private NamespacedKey generationKey() {
        if (generationKey == null) {
            generationKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(SplitZombie.class), "generation");
        }
        return generationKey;
    }

    /** Nombre de générations de copies selon le ratio : 1, 1, 2, 2, 3. */
    private static int generations(int ratio) {
        return 1 + (ratio - 1) / 2;
    }

    @Override
    public void onSpawn(ActiveMob mob) {
        setBase(mob.entity(), Attribute.SCALE, SIZE);
    }

    @Override
    public void onDeath(ActiveMob mob, EntityDeathEvent event) {
        LivingEntity parent = mob.entity();
        int generation = parent.getPersistentDataContainer().getOrDefault(generationKey(), PersistentDataType.INTEGER, 0);
        if (generation >= generations(mob.ratio())) {
            return;
        }
        AttributeInstance parentMax = parent.getAttribute(Attribute.MAX_HEALTH);
        double health = (parentMax == null ? 20 : parentMax.getValue()) / 2;
        double size = SIZE * Math.pow(SHRINK, generation + 1);
        double damageFactor = Math.pow(SHRINK, generation + 1);
        Location location = parent.getLocation();
        Player killer = parent.getKiller();
        location.getWorld().spawnParticle(Particle.ITEM_SLIME, location.clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
        location.getWorld().playSound(location, Sound.ENTITY_SLIME_SQUISH, 1f, 0.7f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < COPIES; i++) {
            Location spot = location.clone().add(random.nextDouble(-0.5, 0.5), 0, random.nextDouble(-0.5, 0.5));
            Minions.spawn(spot, Zombie.class, copy -> {
                copy.setAdult();
                service().apply(copy, this, mob.ratio());
                copy.getPersistentDataContainer().set(generationKey(), PersistentDataType.INTEGER, generation + 1);
                setBase(copy, Attribute.SCALE, size);
                AttributeInstance damage = copy.getAttribute(Attribute.ATTACK_DAMAGE);
                if (damage != null) {
                    damage.setBaseValue(damage.getBaseValue() * damageFactor);
                }
                setMaxHealth(copy, health);
                if (killer != null) {
                    copy.setTarget(killer);
                }
            });
        }
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Vie maximale exacte, en tenant compte du modificateur de ratio déjà posé ; le mob est soigné à fond. */
    private static void setMaxHealth(LivingEntity entity, double health) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        double factor = max.getValue() / max.getBaseValue();
        max.setBaseValue(health / factor);
        entity.setHealth(Math.min(health, max.getValue()));
    }

    /** Fléau : il suinte. */
    @Override
    public void ambient(ActiveMob mob) {
        puff(mob, Particle.ITEM_SLIME, 3, 1, 0.35);
    }
}
