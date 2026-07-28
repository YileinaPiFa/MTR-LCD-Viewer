@echo off
setlocal
cd /d "%~dp0"
set CP=build
for %%f in (lib\*.jar) do call set CP=%%CP%%;%%f

echo === COMPILE ===
dir /s /b src\*.java > "%TEMP%\lcdsrc.txt"
javac -encoding UTF-8 -d build -cp "%CP%" @"%TEMP%\lcdsrc.txt"
if errorlevel 1 goto :eof

echo === RUN ===
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "%CP%" lcdviewer.SmokeTest %*
