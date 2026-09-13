param(
    [string]$Contenedor='uml-collab-postgresql-1',
    [string]$Base='uml_collab',
    [string]$Administrador='uml_admin',
    [switch]$Aplicar
)
$ErrorActionPreference='Stop'
$dockerExe='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$raizDatabase=$PSScriptRoot
$fechaPrueba=Get-Date -Format 'yyyy-MM-dd_HHmmss'
$identificadorPrueba=[guid]::NewGuid().ToString('N')
$contenedorPrueba='uml-correcciones-20260913-'+$identificadorPrueba.Substring(0,8)
$rutaRespaldo=Join-Path $raizDatabase ('.privado/'+$fechaPrueba+'_antes_correcciones.dump')
$pruebaCreada=$false
function InvocarDocker {
    param([string[]]$Argumentos)
    $salida=& $dockerExe @Argumentos 2>&1
    if($LASTEXITCODE -ne 0) { throw ($salida | Out-String) }
    return $salida
}
function ObtenerConteos {
    param([string]$NombreContenedor,[string]$NombreBase,[string]$UsuarioBase)
    $consulta=@'
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT jsonb_object_agg(tablename,
    (xpath('//filas/text()',query_to_xml(format('SELECT count(*) AS filas FROM uml_colaborativo.%I',tablename),false,true,'')))[1]::text::bigint
    ORDER BY tablename)
FROM pg_tables WHERE schemaname='uml_colaborativo';
COMMIT;
'@
    $salida=InvocarDocker -Argumentos @('exec',$NombreContenedor,'psql','-X','-U',$UsuarioBase,'-d',$NombreBase,'-v','ON_ERROR_STOP=1','-At','-c',$consulta)
    return ($salida | Where-Object { $_ -match '^\{' } | Select-Object -Last 1)
}
if(-not (Test-Path -LiteralPath $dockerExe)) { throw 'Docker no esta instalado en la ubicacion esperada' }
if($Contenedor -ne 'uml-collab-postgresql-1' -or $Base -ne 'uml_collab') {
    throw 'Este despliegue se limita al contenedor/base identificados del proyecto; revise el script para otra instalacion'
}
if(Test-Path -LiteralPath $rutaRespaldo) { throw 'El respaldo ya existe; no se sobreescribe' }
$estadoReal=(InvocarDocker -Argumentos @('inspect',$Contenedor) | ConvertFrom-Json)[0]
if(-not $estadoReal.State.Running) { throw 'El contenedor original debe estar activo' }
$conteosPrevios=ObtenerConteos $Contenedor $Base $Administrador
Write-Output ('Conteos previos: '+$conteosPrevios)
InvocarDocker -Argumentos @('exec',$Contenedor,'pg_dump','-U',$Administrador,'-d',$Base,'-Fc','-f',('/tmp/'+$identificadorPrueba+'.dump')) | Out-Null
InvocarDocker -Argumentos @('cp',($Contenedor+':/tmp/'+$identificadorPrueba+'.dump'),$rutaRespaldo) | Out-Null
if((Get-Item -LiteralPath $rutaRespaldo).Length -lt 1000) { throw 'Respaldo inesperadamente pequeno' }
Write-Output ('Respaldo privado: '+$rutaRespaldo)
try {
    InvocarDocker -Argumentos @('run','--name',$contenedorPrueba,'--label',('codex.prueba='+$identificadorPrueba),
        '--network','none','--tmpfs','/var/lib/postgresql/data','-e','POSTGRES_HOST_AUTH_METHOD=trust','-d','postgres:17-alpine') | Out-Null
    $pruebaCreada=$true
    $limiteDisponibilidad=(Get-Date).AddSeconds(30)
    do {
        & $dockerExe exec $contenedorPrueba pg_isready -U postgres *> $null
        if($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 250
    } while((Get-Date) -lt $limiteDisponibilidad)
    if($LASTEXITCODE -ne 0) { throw 'PostgreSQL de pruebas no estuvo disponible en 30 segundos' }
    InvocarDocker -Argumentos @('exec',$contenedorPrueba,'createdb','-U','postgres','uml_validacion') | Out-Null
    foreach($archivo in @('2026-09-12_00_cuenta_aplicacion.sql','2026-09-13_18_correcciones_desarrollo.sql',
        '2026-09-13_19_pruebas_regresion_espanol.sql','2026-09-13_21_pruebas_correcciones.sql')) {
        InvocarDocker -Argumentos @('cp',(Join-Path $raizDatabase $archivo),($contenedorPrueba+':/tmp/'+$archivo)) | Out-Null
    }
    InvocarDocker -Argumentos @('cp',$rutaRespaldo,($contenedorPrueba+':/tmp/respaldo.dump')) | Out-Null
    InvocarDocker -Argumentos @('exec',$contenedorPrueba,'psql','-X','-U','postgres','-d','uml_validacion','-v','ON_ERROR_STOP=1',
        '-f','/tmp/2026-09-12_00_cuenta_aplicacion.sql') | Out-Null
    InvocarDocker -Argumentos @('exec',$contenedorPrueba,'pg_restore','--exit-on-error','--no-owner','-U','postgres',
        '-d','uml_validacion','/tmp/respaldo.dump') | Out-Null
    if((ObtenerConteos $contenedorPrueba 'uml_validacion' 'postgres') -ne $conteosPrevios) { throw 'El respaldo restaurado no coincide con los conteos originales' }
    Write-Output 'Respaldo restaurado y conteos verificados en copia aislada.'
    InvocarDocker -Argumentos @('exec',$contenedorPrueba,'psql','-X','-U','postgres','-d','uml_validacion','-v','ON_ERROR_STOP=1',
        '-f','/tmp/2026-09-13_18_correcciones_desarrollo.sql') | Out-Null
    # Reejecucion verifica que la migracion es idempotente.
    InvocarDocker -Argumentos @('exec',$contenedorPrueba,'psql','-X','-U','postgres','-d','uml_validacion','-v','ON_ERROR_STOP=1',
        '-f','/tmp/2026-09-13_18_correcciones_desarrollo.sql') | Out-Null
    foreach($archivo in @('2026-09-13_19_pruebas_regresion_espanol.sql','2026-09-13_21_pruebas_correcciones.sql')) {
        InvocarDocker -Argumentos @('exec',$contenedorPrueba,'psql','-X','-U','postgres','-d','uml_validacion','-v','ON_ERROR_STOP=1',
            '-f',('/tmp/'+$archivo))
    }
    if((ObtenerConteos $contenedorPrueba 'uml_validacion' 'postgres') -ne $conteosPrevios) { throw 'La migracion/pruebas alteraron registros existentes' }
    $scriptCarga=Join-Path $raizDatabase '2026-09-13_22_prueba_concurrencia.ps1'
    if(Test-Path -LiteralPath $scriptCarga) { & $scriptCarga -Contenedor $contenedorPrueba }
    if($Aplicar) {
        # Evitar aplicar si hubo escrituras de negocio durante la validacion.
        if((ObtenerConteos $Contenedor $Base $Administrador) -ne $conteosPrevios) { throw 'La base original cambio durante las pruebas; respalde y reintente en una ventana sin escrituras' }
        InvocarDocker -Argumentos @('cp',(Join-Path $raizDatabase '2026-09-13_18_correcciones_desarrollo.sql'),
            ($Contenedor+':/tmp/2026-09-13_18_correcciones_desarrollo.sql')) | Out-Null
        InvocarDocker -Argumentos @('exec',$Contenedor,'psql','-X','-U',$Administrador,'-d',$Base,'-v','ON_ERROR_STOP=1',
            '-f','/tmp/2026-09-13_18_correcciones_desarrollo.sql') | Out-Null
        if((ObtenerConteos $Contenedor $Base $Administrador) -ne $conteosPrevios) { throw 'Los conteos posteriores difieren; revisar antes de seguir operando' }
        Write-Output 'APLICADO: base original migrada atomicamente; todas las cantidades de registros coinciden.'
    } else { Write-Output 'VALIDADO: no se modifico el esquema original. Use -Aplicar para migrarlo.' }
} finally {
    if($pruebaCreada) {
        $estadoPrueba=(InvocarDocker -Argumentos @('inspect',$contenedorPrueba) | ConvertFrom-Json)[0]
        if($estadoPrueba.Config.Labels.'codex.prueba' -eq $identificadorPrueba -and
            $estadoPrueba.HostConfig.NetworkMode -eq 'none' -and
            $estadoPrueba.HostConfig.Tmpfs.PSObject.Properties.Name -contains '/var/lib/postgresql/data') {
            InvocarDocker -Argumentos @('rm','-f',$contenedorPrueba) | Out-Null
            Write-Output 'Se retiro solo el contenedor temporal de pruebas, sin tocar el contenedor ni el volumen originales.'
        } else { Write-Warning 'No se retiro la instancia temporal: no coincide la identidad de seguridad' }
    }
}
