@echo off
REM Пересборка самодостаточного .exe (с вшитой JRE) в dist\PoE2RuneChecker\
cd /d "%~dp0"

echo [1/4] Maven package (fat-jar)...
call mvn -q clean package || goto :err

echo [2/4] Staging...
if exist stage rmdir /s /q stage
mkdir stage
copy /y target\poe2-rune-checker-0.1.0.jar stage\ >nul

echo [3/4] jpackage app-image...
if exist dist rmdir /s /q dist
jpackage --type app-image --name PoE2RuneChecker ^
  --input stage --main-jar poe2-rune-checker-0.1.0.jar ^
  --main-class com.poe2runechecker.Launcher --icon app.ico ^
  --module-path tools\javafx-jmods-23.0.1 ^
  --add-modules javafx.controls,javafx.graphics,javafx.base,java.net.http,java.desktop,java.prefs,java.sql,java.logging,jdk.unsupported,jdk.crypto.ec ^
  --dest dist --java-options "-Dfile.encoding=UTF-8" || goto :err

echo [4/4] Copy tessdata...
xcopy /e /i /y tessdata dist\PoE2RuneChecker\tessdata >nul

echo.
echo DONE: dist\PoE2RuneChecker\PoE2RuneChecker.exe
goto :eof

:err
echo BUILD FAILED
exit /b 1
