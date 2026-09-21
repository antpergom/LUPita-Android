#!/bin/sh
# Instala los hooks y la configuración de seguridad de git. Idempotente.
#   sh scripts/git-workflow/install.sh
#
# Los hooks se COPIAN a `.git/wf/` y `core.hooksPath` apunta ahí, no al árbol de trabajo. Motivo
# (comprobado 2026-09-20): con `core.hooksPath=scripts/git-workflow/hooks`, un `git checkout` a un
# commit anterior a la creación de los scripts BORRA los hooks del árbol antes de ejecutarlos — se
# quedaban sin efecto precisamente en el caso peligroso (ese checkout también machaca la
# documentación local). Dentro de `.git` sobreviven a cualquier checkout, reset o `git clean -fdX`.
#
# El origen sigue siendo `scripts/git-workflow/` (versionado y revisable): tras tocarlo, reinstala.
set -e
cd "$(git rev-parse --show-toplevel)"
src="scripts/git-workflow"
dst="$(git rev-parse --git-dir)/wf"

mkdir -p "$dst/hooks"
for f in lib.sh snapshot-docs.sh restore-docs.sh secret-patterns.txt secret-allowlist.txt; do
    cp "$src/$f" "$dst/$f"
done
for f in "$src"/hooks/*; do
    cp "$f" "$dst/hooks/$(basename "$f")"
    chmod +x "$dst/hooks/$(basename "$f")" 2>/dev/null || true
done
chmod +x "$dst"/*.sh 2>/dev/null || true
# Huella del origen instalado: los hooks avisan (sin bloquear) si el árbol tiene una versión distinta.
cat "$src"/lib.sh "$src"/hooks/* "$src"/secret-patterns.txt "$src"/secret-allowlist.txt |
    sha1sum | cut -d' ' -f1 > "$dst/INSTALLED_FROM"

git config core.hooksPath "$dst/hooks"
# `git push` sin argumentos no hace nada: hay que decir siempre qué se sube (los hooks lo limitan a main).
git config push.default nothing
git config push.followTags false
git config push.autoSetupRemote false
# No evita que checkout machaque la documentación ignorada (comprobado que NO basta), pero ayuda en
# otras operaciones y no estorba.
git config checkout.overwriteIgnore false

sh "$dst/snapshot-docs.sh"
echo "git-workflow: instalado en $dst (core.hooksPath=$dst/hooks)."
