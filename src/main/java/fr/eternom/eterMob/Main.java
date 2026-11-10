package fr.eternom.eterMob;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.combat.CombatRoles;
import fr.eternom.eterLib.module.combat.Stat;
import fr.eternom.eterLib.module.state.StateType;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.boss.Boss;
import fr.eternom.eterMob.module.boss.BossCommand;
import fr.eternom.eterMob.module.boss.BossFight;
import fr.eternom.eterMob.module.boss.BossListener;
import fr.eternom.eterMob.module.boss.BossRegistry;
import fr.eternom.eterMob.module.boss.BossService;
import fr.eternom.eterMob.module.boss.BrainBoss;
import fr.eternom.eterMob.module.boss.mortarius.Mortarius;
import fr.eternom.eterMob.module.loot.KillStats;
import fr.eternom.eterMob.module.menu.MobMenu;
import fr.eternom.eterMob.module.mob.ActiveMob;
import fr.eternom.eterMob.module.mob.MobCommand;
import fr.eternom.eterMob.module.mob.MobListener;
import fr.eternom.eterMob.module.mob.MobService;
import fr.eternom.eterMob.module.mob.Rarity;
import fr.eternom.eterMob.module.mob.Ratios;
import fr.eternom.eterMob.module.mob.TempBlocks;
import fr.eternom.eterMob.module.mob.Tier;
import fr.eternom.eterMob.module.mob.Variants;
import fr.eternom.eterMob.module.spawn.SpawnDirector;
import fr.eternom.eterMob.module.spawn.SpawnGuard;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EterMob : les monstres du réseau. Variants et meneurs (variants/*.yml), boss (bosses/*.yml), capacités en briques
 * (Ability), IA commune (Brain : cible et capacités), états d'EterLib, ratios de puissance, apparition automatique
 * (SpawnDirector), butin, statistiques et quêtes.
 */
public final class Main extends JavaPlugin {

    private BossService bosses;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveExample("variants/_exemple.yml");
        EterLib lib = EterLib.get();
        Messages messages = lib.messages(this, "en_us", "fr_fr");

        Variants variants = new Variants(new HashSet<>(getConfig().getStringList("disabled-variants")));
        MobService mobs = new MobService(this, variants, Ratios.load(getConfig()),
                getConfig().getString("tag.format", "<white><name> <dark_gray>·</dark_gray> <status>"));
        SpawnGuard guard = new SpawnGuard(getConfig().getConfigurationSection("guard"));
        Services.init(this, mobs, guard);
        loadVariants(variants);
        TempBlocks.start(this);

        BossService.configure(getConfig().getConfigurationSection("boss"));
        bosses = new BossService(this, new BossRegistry(this), List.of(new Mortarius(),
                new fr.eternom.eterMob.module.boss.disciple.Scribe(), new fr.eternom.eterMob.module.boss.disciple.Gravedigger(),
                new fr.eternom.eterMob.module.boss.disciple.SoulWarden()));
        mobs.setBossCasters(this::bossCaster);
        getServer().getPluginManager().registerEvents(new BossListener(bosses), this);

        KillStats stats = new KillStats(this, lib.database("etermob_"));
        SpawnDirector director = new SpawnDirector(this, mobs, guard, messages, getConfig(), lib.getServerName());
        getServer().getPluginManager().registerEvents(new MobListener(mobs, director, stats,
                getConfig().getBoolean("creeper-block-damage", false),
                Math.clamp(getConfig().getDouble("loot-share", 0.5), 0, 1)), this);

        // Combat d'EterLib : le perce-armure des ratios et des boss, et le plafond par coup des monstres (hors boss)
        lib.getCombatSystem().addProvider((entity, sheet) -> {
            Caster caster = mobs.caster(entity);
            if (caster != null && caster.piercing() > 0) {
                sheet.add(Stat.ARMOR_PEN, caster.piercing(), caster.tier() == Tier.BOSS ? "EterMob : boss" : "EterMob : ratio");
            }
        });
        lib.getCombatSystem().addProvider((entity, sheet) -> {
            BossFight fight = bosses.fight(entity);
            if (fight != null && fight.boss() instanceof BrainBoss boss) {
                boss.contribute(fight, sheet);
            }
        });
        lib.getStates().register(StateType.builder("rot", StateType.Category.DEBUFF)
                .weight(2).icon("<dark_green>☣").particle(org.bukkit.Particle.SPORE_BLOSSOM_AIR).build());
        // Lien d'âmes (la Gardienne) et pacte : l'icône ; le partage des dégâts est un crochet du combat
        lib.getStates().register(StateType.builder("soullink", StateType.Category.DEBUFF)
                .weight(3).icon("<aqua>∞").particle(org.bukkit.Particle.SOUL).build());
        lib.getStates().register(StateType.builder("pact", StateType.Category.DEBUFF)
                .weight(1).icon("<aqua>✍").build());
        lib.getCombatSystem().addHook((attacker, victim, kind, damage) -> fr.eternom.eterMob.module.boss.SoulLinks.share(victim, damage));
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            // Pourriture : aucun soin tant qu'elle dure
            @org.bukkit.event.EventHandler(ignoreCancelled = true)
            public void onRegain(org.bukkit.event.entity.EntityRegainHealthEvent event) {
                if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living && lib.getStates().has(living, "rot")) {
                    event.setCancelled(true);
                }
            }
        }, this);
        double damageCap = Math.clamp(getConfig().getDouble("damage-cap", 0.6), 0, 1);
        lib.getCombatSystem().addHook((attacker, victim, kind, damage) -> {
            Caster caster = damageCap <= 0 || !(victim instanceof org.bukkit.entity.Player) ? null : mobs.caster(attacker);
            if (caster == null || caster.tier() == Tier.BOSS) {
                return damage;
            }
            org.bukkit.attribute.AttributeInstance max = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            return Math.min(damage, (max == null ? 20 : max.getValue()) * damageCap);
        });
        // Ténacité des monstres (contrôles plus courts) et des boss ; les boss ne subissent qu'un tiers des contrôles
        lib.getStates().addTenacity(entity -> {
            ActiveMob mob = mobs.get(entity);
            return mob == null ? 0 : mob.level().tenacity() + mob.variant().tenacity();
        });
        lib.getStates().addImmunity((entity, type) -> type.category() == StateType.Category.CONTROL
                && bosses.fight(entity) != null ? getConfig().getDouble("boss.control-factor", 0.33) : 1);
        // Soins et protections des classes : les monstres en combat autour les comptent comme une menace
        CombatRoles.addListener((player, about, amount) -> {
            for (ActiveMob mob : mobs.active()) {
                if (mob.brain().target() != null && mob.entity().getWorld().equals(about.getWorld())
                        && mob.entity().getLocation().distanceSquared(about.getLocation()) < 24 * 24) {
                    mob.brain().supportThreat(player, amount);
                }
            }
            for (BossFight fight : bosses.fights()) {
                Caster caster = bossCaster(fight.entity());
                if (caster != null && fight.entity().getWorld().equals(about.getWorld())
                        && fight.entity().getLocation().distanceSquared(about.getLocation()) < 48 * 48) {
                    caster.brain().supportThreat(player, amount);
                }
            }
        });

        MobMenu menu = new MobMenu(mobs, bosses);
        getServer().getPluginManager().registerEvents(menu, this);
        PluginCommand command = getCommand("etermob");
        if (command != null) {
            MobCommand executor = new MobCommand(mobs, director, new BossCommand(bosses), menu, budgets(), sender -> {
                reloadConfig();
                loadVariants(variants);
                director.reloadRules(getConfig());
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>EterMob relu : " + variants.all().size()
                        + " variants, " + director.rules().size() + " règles <gray>(ratios et boss : au redémarrage)."));
            });
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        // Sous-boss : en survie, dans leurs biomes (bosses.<id>.spawn)
        List<SpawnDirector.SubBoss> subBosses = new java.util.ArrayList<>();
        for (Boss boss : bosses.bosses()) {
            if (boss.subBoss() && boss instanceof BrainBoss brain && brain.naturalSpawn() != null) {
                BrainBoss.NaturalSpawn spawn = brain.naturalSpawn();
                subBosses.add(new SpawnDirector.SubBoss(boss.id(), boss.name(), spawn.where(), spawn.chance(), spawn.cooldownMinutes()));
            }
        }
        director.setSubBosses(subBosses, (id, location) -> {
            Boss boss = bosses.boss(id);
            return boss == null ? "inconnu" : bosses.spawn(boss, location);
        }, bosses::subBossesIn);
        mobs.start();
        bosses.start();
        director.start();
    }

    @Override
    public void onDisable() {
        if (bosses != null) {
            bosses.stop();
        }
        TempBlocks.clearAll();
    }

    private Caster bossCaster(Entity entity) {
        BossFight fight = bosses == null ? null : bosses.fight(entity);
        return fight != null && fight.boss() instanceof BrainBoss boss ? boss.caster(fight) : null;
    }

    private void saveExample(String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    private void loadVariants(Variants variants) {
        File folder = new File(getDataFolder(), "variants");
        folder.mkdirs();
        int count = variants.load(folder, getLogger());
        getLogger().info(count + " variant(s) et meneur(s) chargé(s)");
    }


    /** Budget d'équilibrage par rareté (config.yml > budgets ; un meneur a 1,5 fois celui de sa rareté). */
    private Map<Rarity, Double> budgets() {
        Map<Rarity, Double> budgets = new EnumMap<>(Rarity.class);
        double[] defaults = {40, 55, 75, 100, 140};
        for (Rarity rarity : Rarity.values()) {
            budgets.put(rarity, getConfig().getDouble("budgets." + rarity.id(), defaults[rarity.ordinal()]));
        }
        return budgets;
    }
}
