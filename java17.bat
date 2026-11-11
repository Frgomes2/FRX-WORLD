@echo off
rem ===== FRX-WORLD: coloca o Java 17 na frente do PATH (o Forge 1.20.1 exige Java 17) =====
rem Se o seu JDK 17 estiver em outro lugar, defina FRX_JAVA17 com a pasta dele.
if defined FRX_JAVA17 goto :achou

rem JDK descompactado dentro do projeto (FRX-WORLD\tools\jdk-17...)
for /d %%D in ("%~dp0tools\jdk-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou

for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou
for /d %%D in ("C:\Program Files\Java\jdk-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou
for /d %%D in ("C:\Program Files\Microsoft\jdk-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou
for /d %%D in ("C:\Program Files\Zulu\zulu-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou

for /d %%D in ("C:\Program Files (x86)\Eclipse Adoptium\jdk-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou
for /d %%D in ("%USERPROFILE%\.jdks\*17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jre-17*") do set "FRX_JAVA17=%%~fD"
if defined FRX_JAVA17 goto :achou

echo [ERRO] Nao encontrei o Java 17. Defina a variavel FRX_JAVA17 com a pasta do JDK 17.
exit /b 1

:achou
set "JAVA_HOME=%FRX_JAVA17%"
set "PATH=%FRX_JAVA17%\bin;%PATH%"
echo Usando Java 17 em: %FRX_JAVA17%
exit /b 0
