# Sésame

[![CI](https://github.com/IbrahimKakaev/Sesame/actions/workflows/ci.yml/badge.svg)](https://github.com/IbrahimKakaev/Sesame/actions/workflows/ci.yml)
[![Démo](https://github.com/IbrahimKakaev/Sesame/actions/workflows/pages.yml/badge.svg)](https://github.com/IbrahimKakaev/Sesame/actions/workflows/pages.yml)
[![CodeQL](https://github.com/IbrahimKakaev/Sesame/actions/workflows/codeql.yml/badge.svg)](https://github.com/IbrahimKakaev/Sesame/actions/workflows/codeql.yml)
[![Tests](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Ftests.json)](https://github.com/IbrahimKakaev/Sesame/actions/workflows/ci.yml)
[![Étanchéité des droits](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Fetancheite.json)](backend/src/test/java/fr/sesame/etancheite)
[![Bonnes sources retrouvées](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Fbonnes-sources.json)](https://ibrahimkakaev.github.io/Sesame/rapports/evaluation-recherche.json)

[![Couverture Java](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Fcouverture-java.json)](https://ibrahimkakaev.github.io/Sesame/rapports/couverture-java/)
[![Couverture Angular](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Fcouverture-angular.json)](https://ibrahimkakaev.github.io/Sesame/rapports/couverture-angular/)
[![Audit Java](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Faudit-java.json)](.trivyignore)
[![npm audit](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Faudit-npm.json)](https://github.com/IbrahimKakaev/Sesame/actions/workflows/ci.yml)
[![Lighthouse accessibilité](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Flighthouse-accessibilite.json)](https://ibrahimkakaev.github.io/Sesame/rapports/lighthouse.html)
[![Lighthouse performance](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Flighthouse-performance.json)](https://ibrahimkakaev.github.io/Sesame/rapports/lighthouse.html)
[![Lighthouse bonnes pratiques](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Flighthouse-bonnes-pratiques.json)](https://ibrahimkakaev.github.io/Sesame/rapports/lighthouse.html)
[![Lighthouse SEO](https://img.shields.io/endpoint?url=https%3A%2F%2Fibrahimkakaev.github.io%2FSesame%2Fbadges%2Flighthouse-seo.json)](https://ibrahimkakaev.github.io/Sesame/rapports/lighthouse.html)

[![Java](https://img.shields.io/badge/Java-21-e76f00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22-dd0031?logo=angular&logoColor=white)](https://angular.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-4169e1?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![Claude](https://img.shields.io/badge/LLM-Claude%20Haiku%205.5-d97757)](https://platform.claude.com/docs)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ed?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.21-291a3f)](https://testcontainers.com/)
[![Dernier commit](https://img.shields.io/github/last-commit/IbrahimKakaev/Sesame?label=dernier%20commit)](https://github.com/IbrahimKakaev/Sesame/commits/main)

Moteur de recherche documentaire d'entreprise : on pose une question en français
sur des PDF, un LLM répond en citant ses sources, et **aucune réponse ne s'appuie
sur un document que l'utilisateur n'a pas le droit de voir**. Backend **Java 21 /
Spring Boot**, frontend **Angular**, base **PostgreSQL + pgvector**, rédaction par
l'**API Claude**.

Les « chatbots sur documents » sont courants. Ce qui distingue celui-ci est le
cloisonnement des droits d'accès, et le fait qu'il est démontré par les tests
plutôt qu'affirmé : voir le [modèle de menace](#modèle-de-menace).

![La même question, posée par un membre du service RH : la réponse cite la grille salariale](docs/reponse-avec-droits.png)

## En bref

- **Droits par groupes** : un utilisateur voit un document seulement s'il partage
  un groupe (RH, Finance, Direction, Tous) avec lui. Trois rôles : lecteur,
  contributeur, administrateur.
- **Filtrage avant le modèle** : la recherche des passages se fait en base, parmi
  les seuls documents autorisés. Le LLM ne reçoit jamais un passage interdit ; on
  ne lui demande donc jamais de « ne pas révéler » quoi que ce soit.
- **Réponses sourcées, en flux** : chaque affirmation renvoie au document, à la
  page et à l'extrait utilisés. Chaque citation est revérifiée par le serveur.
- **« Je ne sais pas »** plutôt qu'une invention : sans passage autorisé pour
  répondre, la réponse est la même que le document interdit existe ou non.
- **Dépôt de PDF** par les contributeurs, indexés en arrière-plan : extraction
  page par page, découpage, embeddings calculés localement.
- **Journal d'audit** : qui a posé quelle question, quels documents ont servi,
  qui a changé quels droits.
- **Mode démo** : l'application fonctionne seule dans le navigateur, sans serveur.
- **Testé** : une suite d'étanchéité vérifie chaque couple utilisateur-document
  sur chaque route, et des tests de fuite inspectent la requête exacte envoyée au
  LLM. Le nombre de tests, les couvertures et le taux de bonnes sources sont
  publiés dans les badges ci-dessus.

## Démo en ligne

**[ibrahimkakaev.github.io/Sesame](https://ibrahimkakaev.github.io/Sesame/)**

La démo est l'application Angular réelle, publiée à chaque push. GitHub Pages
n'héberge que des fichiers statiques : un faux serveur embarqué
([frontend/src/app/demo/](frontend/src/app/demo)) remplace le backend, avec les
mêmes comptes, les mêmes documents et la même règle d'accès, et des réponses
préenregistrées. Elle montre l'interface et le principe ; la preuve du
cloisonnement est dans les tests du backend, pas dans la démo.

**Le scénario à essayer** : posez « Quel est le salaire d'un développeur
senior ? », puis changez de compte dans le bandeau et reposez la question.

| Compte | Rôle | Groupes | Réponse à la question |
| --- | --- | --- | --- |
| Léa Martin | Lecteur | Tous | « Je ne trouve pas la réponse… » |
| Karim Benali | Contributeur | Tous, RH | La fourchette, avec la grille salariale en source |
| Inès Roux | Contributeur | Tous, Finance | « Je ne trouve pas la réponse… » |
| Hugo Lefèvre | Administrateur | Tous, Direction | « Je ne trouve pas la réponse… » |

L'administrateur n'a pas la réponse non plus : il gère les droits de la grille
salariale sans pouvoir la lire. Depuis son compte, cochez « Tous » sur la grille
dans *Administration → Droits des documents*, puis reposez la question avec Léa.

| Sans le droit | L'administrateur gère les droits |
| --- | --- |
| ![La même question posée par une lectrice : aucune source autorisée](docs/reponse-sans-droits.png) | ![Tableau des groupes autorisés par document](docs/administration-droits.png) |

| Documents visibles | Journal d'audit |
| --- | --- |
| ![Liste des documents accessibles au compte](docs/documents.png) | ![Journal : questions posées et droits modifiés](docs/journal.png) |

Les captures sont refaites par [scripts/captures.mjs](scripts/captures.mjs), qui
joue ce scénario dans un navigateur.

## Architecture

```mermaid
flowchart LR
    N[Navigateur<br/>Angular 22] -- "/api, flux SSE" --> G[nginx]
    G --> B[Backend<br/>Spring Boot 3]
    B -- "comptes, droits, passages,<br/>vecteurs, audit" --> P[(PostgreSQL 16<br/>pgvector)]
    B -- "embeddings<br/>dans la JVM" --> M[multilingual-e5-small<br/>ONNX]
    B -- "passages autorisés<br/>+ question" --> C[API Claude]
    N -. "mode démo" .-> D[DemoBackend<br/>dans le navigateur]
```

- **Frontend** : Angular 22, composants autonomes, signaux, sans bibliothèque d'interface.
- **Backend** : Java 21, Spring Boot 3.5, Spring Security, Flyway, documentation
  OpenAPI sur `/swagger-ui.html`.
- **Base** : PostgreSQL 16 et pgvector. Les documents, leurs passages, leurs
  vecteurs et les droits vivent dans la même base : le filtrage par droits et la
  recherche se font dans **une seule requête SQL**.
- **Embeddings** : `multilingual-e5-small` (384 dimensions, licence MIT) exécuté
  dans la JVM avec ONNX Runtime. Aucun service payant ni conteneur supplémentaire.
- **Génération** : `claude-haiku-5-5`, le modèle le moins cher de la gamme, avec
  la fonction *Citations* de l'API.

### Déroulé d'une question

```mermaid
sequenceDiagram
    participant U as Navigateur
    participant B as Backend
    participant P as PostgreSQL
    participant C as API Claude
    U->>B: POST /api/questions (jeton d'accès)
    B->>P: compte actif ? rôle ? (relus à chaque requête)
    B->>B: limite de débit, embedding de la question
    B->>P: recherche parmi les passages autorisés (une requête)
    P-->>B: 5 passages au plus, tous visibles par l'utilisateur
    alt aucun passage pertinent
        B-->>U: « Je ne trouve pas la réponse… » (sans appel au modèle)
    else
        B->>C: consigne fixe + passages (documents à citer) + question
        C-->>B: texte et citations, en flux
        B->>B: chaque citation est revérifiée
        B-->>U: événements SSE : texte, citation, fin
        B->>P: conversation, citations, journal d'audit
    end
```

La requête de recherche commence par l'étape `autorises`, qui ne garde que les
passages des documents que l'utilisateur partage avec l'un de ses groupes
([ServiceRecherche.java](backend/src/main/java/fr/sesame/recherche/ServiceRecherche.java)).
Les deux branches de la recherche ne lisent que cette étape. La même condition
SQL, écrite une seule fois
([DepotDocuments.VISIBLE](backend/src/main/java/fr/sesame/documents/DepotDocuments.java)),
sert à la liste, au détail, au fichier, aux extraits et à l'historique.

Les droits ne sont ni dans le jeton ni dans un cache : le jeton d'accès ne porte
que l'identifiant du compte, et les groupes sont relus en base à chaque requête.
C'est ce qui rend un retrait de droit effectif dès la requête suivante.

## Modèle de menace

Chaque ligne est un risque, la mesure qui y répond, et le test qui échoue si la
mesure disparaît. Les tests Java sont dans
[backend/src/test/java/fr/sesame/](backend/src/test/java/fr/sesame).

| Risque | Mesure | Test |
| --- | --- | --- |
| Un passage interdit est transmis au LLM, qui le restitue | Filtrage par droits dans la requête SQL, avant tout appel au modèle | `FuiteTest.requeteAuModeleSansPassageInterdit` lit le corps HTTP reçu par un faux serveur Claude ; `EtancheiteTest.matrice` (route question) |
| Un utilisateur atteint un document interdit par une autre route | Même condition SQL sur toutes les lectures | `EtancheiteTest.matrice` : 6 comptes × 6 documents × 6 routes (liste, détail, fichier, extrait, recherche, question), résultat attendu recalculé indépendamment |
| Accès direct par identifiant | Un document interdit répond 404, octet pour octet comme un identifiant inexistant | `EtancheiteTest.matrice`, `EtancheiteTest.extraitCroise` |
| Fuite indirecte : titre, nombre de résultats, message ou appel au modèle qui trahit l'existence d'un document | La réponse ne dépend que des documents autorisés | `EtancheiteTest.nonInterference`, `FuiteTest.nonInterference`, `FuiteTest.aucunIndiceDExistence` : supprimer ou ajouter des documents interdits ne change pas un octet |
| Un droit retiré reste effectif (jeton encore valide, cache) | Groupes et rôle relus en base à chaque requête, aucun cache des droits | `RevocationTest` (retrait du groupe, du droit sur le document, suppression du groupe, désactivation), `AdministrationTest.retraitDeRoleImmediat` |
| Un PDF piégé donne des instructions au modèle | Le texte des PDF est transmis comme document à citer, jamais dans la consigne ; le modèle n'a aucun outil et ne reçoit rien d'interdit | `DocumentPiegeTest` |
| Le modèle invente une citation ou cite un passage qu'il n'a pas reçu | Chaque citation doit viser un passage transmis pour cette requête et reprendre un texte qui s'y trouve | `QuestionsTest.citationsReverifiees`, `FuiteTest.citationDUnPassageInterdit`, `DocumentPiegeTest.citationsInventees` |
| Le modèle répond sans s'appuyer sur les documents | Une réponse sans citation vérifiée est remplacée par « Je ne trouve pas… » | `QuestionsTest.reponseSansCitation`, `DocumentPiegeTest.modeleQuiObeit` |
| L'historique restitue un extrait après le retrait du droit | Citations relues avec les droits du moment ; chaque question est envoyée seule au modèle | `QuestionsTest.historiqueApresRevocation`, `QuestionsTest.questionsIndependantes` |
| L'historique d'un autre utilisateur | Conversation liée à son auteur, 404 pour les autres | `QuestionsTest.conversationDUnAutre` |
| Jeton absent, expiré, falsifié, non signé | JWT HS256 vérifié à chaque requête ; toute route est fermée par défaut | `AccesProtegeTest`, sur toutes les routes déclarées par l'application |
| Vol du jeton de renouvellement | Cookie `HttpOnly`, `SameSite=Strict` ; rotation à chaque usage ; un jeton rejoué révoque la session | `AuthentificationTest.rejeuDetecte`, `AuthentificationTest.cookieDeRenouvellementProtege` |
| Élévation de privilèges | Routes d'administration réservées au rôle, relu en base | `AdministrationTest.routesReserveesAuxAdministrateurs`, `AdministrationTest.pasDAutoRetrogradation` |
| L'administrateur lit tous les documents | Administrer n'est pas lire : il gère les droits, ses questions passent par ses groupes | `DepotDeDocumentsTest.administrerNEstPasLire` |
| Force brute et énumération des comptes | 5 essais par e-mail et 20 par adresse par quart d'heure ; refus identiques ; bcrypt | `LimiteConnexionTest`, `AuthentificationTest.refusIndiscernables` |
| Abus de l'API (coût du modèle) | 20 questions ou recherches par compte et par minute | `QuestionsTest.limiteDeDebit` |
| Fichier hostile | Taille limitée à 10 Mo, type vérifié sur le contenu, nom assaini, PDF corrompu, chiffré, trop long ou trop volumineux refusé à l'indexation | `DepotDeDocumentsTest` (faux PDF, noms hostiles, PDF inexploitables), `ExtracteurPdfTest` |
| Un contributeur dépose dans le groupe d'un autre service | Dépôt limité à ses propres groupes | `DepotDeDocumentsTest.groupesDuContributeur` |
| Injection SQL ou plein texte par la question | Requêtes paramétrées, caractères de contrôle retirés | `EtancheiteTest.questionHostile` |
| Script injecté par le contenu d'un document | Affichage en texte par Angular, politique CSP sans script en ligne | `reponse.spec.ts` (« sans interpréter de HTML ») |
| Secret dans le dépôt | Aucun : secrets tirés au hasard au premier démarrage (Compose) ou lus dans l'environnement ; refus de démarrer sans clé de signature | `SocleIntegration` tire sa clé au hasard ; contrôle au démarrage dans `ProprietesSecurite` |

Le faux serveur Claude des tests
([FauxClaude.java](backend/src/test/java/fr/sesame/FauxClaude.java)) est un vrai
serveur HTTP auquel l'application parle avec le SDK officiel : il enregistre le
corps exact de chaque requête. Les tests de fuite vérifient donc ce qui part
réellement sur le réseau, pas une représentation interne.

## Tests et qualité

```bash
cd backend && mvn verify          # tests Java (Docker requis pour Testcontainers)
cd frontend && npm test           # tests Angular
cd frontend && npm run couverture # avec rapport de couverture
```

| Ce qui est vérifié | Comment | Où |
| --- | --- | --- |
| Étanchéité des droits | chaque couple utilisateur-document sur chaque route, révocation, fuites, document piégé | [etancheite/](backend/src/test/java/fr/sesame/etancheite) |
| Comptes et jetons | connexion, rotation, rejeu, jetons falsifiés, rôles, limite de débit | [securite/](backend/src/test/java/fr/sesame/securite), [comptes/](backend/src/test/java/fr/sesame/comptes) |
| Dépôt et indexation | fichiers hostiles, états d'indexation, numéros de page | [documents/](backend/src/test/java/fr/sesame/documents) |
| Réponses | flux, citations revérifiées, historique, pannes de l'API | [questions/](backend/src/test/java/fr/sesame/questions) |
| Qualité de la recherche | 24 questions avec le document attendu, vrai modèle d'embeddings | [EvaluationRechercheTest.java](backend/src/test/java/fr/sesame/recherche/EvaluationRechercheTest.java) |
| Angular | composants, services, gardes de route, mode démo | fichiers `*.spec.ts` dans [frontend/src/app/](frontend/src/app) |

Les tests d'intégration tournent sur une vraie base PostgreSQL + pgvector lancée
par Testcontainers. Le LLM est remplacé par le faux serveur décrit plus haut.
Les embeddings sont remplacés par un moteur déterministe dans les tests
d'étanchéité : un filtrage par droits ne doit dépendre d'aucun modèle, et ces
tests restent ainsi rapides et reproductibles. Le vrai modèle est exercé par
`MoteurEmbeddingsOnnxTest` et par l'évaluation de la recherche.

À chaque push, GitHub Actions rejoue ces tests et y ajoute **CodeQL** (Java et
TypeScript), l'**audit des dépendances** (npm audit, et Trivy sur le jar),
**Lighthouse** sur la démo et le **build des images Docker**.

### Évaluation de la recherche, et verdict sur la recherche hybride

[24 questions](backend/src/test/resources/evaluation/questions.json) portent sur
les documents de démonstration. Une source est « retrouvée » quand le document
attendu figure parmi les 5 passages transmis au LLM.

| Mode | Sources retrouvées | Bonne source en première position |
| --- | --- | --- |
| Vecteurs seuls | 23 / 24 (96 %) | 21 / 24 |
| Hybride : vecteurs + plein texte français, fusion par rangs | 24 / 24 (100 %) | 19 / 24 |

La recherche hybride est gardée : elle rattrape la question que les vecteurs
manquent (un terme exact, « enveloppe d'augmentation ») et c'est la présence de
la source parmi les passages transmis qui compte pour la réponse. Son coût reste
faible : une colonne générée, un index et une étape de fusion, les deux branches
lisant la même étape `autorises`. Le gain est toutefois mince sur ce petit corpus,
et la branche lexicale dégrade un peu le classement de tête. Ces chiffres ont été
mesurés sur une machine ARM ; le modèle étant quantifié, ils peuvent varier
légèrement d'un processeur à l'autre.

## Récupérer le projet

```bash
git clone https://github.com/IbrahimKakaev/Sesame.git
cd Sesame
```

## Démarrage rapide avec Docker

```bash
docker compose up --build
```

L'application est disponible sur [http://localhost:4200](http://localhost:4200).
Cette commande lance PostgreSQL, le backend et le frontend, puis charge au
premier démarrage l'entreprise fictive Hélianthe Ingénierie : quatre comptes et
dix PDF ([backend/src/main/resources/demo/](backend/src/main/resources/demo)).

Aucun réglage n'est nécessaire. Le mot de passe de la base et la clé de
signature des jetons sont tirés au hasard au premier démarrage et gardés dans un
volume Docker ; ils ne figurent nulle part dans le dépôt.

Pour que les réponses soient rédigées, fournissez une clé de l'API Claude :

```bash
ANTHROPIC_API_KEY=sk-ant-... docker compose up --build
```

Sans clé, tout fonctionne (comptes, dépôt, recherche, droits) sauf la rédaction :
une question qui la demanderait répond 503.

### Comptes de démonstration

| E-mail | Rôle | Groupes |
| --- | --- | --- |
| `lea.martin@helianthe.example` | Lecteur | Tous |
| `karim.benali@helianthe.example` | Contributeur | Tous, RH |
| `ines.roux@helianthe.example` | Contributeur | Tous, Finance |
| `hugo.lefevre@helianthe.example` | Administrateur | Tous, Direction |

Mot de passe commun : `demo-sesame-2026`. Il est public : ce chargement
(`SESAME_DEMO=true`) est réservé à la démonstration.

Pour repartir de données neuves : `docker compose down -v`.

## Lancement sans Docker

Prérequis : Java 21, Maven, Node.js 22 ou plus récent, et un PostgreSQL 16 avec
l'extension pgvector.

```bash
scripts/telecharger-modele.sh          # modèle d'embeddings (118 Mo, somme de contrôle vérifiée)
cp .env.example .env                   # puis renseigner les valeurs
cd backend && set -a && . ../.env && set +a && mvn spring-boot:run
cd frontend && npm install && npm start   # http://localhost:4200, /api relayé vers le backend
```

Pour voir le mode démo en local, sans backend : `npm start` puis
[http://localhost:4200/?demo](http://localhost:4200/?demo).

## Variables d'environnement

[.env.example](.env.example) les liste toutes.

| Variable | Rôle |
| --- | --- |
| `ANTHROPIC_API_KEY` | clé de l'API Claude, pour la rédaction des réponses |
| `SESAME_DEMO` | `true` charge l'entreprise fictive au premier démarrage |
| `SESAME_ADMIN_EMAIL`, `SESAME_ADMIN_MOT_DE_PASSE` | premier administrateur d'une installation sans données de démo |
| `SESAME_COOKIE_SECURISE` | `false` seulement en local sans HTTPS |
| `SESAME_JWT_SECRET` | clé de signature des jetons (hors Docker ; Compose la génère) |
| `SESAME_DB_URL`, `SESAME_DB_UTILISATEUR`, `SESAME_DB_MOT_DE_PASSE` | base PostgreSQL (hors Docker) |

## Structure du projet

- [backend/](backend) : API Spring Boot
  - [securite/](backend/src/main/java/fr/sesame/securite) : jetons, filtre, limite de débit
  - [comptes/](backend/src/main/java/fr/sesame/comptes) : comptes, groupes, administration
  - [documents/](backend/src/main/java/fr/sesame/documents) : dépôt, extraction, découpage, indexation, règle d'accès
  - [recherche/](backend/src/main/java/fr/sesame/recherche) : recherche hybride filtrée
  - [questions/](backend/src/main/java/fr/sesame/questions) : appel à Claude, citations, conversations
  - [audit/](backend/src/main/java/fr/sesame/audit) : journal d'audit
  - [db/migration/](backend/src/main/resources/db/migration) : schéma (Flyway)
- [frontend/](frontend) : application Angular, dont le [mode démo](frontend/src/app/demo)
- [demo-donnees/](demo-donnees) : textes des documents fictifs et réponses de la démo
- [scripts/](scripts) : téléchargement du modèle, données de démo, captures
- [.github/](.github) : intégration continue, publication de la démo et des badges
- [docker-compose.yml](docker-compose.yml) : PostgreSQL, backend et frontend

## Limites connues

- **Résistance du modèle aux instructions piégées.** Les tests simulent le LLM :
  ils prouvent ce qui ne dépend pas de lui (aucun passage interdit transmis,
  aucun outil, citations revérifiées), pas que le vrai modèle ignore toujours
  une instruction cachée dans un PDF. Un modèle manipulé peut au pire déformer
  une réponse construite sur des documents que l'utilisateur a déjà le droit de
  lire.
- **Le serveur garantit les citations, pas chaque phrase.** Une réponse qui mêle
  des affirmations citées et une phrase sans source est acceptée. Seule une
  réponse sans aucune citation valide est remplacée, et son texte a déjà été
  affiché au fil de l'eau avant d'être retiré.
- **Historique après un retrait de droit.** Les sources ne sont plus rendues,
  mais le texte de la réponse, que l'utilisateur avait légitimement reçu, reste
  dans son historique.
- **Pas de questions de suivi.** Chaque question est envoyée seule au modèle :
  c'est le prix du point précédent.
- **Canal temporel.** Le temps de réponse n'est pas uniformisé.
- **Limite de débit en mémoire**, valable pour une seule instance du backend. Un
  tiers peut bloquer la connexion d'un compte pendant un quart d'heure en
  épuisant ses cinq essais.
- **Seuil de pertinence étroit.** Le seuil qui évite d'appeler le modèle pour une
  question hors sujet (0,80) est proche des similarités des questions légitimes
  (0,83 au plus bas dans l'évaluation). Il est réglé pour ne jamais faire taire
  une question légitime ; une question hors sujet peut donc coûter un appel.
- **Recherche exacte.** Sans index vectoriel approché, la recherche parcourt tous
  les passages autorisés : adaptée à quelques dizaines de milliers de passages.
- **PDF scannés** : sans couche de texte, un PDF passe en échec (pas d'OCR).
- **Dépendances.** L'audit signale deux failles de Spring Framework 6.2 sans
  correctif dans cette branche. Elles ne s'appliquent pas à cette application ;
  la justification est dans [.trivyignore](.trivyignore).
- **Le mode démo** rejoue des réponses préenregistrées : il illustre le
  comportement, il ne le prouve pas.
