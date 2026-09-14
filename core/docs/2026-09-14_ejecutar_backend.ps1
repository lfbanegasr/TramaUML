param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$VerificarEntorno,
    [switch]$HttpLocal,
    [switch]$InicializarAdministrador,
    [string]$Usuario,
    [string]$Correo,
    [string]$Nombre
)
$ErrorActionPreference='Stop'
$jar=Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'backend\target\plataforma-0.1.0-SNAPSHOT.jar'
if(-not (Test-Path -LiteralPath $jar)) {throw 'Primero compila y empaqueta core/backend.'}
$java=$null
if($JavaHome) {
    $candidato=Join-Path $JavaHome 'bin\java.exe'
    if(Test-Path -LiteralPath $candidato -PathType Leaf) {$java=$candidato}
    elseif($PSBoundParameters.ContainsKey('JavaHome')) {throw 'La ruta indicada en -JavaHome no contiene bin\java.exe.'}
    else {Write-Warning 'JAVA_HOME apunta a una instalación que no existe. Se buscará otra instalación local.'}
}
if(-not $java) {
    $javaEnPath=Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if($javaEnPath) {$java=$javaEnPath.Source}
    elseif(Test-Path -LiteralPath 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -PathType Leaf) {$java='C:\Program Files\Android\Android Studio\jbr\bin\java.exe'}
}
if(-not $java) {throw 'No se encontró Java 21. Usa -JavaHome con la carpeta de un JDK 21 instalado.'}
$versionJava=@(& $java --version)
if($LASTEXITCODE -ne 0 -or -not ($versionJava -match '^(openjdk|java)\s+21([.\s]|$)')) {throw 'Este núcleo requiere Java 21. Usa -JavaHome con una instalación de esa versión.'}
Write-Host ('Java verificado: ' + $java)
if($VerificarEntorno) {Write-Host 'Java 21 y el archivo del backend están disponibles. No se solicitó ninguna contraseña ni se inició la aplicación.';return}
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
