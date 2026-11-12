@echo off
set GIT_EXE=C:\Users\shrey\.gemini\antigravity\scratch\mingit\cmd\git.exe
cd /d "C:\Users\shrey\.gemini\antigravity\scratch\ClawDroid"

echo ========================================================
echo Pushing ClawDroid to https://github.com/shreyastouih-ctrl/ClawDroid.git
echo ========================================================
echo.
"%GIT_EXE%" push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo SUCCESS: ClawDroid is live on GitHub!
    echo Visit: https://github.com/shreyastouih-ctrl/ClawDroid
    echo ========================================================
) else (
    echo.
    echo Push paused. If prompted, please enter your GitHub Personal Access Token or credentials.
)
echo.
pause
