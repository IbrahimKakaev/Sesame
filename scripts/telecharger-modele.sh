#!/usr/bin/env bash
# Télécharge le modèle d'embeddings multilingual-e5-small (licence MIT) au format ONNX.
# La révision est épinglée et chaque fichier est vérifié par sa somme SHA-256 :
# un fichier modifié en amont fait échouer le script au lieu d'être chargé.
# Usage : scripts/telecharger-modele.sh [dossier de destination]
set -euo pipefail

DESTINATION="${1:-$(dirname "$0")/../backend/modeles/multilingual-e5-small}"
REVISION="614241f622f53c4eeff9890bdc4f31cfecc418b3"
SOURCE="https://huggingface.co/intfloat/multilingual-e5-small/resolve/${REVISION}/onnx"

somme() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

# recuperer <fichier distant> <nom local> <somme attendue>
recuperer() {
  local cible="${DESTINATION}/$2"
  if [ -f "$cible" ] && [ "$(somme "$cible")" = "$3" ]; then
    echo "déjà présent : $2"
    return
  fi
  echo "téléchargement : $2"
  curl --fail --location --silent --show-error --output "${cible}.partiel" "${SOURCE}/$1"
  if [ "$(somme "${cible}.partiel")" != "$3" ]; then
    rm -f "${cible}.partiel"
    echo "somme de contrôle incorrecte pour $2" >&2
    exit 1
  fi
  mv "${cible}.partiel" "$cible"
}

mkdir -p "$DESTINATION"
# Version quantifiée en entiers 8 bits : 118 Mo au lieu de 470
recuperer model_qint8_avx512_vnni.onnx model.onnx dd476dd0c2514e9b9be83aeb3853fac0763e0bdf4a71645407587d77c48a2d88
recuperer tokenizer.json tokenizer.json 0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39
echo "modèle prêt dans ${DESTINATION}"
