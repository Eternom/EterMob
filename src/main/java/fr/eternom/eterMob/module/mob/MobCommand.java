package fr.eternom.eterMob.module.mob;

import fr.eternom.eterLib.module.state.StateInstance;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.boss.BossCommand;
import fr.eternom.eterMob.module.menu.MobMenu;
import fr.eternom.eterMob.module.spawn.SpawnDirector;
import fr.eternom.eterMob.module.spawn.SpawnRule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * /etermob (staff) :
 * <pre>
 *   menu                         menu des variants et des boss (etermob.spawn)
 *   list | info <variant>        variants, meneurs, leur valeur d'équilibrage et leur budget (etermob.debug)
 *   spawn <variant> [ratio]      en faire apparaître un devant soi (etermob.spawn)
 *   inspect                      le monstre visé : ratio, vie, états, IA (cible, menace, capacités) (etermob.debug)
 *   debug                        ici : règles d'apparition qui marchent, densité, Nuit de sang (etermob.debug)
 *   rules                        règles d'apparition et apparitions depuis le démarrage (etermob.debug)
 *   balance                      ratios : multiplicateurs, perce-armure, coups pour tuer un joueur en fer / diamant / netherite
 *   clear [rayon]                retire les monstres d'EterMob autour de soi (32) (etermob.spawn)
 *   event bloodnight [minutes|stop]   Nuit de sang (etermob.admin)
 *   reload                       relit variants/ et les règles (les boss : au redémarrage) (etermob.admin)
 *   boss ...                     boss (BossCommand) (etermob.admin)
 * </pre>
 */
public class MobCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("menu", "list", "info", "spawn", "inspect", "debug", "rules", "balance",
            "clear", "event", "reload", "boss");

    private final MobService mobs;
    private final SpawnDirector director;
    private final BossCommand bosses;
    private final MobMenu menu;
    private final Map<Rarity, Double> budgets;
    private final Consumer<CommandSender> reload;

    public MobCommand(MobService mobs, SpawnDirector director, BossCommand bosses, MobMenu menu, Map<Rarity, Double> budgets,
                      Consumer<CommandSender> reload) {
        this.mobs = mobs;
        this.director = director;
        this.bosses = bosses;
        this.menu = menu;
        this.budgets = budgets;
        this.reload = reload;
    }

    private static void say(CommandSender sender, String text) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(text));
    }

    private static String permission(String sub) {
        return switch (sub) {
            case "menu", "spawn", "clear" -> "etermob.spawn";
            case "list", "info", "inspect", "debug", "rules", "balance" -> "etermob.debug";
            default -> "etermob.admin";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            say(sender, "<red>/etermob " + String.join("|", SUBS));
            return true;
        }
        if (!sender.hasPermission(permission(sub))) {
            say(sender, "<red>Il te faut " + permission(sub) + ".");
            return true;
        }
        switch (sub) {
            case "menu" -> {
                if (sender instanceof Player player) {
                    menu.open(player);
                }
            }
            case "list" -> list(sender);
            case "info" -> info(sender, args.length > 1 ? mobs.variants().get(args[1]) : null);
            case "spawn" -> spawn(sender, args);
            case "inspect" -> inspect(sender);
            case "debug" -> debug(sender);
            case "rules" -> rules(sender);
            case "balance" -> balance(sender);
            case "clear" -> clear(sender, args);
            case "event" -> event(sender, args);
            case "reload" -> reload.accept(sender);
            case "boss" -> bosses.handle(sender, args);
            default -> {
            }
        }
        return true;
    }

    private double budget(MobVariant variant) {
        double base = budgets.getOrDefault(variant.rarity(), 50.0);
        return variant.tier() == Tier.LEADER ? base * 1.5 : base;
    }

    private void list(CommandSender sender) {
        say(sender, "<gold>Variants et meneurs (" + mobs.variants().all().size() + ") <dark_gray>· valeur / budget de la rareté");
        for (MobVariant variant : mobs.variants().all()) {
            double value = variant.value();
            double budget = budget(variant);
            say(sender, " <yellow>" + variant.id() + " <gray>" + variant.type().name().toLowerCase(Locale.ROOT) + " · " + variant.tier().id()
                    + " · " + variant.rarity().id() + " · " + variant.abilities().size() + " capacité(s) · "
                    + (value > budget ? "<red>" : "<green>") + format(value) + "<gray>/" + format(budget)
                    + (mobs.variants().disabled(variant.id()) ? " <dark_gray>(désactivé)" : ""));
        }
        if (mobs.variants().all().isEmpty()) {
            say(sender, "<gray>Aucun : à écrire dans plugins/EterMob/variants/ (modèle : _exemple.yml), puis /etermob reload.");
        }
    }

    private void info(CommandSender sender, MobVariant variant) {
        if (variant == null) {
            say(sender, "<red>/etermob info <variant> (/etermob list)");
            return;
        }
        say(sender, "<gold>" + variant.name() + " <dark_gray>(" + variant.id() + ", " + variant.type().name().toLowerCase(Locale.ROOT)
                + ", " + variant.tier().id() + ", " + variant.rarity().id() + ")");
        say(sender, "<gray>Vie ×" + format(variant.health()) + " · dégâts ×" + format(variant.damage()) + " · vitesse ×"
                + format(variant.speed()) + " · armure +" + format(variant.armor()) + " · ténacité " + percent(variant.tenacity())
                + " · taille ×" + format(variant.scale()));
        AiProfile ai = variant.ai();
        say(sender, "<gray>IA : portée " + format(ai.range()) + " · menace " + format(ai.threat()) + " · proximité " + format(ai.proximity())
                + " · achever " + format(ai.finish()) + " · tireurs " + format(ai.ranged()) + " · soigneurs " + format(ai.healer())
                + " · tanks " + format(ai.tank()) + " · collant ×" + format(ai.sticky()) + " · pause " + format(ai.globalCooldown()) + " s");
        for (Ability ability : variant.abilities()) {
            say(sender, " <yellow>" + ability.id() + " <gray>(" + ability.kind() + ", " + ability.trigger().name().toLowerCase(Locale.ROOT)
                    + ", " + format(ability.cooldown()) + " s, télégraphe " + format(ability.castTime()) + " s) <dark_gray>valeur "
                    + format(ability.value()) + " · " + format(ability.valuePerSecond()) + "/s");
        }
        say(sender, "<gray>Valeur " + format(variant.value()) + " / budget " + format(budget(variant)) + " · butin : "
                + variant.loot().entries().size() + " ligne(s) · xp +" + variant.xp());
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            say(sender, "<red>En jeu seulement.");
            return;
        }
        MobVariant variant = args.length > 1 ? mobs.variants().get(args[1]) : null;
        if (variant == null) {
            say(sender, "<red>/etermob spawn <variant> [ratio] (/etermob list)");
            return;
        }
        int ratio = 1;
        if (args.length > 2) {
            try {
                ratio = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                say(sender, "<red>Ratio : un nombre de 1 à " + mobs.ratios().max() + ".");
                return;
            }
        }
        Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
        mobs.spawn(variant, ratio, at, null);
        say(sender, "<green>" + variant.name() + " apparu, ratio " + mobs.ratios().clamp(ratio) + ".");
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            say(sender, "<red>En jeu seulement.");
            return;
        }
        Entity looked = player.getTargetEntity(32);
        Caster caster = mobs.caster(looked);
        if (caster == null) {
            say(sender, "<red>Vise un monstre d'EterMob (32 blocs).");
            return;
        }
        LivingEntity entity = caster.entity();
        String ratio = caster instanceof ActiveMob mob ? mobs.ratios().status(mob.ratio()) + " <dark_gray>(ratio " + mob.ratio() + ")" : "";
        say(sender, "<gold>" + caster.displayName() + " <gray>" + caster.tier().id() + " " + ratio);
        var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        say(sender, "<gray>Vie " + format(entity.getHealth()) + "/" + format(max == null ? 0 : max.getValue()) + " · puissance ×"
                + format(caster.abilityPower()) + " · perce-armure " + percent(caster.piercing()));
        if (Services.states() != null) {
            List<String> states = new ArrayList<>();
            for (StateInstance state : Services.states().active(entity)) {
                states.add(state.type().id() + (state.stacks() > 1 ? "×" + state.stacks() : ""));
            }
            say(sender, "<gray>États : <white>" + (states.isEmpty() ? "-" : String.join(", ", states)));
        }
        caster.brain().describe().forEach(line -> say(sender, line));
    }

    private void debug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            say(sender, "<red>En jeu seulement.");
            return;
        }
        Location at = player.getLocation();
        say(sender, "<gold>Apparitions ici <dark_gray>(" + (director.enabled() ? "actives" : "coupées sur ce serveur") + ", densité ×"
                + format(director.density()) + ")");
        say(sender, "<gray>Monstres d'EterMob à 48 blocs : <white>" + mobs.countNear(at, 48) + "<gray> · dans ce monde : <white>"
                + mobs.countIn(at.getWorld()));
        for (SpawnRule rule : director.rules()) {
            say(sender, (rule.matches(at) ? " <green>✔ " : " <dark_gray>✘ ") + rule.id() + " <gray>(" + rule.mode().name().toLowerCase(Locale.ROOT) + ")");
        }
        say(sender, director.bloodNight().active() ? "<red>Nuit de sang : encore " + director.bloodNight().remainingMillis() / 60_000 + " min"
                : "<gray>Pas de Nuit de sang.");
    }

    private void rules(CommandSender sender) {
        say(sender, "<gold>Règles d'apparition (" + director.rules().size() + ")");
        for (SpawnRule rule : director.rules()) {
            say(sender, " <yellow>" + rule.id() + " <gray>" + rule.mode().name().toLowerCase(Locale.ROOT)
                    + (rule.mode() == SpawnRule.Mode.BOSS ? " · boss " + rule.boss() : " · " + rule.variants().keySet())
                    + " · apparus : <white>" + director.counters().getOrDefault(rule.id(), 0));
        }
    }

    /**
     * Ratios, et coups d'une frappe de référence (6 dégâts × dégâts du ratio) pour tuer un joueur de 20 PV en fer
     * (15 d'armure), diamant (20 + 8 de robustesse), netherite (20 + 12), sans enchantement, perce-armure compris.
     */
    private void balance(CommandSender sender) {
        say(sender, "<gold>Ratios <dark_gray>· coups pour tuer (frappe de 6) : fer / diamant / netherite");
        Ratios ratios = mobs.ratios();
        for (int ratio = 1; ratio <= ratios.max(); ratio++) {
            Ratios.Level level = ratios.level(ratio);
            double hit = 6 * level.damage();
            say(sender, " " + level.status() + " <gray>vie ×" + format(level.health()) + " · dégâts ×" + format(level.damage())
                    + " · capacités ×" + format(level.ability()) + " · perce " + percent(level.armorPiercing()) + " · ténacité "
                    + percent(level.tenacity()) + " · <white>" + hits(hit, 15, 0, level.armorPiercing()) + " / "
                    + hits(hit, 20, 8, level.armorPiercing()) + " / " + hits(hit, 20, 12, level.armorPiercing()));
        }
    }

    /** Formule de l'armure de Minecraft, la part ignorée par le perce-armure retirée de la réduction. */
    static int hits(double damage, double armor, double toughness, double piercing) {
        double reduction = Math.min(20, Math.max(armor / 5, armor - 4 * damage / (toughness + 8))) / 25;
        double dealt = damage * (1 - reduction * (1 - piercing));
        return (int) Math.ceil(20 / Math.max(0.1, dealt));
    }

    private void clear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            say(sender, "<red>En jeu seulement.");
            return;
        }
        double radius = 32;
        if (args.length > 1) {
            try {
                radius = Math.clamp(Double.parseDouble(args[1]), 1, 256);
            } catch (NumberFormatException ignored) {
                // rayon par défaut
            }
        }
        int removed = 0;
        Location at = player.getLocation();
        for (ActiveMob mob : new ArrayList<>(mobs.active())) {
            Location there = mob.entity().getLocation();
            if (there.getWorld().equals(at.getWorld()) && there.distance(at) <= radius) {
                mob.entity().remove();
                removed++;
            }
        }
        say(sender, "<green>" + removed + " monstre(s) retiré(s) dans un rayon de " + format(radius) + " blocs.");
    }

    private void event(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("bloodnight")) {
            say(sender, "<red>/etermob event bloodnight [minutes|stop]");
            return;
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("stop")) {
            director.bloodNight().end();
            say(sender, "<green>Nuit de sang arrêtée.");
            return;
        }
        double minutes = 8;
        if (args.length > 2) {
            try {
                minutes = Math.clamp(Double.parseDouble(args[2]), 1, 120);
            } catch (NumberFormatException ignored) {
                // durée par défaut
            }
        }
        director.bloodNight().begin(Math.round(minutes * 60_000));
        say(sender, "<green>Nuit de sang pour " + format(minutes) + " min.");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private static String percent(double part) {
        return Math.round(part * 100) + " %";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)) && sender.hasPermission(permission(sub))).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(permission(sub))) {
            return List.of();
        }
        if (sub.equals("boss")) {
            return bosses.complete(args);
        }
        if (args.length == 2 && (sub.equals("spawn") || sub.equals("info"))) {
            return mobs.variants().all().stream().map(MobVariant::id).filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && sub.equals("spawn")) {
            List<String> ratios = new ArrayList<>();
            for (int i = 1; i <= mobs.ratios().max(); i++) {
                ratios.add(String.valueOf(i));
            }
            return ratios;
        }
        if (args.length == 2 && sub.equals("event")) {
            return List.of("bloodnight");
        }
        if (args.length == 3 && sub.equals("event")) {
            return List.of("8", "stop");
        }
        return List.of();
    }
}
