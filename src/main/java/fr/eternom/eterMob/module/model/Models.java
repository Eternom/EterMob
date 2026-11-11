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

        /** Recharge BetterModel (modèles, config, pack) : bloquant, à lancer hors du thread principal. */
        boolean reload();

        /** Tâche lancée après chaque rechargement réussi (démarrage, /bm reload, synchronisation). */
        void onReloaded(Runnable task);
    }

    private static Bridge bridge;
    private static JavaPlugin owner;
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

    /**
     * Pose le modèle au tick suivant : pendant l'ajout d'une entité au monde (EntityAddToWorldEvent), elle n'est pas
     * encore prête et BetterModel ne peut pas s'y attacher.
     */
    public static void attachLater(LivingEntity entity, String model) {
        if (bridge == null || model == null || owner == null) {
            return;
        }
        Bukkit.getScheduler().runTask(owner, () -> {
            if (entity.isValid() && attach(entity, model) && SEEN.add(model)) {
                // Une fois par modèle : la preuve qu'il est bien posé (diagnostic)
                owner.getLogger().info("Modèles : « " + model + " » posé sur un " + entity.getType().name().toLowerCase());
            }
        });
    }

    /** Pose le modèle de cet identifiant sur le monstre (sans effet s'il l'a déjà ou si le modèle n'existe pas). */
    public static boolean attach(LivingEntity entity, String model) {
        if (bridge == null || model == null) {
            return false;
        }
        try {
            return bridge.attach(entity, model);
        } catch (RuntimeException | LinkageError e) {
            fail("modèle " + model, e);
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
            fail("animation", e);
            return false;
        }
    }

    /** Recharge BetterModel (bloquant : hors du thread principal) ; false s'il n'a pas pu (absent, déjà en cours...). */
    public static boolean reload() {
        if (bridge == null) {
            return false;
        }
        try {
            return bridge.reload();
        } catch (RuntimeException | LinkageError e) {
            fail("BetterModel", e);
            return false;
        }
    }

    /**
     * Après chaque rechargement réussi de BetterModel, cette tâche est lancée sur le thread principal : les monstres
     * déjà là prennent les modèles qui viennent d'arriver.
     */
    public static void onReloaded(Runnable task) {
        if (bridge == null) {
            return;
        }
        try {
            bridge.onReloaded(() -> Bukkit.getScheduler().runTask(owner, task));
        } catch (RuntimeException | LinkageError e) {
            fail("BetterModel", e);
        }
    }

    /** Une erreur de BetterModel ne casse jamais un combat : signalée une fois par cas (modèle et erreur), puis ignorée. */
    private static void fail(String what, Throwable error) {
        if (owner != null && WARNED.add(what + " " + error.getClass().getName())) {
            owner.getLogger().log(Level.WARNING, "BetterModel : erreur ignorée (" + what + ", signalée une fois)", error);
        }
    }
}
