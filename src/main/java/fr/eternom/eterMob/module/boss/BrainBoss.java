package fr.eternom.eterMob.module.boss;

import fr.eternom.eterLib.module.quest.QuestBridge;
import fr.eternom.eterMob.module.ability.Ability;
import fr.eternom.eterMob.module.ability.Services;
import fr.eternom.eterMob.module.ability.kind.Summon;
import fr.eternom.eterMob.module.ai.AiProfile;
import fr.eternom.eterMob.module.ai.Brain;
import fr.eternom.eterMob.module.ai.Caster;
import fr.eternom.eterMob.module.loot.LootTable;
import fr.eternom.eterMob.module.mob.Tier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un boss codé en dur qui se bat avec l'IA commune (Brain) et les briques de capacités : une IA par combat, ses
 * capacités selon la phase, la menace des joueurs, sa défense (combat d'EterLib), son butin personnel. Le boss
 * (sous-classe) n'écrit que ce qui lui est propre : ses phases, ses passifs, ses mécaniques signatures.
 */
public abstract class BrainBoss extends Boss {

    /** Un boss sonné (charge dans un mur...) prend ce surplus de dégâts. */
    private static final double DAZED_DAMAGE = 1.3;

    private final Map<UUID, BossCaster> casters = new HashMap<>();

    /** Ce boss pendant un combat : il lance ses capacités et réfléchit (Brain). */
    public final class BossCaster implements Caster {

        private final BossFight fight;
        private final Brain brain;

        BossCaster(BossFight fight) {
            this.fight = fight;
            this.brain = new Brain(this, ai(), abilities(fight.phase()));
            brain.setGlobalCooldown(globalCooldown(fight.phase()));
        }

        public BossFight fight() {
            return fight;
        }

        @Override
        public LivingEntity entity() {
            return fight.entity();
        }

        @Override
        public Tier tier() {
            return Tier.BOSS;
        }

        @Override
        public double abilityPower() {
            return fight.enraged() ? BossService.ENRAGE_DAMAGE : 1;
        }

        @Override
        public double piercing() {
            return BrainBoss.this.piercing();
        }

        @Override
        public Brain brain() {
            return brain;
        }

        @Override
        public String displayName() {
            return name();
        }
    }

    protected BrainBoss(String id, String name, String title) {
        super(id, name, title);
    }

    // ---------- À écrire par le boss ----------

    /** Ses capacités dans cette phase (1 à n). */
    protected abstract List<Ability> abilities(int phase);

    /** Pause entre deux capacités dans cette phase (secondes). */
    protected abstract double globalCooldown(int phase);

    protected AiProfile ai() {
        return AiProfile.defaults(Tier.BOSS);
    }

    /** Pénétration d'armure (part de l'armure des joueurs ignorée). */
    public abstract double piercing();

    /** Armure en plus de son équipement (combat d'EterLib). */
    public abstract double armor();

    /** Résistance magique (combat d'EterLib). */
    public abstract double magicResist();

    protected abstract LootTable loot();

    /** Part minimale des dégâts pour avoir son butin. */
    protected double lootShare() {
        return 0.05;
    }

    /** Apparition naturelle d'un sous-boss (bosses.<id>.spawn) : où et quand, chance par tour du directeur, délai par monde. */
    public record NaturalSpawn(fr.eternom.eterMob.module.spawn.SpawnRule where, double chance, double cooldownMinutes) {
    }

    private NaturalSpawn natural;

    /** Lit bosses.<id>.spawn (sous-boss) : conditions comme une règle d'apparition, chance, cooldown-minutes, enabled. */
    protected void readSpawn(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            natural = null;
            return;
        }
        natural = new NaturalSpawn(fr.eternom.eterMob.module.spawn.SpawnRule.conditions(id(), section,
                java.util.logging.Logger.getLogger("EterMob")), Math.clamp(section.getDouble("chance", 0.01), 0, 1),
                Math.max(1, section.getDouble("cooldown-minutes", 45)));
    }

    /** null : il n'apparaît jamais de lui-même. */
    public NaturalSpawn naturalSpawn() {
        return natural;
    }

    /** Facteur sur les dégâts qu'il porte à victim (avant le calcul du combat) : coup dans le dos, etc. */
    public double attackFactor(BossFight fight, LivingEntity victim) {
        return 1;
    }

    /** Ses statistiques de combat (EterLib) : armure, résistance magique, et ce que le boss ajoute (bonus magiques...). */
    public void contribute(BossFight fight, fr.eternom.eterLib.module.combat.StatSheet sheet) {
        sheet.add(fr.eternom.eterLib.module.combat.Stat.ARMOR, armor(), "boss");
        sheet.add(fr.eternom.eterLib.module.combat.Stat.MAGIC_RESIST, magicResist(), "boss");
    }

    // ---------- Commun ----------

    public BossCaster caster(BossFight fight) {
        return casters.computeIfAbsent(fight.id(), id -> new BossCaster(fight));
    }

    protected Brain brain(BossFight fight) {
        return caster(fight).brain();
    }

    @Override
    public void onPhase(BossFight fight, int phase, boolean resumed) {
        Brain brain = brain(fight);
        brain.setAbilities(abilities(phase));
        brain.setGlobalCooldown(globalCooldown(phase));
    }

    @Override
    public void onLoad(BossFight fight) {
        caster(fight);
        if (fight.entity() instanceof Mob mob && !mob.isAware()) {
            mob.setAware(true);
        }
    }

    @Override
    public void tick(BossFight fight) {
        if (!fight.dying() && Services.mobs() != null) {
            brain(fight).tick(Services.mobs().players());
        }
    }

    @Override
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
        Brain brain = brain(fight);
        Entity source = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        boolean ranged = source instanceof Projectile;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (source instanceof Player player) {
            brain.damagedBy(player, event.getDamage(), ranged);
        }
        if (brain.dazed()) {
            event.setDamage(event.getDamage() * DAZED_DAMAGE);
        }
        if (Services.states() == null || !Services.states().isStateDamage()) {
            brain.onDamaged(source instanceof LivingEntity living ? living : null);
        }
    }

    @Override
    public void onReset(BossFight fight) {
        BossCaster caster = casters.get(fight.id());
        if (caster != null) {
            caster.brain().interrupt(false);
            caster.brain().threat().clear();
            caster.brain().setAbilities(abilities(1));
            caster.brain().setGlobalCooldown(globalCooldown(1));
        }
        Summon.removeMinionsOf(fight.id());
    }

    @Override
    public void onDeath(BossFight fight) {
        brain(fight).onDeath(fight.entity().getKiller());
        giveLoot(fight);
        Summon.removeMinionsOf(fight.id());
    }

    @Override
    public void onEnd(BossFight fight) {
        BossCaster caster = casters.remove(fight.id());
        if (caster != null) {
            caster.brain().interrupt(false);
        }
        Summon.removeMinionsOf(fight.id());
    }

    /** Butin personnel : chaque joueur qui a fait au moins lootShare des dégâts tire le sien ; quêtes prévenues. */
    private void giveLoot(BossFight fight) {
        double total = fight.allStats().values().stream().mapToDouble(stats -> stats.dealt).sum();
        if (total <= 0) {
            return;
        }
        fight.allStats().forEach((uuid, stats) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || stats.dealt / total < lootShare()) {
                return;
            }
            QuestBridge.signal(player, "etermob:boss", Map.of("boss", id()), 1);
            for (ItemStack item : loot().roll(1, 1)) {
                player.getInventory().addItem(item).values().forEach(left -> {
                    Item dropped = player.getWorld().dropItem(player.getLocation(), left);
                    dropped.setOwner(uuid);
                });
            }
        });
    }
}
