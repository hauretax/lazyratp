# LazyRATP

TUI Node (`src/`, blessed) + app Android (`android/`, Compose + widget Glance).
Les deux consomment l'API Navitia d'Ile-de-France Mobilites (PRIM).

## Le modele : favori et regle

C'est la distinction structurante du projet. Ne pas la melanger.

**Un favori definit un trajet.** C'est le QUOI : d'ou, vers ou, et *comment on calcule*
les horaires. Le mode de calcul fait partie du favori, pas d'un reglage a cote :

- les prochains departs (`TripMode.NEXT_DEPARTURES`)
- le dernier trajet du jour (`TripMode.LAST_JOURNEY`)
- arriver a une heure precise (`TripMode.ARRIVE_BY`, cf. ticket dedie)

Deux favoris peuvent viser les memes gares et repondre differemment : c'est pourquoi
l'identifiant d'un favori derive de la requete entiere (gares + mode + exclusions), et
pas seulement de la paire de gares. Voir `Favorite.id` dans `data/Models.kt`.

**Une regle definit la raison d'afficher un favori.** C'est le QUAND et le POURQUOI.
Une regle ne calcule aucun trajet : elle *designe* un favori (`Rule.favoriteId`) quand
ses conditions sont reunies — jour, plage horaire, proximite d'un lieu, epinglage.

Les regles sont evaluees dans l'ordre de la liste, la premiere qui matche gagne : l'ordre
EST la priorite, et il appartient a l'utilisateur. Quand aucune ne matche, on retombe sur
le favori de repli (`Prefs.selected`). Voir `rules/RuleEngine.kt`.

Consequence pratique quand on recoit une demande : se demander de quel cote elle tombe.
« dernier train », « arriver a 19h », « sans bus » -> c'est le favori.
« quand je suis loin de chez moi », « apres 22h », « le vendredi » -> c'est la regle.
Une demande peut avoir une moitie de chaque : ajouter un mode de trajet ET la condition
qui le fait apparaitre.

## Build Android

Gradle 8.14 ne supporte pas le JDK 25, et c'est le seul JDK installe sur la machine.
Utiliser celui d'Android Studio :

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # JDK 21
cd android && ./gradlew installDebug
```

Sans ca, Gradle echoue sur un message opaque qui n'est que le numero de version : `25.0.2`.

## Verifier sur l'appareil

Le telephone de dev est un **Xiaomi Redmi Note 10 Pro en USB** (Android 12), sur lequel
l'app et le widget sont deja poses. Le Pixel 10a qui apparait parfois en wifi adb est le
telephone **personnel** de l'utilisateur : ne pas le piloter. Si les deux sont visibles,
deconnecter le Pixel plutot que de jongler avec `-s`.

Un changement de widget ne se verifie pas au typecheck. Le protocole qui marche :

```bash
adb exec-out screencap -p > /tmp/shot.png   # lire l'image, comparer l'horodatage du header
adb logcat -d | grep -i "lazyratp\|glance.session"
adb shell dumpsys appwidget | grep lazyratp  # instances posees sur l'ecran d'accueil
```

**C'est un telephone personnel, pas un banc de test.** Toujours verifier que LazyRATP est
au premier plan AVANT de capturer l'ecran, jamais apres : une capture prise pendant que
l'utilisateur se sert de son telephone ramene ses messages prives. Garder chaque capture :

```bash
# topResumedActivity n'existe pas avant Android 13 : le Xiaomi dit mResumedActivity.
adb shell dumpsys activity activities \
  | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q fr.lazyratp \
  && adb exec-out screencap -p > /tmp/shot.png \
  || echo "telephone occupe, ne pas capturer"
```

Si l'appareil est pris, s'arreter et le dire, plutot que de relancer l'app par-dessus ce
que l'utilisateur est en train de faire.

Piege : le widget peut afficher le resultat du rafraichissement *precedent*. Comparer
l'horodatage du header a `adb shell date` avant de conclure qu'un refresh a fonctionne.

## Tests

Cote Android, les regles et les modeles sont couverts par des tests unitaires JVM
(`android/app/src/test/`) : `./gradlew test`. La TUI Node n'a aucun test.
