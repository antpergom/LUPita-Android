#!/bin/sh
# Copia a `main` (pública) los commits nuevos de `dev`, uno a uno, con las mismas comprobaciones que
# el pre-push (rutas protegidas y secretos). Sin estado que se pueda corromper: lo pendiente lo decide
# `git cherry` (por contenido del cambio) con `public-base` como límite. Al terminar, `dev` y `main`
# deben tener el árbol idéntico; si no, avisa. Uso:  sh scripts/git-workflow/sync-public.sh
. "$(dirname "$0")/lib.sh"
cd "$(git rev-parse --show-toplevel)" || exit 1

[ "$(git rev-parse --abbrev-ref HEAD)" = "dev" ] || wf_die "ejecútalo desde la rama dev"
[ -z "$(git status --porcelain --untracked-files=no)" ] || wf_die "hay cambios sin commitear en dev"
git rev-parse --verify -q refs/tags/public-base >/dev/null || wf_die "falta la etiqueta public-base (¿se ejecutó setup-public.sh?)"

sh "$WF_DIR/snapshot-docs.sh"

pending="$(git cherry main dev public-base | sed -n 's/^+ //p')"
if [ -z "$pending" ]; then
    echo "Nada que sincronizar."
else
    # Comprobar TODOS los commits pendientes antes de mover nada.
    for c in $pending; do
        short="$(git rev-parse --short "$c")"
        # Un merge o un commit vacío harían fallar el cherry-pick con un mensaje confuso: se avisa antes.
        [ "$(git rev-list --no-walk --count --merges "$c")" = "0" ] ||
            wf_die "$short es un commit de fusión: dev debe ser lineal. Rehazlo como commit normal o copia main a mano con -m."
        git diff-tree --quiet --root "$c" 2>/dev/null &&
            wf_die "$short no cambia ningún fichero (commit vacío): quítalo de dev antes de sincronizar."
        git diff-tree --no-commit-id --name-only -r --root --diff-filter=ACMR "$c" | wf_check_paths || wf_die "commit $short toca rutas protegidas: no se copia"
        git show --no-color -U0 --format= "$c" | wf_added_lines | wf_check_secrets || wf_die "commit $short parece contener un secreto: no se copia"
        git log -1 --format=%B "$c" | wf_check_secrets || wf_die "el mensaje del commit $short parece contener un secreto"
    done
    git checkout -q main
    for c in $pending; do
        if ! git cherry-pick "$c" >/dev/null 2>&1; then
            git cherry-pick --abort >/dev/null 2>&1
            git checkout -q dev
            wf_die "conflicto al copiar $(git rev-parse --short "$c"): main queda como estaba antes de ese commit; resuélvelo a mano"
        fi
        echo "copiado $(git log -1 --format='%h %s' main)"
    done
    git checkout -q dev
fi

if git diff --quiet dev main; then
    echo "OK: dev y main tienen el mismo árbol."
else
    wf_die "dev y main DIFIEREN tras sincronizar — no publiques hasta entenderlo (git diff --stat dev main)"
fi
