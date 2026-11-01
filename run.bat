@echo off
rem StudyPulse - starts dist\StudyPulse-all.jar (Java 8 or newer must be installed).
rem The data folder (studypulse.db, error.log) is created next to the jar.
cd /d "%~dp0"
java -jar StudyPulse-all.jar
if errorlevel 1 (
    echo.
    echo StudyPulse could not start. Is Java 8 or newer installed? Try: java -version
    pause
)
