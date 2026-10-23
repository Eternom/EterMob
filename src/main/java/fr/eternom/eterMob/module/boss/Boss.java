package fr.eternom.eterMob.module.boss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;

/**
 * Un boss, codé en dur : son mob, sa vie, ses phases et ses capacités. Le socle (BossService) s'occupe de la barre, des
 * phases, du nombre de joueurs, des protections, de la laisse et de la remise à zéro ; le boss n'écrit que son combat.
 * Les méthodes on... et tick ne font rien par défaut. Thread principal.
 */
public abstract class Boss {

    private final String id;
    private final String name;
    private final String title;
    private BossService service;

    /**
     * @param id    identifiant stocké sur le mob et utilisé par les commandes : ne jamais le changer en production
     * @param name  nom affiché (barre, messages)
     * @param title titre affiché après le nom
     */
    protected Boss(String id, String name, String title) {
        this.id = id;
        this.name = name;
        this.title = title;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    /** Appelé par BossService à sa création. */
    void bind(BossService service) {
        this.service = service;
    }

    /** Pour rattacher ses entités au combat (BossService#own) et parler (BossService#say). */
    protected BossService service() {
        return service;
    }

    /** Icône dans le menu. */
    public Material icon() {
        return Material.WITHER_SKELETON_SKULL;
    }

    /** Description courte pour le menu. */
    public List<String> lore() {
        return List.of();
    }

    /** Vie (1 024 au plus) ; en groupe, sa résistance augmente (BossFight#toughness, recomptée en continu), pas sa vie. */
    public abstract double baseHealth();

    /**
     * Résistance de base : les dégâts qu'il reçoit en sont divisés (la vie d'un mob ne dépasse pas 1 024, c'est donc ici
     * qu'on allonge un combat). Multipliée par la résistance de groupe ; ne s'applique pas à ses entités (piliers...).
     */
    public double toughness() {
        return 1;
    }

    /** Hauteur de la place libre nécessaire pour le faire apparaître (blocs). */
    public abstract double height();

    /** Mob du boss. */
    public abstract Class<? extends LivingEntity> type();

    /** Règle le mob avant qu'il entre dans le monde (taille, équipement, attributs) ; sa vie est réglée par BossService. */
    public abstract void setup(LivingEntity entity);

    /** Seuils de vie (part de la vie max) qui ouvrent les phases 2, 3... ; la phase 1 est au-dessus du premier. */
    public double[] phaseThresholds() {
        return new double[]{0.66, 0.33};
    }

    /** Nom de la phase (1 à n), affiché dans la barre. */
    public abstract String phaseName(int phase);

    /** Couleur de la barre pendant la phase. */
    public abstract BossBar.Color color(int phase);

    /** Réplique du boss à l'entrée d'une phase (2 à n), ou null. */
    public String phaseLine(int phase) {
        return null;
    }

    /** Réplique à l'apparition, et à sa mort, ou null. */
    public String spawnLine() {
        return null;
    }

    public String deathLine() {
        return null;
    }

    /**
     * Entrée dans une phase : à la transition (resumed false, après le cri commun), ou au rechargement du boss directement
     * dans cette phase (resumed true : tout ce qui n'est pas sauvegardé, comme les piliers, est à recréer).
     */
    public void onPhase(BossFight fight, int phase, boolean resumed) {
    }

    /** Chaque fois que le boss entre dans le monde (apparu ou rechargé) : ce que Minecraft garde et qu'il faut remettre (IA...). */
    public void onLoad(BossFight fight) {
    }

    /** Le boss meurt (avant la fin du combat, qui retire ses entités) : effets de mort. */
    public void onDeath(BossFight fight) {
    }

    /** Toutes les BossService.TICK_PERIOD ticks, si un joueur est dans la zone du combat et hors transition. */
    public void tick(BossFight fight) {
    }

    /** Le boss reçoit des dégâts (après les protections communes). */
    public void onDamaged(BossFight fight, EntityDamageEvent event) {
    }

    /** Une entité du boss (pilier, sbire...) reçoit des dégâts. */
    public void onOwnedDamaged(BossFight fight, Entity owned, EntityDamageEvent event) {
    }

    /** Remise à zéro (plus de joueurs depuis 30 s) : ses entités ont déjà été retirées. */
    public void onReset(BossFight fight) {
    }

    /** Fin du combat (mort, suppression, déchargement) : oublier ce que le boss garde en mémoire pour ce combat. */
    public void onEnd(BossFight fight) {
    }
}
