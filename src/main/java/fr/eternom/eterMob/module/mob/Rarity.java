package fr.eternom.eterMob.module.mob;

/** Rareté d'un variant : sa chance d'être choisi se règle dans config.yml > rarities (clé = id). */
public enum Rarity {
    COMMUN("commun"),
    PEU_COMMUN("peu-commun"),
    RARE("rare"),
    EPIQUE("epique"),
    LEGENDAIRE("legendaire");

    private final String id;

    Rarity(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
