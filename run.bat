@echo off
REM Запуск PoE2 Rune Checker (dev-режим).
REM Maven сам подтянет JavaFX/Tesseract при первом запуске.
cd /d "%~dp0"
echo Starting PoE2 Rune Checker...
echo Open the "Runeshape Combinations" window in game to see the overlay.
echo Press Ctrl+C in this console to stop.
mvn -q javafx:run
pause
