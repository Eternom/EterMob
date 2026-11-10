# EterMob

Les **monstres du réseau** côté Paper : variants, meneurs (sous-boss) et boss, avec des
capacités en briques, une IA commune (choix de la cible et des capacités), les états d'EterLib, des ratios de puissance
et l'apparition automatique. Document développeur, à tenir à jour avec le code.

Depuis 2.1.0, les boss sont **codés en dur** (`module/boss`, ex. `mortarius/Mortarius`) en assemblant les briques de capacités
et l'IA commune (`BrainBoss`) ; leurs chiffres sont dans `config.yml > bosses.<id>`. Les variants et meneurs (encore en YAML :
`plugins/EterMob/variants/*.yml`, modèle `_exemple.yml`) passeront aussi en code. Les
donjons seront des maps à part, gérées par leur propre plugin (qui pourra s'appuyer sur ce socle).

## Prérequis

- **EterLib 1.27.0+** (`depend`) : combat (dégâts physiques, magiques, bruts ; armure, résistance magique, pénétration), états (`getStates`), rôles de combat (`CombatRoles`), base, langues, pont des quêtes,
  classements, vanish.
- **EterClan** (lu à l'usage, s'il est là) : pas d'apparition ni de bloc temporaire sur un terrain de clan.

## Rangs

| Rang | Où | Rôle |
|---|---|---|
| `variant` | `variants/` | Le commun, avec ses capacités |
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

Une capacité est une **brique** (`Ability`) réglée dans le YAML d'un monstre : `kind` + réglages communs (`trigger`,
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
| `pillar` | Blocs temporaires (pic sous la cible, cage, mur), seulement dans l'air, jamais sur un terrain protégé |
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

Un autre plugin peut ajouter une sorte (`Abilities.register`). Les blocs temporaires (`TempBlocks`) sont notés dans
`temp-blocks.yml` : un arrêt brutal ne les laisse jamais pour toujours.

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

`config.yml > spawning` et `spawns` :

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
