param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$HttpLocal,
    [switch]$InicializarAdministrador,
    [string]$Usuario,
    [string]$Correo,
    [string]$Nombre
)
$ErrorActionPreference='Stop'
$jar=Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'backend\target\plataforma-0.1.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $jar)) {throw 'Primero compila y empaqueta core/backend.'}
$java=if($JavaHome) {Join-Path $JavaHome 'bin\java.exe'} else {(Get-Command java).Source}
$variables=@('UML_DB_PASSWORD','UML_COOKIE_SECURE','UML_BOOTSTRAP_ENABLED','UML_ADMIN_USERNAME','UML_ADMIN_EMAIL','UML_ADMIN_NAME','UML_ADMIN_PASSWORD')
$originalEnvironment=@{}
foreach($variable in $variables) {$originalEnvironment[$variable]=[Environment]::GetEnvironmentVariable($variable,'Process')}
function Read-Secret([string]$Mensaje) {
    $secureValue=Read-Host $Mensaje -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secureValue.Dispose()}
}
try {
    if(-not $env:UML_DB_PASSWORD) {$env:UML_DB_PASSWORD=Read-Secret 'Clave PostgreSQL vigente de uml_app (entrada oculta)'}
    if(-not $env:UML_DB_PASSWORD) {throw 'La clave PostgreSQL es obligatoria.'}
    $env:UML_COOKIE_SECURE=if($HttpLocal) {'false'} else {'true'}
    $env:UML_BOOTSTRAP_ENABLED=if($InicializarAdministrador) {'true'} else {'false'}
    if($InicializarAdministrador) {
        if(-not $Usuario) {$Usuario=Read-Host 'Nombre de usuario del primer administrador'}
        if(-not $Correo) {$Correo=Read-Host 'Correo del primer administrador'}
        if(-not $Nombre) {$Nombre=Read-Host 'Nombre completo del primer administrador'}
        $env:UML_ADMIN_USERNAME=$Usuario;$env:UML_ADMIN_EMAIL=$Correo;$env:UML_ADMIN_NAME=$Nombre
        $env:UML_ADMIN_PASSWORD=Read-Secret 'Nueva clave de acceso de la plataforma (12 caracteres como mínimo; hasta 72 bytes UTF-8)'
    }
    & $java -jar $jar
    if($LASTEXITCODE -ne 0) {throw 'El backend no completó la ejecución; revisa el error informado sin publicar credenciales.'}
} finally {
    foreach($variable in $variables) {[Environment]::SetEnvironmentVariable($variable,$originalEnvironment[$variable],'Process')}
}
