# LazyRATP

Application TUI (Terminal User Interface) pour consulter en temps reel les prochains trajets de transports en commun en Ile-de-France. Fini de scroller sur l'app RATP, un coup d'oeil dans le terminal et c'est parti.

## Fonctionnalites

- Horaires en temps reel des prochains trajets entre deux stations (Bus, Metro, Transilien)
- Recherche de stations avec autocompletion
- Details des itineraires : correspondances, lignes, temps de marche
- Systeme de favoris pour sauvegarder ses trajets habituels
- Temps de marche configurables (depart et arrivee)
- Affichage personnalisable (colonnes, banniere, alignement)
- Rafraichissement automatique

## Prerequis

- [Node.js](https://nodejs.org/) (v18+)

## Obtenir une cle API

Le projet utilise l'API **Navitia** fournie par **Ile-de-France Mobilites (IDFM)** via leur plateforme PRIM.

1. Rendez-vous sur le portail PRIM : **https://prim.iledefrance-mobilites.fr/**
2. Cliquez sur **"Creer un compte"** et inscrivez-vous
3. Une fois connecte, allez dans la section **"Mes API"** ou **"Catalogue des API"**
4. Recherchez et souscrivez a l'API **"Navitia"** (aussi appelee "Calcul d'itineraire")
5. Votre cle API sera disponible dans votre espace personnel, section **"Mes cles"**

> La cle est gratuite pour un usage personnel.

## Installation

```bash
git clone <url-du-repo>
cd lazyratp
npm install
```

## Utilisation

Exportez votre cle API puis lancez l'application :

```bash
export TRAIN_API_KEY=votre_cle_api
node src/index.js
```

Ou en une seule ligne :

```bash
TRAIN_API_KEY=votre_cle_api node src/index.js
```

### Raccourcis clavier

| Touche | Action |
|--------|--------|
| `r` | Rafraichir les trajets |
| `d` | Changer la gare de depart |
| `a` | Changer la gare d'arrivee |
| `f` | Ouvrir les favoris |
| `Shift+F` | Ajouter le trajet actuel aux favoris |
| `?` | Afficher/masquer l'aide |
| `q` / `Escape` | Quitter |

**Affichage :**

| Touche | Action |
|--------|--------|
| `Shift+W` | Colonne temps d'attente |
| `Shift+D` | Colonne heure de depart |
| `Shift+R` | Colonne heure d'arrivee |
| `Shift+U` | Colonne duree |
| `Shift+B` | Banniere |
| `Shift+A` | Alignement |
| `Shift+H` | En-tete du tableau |
| `Shift+T` | Mode itineraire (off / code / complet) |

**Temps de marche :**

| Touche | Action |
|--------|--------|
| `+` / `-` | Ajuster le temps de marche au depart |
| `]` / `[` | Ajuster le temps de marche a l'arrivee |

## Configuration

Un fichier `config.json` est genere automatiquement a la racine du projet. Il sauvegarde vos preferences (stations, affichage, favoris). Ce fichier est ignore par git.

## Licence

ISC
