package fr.eternom.eterMob.module.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import fr.eternom.eterLib.module.combat.CombatRoles;
import fr.eternom.eterLib.module.state.StateInstance;
import fr.eternom.eterLib.module.state.States;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.AbilityContext;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.mob.Hits;
import fr.eternom.eterMob.module.mob.Tier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L'IA d'un monstre (variant, meneur, boss) : sa cible et ses capacités. Appelée toutes les 5 ticks quand un joueur est
 * proche (tick) ; elle réfléchit toutes les 500 ms et fait avancer le télégraphe en cours entre deux.
 * <h3>Cible</h3>
 * Une note par joueur (Focus) : menace générée (coups, tirs, soins de ses alliés via CombatRoles, présence au contact),
 * proximité, vie basse, tireur, rôle (soigneur, tank) selon son profil (AiProfile). Pas vu : moitié moins ; hors
 * d'atteinte depuis un moment : beaucoup moins, sauf s'il a de quoi le rejoindre. Il ne change de cible que pour une
 * note nettement meilleure, et pas trop souvent (pas de ping-pong). La provocation (état taunt) passe avant tout. Une
 * cible choisie n'est pas remplacée par l'IA de Minecraft (MobListener annule ses changements de cible).
 * <h3>Capacités</h3>
 * Chaque capacité active prête, à bonne distance et en vue, est notée selon la situation (Situation : joueurs groupés,
 * cible qui tire, tenue, vie basse...), × son poids, × la variété (moins si elle vient de servir), × un peu de hasard ;
 * la meilleure part si sa note dépasse le seuil du profil. Télégraphe (immobile, particules, son) puis effet ; un
 * étourdissement ou un silence l'interrompt. Une pause globale sépare deux capacités. Déclencheurs : au coup, en
 * étant frappé, sous un seuil de vie (une fois), à la mort, périodiques (auras).
 */
public final class Brain {

    private static final long THINK_MILLIS = 500;
    private static final long REACH_CHECK_MILLIS = 1500;
    private static final long UNREACHABLE_MILLIS = 2500;
    private static final long ANTI_CHEESE_MILLIS = 7000;
    private static final long RANGED_MEMORY_MILLIS = 6000;
    private static final long VARIETY_WINDOW_MILLIS = 20_000;
    private static final double DECAY = 0.97;

    private final Caster caster;
    private final AiProfile profile;
    private List<Ability> abilities;
    private final Threat threat = new Threat();
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<String, Deque<Long>> uses = new HashMap<>();
    private final Set<String> belowFired = new HashSet<>();
    private final Map<UUID, Long> rangedAt = new HashMap<>();
    private long globalReadyAt;
    private long nextThink;
    private long nextReachCheck;

    private Ability casting;
    private AbilityContext castContext;
    private long castStart;
    private long castEnd;
    private boolean wasAware = true;
    private long dazedUntil;
    /** Pause globale propre à la phase en cours (boss), 0 : celle du profil. */
    private double globalCooldown;

    private UUID target;
    private double targetScore;
    private long lastSwitch;
    private long lastDamaged;
    private long unreachableSince;
    private Location lastTargetSpot;
    private boolean targetMoving;

    public Brain(Caster caster, AiProfile profile, List<Ability> abilities) {
        this.caster = caster;
        this.profile = profile;
        this.abilities = List.copyOf(abilities);
    }

    public Threat threat() {
        return threat;
    }

    public AiProfile profile() {
        return profile;
    }

    public List<Ability> abilities() {
        return abilities;
    }

    /** Capacités d'une autre phase (boss) : le télégraphe en cours est annulé, les délais déjà lancés restent. */
    public void setAbilities(List<Ability> next) {
        interrupt(false);
        this.abilities = List.copyOf(next);
        belowFired.clear();
    }

    /** Pause globale entre deux capacités pour la phase en cours (secondes ; 0 : celle du profil). */
    public void setGlobalCooldown(double seconds) {
        this.globalCooldown = Math.max(0, seconds);
    }

    /** Cible choisie (null : aucune). */
    public LivingEntity target() {
        LivingEntity self = caster.entity();
        if (target == null) {
            return null;
        }
        Entity entity = self.getServer().getEntity(target);
        return entity instanceof LivingEntity living && living.isValid() && living.getWorld().equals(self.getWorld()) ? living : null;
    }

    public UUID targetId() {
        return target;
    }

    public boolean casting() {
        return casting != null;
    }

    public Ability current() {
        return casting;
    }

    // ---------- Ce qui arrive au monstre ----------

    /** Un joueur l'a frappé (ranged : à distance). La menace suit le rôle du joueur (un tank en génère plus). */
    public void damagedBy(Player player, double amount, boolean ranged) {
        long now = System.currentTimeMillis();
        lastDamaged = now;
        double multiplier = CombatRoles.of(player).threatMultiplier();
        // Premier coup : une base, pour que le joueur qui engage soit visé tout de suite
        double base = threat.get(player.getUniqueId()) == 0 ? 3 : 0;
        threat.add(player.getUniqueId(), base + amount * multiplier * (ranged ? 1.15 : 1));
        if (ranged) {
            rangedAt.put(player.getUniqueId(), now);
        }
    }

    /** Un joueur soigne ou protège quelqu'un près du monstre (CombatRoles) : il devient une menace. */
    public void supportThreat(Player player, double amount) {
        threat.add(player.getUniqueId(), amount * 0.6 * CombatRoles.of(player).threatMultiplier());
    }

    /** Il frappe victim au corps à corps (pas une capacité) : capacités « au coup ». */
    public void onHit(LivingEntity victim) {
        trigger(Ability.Trigger.ON_HIT, victim);
    }

    /** Il est frappé par attacker (peut être null) : capacités « en étant frappé », et sous un seuil de vie. */
    public void onDamaged(LivingEntity attacker) {
        trigger(Ability.Trigger.ON_DAMAGED, attacker);
        double health = healthRatio(caster.entity());
        for (Ability ability : abilities) {
            if (ability.trigger() == Ability.Trigger.BELOW_HEALTH && health < ability.below() && belowFired.add(ability.id())) {
                start(ability, new AbilityContext(caster, target(), situation(target(), System.currentTimeMillis())),
                        System.currentTimeMillis());
            }
        }
    }

    /** Il meurt : capacités « à la mort », télégraphe annulé. */
    public void onDeath(LivingEntity killer) {
        interrupt(false);
        for (Ability ability : abilities) {
            if (ability.trigger() == Ability.Trigger.ON_DEATH) {
                ability.cast(new AbilityContext(caster, killer, null));
            }
        }
    }

    private void trigger(Ability.Trigger trigger, LivingEntity other) {
        long now = System.currentTimeMillis();
        States states = Services.states();
        if (states != null && !states.canUseAbilities(caster.entity())) {
            return;
        }
        for (Ability ability : abilities) {
            if (ability.trigger() != trigger || now < readyAt.getOrDefault(ability.id(), 0L)
                    || ThreadLocalRandom.current().nextDouble() >= ability.chance()) {
                continue;
            }
            readyAt.put(ability.id(), now + Math.round(ability.cooldown() * 1000));
            ability.cast(new AbilityContext(caster, other, null));
        }
    }

    // ---------- Chaque tick (5 ticks) ----------

    public void tick(PlayerIndex players) {
        LivingEntity self = caster.entity();
        if (!self.isValid() || self.isDead()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (dazedUntil > 0) {
            if (now < dazedUntil) {
                self.getWorld().spawnParticle(Particle.CRIT, self.getLocation().add(0, self.getHeight() + 0.3, 0), 3, 0.3, 0.1, 0.3, 0);
                return;
            }
            dazedUntil = 0;
            restoreAware();
        }
        if (casting != null) {
            progressCast(now);
            return;
        }
        if (now < nextThink) {
            return;
        }
        nextThink = now + THINK_MILLIS;
        threat.decay(DECAY);
        rangedAt.values().removeIf(at -> now - at > RANGED_MEMORY_MILLIS);
        LivingEntity chosen = chooseTarget(players, now);
        periodic(now, players);
        if (chosen == null) {
            return;
        }
        checkReach(chosen, now);
        if (now < globalReadyAt) {
            return;
        }
        States states = Services.states();
        if (states != null && !states.canUseAbilities(self)) {
            return;
        }
        Situation situation = situation(chosen, now);
        Ability best = null;
        double bestScore = profile.threshold();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Ability ability : abilities) {
            if (ability.trigger() != Ability.Trigger.ACTIVE || now < readyAt.getOrDefault(ability.id(), 0L)
                    || !ability.inRange(situation.distance(), situation.sight())) {
                continue;
            }
            double score = ability.score(new AbilityContext(caster, chosen, situation)) * ability.weight()
                    * Focus.variety(recentUses(ability, now)) * random.nextDouble(0.85, 1.15);
            if (score > bestScore) {
                bestScore = score;
                best = ability;
            }
        }
        if (best != null) {
            start(best, new AbilityContext(caster, chosen, situation), now);
        }
    }

    private void periodic(long now, PlayerIndex players) {
        for (Ability ability : abilities) {
            if (ability.trigger() == Ability.Trigger.PERIODIC && now >= readyAt.getOrDefault(ability.id(), 0L)
                    && players.any(caster.entity().getLocation(), 16)) {
                readyAt.put(ability.id(), now + Math.round(Math.max(0.5, ability.cooldown()) * 1000));
                ability.cast(new AbilityContext(caster, target(), null));
            }
        }
    }

    private int recentUses(Ability ability, long now) {
        Deque<Long> times = uses.get(ability.id());
        if (times == null) {
            return 0;
        }
        while (!times.isEmpty() && now - times.peekFirst() > VARIETY_WINDOW_MILLIS) {
            times.pollFirst();
        }
        return times.size();
    }

    // ---------- Capacités ----------

    private void start(Ability ability, AbilityContext context, long now) {
        ability.begin(context);
        readyAt.put(ability.id(), now + Math.round((ability.cooldown() + ability.castTime()) * 1000));
        uses.computeIfAbsent(ability.id(), id -> new ArrayDeque<>()).addLast(now);
        if (ability.castTime() <= 0) {
            finish(ability, context, now);
            return;
        }
        casting = ability;
        castContext = context;
        castStart = now;
        castEnd = now + Math.round(ability.castTime() * 1000);
        if (caster.entity() instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            wasAware = mob.isAware();
            mob.setAware(false);
            mob.lookAt(context.spot());
        }
        ability.telegraph(context, 0);
    }

    private void progressCast(long now) {
        LivingEntity self = caster.entity();
        States states = Services.states();
        if (states != null && !states.canUseAbilities(self) && casting.interruptible()) {
            // Interrompu (étourdi, réduit au silence) : la moitié du délai seulement, la réponse des joueurs paie
            interrupt(true);
            self.getWorld().playSound(self.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.8f, 1.2f);
            self.getWorld().spawnParticle(Particle.SMOKE, self.getLocation().add(0, self.getHeight(), 0), 8, 0.3, 0.2, 0.3, 0.02);
            return;
        }
        if (now >= castEnd) {
            Ability ability = casting;
            AbilityContext context = castContext;
            restoreAware();
            casting = null;
            castContext = null;
            finish(ability, context, now);
            return;
        }
        if (self instanceof Mob mob) {
            mob.lookAt(castContext.spot());
        }
        casting.telegraph(castContext, (double) (now - castStart) / Math.max(1, castEnd - castStart));
    }

    private void finish(Ability ability, AbilityContext context, long now) {
        globalReadyAt = now + Math.round((globalCooldown > 0 ? globalCooldown : profile.globalCooldown()) * 1000);
        ability.cast(context);
    }

    /**
     * Sonné (une charge finie dans un mur...) : plus d'IA ni de capacité pendant seconds, télégraphe annulé. Indépendant
     * des contrôles d'EterLib (un boss n'en subit qu'une partie) : c'est la récompense d'une bonne réponse des joueurs.
     */
    public void daze(double seconds) {
        interrupt(false);
        dazedUntil = System.currentTimeMillis() + Math.round(seconds * 1000);
        if (caster.entity() instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            wasAware = mob.isAware();
            mob.setAware(false);
        }
        caster.entity().getWorld().playSound(caster.entity().getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.2f, 0.6f);
    }

    /** Sonné en ce moment ? */
    public boolean dazed() {
        return dazedUntil > System.currentTimeMillis();
    }

    /** Annule le télégraphe en cours (halfCooldown : son délai est ramené à la moitié). */
    public void interrupt(boolean halfCooldown) {
        if (casting == null) {
            return;
        }
        if (halfCooldown) {
            readyAt.put(casting.id(), System.currentTimeMillis() + Math.round(casting.cooldown() * 500));
            globalReadyAt = System.currentTimeMillis() + Math.round(profile.globalCooldown() * 500);
        }
        restoreAware();
        casting = null;
        castContext = null;
    }

    private void restoreAware() {
        if (caster.entity() instanceof Mob mob) {
            mob.setAware(wasAware);
        }
    }

    // ---------- Cible ----------

    private LivingEntity chooseTarget(PlayerIndex players, long now) {
        LivingEntity self = caster.entity();
        States states = Services.states();
        LivingEntity taunter = states == null ? null : states.taunted(self);
        if (taunter != null) {
            apply(taunter, Double.MAX_VALUE, now);
            return taunter;
        }
        if (caster.respectsVanillaAggro() && self instanceof Mob vanillaMob && threat.view().isEmpty()
                && (vanillaMob.getTarget() == null || !(vanillaMob.getTarget() instanceof Player))) {
            target = null;
            return null;
        }
        Location at = self.getLocation();
        double range = profile.range();
        List<Player> candidates = new ArrayList<>(players.near(at, range));
        // Ceux qui le frappent de plus loin restent des cibles (un tireur ne s'en sort pas en reculant)
        for (UUID id : threat.view().keySet()) {
            Player player = self.getServer().getPlayer(id);
            if (player != null && !candidates.contains(player) && Hits.fighting(player) && player.getWorld().equals(self.getWorld())
                    && player.getLocation().distanceSquared(at) <= range * range * 2.25) {
                candidates.add(player);
            }
        }
        // Plus là, plus en combat, trop loin : oubliés
        threat.view().keySet().stream().filter(id -> candidates.stream().noneMatch(p -> p.getUniqueId().equals(id))).toList()
                .forEach(threat::remove);
        if (candidates.isEmpty()) {
            target = null;
            // Plus personne à viser (staff invisible, créatif...) : il lâche aussi la cible de Minecraft
            if (self instanceof Mob mob && mob.getTarget() instanceof Player player && !Hits.fighting(player)) {
                mob.setTarget(null);
            }
            return null;
        }
        Player best = null;
        double bestScore = -1;
        double currentScore = -1;
        for (Player player : candidates) {
            // Présence au contact : un tank qui bloque le passage prend de la menace
            if (player.getLocation().distanceSquared(at) < 9) {
                threat.add(player.getUniqueId(), 0.2 * CombatRoles.of(player).threatMultiplier());
            }
            double score = score(player, at, range, now);
            if (player.getUniqueId().equals(target)) {
                currentScore = score;
            }
            if (score > bestScore) {
                bestScore = score;
                best = player;
            }
        }
        if (best == null) {
            return null;
        }
        LivingEntity current = target();
        if (current != null && !current.equals(best)
                && Focus.keep(currentScore, bestScore, profile.sticky(), now - lastSwitch, profile.switchMillis())) {
            apply(current, currentScore, now);
            return current;
        }
        apply(best, bestScore, now);
        return best;
    }

    private double score(Player player, Location at, double range, long now) {
        double distance = player.getLocation().distance(at);
        boolean invisible = player.hasPotionEffect(PotionEffectType.INVISIBILITY) && !wearsArmor(player);
        if (invisible && distance > 4 && threat.normalized(player.getUniqueId()) < 0.5) {
            return -1;
        }
        CombatRoles.Profile role = CombatRoles.of(player);
        boolean seen = caster.entity().hasLineOfSight(player);
        double penalty = 1;
        if (player.getUniqueId().equals(target) && unreachableSince > 0 && now - unreachableSince > UNREACHABLE_MILLIS && !hasGapCloser()) {
            penalty = 0.4;
        }
        Focus.Criteria criteria = new Focus.Criteria(threat.normalized(player.getUniqueId()), Focus.proximity(distance, range),
                Focus.finish(healthRatio(player)), ranged(player, now), role.role() == CombatRoles.Role.HEALER,
                role.role() == CombatRoles.Role.TANK, seen ? 1 : 0.5, penalty);
        return Focus.score(profile, criteria);
    }

    private void apply(LivingEntity chosen, double score, long now) {
        if (!chosen.getUniqueId().equals(target)) {
            target = chosen.getUniqueId();
            lastSwitch = now;
            unreachableSince = 0;
            lastTargetSpot = null;
        }
        targetScore = score;
        if (caster.entity() instanceof Mob mob && mob.getTarget() != chosen) {
            mob.setTarget(chosen);
        }
        Location spot = chosen.getLocation();
        targetMoving = lastTargetSpot != null && lastTargetSpot.getWorld().equals(spot.getWorld()) && lastTargetSpot.distanceSquared(spot) > 0.36;
        lastTargetSpot = spot;
    }

    private boolean ranged(Player player, long now) {
        if (now - rangedAt.getOrDefault(player.getUniqueId(), 0L) < RANGED_MEMORY_MILLIS) {
            return true;
        }
        Material hand = player.getInventory().getItemInMainHand().getType();
        Material off = player.getInventory().getItemInOffHand().getType();
        return isRangedWeapon(hand) || isRangedWeapon(off);
    }

    private static boolean isRangedWeapon(Material material) {
        return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT;
    }

    private static boolean wearsArmor(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGapCloser() {
        for (Ability ability : abilities) {
            switch (ability.kind()) {
                case "leap", "blink", "pull", "volley", "charge", "curse" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    // ---------- Atteinte (tours, piliers, eau) ----------

    private void checkReach(LivingEntity chosen, long now) {
        if (!(caster.entity() instanceof Mob mob) || abilities.isEmpty() && caster.tier() == Tier.VARIANT) {
            return;
        }
        if (now < nextReachCheck) {
            return;
        }
        nextReachCheck = now + REACH_CHECK_MILLIS;
        double distance = mob.getLocation().distance(chosen.getLocation());
        boolean reachable = distance < 2.5;
        if (!reachable) {
            Pathfinder.PathResult path = mob.getPathfinder().findPath(chosen);
            Location end = path == null ? null : path.getFinalPoint();
            reachable = end != null && end.distanceSquared(chosen.getLocation()) <= 4.5;
        }
        if (reachable) {
            unreachableSince = 0;
            return;
        }
        if (unreachableSince == 0) {
            unreachableSince = now;
        }
        if (profile.antiCheese() && now - unreachableSince > ANTI_CHEESE_MILLIS) {
            antiCheese(mob, chosen);
            unreachableSince = now;
        }
    }

    /** Boss coincé loin d'une cible perchée : il apparaît à côté d'elle (fumée, son), pas de combat gagné sans risque. */
    private static void antiCheese(Mob mob, LivingEntity chosen) {
        Location around = chosen.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Block feet = around.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2).getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, 2, 0).isPassable()
                    && feet.getRelative(0, -1, 0).getType().isSolid()) {
                mob.getWorld().spawnParticle(Particle.LARGE_SMOKE, mob.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.02);
                mob.teleport(feet.getLocation().add(0.5, 0, 0.5));
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
                return;
            }
        }
    }

    // ---------- Situation ----------

    private Situation situation(LivingEntity chosen, long now) {
        LivingEntity self = caster.entity();
        if (chosen == null) {
            return new Situation(0, false, false, 1, healthRatio(self), 0, 0, 0, 0, false, false, Set.of(), now - lastDamaged);
        }
        Location at = self.getLocation();
        Location there = chosen.getLocation();
        int nearSelf = 0;
        int nearTarget = 0;
        for (Player player : there.getWorld().getPlayers()) {
            if (!Hits.fighting(player)) {
                continue;
            }
            Location spot = player.getLocation();
            if (spot.distanceSquared(at) <= 20.25) {
                nearSelf++;
            }
            if (spot.distanceSquared(there) <= 16) {
                nearTarget++;
            }
        }
        int allies = 0;
        int idle = 0;
        for (Entity entity : self.getNearbyEntities(12, 6, 12)) {
            if (entity instanceof Monster monster && monster.isValid()) {
                allies++;
                if (monster.getTarget() == null) {
                    idle++;
                }
            }
        }
        Set<String> targetStates = new HashSet<>();
        States states = Services.states();
        if (states != null) {
            for (StateInstance state : states.active(chosen)) {
                targetStates.add(state.type().id());
            }
        }
        boolean ranged = chosen instanceof Player player && ranged(player, now);
        return new Situation(at.distance(there), self.hasLineOfSight(chosen), ranged, healthRatio(chosen), healthRatio(self),
                nearSelf, nearTarget, allies, idle, unreachableSince > 0 && now - unreachableSince > UNREACHABLE_MILLIS,
                targetMoving, targetStates, now - lastDamaged);
    }

    static double healthRatio(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? 20 : max.getValue();
        return full <= 0 ? 1 : Math.clamp(entity.getHealth() / full, 0, 1);
    }

    /** Pour /etermob inspect : cible, note, menace, capacités et leurs délais. */
    public List<String> describe() {
        long now = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        LivingEntity current = target();
        lines.add("<gray>Cible : <white>" + (current == null ? "-" : current.getName())
                + (current == null ? "" : " <dark_gray>(note " + String.format(java.util.Locale.ROOT, "%.2f", targetScore) + ")"));
        threat.view().entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5).forEach(entry -> {
            Player player = caster.entity().getServer().getPlayer(entry.getKey());
            lines.add("<dark_gray> menace <white>" + (player == null ? entry.getKey().toString().substring(0, 8) : player.getName())
                    + " <gray>" + String.format(java.util.Locale.ROOT, "%.1f", entry.getValue()));
        });
        for (Ability ability : abilities) {
            long left = readyAt.getOrDefault(ability.id(), 0L) - now;
            lines.add("<dark_gray> · <white>" + ability.id() + " <gray>" + ability.trigger().name().toLowerCase(java.util.Locale.ROOT)
                    + (left > 0 ? " <red>" + (left / 1000 + 1) + " s" : " <green>prête")
                    + (ability == casting ? " <gold>(en cours)" : ""));
        }
        if (unreachableSince > 0) {
            lines.add("<gold>Cible hors d'atteinte depuis " + (now - unreachableSince) / 1000 + " s");
        }
        return lines;
    }
}
