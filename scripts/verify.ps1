<#
.SYNOPSIS
    Verificacion local que gasta los MENOS tokens posibles: compila, testea, lintea, ensambla y
    (si hay dispositivo) instala - y solo cuenta lo imprescindible.

.DESCRIPTION
    Idea central: el log completo de Gradle NUNCA se vuelca a la consola (ni a un agente). Cada fase
    escribe su log a build/verify-logs/ y en pantalla solo salen:
      - una linea OK/FAIL por fase, con tiempo y totales de tests;
      - si falla, hasta -MaxItems errores ya digeridos: "Fichero.kt:LINEA mensaje".
    Para profundizar en un fallo concreto sin releer el log entero:
      token-goat bash-output --file <log> --grep "FAILED|e: " --max-matches 30

    Por que se leen los XML de JUnit y no la salida de consola: `token-goat failures` solo entiende
    pytest/jest/go/cargo (comprobado 2026-09-21: sobre salida de Gradle devuelve "unknown" y mezcla
    lineas de tarea con BUILD FAILED, perdiendo el mensaje de la asercion y el archivo:linea). Los
    XML (build/test-results) son estables, exactos y baratos de parsear.

    Modos (de mas barato a mas completo):
      -Tests <patron>   solo tests que coincidan (comodines de Gradle). Para iterar.
      -Changed          deduce los tests desde git: Foo.kt -> *FooTest (convencion, sin indice).
      (sin parametros)  test + lint + assemble + instalar. Puerta antes de commit.

    REQUISITOS DEL BUILD - este script los da por hechos (los cumple build.gradle.kts raiz):
      1. `gradlew test` cubre modulos JVM y Android; testReleaseUnitTest esta deshabilitado en los
         Android para no ejecutar los tests dos veces.
      2. El filtro llega por PROPIEDAD (-PlupitaTests=a,b), NO por `--tests`: en Android `test` es una
         tarea de ciclo de vida y Gradle rechaza `--tests` si alguna tarea con ese nombre no lo
         soporta (comprobado con el primer build real, 2026-09-21).
      3. `isFailOnNoMatchingTests = false` en TODAS las tareas Test: un patron que solo coincide en
         un modulo no debe hacer fallar a los demas.
      4. Cada modulo tiene su build.gradle.kts versionado (asi se localizan sus resultados).

    Solo ASCII a proposito: Windows PowerShell 5.1 lee mal un .ps1 UTF-8 sin BOM con acentos.

.PARAMETER SkipInstall
    No intenta instalar en el dispositivo aunque haya uno conectado.
.PARAMETER SkipIndex
    No ejecuta `token-goat reconcile` al empezar (por defecto refresca el indice del proyecto).
.PARAMETER Tests
    Patron de --tests de Gradle, p.ej. "*SelectionRectTest" o "com.antoniopg.lupita.core.model.*".
.PARAMETER Changed
    Tests derivados de los .kt modificados/nuevos segun git (respecto a HEAD).
.PARAMETER MaxItems
    Maximo de errores/fallos que se imprimen por fase (el resto se resume como "+N mas").

.EXAMPLE
    .\scripts\verify.ps1 -Tests "*SelectionRectTest"
    .\scripts\verify.ps1 -Changed
    .\scripts\verify.ps1 -SkipInstall
#>
param(
    [switch]$SkipInstall,
    [switch]$SkipIndex,
    [switch]$Changed,
    [string]$Tests,
    [int]$MaxItems = 10
)

# Sin $ErrorActionPreference = "Stop": en PS 5.1 la salida de stderr de un ejecutable nativo se
# envuelve en un NativeCommandError terminante y abortaria el script antes de leer $LASTEXITCODE.
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $projectRoot "gradlew.bat"
$logDir = Join-Path $projectRoot "build\verify-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$script:overallOk = $true

if (-not (Test-Path $gradlew)) {
    Write-Host "No hay gradlew.bat en $projectRoot - el wrapper lo crea el paso 1 de F0." -ForegroundColor Yellow
    exit 2
}

# --- Utilidades --------------------------------------------------------------------------------

# Directorios de modulo = los que tienen un build.gradle.kts (rapido: no recorre build/). Incluye
# los aun SIN commitear (--others): con `ls-files` a secas un modulo recien creado contaba "0 tests"
# en silencio, un falso verde (visto en el primer build real, 2026-09-21).
function Get-ModuleDirs {
    $files = & git -C $projectRoot ls-files --cached --others --exclude-standard "build.gradle.kts" "*/build.gradle.kts" 2>$null
    foreach ($f in $files) {
        $d = Split-Path -Parent (Join-Path $projectRoot $f)
        if ($d) { $d } else { $projectRoot }
    }
}

# Errores del compilador de Kotlin: "e: file:///C:/x/Foo.kt:12:5 mensaje" -> "Foo.kt:12 mensaje".
function Get-CompileErrors([string]$LogFile) {
    $rx = '^e: (?:file:///)?(?<f>.+?\.kt):(?<l>\d+):(?<c>\d+)\s*(?<m>.*)$'
    foreach ($hit in (Select-String -Path $LogFile -Pattern '^e: ' -CaseSensitive -Context 0, 1)) {
        $m = [regex]::Match($hit.Line.Trim(), $rx)
        if ($m.Success) {
            $msg = $m.Groups['m'].Value
            # Kotlin 2.x deja el mensaje en la linea SIGUIENTE ("e: file:///X.kt:3:8 " + "Unresolved
            # reference..."), comprobado con salida real; el formato de una sola linea tambien vale.
            if (-not $msg -and $hit.Context.PostContext.Count -gt 0) { $msg = $hit.Context.PostContext[0].Trim() }
            "{0}:{1} {2}" -f (Split-Path -Leaf $m.Groups['f'].Value), $m.Groups['l'].Value, $msg
        } else {
            $hit.Line.Trim()
        }
    }
}

# Resultados de JUnit. -FreshSince limita a XML escritos en esa fase (asi un fallo antiguo no se
# cuela); sin el, se cuentan todos (tras un exito Gradle puede haber saltado la tarea por estar al dia).
function Get-TestReport($FreshSince) {
    $total = 0; $failed = 0; $skipped = 0; $failures = @()
    foreach ($mod in (Get-ModuleDirs)) {
        $dir = Join-Path $mod "build\test-results"
        if (-not (Test-Path $dir)) { continue }
        $xmls = Get-ChildItem -Path $dir -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue
        if ($FreshSince) { $xmls = $xmls | Where-Object { $_.LastWriteTime -ge $FreshSince } }
        foreach ($x in $xmls) {
            try { $doc = [xml](Get-Content -Raw -LiteralPath $x.FullName) } catch { continue }
            foreach ($tc in $doc.testsuite.testcase) {
                $total++
                if ($tc.skipped -ne $null) { $skipped++ }
                # @(...) exterior imprescindible: con UN solo fallo, Where-Object devuelve el objeto
                # suelto y `.Count` sobre un XmlElement es $null (el fallo se perdia en silencio).
                $bad = @(@($tc.failure) + @($tc.error) | Where-Object { $_ -ne $null })
                if ($bad.Count -gt 0) {
                    $failed++
                    $b = $bad[0]
                    $msg = ("" + $b.message).Split("`n")[0].Trim()
                    if ($msg.Length -gt 160) { $msg = $msg.Substring(0, 160) + "..." }
                    # Primer frame del propio proyecto: da archivo:linea sin volcar la traza entera.
                    $where = ""
                    foreach ($l in (("" + $b.'#text').Split("`n"))) {
                        if ($l -match 'com\.antoniopg\.lupita' -and $l -match '\(([\w$]+\.kt):(\d+)\)') {
                            $where = "{0}:{1}" -f $Matches[1], $Matches[2]; break
                        }
                    }
                    $short = ($tc.classname -split '\.')[-1]
                    $failures += ("{0}.{1}  {2}  [{3}]" -f $short, $tc.name, $msg, $where)
                }
            }
        }
    }
    [pscustomobject]@{ Total = $total; Failed = $failed; Skipped = $skipped; Failures = $failures }
}

# Hallazgos de lint (XML) en los modulos que lo tengan.
function Get-LintIssues {
    foreach ($mod in (Get-ModuleDirs)) {
        $f = Join-Path $mod "build\reports\lint-results-debug.xml"
        if (-not (Test-Path $f)) { continue }
        try { $doc = [xml](Get-Content -Raw -LiteralPath $f) } catch { continue }
        foreach ($i in $doc.issues.issue) {
            if ($i.severity -in @("Error", "Fatal")) {
                $loc = $i.location | Select-Object -First 1
                "{0}:{1} [{2}] {3}" -f (Split-Path -Leaf $loc.file), $loc.line, $i.id, $i.message
            }
        }
    }
}

function Write-Capped($Items, [string]$Color) {
    $arr = @($Items)
    foreach ($x in ($arr | Select-Object -First $MaxItems)) {
        Write-Host "    $x" -ForegroundColor $Color
    }
    if ($arr.Count -gt $MaxItems) {
        Write-Host ("    +{0} mas (ver log)" -f ($arr.Count - $MaxItems)) -ForegroundColor DarkGray
    }
}

# Una fase = una invocacion de Gradle. Devuelve $true/$false y deja el log en build/verify-logs.
function Run-Stage([string]$Name, [string[]]$GradleArgs, [string]$Kind) {
    $logFile = Join-Path $logDir "$stamp-$Name.log"
    Write-Host -NoNewline ("  {0,-9} " -f $Name)
    $started = Get-Date
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $gradlew --console=plain $GradleArgs *> $logFile
    $exit = $LASTEXITCODE
    $sw.Stop()
    $secs = "{0:N0}s" -f $sw.Elapsed.TotalSeconds

    if ($exit -eq 0) {
        $extra = ""
        if ($Kind -eq "test") {
            $r = Get-TestReport $null
            $extra = ", {0} tests, {1} saltados" -f $r.Total, $r.Skipped
        }
        Write-Host "OK ($secs$extra)" -ForegroundColor Green
        # Un verde con 0 tests casi nunca es real (se habia colado un falso verde): avisar.
        if ($Kind -eq "test" -and $r.Total -eq 0) {
            Write-Host "    AVISO: 0 tests ejecutados o no localizados - no lo des por bueno sin mirar el log." -ForegroundColor Yellow
        }
        return $true
    }

    Write-Host "FAIL ($secs)" -ForegroundColor Red
    $printed = $false
    $compile = @(Get-CompileErrors $logFile)
    if ($compile.Count -gt 0) {
        $printed = $true
        Write-Host ("  compilacion: {0} error(es)" -f $compile.Count) -ForegroundColor DarkYellow
        Write-Capped $compile "DarkYellow"
    }
    # Fronteras entre modulos (:tools:boundaries): lineas "  :modulo: mensaje" en stderr.
    $bounds = @(Select-String -Path $logFile -Pattern '^  :[\w:]+: ' | ForEach-Object { $_.Line.Trim() })
    if ($bounds.Count -gt 0) {
        $printed = $true
        Write-Host ("  fronteras: {0} violacion(es)" -f $bounds.Count) -ForegroundColor DarkYellow
        Write-Capped $bounds "DarkYellow"
    }
    if ($Kind -eq "test") {
        $r = Get-TestReport ($started.AddSeconds(-2))
        if ($r.Failed -gt 0) {
            $printed = $true
            Write-Host ("  tests: {0} fallo(s) de {1}" -f $r.Failed, $r.Total) -ForegroundColor DarkYellow
            Write-Capped $r.Failures "DarkYellow"
        }
    }
    if ($Kind -eq "lint") {
        $lint = @(Get-LintIssues)
        if ($lint.Count -gt 0) {
            $printed = $true
            Write-Host ("  lint: {0} error(es)" -f $lint.Count) -ForegroundColor DarkYellow
            Write-Capped $lint "DarkYellow"
        }
    }
    if (-not $printed) {
        # Nada reconocible (configuracion, dependencias, opcion desconocida...): el motivo de Gradle.
        # Antes solo se hacia para fases que no eran de test/lint y un fallo de configuracion en la
        # fase de tests salia sin ninguna explicacion (visto en el primer build real).
        $why = Select-String -Path $logFile -Pattern "What went wrong" -Context 0, 3 | Select-Object -First 1
        if ($why) { Write-Capped ($why.Context.PostContext | Where-Object { $_.Trim() }) "DarkYellow" }
    }
    # Ruta relativa: cada linea de salida que lee un agente cuesta tokens.
    $rel = $logFile.Substring($projectRoot.Length).TrimStart('\')
    Write-Host "    mas: token-goat bash-output --file $rel --grep `"FAILED|e: |went wrong`" --max-matches 30" -ForegroundColor DarkGray
    return $false
}

# --- Modo -Changed: deducir tests desde git, por convencion Foo.kt -> FooTest -------------------
function Get-ChangedTestFilters {
    $changed = @(& git -C $projectRoot diff --name-only HEAD 2>$null) +
               @(& git -C $projectRoot ls-files --others --exclude-standard 2>$null)
    $names = $changed | Where-Object { $_ -match '\.kt$' } |
        ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) } | Sort-Object -Unique
    $existing = @(& git -C $projectRoot ls-files --cached --others --exclude-standard "*Test.kt" 2>$null) |
        ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_) }
    $wanted = foreach ($n in $names) { if ($n -like "*Test") { $n } else { "${n}Test" } }
    # Solo los que existen de verdad: Gradle falla si un patron no coincide con nada.
    $wanted | Sort-Object -Unique | Where-Object { $existing -contains $_ }
}

# --- Ejecucion ---------------------------------------------------------------------------------
Write-Host "LUPita - verificacion local" -ForegroundColor Cyan

# Mantiene fresco el indice de token-goat (lo editado fuera de sesion), para que las lecturas
# posteriores por simbolo/seccion coincidan con el disco. Best-effort y silencioso.
if (-not $SkipIndex -and (Get-Command token-goat -ErrorAction SilentlyContinue)) {
    & token-goat --cwd $projectRoot reconcile *> $null
}

$unitTests = @("test")

if ($Tests -or $Changed) {
    $patterns = @()
    if ($Tests) { $patterns += $Tests }
    if ($Changed) {
        $patterns += @(Get-ChangedTestFilters | ForEach-Object { "*.$_" })
        if ($patterns.Count -eq 0) {
            Write-Host "  Sin tests asociados a lo modificado (convencion Foo.kt -> FooTest)." -ForegroundColor Yellow
            Write-Host "  Nada que ejecutar; usa -Tests <patron> o una pasada completa." -ForegroundColor DarkGray
            exit 0
        }
    }
    Write-Host ("  modo rapido: {0}" -f ($patterns -join ", ")) -ForegroundColor Yellow
    Write-Host "  (no sustituye la pasada completa antes de commit)`n" -ForegroundColor DarkGray
    # Por propiedad, no por `--tests` (ver build.gradle.kts raiz): en Android `test` no es de tipo Test.
    $args = @($unitTests) + @("-PlupitaTests=" + ($patterns -join ","))
    if (-not (Run-Stage "tests" $args "test")) { exit 1 }
    exit 0
}

Write-Host ""
if (-not (Run-Stage "tests"    $unitTests               "test"))  { $script:overallOk = $false }
if ($script:overallOk -and -not (Run-Stage "lint"     @("lintDebug")     "lint"))  { $script:overallOk = $false }
if ($script:overallOk -and -not (Run-Stage "assemble" @("assembleDebug") "build")) { $script:overallOk = $false }

if (-not $script:overallOk) {
    Write-Host "`nVerificacion FALLIDA." -ForegroundColor Red
    exit 1
}
Write-Host "`nVerificacion OK - tests, lint y APK en verde." -ForegroundColor Green
if ($SkipInstall) { exit 0 }

# --- Instalacion (best-effort: no es un fallo que no haya dispositivo) -------------------------
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "(adb no encontrado: instalacion omitida)" -ForegroundColor DarkGray; exit 0 }
if (-not (& $adb devices | Select-String -Pattern "\tdevice$")) {
    Write-Host "(sin dispositivo conectado: instalacion omitida)" -ForegroundColor DarkGray; exit 0
}
$apk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Host "(APK no encontrado en $apk)" -ForegroundColor Yellow; exit 0 }
Write-Host -NoNewline "  install   "
$installLog = Join-Path $logDir "$stamp-install.log"
& $adb install -r $apk *> $installLog
if ($LASTEXITCODE -eq 0) { Write-Host "OK" -ForegroundColor Green } else {
    Write-Host "FAIL - ver $installLog" -ForegroundColor Red; exit 1
}
