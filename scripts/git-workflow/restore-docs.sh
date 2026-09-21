#!/bin/sh
# Recupera la documentación local (docs/, mock/, CLAUDE.md, TECHNICAL.md, NOTAS.md) desde las instantáneas guardadas en
# .git/docs-snapshots/. Sin argumentos: lista las disponibles. Con una instantánea: la extrae.
#   sh scripts/git-workflow/restore-docs.sh              -> lista
#   sh scripts/git-workflow/restore-docs.sh docs-2026....tgz  -> restaura esa
#   sh scripts/git-workflow/restore-docs.sh --last       -> restaura la más reciente
set -e
cd "$(git rev-parse --show-toplevel)"
dir="$(git rev-parse --git-dir)/docs-snapshots"
[ -d "$dir" ] || { echo "No hay instantáneas en $dir"; exit 1; }

case "$1" in
    "")
        echo "Instantáneas disponibles (más reciente primero):"
        ls -1t "$dir"/docs-*.tgz 2>/dev/null | while read -r f; do
            printf '  %s  (%s, %s ficheros)\n' "$(basename "$f")" \
                "$(du -h "$f" | cut -f1)" "$(tar -tzf "$f" | grep -vc '/$')"
        done
        echo "Restaura con:  sh scripts/git-workflow/restore-docs.sh --last"
        ;;
    --last) snap="$(ls -1t "$dir"/docs-*.tgz | head -1)" ;;
    *) snap="$dir/$(basename "$1")" ;;
esac

[ -n "${snap:-}" ] || exit 0
[ -f "$snap" ] || { echo "No existe: $snap"; exit 1; }
# Se extrae sobre el árbol: sustituye lo que haya con la versión de la instantánea.
tar -xzf "$snap"
echo "Restaurado desde $(basename "$snap")."
