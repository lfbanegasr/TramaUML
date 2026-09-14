param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$Puerto = 55433,
    [string]$Maven = 'mvn',
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SoloPreparar
)
$ErrorActionPreference = 'Stop'
$implementationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$clusterRoot = Join-Path $implementationRoot '.qa\postgres'
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
$tcpProbe = [Net.Sockets.TcpClient]::new()
try { $portAvailable = $tcpProbe.ConnectAsync('127.0.0.1',$Puerto).Wait(1500) -and $tcpProbe.Connected }
catch { $portAvailable = $false }
finally { $tcpProbe.Dispose() }
if (-not $portAvailable) {
    $pgProcess = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
        '-D','data','-l','server.log','-o',('"-p '+$Puerto+' -h 127.0.0.1"'),'start','-w','-t','30'
    ) -WorkingDirectory $clusterRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $clusterRoot 'inicio.stdout.log') -RedirectStandardError (Join-Path $clusterRoot 'inicio.stderr.log')
    if (-not $pgProcess.WaitForExit(40000) -or $pgProcess.ExitCode -ne 0) { throw 'No inició PostgreSQL aislado; revisa .qa/postgres/server.log.' }
}
$actualCluster = & (Join-Path $PostgresBin 'psql.exe') -X -At -h 127.0.0.1 -p $Puerto -U uml_test_owner -d postgres -c 'show data_directory'
if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($actualCluster.Trim()) -ne [IO.Path]::GetFullPath((Join-Path $clusterRoot 'data'))) {
    throw 'El servidor no pertenece al clúster esperado. No se creó ni modificó una base.'
}
$testDatabase = 'uml_collab_test_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
Invoke-Pg 'createdb.exe' @('-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner',$testDatabase)
$installationLog = Join-Path $implementationRoot ('.qa\'+$testDatabase+'_instalacion.log')
foreach ($sqlSource in @('2026-09-12_00_cuenta_aplicacion.sql','2026-09-12_16_esquema_espanol.sql','2026-09-12_17_catalogos_espanol.sql','2026-09-13_18_correcciones_desarrollo.sql')) {
    & (Join-Path $PostgresBin 'psql.exe') -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $Puerto -U uml_test_owner -d $testDatabase -f (Join-Path $implementationRoot ('database\'+$sqlSource)) *>> $installationLog
    if ($LASTEXITCODE -ne 0) { throw "Falló la instalación de $sqlSource; revisa $installationLog." }
}
$env:UML_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Puerto/$testDatabase" + '?currentSchema=uml_colaborativo,public'
Invoke-Pg 'psql.exe' @('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Puerto",'-U','uml_test_owner','-d',$testDatabase,'-c','ALTER ROLE uml_app LOGIN')
Write-Output "Base ficticia preparada: $testDatabase (127.0.0.1:$Puerto). Se conserva para inspección."
if (-not $SoloPreparar) {
    Push-Location (Join-Path $implementationRoot 'core\backend')
    try {
        & $Maven '-Dmaven.repo.local=../../.qa/maven-repository' test
        if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del núcleo.' }
    } finally { Pop-Location }
}
