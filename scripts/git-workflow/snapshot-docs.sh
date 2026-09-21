#!/bin/sh
# Copia de seguridad LOCAL Y BARATA de la documentación que no se versiona (docs/, CLAUDE.md,
# TECHNICAL.md). Vive dentro de .git (así sobrevive a `git clean -fdX`, a checkout/reset que pisen los
# ficheros y a cambios de rama), se hace solo si el contenido cambió y conserva las últimas 30.
# NO es una copia de seguridad real (mismo disco): si se borra la carpeta del proyecto se pierde.
# Se ejecuta desde los hooks post-commit/post-checkout y desde los scripts de sincronización.

cd "$(git rev-parse --show-toplevel)" || exit 0
files=""
for f in docs mock CLAUDE.md TECHNICAL.md NOTAS.md; do [ -e "$f" ] && files="$files $f"; done
[ -z "$files" ] && exit 0

dir="$(git rev-parse --git-dir)/docs-snapshots"
mkdir -p "$dir"
hash="$(find $files -type f -exec sha1sum {} + 2>/dev/null | sort -k2 | sha1sum | cut -d' ' -f1)"
[ -f "$dir/LAST" ] && [ "$(cat "$dir/LAST")" = "$hash" ] && exit 0

out="$dir/docs-$(date +%Y%m%d-%H%M%S).tgz"
if tar -czf "$out" $files 2>/dev/null; then
    printf '%s' "$hash" > "$dir/LAST"
    ls -1t "$dir"/docs-*.tgz 2>/dev/null | tail -n +31 | while read -r old; do rm -f "$old"; done
fi
exit 0
