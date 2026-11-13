param(
    [int]$Puerto = 55436,
    [string]$Pruebas = 'GeneracionTest,IaTest,ConectorGeminiRespuestaTest,ImagenesGeminiTest,NucleoIntegracionTest#guardarMismoNombreGenerarSinValidarYDescargarDuranteRevalidacion+versionHistoricaSeValidaYRegeneraSinRestaurarNiPerderLaDescargaAnterior+configuracionYGeneracionBackendYDescarga+generacionAvanzadaCongelaLaVersionYAislaConfiguracionesYArtefactos+validacionBackendControlaPermisosCsrfIntegridadYErroresDeCompilacion'
)
$ErrorActionPreference = 'Stop'
$raizFlujos = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$pgFlujos = Join-Path $raizFlujos '.qa/tools/postgres17/pgsql/bin'
$clusterFlujos = Join-Path $raizFlujos '.qa/2026-11-13_flujos/postgres'
$mavenFlujos = Join-Path $raizFlujos '.qa/tools/apache-maven-3.9.9'
if ($Puerto -in @(5432,55432,55435) -or $Puerto -lt 1024 -or $Puerto -gt 65535) {
    throw 'Usa un puerto exclusivo de pruebas.'
}
function Invoke-PgFlujos([string]$Programa, [string[]]$Argumentos) {
    & (Join-Path $pgFlujos $Programa) @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "Falló $Programa en la base ficticia." }
}
New-Item -ItemType Directory -Force -Path $clusterFlujos | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $clusterFlujos 'data/PG_VERSION'))) {
    Invoke-PgFlujos 'initdb.exe' @('-D',(Join-Path $clusterFlujos 'data'),'--username=uml_test_owner','--auth=trust','--no-locale','-E','UTF8')
}
& (Join-Path $pgFlujos 'pg_isready.exe') -h 127.0.0.1 -p $Puerto -t 1 *> $null
if ($LASTEXITCODE -ne 0) {
    $inicioFlujos = Start-Process -FilePath (Join-Path $pgFlujos 'pg_ctl.exe') -ArgumentList @(
        '-D','data','-l','server.log','-o',('"-p '+$Puerto+' -h 127.0.0.1"'),'start','-w','-t','60'
    ) -WorkingDirectory $clusterFlujos -WindowStyle Hidden -PassThru
    if (-not $inicioFlujos.WaitForExit(60000) -or $inicioFlujos.ExitCode -ne 0) { throw 'No arrancó el cluster de pruebas de flujos.' }
}
$realFlujos = & (Join-Path $pgFlujos 'psql.exe') -X -At -h 127.0.0.1 -p $Puerto -U uml_test_owner -d postgres -c 'show data_directory'
if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($realFlujos.Trim()) -ne [IO.Path]::GetFullPath((Join-Path $clusterFlujos 'data'))) {
    throw 'El puerto pertenece a otro cluster; no se modifica ninguna base.'
}
$baseFlujos = 'uml_collab_test_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
Invoke-PgFlujos 'createdb.exe' @('-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner',$baseFlujos)
$env:PGOPTIONS = '-c client_min_messages=warning'
$env:UML_TEST_DB_PASSWORD = [Guid]::NewGuid().ToString()
$env:UML_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Puerto/$baseFlujos" + '?currentSchema=uml_colaborativo,public'
$fuentesFlujos = @('2026-09-12_00_cuenta_aplicacion.sql','2026-09-12_16_esquema_espanol.sql','2026-09-12_17_catalogos_espanol.sql','2026-09-13_18_correcciones_desarrollo.sql') |
    ForEach-Object { Join-Path $raizFlujos ('database/' + $_) }
$fuentesFlujos += Get-ChildItem -LiteralPath (Join-Path $raizFlujos 'core/docs/migraciones') -Filter '*.sql' |
    Sort-Object Name | ForEach-Object { $_.FullName }
foreach ($fuenteFlujos in $fuentesFlujos) {
    Invoke-PgFlujos 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$baseFlujos,'-f',$fuenteFlujos)
}
Invoke-PgFlujos 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$baseFlujos,'-c','ALTER ROLE uml_app LOGIN')
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:UML_COMPILACION_MAVEN_HOME = $mavenFlujos
$env:UML_COMPILACION_REPOSITORIO = Join-Path $raizFlujos '.qa/maven-repository'
$env:UML_COMPILACION_DIRECTORIO = Join-Path $raizFlujos '.qa/compilaciones'
$env:UML_IA_PROVEEDOR = 'LOCAL'
$env:UML_IA_CONTEXTO_REMOTO = 'false'
Write-Output "Base ficticia de flujos: $baseFlujos (puerto $Puerto). Se conserva para inspección."
Push-Location (Join-Path $raizFlujos 'core/backend')
try {
    $ErrorActionPreference = 'Continue'
    & (Join-Path $mavenFlujos 'bin/mvn.cmd') '-B' '-ntp' '-Dmaven.repo.local=../../.qa/maven-repository' ('-Dtest=' + $Pruebas) test
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de flujos.' }
} finally { Pop-Location }
