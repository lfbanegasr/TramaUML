param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Aplicar,
    [switch]$InstalarFormateador
)
$ErrorActionPreference = 'Stop'
$raiz = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$nombre = 'google-java-format-1.36.1-all-deps.jar'
$jar = Join-Path $raiz ('.qa\tools\' + $nombre)
$hashEsperado = '25B400F003089D23CC5320CDAF1A16CABEE19B8AA3434D0FF021B3D9F42154B4'
if (-not (Test-Path -LiteralPath $jar)) {
    if (-not $InstalarFormateador) { throw 'Falta el formateador. Ejecuta este comando con -InstalarFormateador para descargarlo de la publicación oficial.' }
    New-Item -ItemType Directory -Path (Split-Path -Parent $jar) -Force | Out-Null
    Invoke-WebRequest -Uri ('https://github.com/google/google-java-format/releases/download/v1.36.1/' + $nombre) -OutFile $jar
}
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $hashEsperado) { throw 'El formateador no coincide con el SHA-256 verificado.' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { '' }
if (-not $java -or -not (Test-Path -LiteralPath $java)) {
    $candidato = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $java = if ($candidato) { $candidato.Source } else { 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' }
}
if (-not (Test-Path -LiteralPath $java)) { throw 'Indica un JDK 21 con -JavaHome.' }
Push-Location $raiz
try {
    $archivos = @(rg --files core/backend/src -g '*.java')
    if ($LASTEXITCODE -ne 0 -or $archivos.Count -eq 0) { throw 'No se encontraron fuentes Java dentro del núcleo.' }
    $opciones = if ($Aplicar) { @('--aosp', '--replace') } else { @('--aosp', '--dry-run', '--set-exit-if-changed') }
    & $java -jar $jar @opciones @archivos
    if ($LASTEXITCODE -ne 0) { throw 'El formato Java requiere revisión.' }
} finally { Pop-Location }
