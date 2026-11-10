# EterMob

Les **monstres du réseau** côté Paper : variants, meneurs (sous-boss) et boss, avec des
capacités en briques, une IA commune (choix de la cible et des capacités), les états d'EterLib, des ratios de puissance
et l'apparition automatique. Document développeur, à tenir à jour avec le code.

Depuis 2.1.0, les boss sont **codés en dur** (`module/boss`, ex. `mortarius/Mortarius`) en assemblant les briques de capacités
et l'IA commune (`BrainBoss`) ; leurs chiffres sont dans `config.yml > bosses.<id>`. Les variants aussi
(`module/variant`, `MobVariant.builder`) : leur **ratio débloque leur kit** (capacités, statistiques, crochets, chacun avec un
ratio minimal : Ordinaire la base, Fléau tout). Les monstres naissent avec leurs boss (famille du Livre : Mortarius). Les
donjons seront des maps à part, gérées par leur propre plugin (qui pourra s'appuyer sur ce socle).

## Prérequis

- **EterLib 1.27.0+** (`depend`) : combat (dégâts physiques, magiques, bruts ; armure, résistance magique, pénétration), états (`getStates`), rôles de combat (`CombatRoles`), base, langues, pont des quêtes,
  classements, vanish.
- **EterClan** (lu à l'usage, s'il est là) : pas d'apparition sur un terrain de clan.

## Rangs

| Rang | Où | Rôle |
|---|---|---|
| `variant` | `variants/` | Le commun, avec ses capacités |
| `sous-boss` | codé (`SubBoss`) | Au-dessus d'un Fléau, tout son kit, 2 phases avec barre ; apparaît en survie dans ses biomes (`bosses.<id>.spawn`, un par monde, délai), sans rayon de déplacement, disparaît après `despawn-minutes` sans joueur |
| `meneur` | `variants/` (`tier: meneur`) | Sous-boss à la tête d'un groupe (`followers`) ; ses suiveurs le rejoignent quand ils n'ont pas de cible |
| `boss` | codé (`BrainBoss`) | Combat à phases, rayon de déplacement maximal (`leash-radius`, ou la salle du donjon : `BossService#spawn(boss, lieu, rayon)`, `/etermob boss <id> [rayon]`), jamais d'apparition naturelle (`BossService` : barre, phases, résistance de groupe plafonnée, laisse, remise à zéro, rage, bilan) |

Les mobs de Minecraft de `spawning.natural-ratios` reçoivent un **ratio** en apparaissant (sans devenir un variant) et
passent par l'IA de ciblage, en gardant leur caractère (neutres tant qu'on ne les attaque pas).

## Ratios (`config.yml > ratios`)

Ordinaire, Robuste, Dangereux, Élite, Fléau : multiplicateurs **séparés** de vie, de dégâts et de capacités ;
**perce-armure** (pénétration d'armure du combat d'EterLib : part de l'armure de la cible ignorée), **ténacité** (contrôles
plus courts), résistance au recul, butin, expérience. Tirage favorisé par la profondeur, la nuit, le Nether et l'End
(`ratio-bonus`), +1 pendant la Nuit de sang ; un seul Fléau par rayon (`top-ratio-radius`) ; les débutants
(`beginner.hours`) ne croisent pas plus fort que `beginner.max-ratio`. Un coup d'un monstre (hors boss) ne retire jamais
plus de `damage-cap` de la vie max d'un joueur (crochet du combat d'EterLib). `/etermob balance` : coups pour tuer selon l'armure.

## Capacités (`module/ability`)

Une capacité est une **brique** (`Ability`) assemblée dans le code d'un monstre (`Spec.of(...)`) : réglages communs (`trigger`,
`cooldown`, `cast` = télégraphe, `range`, `sight`, `weight`, `chance`, `below`, `damage`, `radius`, `knockback`,
`lift`, `states`, `damage-type` : physical, magic, true ; défaut physique, magique pour `curse` et `aura`). Les dégâts et la puissance des états suivent le ratio ; jamais la durée des contrôles.

| Sorte | Effet |
|---|---|
| `strike` | Coup chargé (au coup par défaut) : dégâts et états en plus |
| `slam` | Frappe du sol autour de lui (cercle rouge), pour les joueurs groupés |
| `leap` | Bond sur le point visé, onde à l'atterrissage |
| `charge` | Ruée en ligne droite (point fixé au début : on l'esquive) |
| `volley` | Salve de projectiles à visée anticipée (flèche, boule de neige, boule de feu, crâne) |
| `pull` | Crochet qui ramène la cible (tireurs, joueurs perchés) |
| `curse` | Sort à distance qui pose des états |
| `blink` | Téléportation dans le dos de la cible, ou fuite (vie basse) |
| `pillar` | Faux blocs (pic sous la cible ; cage qui retient ; mur qui repousse et arrête les projectiles) ; `health` : cassables (une cage cassée s'ouvre) |
| `aura` | Périodique : dégâts et états autour de lui |
| `heal` | Soin sous un seuil, long télégraphe (un étourdissement l'interrompt) |
| `call` | Appel à l'aide : les monstres sans cible prennent la sienne |
| `evade` | En étant frappé : bond en arrière, états à l'attaquant |
| `burst` | Déflagration (à la mort, sous un seuil, ou active ; `suicide`) |
| `summon` | Sbires (mobs ou variants), autour de lui ou des joueurs, plafonnés, sans butin |
| `cleave` | Coup en cône (fissure au sol), bloquable au bouclier, ne s'interrompt pas |
| `wave` | Anneaux qui s'élargissent, à sauter |
| `tombs` | Une tombe sous chaque joueur (affichage), à fuir |
| `vortex` | Aspiration : attirés, puis frappés et étourdis ; interruptible |
| `beam` | Trait en ligne droite (direction fixée au début) |
| `burrow` | Plonge sous terre, ressort sous sa cible (cercle qui se fige) |
| `zone` | Flaques qui restent au sol (linger) ou impacts (impact) |
| `boulder` | Rochers en faux blocs : lancés en cloche (throw) ou tombés du ciel (fall), ombre au sol |

Un autre plugin peut ajouter une sorte (`Abilities.register`). **Faux blocs** (`FakeBlocks`) : colonnes affichées, avec ou sans vie (un coup compté par joueur toutes les 500 ms), pour
les capacités et les boss (piliers de Mortarius). **Rien ne touche la map** : pierres, tombes, piliers, éboulements sont des affichages (BlockDisplay) ou des
particules qui imitent les blocs et disparaissent (et à l'arrêt) ; aucune explosion ne casse de bloc.

**Équilibrage** : `Ability#value` (dégâts + valeur EterLib des états), par seconde de délai ; la valeur d'un variant
(vie, dégâts, capacités) est comparée au **budget de sa rareté** (`budgets`, ×1,5 pour un meneur) dans `/etermob list`.

## IA (`module/ai`)

`Brain`, commun aux variants, meneurs et boss, appelé toutes les 5 ticks près d'un joueur (réflexion toutes les 500 ms) :

- **Cible** : note par joueur (`Focus`) = menace (`Threat` : coups, tirs, soins et protections signalés par
  `CombatRoles`, présence au contact, qui s'efface) + proximité + vie basse + tireur + rôle (soigneur, tank) selon le
  profil (`AiProfile`, section `ai`). Pas vu : moitié moins ; hors d'atteinte : beaucoup moins sauf s'il a de quoi le
  rejoindre. Il ne change que pour une note `sticky` fois meilleure et pas avant `switch-seconds`. La provocation
  (`taunt`) passe avant tout ; l'IA de Minecraft ne remplace pas sa cible (`MobListener#onTarget`).
- **Capacités** : chaque capacité active prête, à bonne distance et en vue, est notée selon la `Situation` (joueurs
  groupés, cible qui tire, tenue, vie basse, alliés...) × son poids × la variété × un peu de hasard ; la meilleure part
  au-dessus de `threshold`. Télégraphe (immobile, particules, son), interrompu par un étourdissement ou un silence (délai
  à moitié). Pause globale entre deux capacités. Déclencheurs : au coup, en étant frappé, sous un seuil, à la mort,
  périodiques.
- **Atteinte** : chemin vérifié toutes les 1,5 s ; un boss coincé loin d'une cible perchée apparaît près d'elle
  (`anti-cheese`).
- **Classes** (plus tard) : `CombatRoles.addProvider` donne le rôle et le multiplicateur de menace d'un joueur,
  `CombatRoles.threat` signale un soin ; l'IA en tient compte sans rien changer ici.

## Apparitions (`module/spawn`)

`config.yml > spawning` et `spawns` : (les règles de la famille du Livre y sont par défaut)

- **replace** : une apparition naturelle de ce type devient un des variants (chance) ;
- **director** : toutes les `interval-seconds`, un groupe autour de chaque joueur qui a de la place (`per-player` × densité
  du serveur et de la Nuit de sang), avec plafonds par chunk et par monde ; parfois mené (`group.leader`) ;
- les **boss** n'apparaissent jamais d'eux-mêmes (commande, menu, plugin de donjon).

Conditions : mondes, environnements, groupes de biomes (`Spawns`, Terralith compris), hauteur, lumière, grotte, heure,
météo. Jamais près du point d'apparition du monde ni sur un terrain de clan (`SpawnGuard`). Réglages par serveur
(`servers.<début du nom>`) : lobby coupé, monde ressource plus dense. **Nuit de sang** (`blood-night`) : certaines nuits
ou `/etermob event bloodnight`.

## Butin, statistiques, quêtes (`module/loot`)

- `loot` d'un variant : chances × butin du ratio ; seulement si au moins `loot-share` des dégâts viennent des joueurs.
  Objets marqués `etermob:item` (`tag`) pour les textures à venir. Boss : butin personnel à chaque joueur qui a fait au
  moins `loot-share` des dégâts.
- Table `etermob_kills` (joueur, variant, ratio) ; classements EterLib `mob_kills` et `mob_elites` (ratio 4 et plus).
- Signaux EterQuest : `etermob:kill {type, variant, ratio, tier}`, `etermob:boss {boss}`.

## Commandes et permissions

| Commande | Permission |
|---|---|
| `/etermob menu`, `spawn <variant> [ratio]`, `clear [rayon]` | `etermob.spawn` |
| `/etermob list`, `info <variant>`, `inspect` (IA du monstre visé : cible, menace, capacités), `debug` (règles ici), `rules`, `balance` | `etermob.debug` |
| `/etermob event bloodnight [minutes\|stop]`, `reload`, `boss <id\|list\|remove [n°\|all]>` | `etermob.admin` |

Toutes à `op` par défaut ; `etermob.admin` donne les autres.

## Technique

- Variant, ratio, meneur et sbire sont écrits sur le mob (données persistantes) : rien en base. Un mob d'un variant qui
  n'existe plus (EterMob 1.x, variant retiré) redevient celui de Minecraft au chargement.
- Les boss ne survivent pas à un changement de leur fichier en cours de combat : `/etermob reload` relit les variants et
  les règles, pas les boss ni les ratios (redémarrage).

## Mortarius (`module/boss/mortarius`)

Le Seigneur des Tombes, tenu par le Livre des morts : mage-guerrier tank pour un donjon de 4 joueurs au plus. Masse
(physique, ne s'interrompt pas), sorts du Livre (magiques, interruptibles), pourriture (brute, état `rot` : aucun soin).
Phase 1 : masse, bond, Lecture funeste (silence), Levée des morts. Phase 2 : Livre planté, 4 piliers à rôle (Garde :
intouchable ; Vie : soin ; Tourment : aura élargie ; Appel : morts plus fréquents), tous tombés : 60 s de vulnérabilité ;
charge (sonné 3 s dans un mur), Chaînes d'âmes. Phase 3 : plus rapide, onde sismique, tombes, aspiration (étourdi
1,5 s), dernier souffle à 10 %. Butin personnel, dont une Page du Livre des morts (`etermob:item` = `page-livre-des-morts`).
Chiffres : `config.yml > bosses.mortarius`.

## Les disciples (`module/boss/disciple`, sous-boss)

- **Le Scribe** (`scribe`, forêts sombres la nuit) : mage d'arrière-ligne, résistance magique haute. Chapitres (+10 %
  magique par pile, une Page volante tuée en retire une) ; phase 1 : Trait d'encre, Lecture interdite, Page envolée,
  Pages volantes ; phase 2 : Copie conforme (seul le vrai a la plume qui brille), Flaques d'encre, Rature, Dernière page
  (canalisée 4 s, à interrompre).
- **Le Fossoyeur** (`fossoyeur`, marais et plaines la nuit) : brute à grosse armure. Phase 1 : Coup de pelle (saignement),
  Pic et Cage d'os, Enterrement ; phase 2 : Tombes ouvertes (une goule toutes les 15 s, rester 2 s dessus pour la
  refermer), Pelletée, Fosse commune ; rage à 25 %.
- **La Gardienne des âmes** (`gardienne`, neiges, montagnes, vallée des âmes) : duelliste vampirique (vol de vie 20 %),
  3 âmes enchaînées (−15 % de dégâts chacune) ; phase 1 : Faux, Lien d'âmes (`SoulLinks` : dégâts partagés, casse à
  8 blocs), Moisson, Pas de l'ombre ; phase 2 : Pacte, Pluie d'âmes, Lien ×3, Récolte (dégâts bruts).

États ajoutés par EterMob : `rot` (aucun soin), `soullink`, `pact`. Sbires à états (`Minions.hitStates`) : Pages qui
marquent, goules qui saignent.

## La famille du Livre (`module/variant/book`)

Les morts-vivants de Mortarius, la nuit (règles `spawns` par défaut). Kit par ratio : Ordinaire / Robuste / Dangereux /
Élite / Fléau.

| Variant | Mob | Kit |
|---|---|---|
| `goule` | Husk | Griffes (saignement) / Affamée (+20 % de vitesse sur une cible qui saigne) / Bond / Dévorer / Frénésie |
| `page-volante` | Vex | Coupure (marque) / esquive 15 % / Tourbillon / Encre (ralentit) / Reliure (3 Pages → Livre volant, rayon) |
| `ame-enchainee` | Vex | Garde des morts (état `ward`) / Plainte / Lien / Hantise (peur) / Délivrance (soigne en mourant) |
| `garde-os` | Squelette, épée | Coup d'épée (fragilité) / Bouclier (−30 % aux tirs de face) / Charge / Interposition / Mur d'os |
| `archer-os` | Squelette, arc | Salve / critique 15 % / Recul / Flèche clouante / Pluie de flèches |
| `acolyte` | Evoker (sans ses sorts) | Prière noire (soigne un mort-vivant) / Fuyant / Silence / Relever / Bénédiction du Livre |
| `zombie-mineur` | Zombie, pioche (grottes) | Coup de pioche / armure +40 / Éboulement / Lanterne (cécité) / Effondrement |

Les boss, sous-boss, leurs sbires et les piliers ne ramassent aucun objet (il disparaîtrait avec eux).

## Orveth (`module/boss/golem`, Hollow Dungeon)

Le Gardien du monument : golem de pierre (×2,2) à qui les mineurs ont volé son cœur. Armure 300, résistance magique 40
(le tank magique y est roi). Point faible dans le dos (+30 %, +50 % en phase 3) ; Enracinement s'il reste immobile 3 s
(régénère, −30 % de dégâts : il faut le faire bouger). Phase 1 : Poing de pierre, Jet de rocher, Frappe sismique,
Ronces. Phase 2 : Fragments de mousse (arrivent des bords, le soignent et l'arment s'ils l'atteignent), Rouleau (sonné
3 s dans un mur), Éboulement. Phase 3 : Onde du sceau, Poigne, Jet de rocher ×2 ; Dernier éboulement à 10 % (8 s de
rochers sur ceux qu'il voit : se cacher derrière un pilier). Butin dont un Éclat du cœur (`eclat-du-coeur`).
Chiffres : `config.yml > bosses.orveth`.

## Les sous-boss d'Orveth (`module/boss/hollow`)

- **Le Contremaître** (`contremaitre`, grottes) : brute qui commande ; Lanterne (marque), Chef d'équipe (ses zombies :
  état `fury`) ; phase 1 : Coup de pioche, Appel des mineurs (zombies mineurs Dangereux), Dynamite (mèche 2 s),
  Wagonnet ; phase 2 : Grisou (nuages qui détonent quand une dynamite explose dedans), Dynamite ×3, Éboulement.
- **La Dryade des ronces** (`dryade`, jungles et marais) : soutien ; Photosynthèse, Pollen ; phase 1 : Spores, Ronces,
  Bourgeon (faux bloc avec de la vie qui la soigne), Fuite ; phase 2 : Liane, Pluie de pollen, Bourgeons ×3, Étreinte
  végétale (enracine le groupe, s'interrompt).
- **Le Voleur du cœur** (`voleur`, badlands et forêts sombres) : assassin ; Éclats (dégâts bruts quand on le frappe),
  Ombre (invisible tant qu'il ne frappe pas), Coup dans le dos (+50 %) ; phase 1 : Pas de l'ombre, Larcin, Bombe
  fumigène ; phase 2 : Éclats d'améthyste, Pièges (cristaux qui enracinent), Résonance (les cristaux explosent),
  Frénésie sous 25 %.
