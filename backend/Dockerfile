# syntax=docker/dockerfile:1
# Image du backend. Contexte de construction : la racine du dépôt.

FROM maven:3.9-eclipse-temurin-21 AS construction
WORKDIR /app
COPY backend/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY backend/src src
# Les tests tournent dans la CI, pas pendant la construction de l'image
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# Modèle d'embeddings : révision épinglée et sommes de contrôle vérifiées par le script
FROM debian:bookworm-slim AS modele
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
COPY scripts/telecharger-modele.sh /
RUN /telecharger-modele.sh /modele

FROM eclipse-temurin:21-jre
# Le serveur ne tourne pas en root
RUN useradd --system --uid 10001 --create-home sesame
WORKDIR /app
COPY --from=modele /modele modeles/multilingual-e5-small
COPY --from=construction /app/target/sesame-*.jar sesame.jar
USER sesame
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "sesame.jar"]
