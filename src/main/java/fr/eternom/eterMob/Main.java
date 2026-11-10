package fr.eternom.eterMob;

import fr.eternom.eterMob.module.boss.BossCommand;
import fr.eternom.eterMob.module.boss.BossListener;
import fr.eternom.eterMob.module.boss.BossRegistry;
import fr.eternom.eterMob.module.boss.BossService;
import fr.eternom.eterMob.module.menu.MobMenu;
import fr.eternom.eterMob.module.mob.MobCommand;
import fr.eternom.eterMob.module.mob.MobListener;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.MobVariant;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Ratios;
import fr.eternom.eterMob.module.mob.TempBlocks;
import fr.eternom.eterMob.module.mob.Variants;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Variants de mobs : chaque apparition naturelle devient un variant de son mob, avec un ratio de puissance. */
public final class Main extends JavaPlugin {

    private BossService bosses;

    /** Tous les variants : en ajouter un = l'écrire dans module/variant puis l'ajouter ici. */
    private static List<MobVariant> variants() {
        return List.of();
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
                getConfig().getString("tag.format", "<white><name> <dark_gray>·</dark_gray> <status>"));

        getServer().getPluginManager().registerEvents(new MobListener(mobs, getConfig().getBoolean("creeper-block-damage", false)), this);
        // Boss : seulement par commande pour l'instant (pas d'apparition naturelle)
        bosses = new BossService(this, new BossRegistry(this), List.of());
        getServer().getPluginManager().registerEvents(new BossListener(bosses), this);
        MobMenu menu = new MobMenu(mobs, bosses);
        getServer().getPluginManager().registerEvents(menu, this);
        PluginCommand command = getCommand("etermob");
        if (command != null) {
            MobCommand executor = new MobCommand(mobs, new BossCommand(bosses), menu);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        mobs.start();
        bosses.start();
    }

    @Override
    public void onDisable() {
        if (bosses != null) {
            bosses.stop();
        }
        TempBlocks.clearAll();
    }
}
