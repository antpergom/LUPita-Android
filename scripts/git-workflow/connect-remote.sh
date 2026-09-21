#!/bin/sh
# Conecta el remoto de GitHub con la configuración segura. Uso:
#   sh scripts/git-workflow/connect-remote.sh https://github.com/<usuario>/<repo>.git
# No sube nada. NO pongas el token en la URL: git-credential-manager lo pedirá y guardará al hacer push.
set -e
[ -n "$1" ] || { echo "Falta la URL del repositorio (https, sin token)."; exit 1; }
case "$1" in *@*:*|*"://"*:*@*) echo "La URL parece llevar credenciales: no las pongas ahí."; exit 1 ;; esac
cd "$(git rev-parse --show-toplevel)"

git remote add origin "$1" 2>/dev/null || git remote set-url origin "$1"
git config remote.origin.push refs/heads/main:refs/heads/main
git config remote.origin.tagOpt --no-tags
git config remote.origin.mirror false
git config branch.dev.remote ""       # dev nunca tiene destino de subida
echo "Remoto configurado. Primera subida:  git push -u origin main"
