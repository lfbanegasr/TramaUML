param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$raiz=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$maven=Join-Path $raiz '.qa\tools\apache-maven-3.9.9\bin\mvn.cmd'
if(-not (Test-Path -LiteralPath $maven)) { throw 'Falta Maven 3.9.9 en .qa/tools/apache-maven-3.9.9.' }
if(-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    $JavaHome=''
    foreach($candidato in @('C:\Program Files\Android\Android Studio\jbr','D:\Android Studio Instalacion 2025\jbr','D:\Program Files\Android\Android Studio\jbr')) {
        if(Test-Path -LiteralPath (Join-Path $candidato 'bin\java.exe')) { $JavaHome=$candidato; break }
    }
}
if(-not $JavaHome) { throw 'Indica un JDK 21 con -JavaHome.' }
$javaAnterior=$env:JAVA_HOME
try {
    $env:JAVA_HOME=$JavaHome
    $pom=Join-Path $PSScriptRoot 'pruebas\fixtures\2026-09-21_compilador_backend_pom.xml'
    $cache=Join-Path $raiz '.qa\maven-repository'
    & $maven '-B' '-ntp' '-f' $pom "-Dmaven.repo.local=$cache" '-Dmaven.compiler.proc=none' 'org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile'
    if($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar las dependencias del compilador.' }
    Write-Output 'Compilador preparado. Las validaciones de la aplicación se ejecutan offline y no arrancan PostgreSQL.'
} finally { $env:JAVA_HOME=$javaAnterior }
