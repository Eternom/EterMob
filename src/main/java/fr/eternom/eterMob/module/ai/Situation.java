package fr.eternom.eterMob.module.ai;

import java.util.Set;

/**
 * Ce que voit l'IA au moment de choisir une capacité : de quoi noter chaque capacité selon la situation (joueurs groupés,
 * cible au loin, cible qui tire, vie basse...). Calculé une fois par réflexion.
 *
 * @param distance          distance à la cible (blocs)
 * @param sight             ligne de vue vers la cible
 * @param targetRanged      la cible tire (arc, arbalète, trident en main, ou dégâts à distance récents)
 * @param targetHealth      vie de la cible (0 à 1)
 * @param selfHealth        vie du lanceur (0 à 1)
 * @param playersNearSelf   joueurs en combat à moins de 4,5 blocs du lanceur
 * @param playersNearTarget joueurs en combat à moins de 4 blocs de la cible (cible comprise)
 * @param allies            monstres à moins de 12 blocs (sans compter le lanceur)
 * @param idleAllies        parmi eux, ceux qui n'ont pas de cible
 * @param unreachable       la cible est hors d'atteinte depuis un moment (tour, pilier, eau)
 * @param targetMoving      la cible bouge
 * @param targetStates      les états EterLib de la cible (stun, root, bleed...)
 * @param sinceDamaged      millisecondes depuis le dernier coup reçu par le lanceur
 */
public record Situation(double distance, boolean sight, boolean targetRanged, double targetHealth, double selfHealth,
                        int playersNearSelf, int playersNearTarget, int allies, int idleAllies, boolean unreachable,
                        boolean targetMoving, Set<String> targetStates, long sinceDamaged) {

    /** La cible ne peut plus bouger (étourdie, enracinée, gelée) : le bon moment pour une grosse capacité. */
    public boolean targetHeld() {
        return targetStates.contains("stun") || targetStates.contains("root");
    }
}
