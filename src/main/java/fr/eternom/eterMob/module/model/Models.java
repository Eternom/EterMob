package fr.eternom.eterMob.module.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Modèles Blockbench des monstres, par BetterModel s'il est installé (sinon rien ne change : apparence de Minecraft).
 * Le modèle porte l'identifiant du jeu (« goule », « mortarius »...) : un monstre sans modèle de ce nom reste vanilla.
 * BetterModel joue seul idle, walk et death ; EterMob joue le reste (attack, hurt, capacités).
 */
public final class Models {

    /** Ce qui parle à BetterModel ; isolé pour que ses classes ne soient chargées que s'il est là. */
    interface Bridge {
        boolean attach(LivingEntity entity, String model);

        boolean play(Entity entity, String... animations);
    }

    private static Bridge bridge;
    private static JavaPlugin owner;
    private static boolean warned;

    private Models() {
    }

    /** Branche BetterModel s'il est installé et que la config le permet (models.enabled). */
    public static void init(JavaPlugin plugin, boolean enabled) {
        owner = plugin;
        bridge = null;
        if (!enabled || Bukkit.getPluginManager().getPlugin("BetterModel") == null) {
            return;
        }
        try {
            bridge = new BetterModelBridge();
            plugin.getLogger().info("BetterModel trouvé : modèles des monstres actifs");
        } catch (LinkageError e) {
            plugin.getLogger().warning("BetterModel présent mais son API ne correspond pas : modèles désactivés (" + e + ")");
        }
    }

    public static boolean active() {
        return bridge != null;
    }

    /** Pose le modèle de cet identifiant sur le monstre (sans effet s'il l'a déjà ou si le modèle n'existe pas). */
    public static boolean attach(LivingEntity entity, String model) {
        if (bridge == null || model == null) {
            return false;
        }
        try {
            return bridge.attach(entity, model);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Joue la première de ces animations que le modèle possède (ex : l'id de la capacité, puis son type). */
    public static boolean play(Entity entity, String... animations) {
        if (bridge == null || entity == null) {
            return false;
        }
        try {
            return bridge.play(entity, animations);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Une erreur de BetterModel ne casse jamais un combat : signalée une fois, puis ignorée. */
    private static void fail(Throwable error) {
        if (!warned && owner != null) {
            warned = true;
            owner.getLogger().log(Level.WARNING, "BetterModel : erreur ignorée (signalée une seule fois)", error);
        }
    }
}
