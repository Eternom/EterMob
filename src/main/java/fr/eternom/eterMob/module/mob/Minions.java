package fr.eternom.eterMob.module.mob;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * Mobs invoqués par un variant (sbires, bébés d'un zombie qui se divise) : des mobs de Minecraft, jamais des variants,
 * qui ne lâchent ni objets ni expérience (sinon un invocateur serait une ferme) et ne sont jamais sauvegardés (ils
 * disparaissent quand leur chunk se décharge). Un sbire zombie n'appelle jamais de renforts (sinon boule de neige en
 * Difficile).
 */
public final class Minions {

    private static NamespacedKey key;

    private Minions() {
    }

    private static NamespacedKey key() {
        if (key == null) {
            key = new NamespacedKey(JavaPlugin.getProvidingPlugin(Minions.class), "minion");
        }
        return key;
    }

    /** Fait apparaître un sbire ; setup règle le mob avant qu'il entre dans le monde (bébé, cible...). */
    public static <T extends LivingEntity> T spawn(Location location, Class<T> type, Consumer<T> setup) {
        return location.getWorld().spawn(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM, mob -> {
            mob.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
            mob.setPersistent(false);
            noReinforcements(mob);
            // Retiré en fin de combat : il ne ramasse rien (l'objet disparaîtrait avec lui)
            mob.setCanPickupItems(false);
            setup.accept(mob);
        });
    }

    /** Zombie : n'appelle plus d'autres zombies quand on le frappe (renforts de Minecraft en Difficile). */
    public static void noReinforcements(LivingEntity mob) {
        AttributeInstance reinforcements = mob.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
        if (reinforcements != null) {
            reinforcements.setBaseValue(0);
            // Bonus de Minecraft (zombie « chef » en Difficile) : posés à l'apparition, en plus de la base
            reinforcements.getModifiers().forEach(reinforcements::removeModifier);
        }
    }

    /** États posés par les coups de ce sbire (« mark 0.15 4;slow 0.2 2 ») : une Page qui marque, une goule qui saigne. */
    public static void hitStates(LivingEntity minion, String states) {
        minion.getPersistentDataContainer().set(new NamespacedKey(JavaPlugin.getProvidingPlugin(Minions.class), "hit_states"),
                PersistentDataType.STRING, states);
    }

    public static String hitStates(Entity entity) {
        return entity.getPersistentDataContainer().get(new NamespacedKey(JavaPlugin.getProvidingPlugin(Minions.class), "hit_states"),
                PersistentDataType.STRING);
    }

    public static boolean is(Entity entity) {
        return entity.getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }
}
