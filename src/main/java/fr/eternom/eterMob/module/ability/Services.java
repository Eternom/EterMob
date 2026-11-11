package fr.eternom.eterMob.module.ability;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.module.state.States;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.spawn.SpawnGuard;
import org.bukkit.plugin.java.JavaPlugin;

/** Ce dont les capacités ont besoin (posé une fois au démarrage) : le plugin, les états d'EterLib, les monstres, la garde. */
public final class Services {

    private static JavaPlugin plugin;
    private static MobService mobs;
    private static SpawnGuard guard;
    private static fr.eternom.eterLib.helper.message.Messages messages;

    private Services() {
    }

    public static void init(JavaPlugin owner, MobService mobService, SpawnGuard spawnGuard) {
        plugin = owner;
        mobs = mobService;
        guard = spawnGuard;
    }

    public static JavaPlugin plugin() {
        return plugin;
    }

    /** Les états d'EterLib, ou null (EterLib absent : tests de la logique, sans serveur). */
    public static States states() {
        try {
            EterLib lib = EterLib.get();
            return lib == null ? null : lib.getStates();
        } catch (LinkageError | IllegalStateException e) {
            return null;
        }
    }

    /** Les textes du plugin (lang/), pour les messages des boss aux joueurs. */
    public static void messages(fr.eternom.eterLib.helper.message.Messages lang) {
        messages = lang;
    }

    public static fr.eternom.eterLib.helper.message.Messages messages() {
        return messages;
    }

    public static MobService mobs() {
        return mobs;
    }

    public static SpawnGuard guard() {
        return guard;
    }
}
