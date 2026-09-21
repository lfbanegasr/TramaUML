param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$Puerto = 55433,
    [ValidateSet('postgres','postgres17')][string]$NombreCluster = 'postgres',
    [string]$Maven = 'mvn',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SoloPreparar
)
$ErrorActionPreference = 'Stop'
$implementationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$clusterRoot = Join-Path $implementationRoot ('.qa\'+$NombreCluster)
if ($Puerto -eq 55432 -or $Puerto -lt 1024 -or $Puerto -gt 65535) { throw 'El puerto debe ser exclusivo de pruebas y diferente de 55432.' }
New-Item -ItemType Directory -Path $clusterRoot -Force | Out-Null
$env:PGPASSWORD = ''
$env:PGCONNECT_TIMEOUT = '5'
$env:UML_TEST_DB_PASSWORD = [Guid]::NewGuid().ToString()
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
function Invoke-Pg([string]$Programa,[string[]]$Argumentos) {
    & (Join-Path $PostgresBin $Programa) @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "Falló $Programa en la instalación aislada." }
}
if (-not (Test-Path -LiteralPath (Join-Path $clusterRoot 'data\PG_VERSION'))) {
    Invoke-Pg 'initdb.exe' @('-D',(Join-Path $clusterRoot 'data'),'--username=uml_test_owner','--auth=trust','--no-locale','-E','UTF8')
}
function Test-PgReady {
    & (Join-Path $PostgresBin 'pg_isready.exe') -h 127.0.0.1 -p $Puerto -d postgres -t 1 *> $null
    return $LASTEXITCODE -eq 0
}
$postgresReady = Test-PgReady
if (-not $postgresReady) {
    $pidFile = Join-Path $clusterRoot 'data\postmaster.pid'
    if (Test-Path -LiteralPath $pidFile) {
        $pidLines = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
        if ($pidLines -and $pidLines.Count -gt 0) {
            $oldPid = 0
            if ([int]::TryParse($pidLines[0], [ref]$oldPid) -and $oldPid -gt 0) {
                if (-not (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
                    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
    $pgProcess = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
        '-D','data','-l','server.log','-o',('"-p '+$Puerto+' -h 127.0.0.1"'),'start','-W'
    ) -WorkingDirectory $clusterRoot -WindowStyle Hidden -PassThru
    for ($attempt = 0; $attempt -lt 360 -and -not $postgresReady; $attempt++) {
        Start-Sleep -Milliseconds 250
        $postgresReady = Test-PgReady
    }
    if (-not $postgresReady) {
        if (-not $pgProcess.HasExited) { Stop-Process -Id $pgProcess.Id -Force }
        throw 'PostgreSQL aislado no quedó disponible en 90 segundos; revisa .qa/postgres/server.log.'
    }
    $pgProcess | Wait-Process -Timeout 5 -ErrorAction SilentlyContinue
    $pgProcess.Refresh()
    if (-not $pgProcess.HasExited) { Stop-Process -Id $pgProcess.Id -Force }
}
$actualCluster = & (Join-Path $PostgresBin 'psql.exe') -X -At -h 127.0.0.1 -p $Puerto -U uml_test_owner -d postgres -c 'show data_directory'
if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($actualCluster.Trim()) -ne [IO.Path]::GetFullPath((Join-Path $clusterRoot 'data'))) {
    throw 'El servidor no pertenece al clúster esperado. No se creó ni modificó una base.'
}
$testDatabase = 'uml_collab_test_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
Invoke-Pg 'createdb.exe' @('-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner',$testDatabase)
$installationLog = Join-Path $implementationRoot ('.qa\'+$testDatabase+'_instalacion.log')
foreach ($sqlSource in @('2026-09-12_00_cuenta_aplicacion.sql','2026-09-12_16_esquema_espanol.sql','2026-09-12_17_catalogos_espanol.sql','2026-09-13_18_correcciones_desarrollo.sql')) {
    Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot ('database\'+$sqlSource)))
}
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-14_01_revocacion_permisos_rol.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-15_02_resolucion_conflictos.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-15_03_historial_permisos.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-18_04_xmi_generacion_permisos.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-19_05_asistencia_inteligente_permisos.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-20_06_servicios_plataforma_seguridad.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-20_07_revision_asistencia_lectura.sql'))
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-f',(Join-Path $implementationRoot 'core\docs\migraciones\2026-09-21_08_generacion_backend_avanzada.sql'))
$env:UML_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Puerto/$testDatabase" + '?currentSchema=uml_colaborativo,public'
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-c','ALTER ROLE uml_app LOGIN')
Write-Output "Base ficticia preparada: $testDatabase (127.0.0.1:$Puerto). Se conserva para inspección."
if (-not $SoloPreparar) {
    if ($Maven -eq 'mvn') {
        $mvnLocal = Join-Path $implementationRoot '.qa\tools\apache-maven-3.9.9\bin\mvn.cmd'
        if (Test-Path -LiteralPath $mvnLocal) { $Maven = $mvnLocal }
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        foreach ($ubicacionJava in @(
            'C:\Program Files\Android\Android Studio\jbr',
            'D:\Android Studio Instalacion 2025\jbr',
            'D:\Program Files\Android\Android Studio\jbr'
        )) {
            if (Test-Path -LiteralPath (Join-Path $ubicacionJava 'bin\java.exe')) { $env:JAVA_HOME = $ubicacionJava; break }
        }
    }
    Push-Location (Join-Path $implementationRoot 'core\backend')
    try {
        & $Maven '-Dmaven.repo.local=../../.qa/maven-repository' test
        if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del núcleo.' }
    } finally { Pop-Location }
}
