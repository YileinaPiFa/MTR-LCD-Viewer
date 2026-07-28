@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo   MTR LCD Viewer - Build
echo ============================================

taskkill /f /im MTR-LCD-Viewer.exe >nul 2>&1
taskkill /f /im javaw.exe >nul 2>&1

set CP=lib\*

echo [1/4] Compiling...
if exist build rmdir /s /q build
mkdir build
dir /s /b src\*.java > "%TEMP%\lcd_src.txt"
javac -encoding UTF-8 -nowarn -d build -cp "%CP%" @"%TEMP%\lcd_src.txt"
if errorlevel 1 (
  echo   COMPILE FAILED
  exit /b 1
)

echo [1.5/4] Running Full 79 LCD Unit Tests...
javac -encoding UTF-8 -d build -cp "build;lib\*" Full79Test.java
java -Dfile.encoding=UTF-8 -cp "build;lib\*" Full79Test
if errorlevel 1 (
    echo [ERROR] Full 79 LCD Tests Failed! Aborting build.
    exit /b 1
)

echo [2/4] Preparing Staging and Fat JAR...
if exist staging rmdir /s /q staging
mkdir staging
mkdir staging\fat

xcopy /e /q /y build\* staging\fat\ >nul
powershell -Command "Get-ChildItem lib\*.jar | ForEach-Object { Expand-Archive -Path $_.FullName -DestinationPath staging\fat -Force }" >nul 2>&1
if exist staging\fat\META-INF rmdir /s /q staging\fat\META-INF

jar --create --file staging\lcdviewer.jar --main-class lcdviewer.App -C build .
if errorlevel 1 exit /b 1
copy /y lib\*.jar staging\ >nul

if not exist dist mkdir dist
if exist dist\MTR-LCD-Viewer.jar del /f /q dist\MTR-LCD-Viewer.jar >nul 2>&1
jar --create --file dist\MTR-LCD-Viewer.jar --main-class lcdviewer.App -C staging\fat .

echo [3/4] Building runtime image...
if exist dist\MTR-LCD-Viewer rmdir /s /q dist\MTR-LCD-Viewer

jpackage --type app-image --name "MTR-LCD-Viewer" --app-version 1.0.0 --vendor "LCD Tools" --description "MTR LCD offline inspector" --input staging --main-jar lcdviewer.jar --main-class lcdviewer.App --dest dist --add-modules java.base,java.desktop,java.scripting,java.logging,java.naming,jdk.dynalink,jdk.unsupported,java.management --java-options "-Dfile.encoding=UTF-8" --java-options "-Dstdout.encoding=UTF-8" --java-options "-Dstderr.encoding=UTF-8" --java-options "-Dsun.java2d.uiScale.enabled=true" --java-options "-Xmx2g"

if errorlevel 1 (
  echo   JPACKAGE APP-IMAGE FAILED
  exit /b 1
)

echo [4/4] Done.
echo.
echo Standalone Fat JAR:      %~dp0dist\MTR-LCD-Viewer.jar
echo Portable App Directory:  %~dp0dist\MTR-LCD-Viewer\ (Run MTR-LCD-Viewer.exe here)
