param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$implementationRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectXml = [xml][IO.File]::ReadAllText((Join-Path $implementationRoot 'core/backend/pom.xml'))
$jarPath = Join-Path $implementationRoot ('core/backend/target/plataforma-' + $projectXml.project.version + '.jar')
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { throw 'Primero empaqueta la versión actual del backend con Maven.' }
$javaPath = if ($JavaHome -and (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    Join-Path $JavaHome 'bin/java.exe'
} else { (Get-Command java.exe -ErrorAction Stop).Source }
function Read-PrivateValue([string]$PromptText) {
    $secureValue = Read-Host $PromptText -AsSecureString
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer); $secureValue.Dispose() }
}
$variableNames = @('UML_DB_URL','UML_DB_USER','UML_DB_PASSWORD','UML_BOOTSTRAP_ENABLED',
    'UML_ADMIN_USERNAME','UML_ADMIN_EMAIL','UML_ADMIN_NAME','UML_ADMIN_PASSWORD',
    'UML_IA_WORKER','UML_COMPILACION_WORKER','SPRING_MAIN_WEB_APPLICATION_TYPE','SPRING_PROFILES_ACTIVE')
$previousValues = @{}
foreach ($variableName in $variableNames) { $previousValues[$variableName] = [Environment]::GetEnvironmentVariable($variableName, 'Process') }
try {
    $dbHost = Read-Host 'Host directo de la base NUEVA de Neon (sin usuario ni contraseña)'
    $dbName = Read-Host 'Nombre de la base nueva'
    if ($dbHost -notmatch '^[a-zA-Z0-9.-]+\.neon\.tech$' -or $dbHost -match '-pooler\.' -or $dbName -notmatch '^[a-zA-Z0-9_]+$') {
        throw 'Se requiere host directo *.neon.tech y nombre de base sin caracteres especiales.'
    }
    $env:UML_DB_URL = "jdbc:postgresql://$dbHost/$($dbName)?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema=uml_colaborativo,public"
    $env:UML_DB_USER = 'uml_app'
    $env:UML_DB_PASSWORD = Read-PrivateValue 'Contraseña de uml_app en Neon'
    $env:UML_ADMIN_USERNAME = Read-Host 'Usuario del administrador'
    $env:UML_ADMIN_EMAIL = Read-Host 'Correo del administrador'
    $env:UML_ADMIN_NAME = Read-Host 'Nombre del administrador'
    $env:UML_ADMIN_PASSWORD = Read-PrivateValue 'Contraseña del administrador (mínimo 12 caracteres)'
    $env:UML_BOOTSTRAP_ENABLED = 'true'
    $env:UML_IA_WORKER = 'false'
    $env:UML_COMPILACION_WORKER = 'false'
    $env:SPRING_MAIN_WEB_APPLICATION_TYPE = 'none'
    $env:SPRING_PROFILES_ACTIVE = ''
    & $javaPath -jar $jarPath
    if ($LASTEXITCODE -ne 0) { throw 'No se completó la inicialización; revise el mensaje del backend.' }
} finally {
    foreach ($variableName in $variableNames) { [Environment]::SetEnvironmentVariable($variableName, $previousValues[$variableName], 'Process') }
}
