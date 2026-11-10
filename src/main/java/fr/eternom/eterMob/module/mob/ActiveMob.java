package fr.eternom.eterMob.module.mob;

import fr.eternom.eterMob.module.ai.Brain;
import fr.eternom.eterMob.module.ai.Caster;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un monstre d'EterMob chargé : son variant, son ratio, son IA, et qui lui a fait quoi (part des dégâts des joueurs pour
 * le butin : un monstre tué surtout par la lave, la chute ou un golem ne donne pas le butin en plus).
 */
public final class ActiveMob implements Caster {

    private final LivingEntity entity;
    private final MobVariant variant;
    private final int ratio;
    private final Ratios.Level level;
    private final Brain brain;
    private final UUID leader;
    private final boolean minion;
    private final Map<UUID, Double> damageBy = new HashMap<>();
    private double otherDamage;

    ActiveMob(LivingEntity entity, MobVariant variant, int ratio, Ratios.Level level, UUID leader, boolean minion) {
        this.entity = entity;
        this.variant = variant;
        this.ratio = ratio;
        this.level = level;
        this.leader = leader;
        this.minion = minion;
        this.brain = new Brain(this, variant.ai(), variant.abilities());
    }

    @Override
    public LivingEntity entity() {
        return entity;
    }

    public MobVariant variant() {
        return variant;
    }

    public int ratio() {
        return ratio;
    }

    public Ratios.Level level() {
        return level;
    }

    @Override
    public Tier tier() {
        return variant.tier();
    }

    @Override
    public double abilityPower() {
        return level.ability();
    }

    @Override
    public double piercing() {
        return level.armorPiercing();
    }

    @Override
    public Brain brain() {
        return brain;
    }

    @Override
    public String displayName() {
        return variant.vanilla() ? entity.getName() : variant.name();
    }

    @Override
    public boolean respectsVanillaAggro() {
        return variant.vanilla();
    }

    /** Le meneur de son groupe (null : seul, ou meneur lui-même). */
    public UUID leader() {
        return leader;
    }

    /** Sbire d'une capacité : ni butin, ni expérience, ni statistique. */
    public boolean minion() {
        return minion;
    }

    void damagedBy(UUID player, double amount) {
        damageBy.merge(player, amount, Double::sum);
    }

    void otherDamage(double amount) {
        otherDamage += amount;
    }

    /** Part des dégâts reçus qui viennent des joueurs (0 à 1). */
    public double playerShare() {
        double players = damageBy.values().stream().mapToDouble(Double::doubleValue).sum();
        double total = players + otherDamage;
        return total <= 0 ? 0 : players / total;
    }
}
