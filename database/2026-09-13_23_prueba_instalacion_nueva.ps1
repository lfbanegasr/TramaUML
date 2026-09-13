$ErrorActionPreference='Stop'
$dockerExe='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$identificador=[guid]::NewGuid().ToString('N')
$nombre='uml-correcciones-20260913-init-'+$identificador.Substring(0,8)
$archivos=[ordered]@{
    '2026-09-12_00_cuenta_aplicacion.sql'='00_cuenta.sql'
    '2026-09-12_16_esquema_espanol.sql'='01_esquema.sql'
    '2026-09-12_17_catalogos_espanol.sql'='02_catalogos.sql'
    '2026-09-13_18_correcciones_desarrollo.sql'='03_correcciones.sql'
    '2026-09-12_09_credencial_runtime.sh'='09_credencial_runtime.sh'
    '2026-09-13_19_pruebas_regresion_espanol.sql'='19_regresion.sql'
    '2026-09-13_21_pruebas_correcciones.sql'='21_correcciones.sql'
}
$argumentos=@('run','--name',$nombre,'--label',('codex.prueba='+$identificador),'--network','none',
    '--tmpfs','/var/lib/postgresql/data','-e','POSTGRES_HOST_AUTH_METHOD=trust','-e','POSTGRES_USER=uml_admin',
    '-e','POSTGRES_DB=uml_validacion','-e','UML_APP_PASSWORD=TEST_ONLY_ISOLATED_NOT_A_REAL_PASSWORD')
foreach($archivo in $archivos.GetEnumerator()) {
    $ruta=(Get-Item -LiteralPath (Join-Path $PSScriptRoot $archivo.Key)).FullName
    $argumentos+=@('--mount',('type=bind,source='+$ruta+',target=/docker-entrypoint-initdb.d/'+$archivo.Value+',readonly'))
}
$argumentos+=@('-d','postgres:17-alpine')
& $dockerExe @argumentos | Out-Null
if($LASTEXITCODE -ne 0) { throw 'No se pudo crear la instancia nueva aislada' }
try {
    $limite=(Get-Date).AddSeconds(60)
    do {
        $estado=(& $dockerExe inspect $nombre | ConvertFrom-Json)[0]
        $logs=(& $dockerExe logs $nombre 2>&1 | Out-String)
        if(-not $estado.State.Running) { throw $logs }
        if($logs -match 'PostgreSQL init process complete; ready for start up') {
            & $dockerExe exec $nombre pg_isready -U uml_admin *> $null
            if($LASTEXITCODE -eq 0) { break }
        }
        Start-Sleep -Milliseconds 400
    } while((Get-Date) -lt $limite)
    if($logs -notmatch 'PostgreSQL init process complete; ready for start up' -or $LASTEXITCODE -ne 0) {
        throw ('La inicializacion no finalizo en 60 segundos: '+$logs)
    }
    if($logs -notmatch '\(51 rows\)' -or $logs -notmatch '\(50 rows\)') {
        throw 'No se encontraron ambas suites aprobadas en la inicializacion'
    }
    $consulta=@'
SELECT count(*) FROM pg_tables WHERE schemaname='uml_colaborativo';
SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
WHERE c.relnamespace='uml_colaborativo'::regnamespace AND NOT t.tgisinternal;
SELECT rolcanlogin FROM pg_roles WHERE rolname='uml_app';
'@
    $salida=& $dockerExe exec $nombre psql -X -U uml_admin -d uml_validacion -v ON_ERROR_STOP=1 -At -c $consulta 2>&1
    if($LASTEXITCODE -ne 0 -or ($salida -join ',') -ne '48,88,t') { throw ('Metadatos/credencial nuevos incorrectos: '+($salida | Out-String)) }
    Write-Output 'INSTALACION NUEVA AUTOMATICA: 48 tablas, 88 triggers, credencial runtime asignada con valor ficticio; suites 51/51 y 50/50 aprobadas.'
} finally {
    $estado=(& $dockerExe inspect $nombre | ConvertFrom-Json)[0]
    if($estado.Config.Labels.'codex.prueba' -eq $identificador -and $estado.HostConfig.NetworkMode -eq 'none' -and
        $estado.HostConfig.Tmpfs.PSObject.Properties.Name -contains '/var/lib/postgresql/data') {
        & $dockerExe rm -f $nombre | Out-Null
        Write-Output 'Se retiro solo la instancia nueva de prueba; el contenedor/volumen originales se conservan.'
    }
}
