package fr.eternom.eterMob;

import fr.eternom.eterMob.module.mob.MobCommand;
import fr.eternom.eterMob.module.mob.MobListener;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Ratios;
import fr.eternom.eterMob.module.mob.Variants;
import fr.eternom.eterMob.module.variant.creeper.BoomCreeper;
import fr.eternom.eterMob.module.variant.creeper.ClassicCreeper;
import fr.eternom.eterMob.module.variant.creeper.FrostCreeper;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Variants de mobs : chaque apparition naturelle devient un variant de son mob, avec un ratio de puissance. */
public final class Main extends JavaPlugin {

    /** Tous les variants : en ajouter un = l'écrire dans module/variant puis l'ajouter ici. */
    private static List<MobVariant> variants() {
        return List.of(
                new ClassicCreeper(),
                new BoomCreeper(),
                new FrostCreeper());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Map<Rarity, Double> rarityWeights = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            rarityWeights.put(rarity, getConfig().getDouble("rarities." + rarity.id(), 0));
        }
        Variants all = new Variants(variants(), new HashSet<>(getConfig().getStringList("disabled-variants")), rarityWeights);
        MobService mobs = new MobService(this, all, Ratios.load(getConfig()),
                getConfig().getString("tag.format", "<status> <dark_gray>·</dark_gray> <white><name>"),
                getConfig().getBoolean("tag.always-visible", true));

        getServer().getPluginManager().registerEvents(new MobListener(mobs, getConfig().getBoolean("creeper-block-damage", false)), this);
        PluginCommand command = getCommand("etermob");
        if (command != null) {
            MobCommand executor = new MobCommand(mobs);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        mobs.start();
    }
}
