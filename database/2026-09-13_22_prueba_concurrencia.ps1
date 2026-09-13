param([string]$Contenedor,[string]$Base='uml_validacion')
$ErrorActionPreference='Stop'
$dockerExe='C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$estado=(& $dockerExe inspect $Contenedor | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or $Contenedor -notlike 'uml-correcciones-20260913-*' -or
    -not $estado.Config.Labels.'codex.prueba' -or $estado.HostConfig.NetworkMode -ne 'none' -or
    $estado.HostConfig.Tmpfs.PSObject.Properties.Name -notcontains '/var/lib/postgresql/data') {
    throw 'Esta prueba solo se permite en la copia temporal identificada, sin red y con tmpfs'
}
$clave=[guid]::NewGuid().ToString('N')
$actor=[guid]::NewGuid().ToString()
$raizProyecto=Split-Path -Parent $PSScriptRoot
$rutaLogs=Join-Path $raizProyecto ('.qa/2026-09-13_concurrencia_'+$clave)
New-Item -ItemType Directory -Path $rutaLogs | Out-Null
function EjecutarSql {
    param([string]$Consulta)
    $salida=& $dockerExe exec $Contenedor psql -X -U postgres -d $Base -v ON_ERROR_STOP=1 -At -c $Consulta 2>&1
    if($LASTEXITCODE -ne 0) { throw ($salida | Out-String) }
    return $salida
}
$preparacion=@'
BEGIN;
INSERT INTO uml_colaborativo.usuario(id,nombre_usuario,correo_electronico,nombre_completo,hash_contrasena)
VALUES('__ACTOR__','carga-__CLAVE__','__CLAVE__@example.invalid','Prueba concurrencia','TEST_ONLY');
SELECT id_modelo FROM uml_colaborativo.crear_proyecto_con_propietario('__ACTOR__','HOT-__CLAVE__','Modelo compartido');
SELECT c.id_modelo FROM generate_series(1,20) n
CROSS JOIN LATERAL uml_colaborativo.crear_proyecto_con_propietario('__ACTOR__','LOAD-__CLAVE__-'||n,'Carga '||n) c;
COMMIT;
'@
$salida=EjecutarSql ($preparacion.Replace('__ACTOR__',$actor).Replace('__CLAVE__',$clave))
$modelos=@($salida | Where-Object { $_ -match '^[0-9a-f-]{36}$' })
if($modelos.Count -ne 21) { throw 'Se esperaban 21 modelos de prueba' }
function EjecutarLote {
    param([string[]]$Consultas,[string]$Etiqueta)
    $procesos=@()
    $reloj=[System.Diagnostics.Stopwatch]::StartNew()
    try {
        for($indice=0;$indice -lt $Consultas.Count;$indice++) {
            $rutaSalida=Join-Path $rutaLogs ($Etiqueta+'_'+$indice+'.out')
            $rutaError=Join-Path $rutaLogs ($Etiqueta+'_'+$indice+'.err')
            $argumentos=@('exec',$Contenedor,'psql','-X','-U','postgres','-d',$Base,'-v','ON_ERROR_STOP=1',
                '-v','VERBOSITY=verbose','-c',('"'+$Consultas[$indice].Replace('"','\"')+'"'))
            $parametrosProceso=@{ FilePath=$dockerExe; ArgumentList=$argumentos; PassThru=$true;
                WindowStyle='Hidden'; RedirectStandardOutput=$rutaSalida; RedirectStandardError=$rutaError }
            $proceso=Start-Process @parametrosProceso
            $procesos+= [pscustomobject]@{ Proceso=$proceso; Salida=$rutaSalida; Error=$rutaError }
        }
        $resultados=@()
        foreach($registro in $procesos) {
            $restante=[Math]::Max(1,45000-[int]$reloj.ElapsedMilliseconds)
            if(-not $registro.Proceso.WaitForExit($restante)) { throw 'Lote de concurrencia excedio 45 segundos' }
            $registro.Proceso.Refresh()
            $resultados+= [pscustomobject]@{ Codigo=$registro.Proceso.ExitCode;
                Error=(Get-Content -LiteralPath $registro.Error -Raw) }
        }
        return [pscustomobject]@{ Resultados=$resultados; Milisegundos=$reloj.ElapsedMilliseconds }
    } finally {
        foreach($registro in $procesos) {
            if(-not $registro.Proceso.HasExited) { Stop-Process -Id $registro.Proceso.Id }
        }
    }
}
$plantillaHot=@'
BEGIN;
SET LOCAL ROLE uml_app;
SELECT uml_colaborativo.iniciar_edicion_modelo('__MODEL__',0,'__ACTOR__');
SELECT pg_sleep(0.15);
INSERT INTO uml_colaborativo.clase_uml(id_modelo,nombre) VALUES('__MODEL__','Clase__NUM__');
SELECT uml_colaborativo.guardar_version_modelo('__MODEL__',1,'__ACTOR__','Prueba simultanea');
COMMIT;
'@
$consultasHot=@(for($indice=0;$indice -lt 20;$indice++) {
    $plantillaHot.Replace('__MODEL__',$modelos[0]).Replace('__ACTOR__',$actor).Replace('__NUM__',[string]$indice)
})
$loteHot=EjecutarLote $consultasHot 'compartido'
$confirmados=@($loteHot.Resultados | Where-Object Codigo -eq 0)
$conflictos=@($loteHot.Resultados | Where-Object { $_.Codigo -ne 0 -and $_.Error -match '40001' })
if($confirmados.Count -ne 1 -or $conflictos.Count -ne 19) { throw 'Modelo compartido: se esperaban 1 confirmacion y 19 conflictos 40001' }
$resultadoHot=EjecutarSql ("SELECT m.revision_actual,(SELECT count(*) FROM uml_colaborativo.clase_uml WHERE id_modelo=m.id),"+
    "(SELECT count(*) FROM uml_colaborativo.version_modelo_uml WHERE id_modelo=m.id) FROM uml_colaborativo.modelo_uml m WHERE id='"+$modelos[0]+"'")
if(($resultadoHot | Select-Object -Last 1) -ne '1|1|1') { throw 'El modelo compartido no termino con una revision, clase y version' }
Write-Output ('CONCURRENCIA: 20 intentos sobre revision 0, 1 confirma y 19 reciben 40001. Tiempo lote: '+$loteHot.Milisegundos+' ms.')

$plantillaCarga=@'
BEGIN;
SET LOCAL ROLE uml_app;
DO $$
DECLARE base_rev bigint; reserved bigint; created_class uuid;
BEGIN
    SELECT revision_actual INTO base_rev FROM uml_colaborativo.modelo_uml WHERE id='__MODEL__';
    reserved:=uml_colaborativo.iniciar_edicion_modelo('__MODEL__',base_rev,'__ACTOR__');
    INSERT INTO uml_colaborativo.clase_uml(id_modelo,nombre) VALUES('__MODEL__','Clase__NUM__') RETURNING id INTO created_class;
    INSERT INTO uml_colaborativo.cambio_modelo(id_modelo,id_usuario_actor,id_cambio_cliente,revision_base,revision_aplicada,
        tipo_operacion,tipo_entidad,id_entidad,parche,estado,aplicado_en)
    VALUES('__MODEL__','__ACTOR__',gen_random_uuid(),base_rev,reserved,'CREAR','CLASE_UML',created_class,'{}','APLICADO',clock_timestamp());
    PERFORM uml_colaborativo.guardar_version_modelo('__MODEL__',reserved,'__ACTOR__','Prueba carga SQL');
END;
$$;
COMMIT;
'@
$tiempoCarga=0
for($lote=0;$lote -lt 5;$lote++) {
    $consultas=@(for($indice=0;$indice -lt 20;$indice++) {
        $plantillaCarga.Replace('__MODEL__',$modelos[$indice+1]).Replace('__ACTOR__',$actor).Replace('__NUM__',[string]($lote*20+$indice))
    })
    $resultadoLote=EjecutarLote $consultas ('carga'+$lote)
    if(@($resultadoLote.Resultados | Where-Object Codigo -ne 0).Count -ne 0) {
        throw ('Carga SQL fallo: '+(($resultadoLote.Resultados | Where-Object Codigo -ne 0 | Select-Object -First 1).Error))
    }
    $tiempoCarga+=$resultadoLote.Milisegundos
}
$idsModelos=($modelos | Select-Object -Skip 1 | ForEach-Object { "'"+$_+"'" }) -join ','
$verificacion=@"
SELECT count(*) FROM uml_colaborativo.modelo_uml WHERE id IN($idsModelos) AND revision_actual=5;
SELECT count(*) FROM uml_colaborativo.clase_uml WHERE id_modelo IN($idsModelos);
SELECT count(*) FROM uml_colaborativo.version_modelo_uml WHERE id_modelo IN($idsModelos);
SELECT count(*) FROM uml_colaborativo.cambio_modelo WHERE id_modelo IN($idsModelos) AND estado='APLICADO';
"@
$conteos=@(EjecutarSql $verificacion | Where-Object { $_ -match '^\d+$' })
if(($conteos -join ',') -ne '20,100,100,100') { throw 'Los 100 cambios no coinciden con sus clases/versiones/historial' }
Write-Output ('CARGA SQL: 100 cambios confirmados, 20 conexiones simultaneas por lote, 20 modelos. '+$tiempoCarga+' ms, incluido arranque de procesos Docker/psql.')
Write-Output ('No es un benchmark de React/Spring/WebSocket ni una certificacion de 100 ingenieros. Logs privados de prueba: '+$rutaLogs)
