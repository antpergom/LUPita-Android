#!/bin/sh
# Prepara el modelo de dos ramas, UNA sola vez, sin tocar ningún fichero del disco:
#   dev  = rama de trabajo privada (historial completo, con la documentación antigua). Nunca se publica.
#   main = rama pública: un commit raíz nuevo con el árbol actual (sin docs) y, a partir de ahí, los
#          commits de dev copiados con `sync-public.sh`.
# La etiqueta `public-base` marca el punto de dev del que parte main (límite para `git cherry`).
set -e
cd "$(git rev-parse --show-toplevel)"

[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || { echo "Debes estar en la rama 'main' actual (aún sin renombrar)."; exit 1; }
git rev-parse --verify -q refs/heads/dev >/dev/null && { echo "Ya existe 'dev': el sistema ya está montado."; exit 1; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Hay cambios sin commitear: haz commit primero."; exit 1; }

# Instantánea previa y huella de la documentación, para comprobar al final que no se ha movido nada.
sh scripts/git-workflow/snapshot-docs.sh
before="$(find docs mock CLAUDE.md TECHNICAL.md NOTAS.md -type f -exec sha1sum {} + 2>/dev/null | sort -k2 | sha1sum)"

git branch -m main dev
git tag public-base dev
git checkout -q --orphan main
git commit -q -m "chore: instantanea inicial publica del codigo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
git checkout -q dev

after="$(find docs mock CLAUDE.md TECHNICAL.md NOTAS.md -type f -exec sha1sum {} + 2>/dev/null | sort -k2 | sha1sum)"
[ "$before" = "$after" ] || { echo "ATENCIÓN: la documentación cambió en disco. Recupérala de .git/docs-snapshots/"; exit 1; }
git diff --quiet dev main || { echo "ATENCIÓN: dev y main difieren tras la creación."; exit 1; }

echo "OK: dev (privada) y main (pública, sin historial previo) creadas. Estás en dev."
echo "Documentación intacta ($(git ls-tree -r --name-only main | grep -cE '^(docs/|mock/|CLAUDE|TECHNICAL|NOTAS)') ficheros de docs en main: debe ser 0)."
