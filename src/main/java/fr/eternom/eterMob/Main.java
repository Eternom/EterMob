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
import fr.eternom.eterMob.module.variant.creeper.FragmentCreeper;
import fr.eternom.eterMob.module.variant.creeper.FrostCreeper;
import fr.eternom.eterMob.module.variant.creeper.LaunchCreeper;
import fr.eternom.eterMob.module.variant.creeper.LightningCreeper;
import fr.eternom.eterMob.module.variant.creeper.PoisonCreeper;
import fr.eternom.eterMob.module.variant.skeleton.BlinkSkeleton;
import fr.eternom.eterMob.module.variant.skeleton.ClassicSkeleton;
import fr.eternom.eterMob.module.variant.skeleton.HookSkeleton;
import fr.eternom.eterMob.module.variant.skeleton.RainSkeleton;
import fr.eternom.eterMob.module.variant.skeleton.ReviveSkeleton;
import fr.eternom.eterMob.module.variant.skeleton.SniperSkeleton;
import fr.eternom.eterMob.module.variant.zombie.BurrowZombie;
import fr.eternom.eterMob.module.variant.zombie.ClassicZombie;
import fr.eternom.eterMob.module.variant.zombie.FastZombie;
import fr.eternom.eterMob.module.variant.zombie.IronZombie;
import fr.eternom.eterMob.module.variant.zombie.SplitZombie;
import fr.eternom.eterMob.module.variant.zombie.SummonerZombie;
import fr.eternom.eterMob.module.variant.zombie.VampireZombie;
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
                new FrostCreeper(),
                new LaunchCreeper(),
                new FragmentCreeper(),
                new LightningCreeper(),
                new PoisonCreeper(),
                new ClassicZombie(),
                new IronZombie(),
                new FastZombie(),
                new VampireZombie(),
                new SummonerZombie(),
                new SplitZombie(),
                new BurrowZombie(),
                new ClassicSkeleton(),
                new SniperSkeleton(),
                new RainSkeleton(),
                new BlinkSkeleton(),
                new ReviveSkeleton(),
                new HookSkeleton());
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
        PluginCommand command = getCommand("etermob");
        if (command != null) {
            MobCommand executor = new MobCommand(mobs);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        mobs.start();
    }
}
