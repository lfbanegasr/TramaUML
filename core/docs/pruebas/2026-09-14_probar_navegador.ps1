param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$PuertoBackend = 8181,
    [int]$PuertoWeb = 5174
)
$ErrorActionPreference='Stop'
$implementationRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$coreRoot=Join-Path $implementationRoot 'core'
$jar=Join-Path $coreRoot 'backend\target\plataforma-0.12.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $jar)) {throw 'Primero empaqueta el backend.'}
if($PuertoBackend -eq 8080 -or $PuertoWeb -eq 5173 -or $PuertoBackend -eq $PuertoWeb) {throw 'Utiliza puertos exclusivos para pruebas.'}
$envNames=@('UML_DB_URL','UML_DB_USER','UML_DB_PASSWORD','UML_COOKIE_SECURE','UML_BOOTSTRAP_ENABLED','UML_ADMIN_USERNAME','UML_ADMIN_EMAIL','UML_ADMIN_NAME','UML_ADMIN_PASSWORD','UML_PORT','UML_API_PROXY_TARGET','UML_E2E_URL','UML_E2E_USERNAME','UML_E2E_PASSWORD','UML_TEST_DB_URL','UML_TEST_DB_PASSWORD','UML_SERVICIOS_MASTER_KEY','UML_SERVICIOS_KEY_VERSION','PGPASSWORD','PGCONNECT_TIMEOUT','JAVA_HOME')
$previousEnvironment=@{}
foreach($envName in $envNames) {$previousEnvironment[$envName]=[Environment]::GetEnvironmentVariable($envName,'Process')}
$backendProcess=$null;$webProcess=$null
function Wait-Http([string]$Url) {
    for($attempt=0;$attempt -lt 25;$attempt++) {
        try {if((Invoke-WebRequest -Uri $Url -TimeoutSec 1 -UseBasicParsing).StatusCode -eq 200) {return}}
        catch {}
        Start-Sleep -Milliseconds 300
    }
    throw "El servicio no inició: $Url. Revisa los registros en .qa."
}
try {
    & (Join-Path $PSScriptRoot '2026-09-14_probar_nucleo.ps1') -PostgresBin $PostgresBin -JavaHome $JavaHome -SoloPreparar
    $env:UML_DB_URL=$env:UML_TEST_DB_URL
    $env:UML_DB_USER='uml_app';$env:UML_DB_PASSWORD=$env:UML_TEST_DB_PASSWORD
    $env:UML_COOKIE_SECURE='false';$env:UML_PORT="$PuertoBackend"
    $env:UML_ADMIN_USERNAME='pruebas_'+[Guid]::NewGuid().ToString('N').Substring(0,8)
    $env:UML_ADMIN_EMAIL=$env:UML_ADMIN_USERNAME+'@example.test';$env:UML_ADMIN_NAME='Administrador ficticio'
    $env:UML_ADMIN_PASSWORD=[Guid]::NewGuid().ToString()
    $env:UML_E2E_USERNAME=$env:UML_ADMIN_USERNAME;$env:UML_E2E_PASSWORD=$env:UML_ADMIN_PASSWORD
    $env:UML_SERVICIOS_MASTER_KEY='MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='
    $env:UML_SERVICIOS_KEY_VERSION='pruebas-e2e-v1'
    $env:UML_BOOTSTRAP_ENABLED='true'
    $java=$null
    if($JavaHome -and (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { $java = Join-Path $JavaHome 'bin\java.exe' }
    elseif($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe' }
    else {
        $candidato = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if($candidato) { $java = $candidato.Source }
        else {
            foreach ($ubicacionJava in @(
                'C:\Program Files\Android\Android Studio\jbr\bin\java.exe',
                'D:\Android Studio Instalacion 2025\jbr\bin\java.exe',
                'D:\Program Files\Android\Android Studio\jbr\bin\java.exe'
            )) {
                if (Test-Path -LiteralPath $ubicacionJava) { $java = $ubicacionJava; break }
            }
        }
    }
    if(-not $java) { throw 'No se encontró Java 21.' }
    $bootstrap=Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $implementationRoot '.qa\2026-09-14_bootstrap_pruebas.log') -RedirectStandardError (Join-Path $implementationRoot '.qa\2026-09-14_bootstrap_pruebas.err.log')
    $bootstrap | Wait-Process -Timeout 30
    if($bootstrap.ExitCode -ne 0) {throw 'Falló el administrador ficticio; revisa el registro de bootstrap.'}
    $env:UML_BOOTSTRAP_ENABLED='false'
    foreach($adminVariable in @('UML_ADMIN_PASSWORD','UML_ADMIN_USERNAME','UML_ADMIN_EMAIL','UML_ADMIN_NAME')) {Remove-Item -LiteralPath ('Env:'+$adminVariable)}
    $backendProcess=Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $implementationRoot '.qa\2026-09-14_backend_navegador.log') -RedirectStandardError (Join-Path $implementationRoot '.qa\2026-09-14_backend_navegador.err.log')
    Wait-Http "http://127.0.0.1:$PuertoBackend/api/acceso/csrf"
    $env:UML_API_PROXY_TARGET="http://127.0.0.1:$PuertoBackend";$env:UML_E2E_URL="http://127.0.0.1:$PuertoWeb"
    $webProcess=Start-Process -FilePath (Get-Command node).Source -ArgumentList @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port',"$PuertoWeb") -WorkingDirectory (Join-Path $coreRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $implementationRoot '.qa\2026-09-14_web_navegador.log') -RedirectStandardError (Join-Path $implementationRoot '.qa\2026-09-14_web_navegador.err.log')
    Wait-Http $env:UML_E2E_URL
    Push-Location (Join-Path $coreRoot 'frontend')
    try {& npm.cmd run test:e2e;if($LASTEXITCODE -ne 0) {throw 'Fallaron las pruebas del navegador.'}}
    finally {Pop-Location}
} finally {
    foreach($ownedProcess in @($backendProcess,$webProcess)) {if($ownedProcess -and -not $ownedProcess.HasExited) {Stop-Process -Id $ownedProcess.Id -Force}}
    foreach($envName in $envNames) {[Environment]::SetEnvironmentVariable($envName,$previousEnvironment[$envName],'Process')}
}
