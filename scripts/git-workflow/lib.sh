#!/bin/sh
# Utilidades compartidas por los hooks y los scripts de este directorio (POSIX sh, funciona en Git
# Bash de Windows). NO imprime nunca el contenido de un secreto: solo la ruta / el patrón / el
# commit implicado, para que un fallo sea corto de leer.
#
# Principio de diseño: TODO falla en cerrado. Si falta un fichero de configuración, si no se puede
# leer o si se queda sin patrones, se BLOQUEA — nunca se deja pasar en silencio.

WF_DIR="$(cd "$(dirname "$0")" && pwd)"
# Si se invoca desde un hook (scripts/git-workflow/hooks/xxx) el directorio de datos es el padre.
case "$WF_DIR" in */hooks) WF_DIR="$(dirname "$WF_DIR")" ;; esac

# Documentación local: nunca se versiona ni se publica, sin excepciones.
DOC_PATHS='^(docs/|mock/|CLAUDE\.md$|TECHNICAL\.md$|NOTAS\.md$)'
# Credenciales y secretos en fichero. `credentials/` va anclado a la raíz a propósito: sin anclar
# también coincidiría con el paquete Kotlin `data/credentials/` (bug real de .gitignore, 2026-09-08).
CRED_PATHS='^credentials/|(^|/)(secrets\.properties|local\.properties|google-services\.json|\.env(\..+)?)$|service-account.*\.json$|gcs-key.*\.json$|\.(jks|keystore|pem|p12)$'

wf_die() {
    printf 'git-workflow: %s\n' "$*" >&2
    exit 1
}

# Lee rutas (una por línea) por stdin y falla si alguna está protegida. La exención `*.example`
# (plantillas de credenciales, p.ej. secrets.properties.example) se aplica SOLO a credenciales,
# nunca a la documentación.
wf_check_paths() {
    all="$(cat)"
    docs_bad="$(printf '%s\n' "$all" | grep -aE "$DOC_PATHS" | sort -u | head -5)"
    creds_bad="$(printf '%s\n' "$all" | grep -aE "$CRED_PATHS" | grep -avE '\.example$' | sort -u | head -5)"
    bad="$(printf '%s\n%s' "$docs_bad" "$creds_bad" | grep -av '^$')"
    if [ -n "$bad" ]; then
        printf 'git-workflow: BLOQUEADO — ruta protegida:\n%s\n' "$bad" >&2
        return 1
    fi
    return 0
}

# Excepciones de secretos (falsos positivos conocidos) como una única alternancia ERE, ignorando
# comentarios y líneas vacías del fichero — así una línea de comentario no puede actuar como patrón.
wf_allowlist_regex() {
    grep -avE '^[[:space:]]*(#|$)' "$WF_DIR/secret-allowlist.txt" | paste -sd '|' -
}

# Lee texto (líneas añadidas de un diff, o mensajes de commit) por stdin y falla si hay algo que
# parezca un secreto. Solo informa del PATRÓN que coincidió y de cuántas líneas, nunca del contenido.
wf_check_secrets() {
    [ -r "$WF_DIR/secret-patterns.txt" ] || wf_die "no se puede leer secret-patterns.txt — se bloquea (fallo en cerrado)"
    [ -r "$WF_DIR/secret-allowlist.txt" ] || wf_die "no se puede leer secret-allowlist.txt — se bloquea (fallo en cerrado)"
    grep -aqE '^[^[:space:]#]' "$WF_DIR/secret-patterns.txt" ||
        wf_die "secret-patterns.txt no tiene ningún patrón — se bloquea (fallo en cerrado)"

    allow="$(wf_allowlist_regex)"
    text="$(cat)"
    [ -z "$text" ] && return 0
    found=0
    while IFS= read -r pattern; do
        case "$pattern" in ''|'#'*) continue ;; esac
        hits="$(printf '%s\n' "$text" | grep -aE -e "$pattern")"
        [ -n "$allow" ] && hits="$(printf '%s\n' "$hits" | grep -avE -e "$allow")"
        hits="$(printf '%s' "$hits" | grep -av '^$')"
        if [ -n "$hits" ]; then
            printf 'git-workflow: BLOQUEADO — posible secreto (patrón: %s, %s línea(s))\n' \
                "$pattern" "$(printf '%s\n' "$hits" | wc -l | tr -d ' ')" >&2
            found=1
        fi
    done < "$WF_DIR/secret-patterns.txt"
    return $found
}

# Filtra un diff dejando solo las líneas AÑADIDAS (sin la cabecera `+++`).
wf_added_lines() {
    grep -a '^+' | grep -av '^+++'
}
