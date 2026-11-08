@echo off
REM LFS Oasis Adopted Capsule Launcher — BoxBoy Zombies (Windows x86_64 Desktop & Server)
REM Supports Godot 4.7.2 Native Arena (--godot), Headless Konsole TCP Server (--server),
REM and Sandboxed Web Capsule Mode (--web). Zero hardcoded user paths (RATCHET-5).

setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "PROJECT_ROOT=%%~fI"
set "GODOT_PROJECT_DIR=%PROJECT_ROOT%\godot"
set "WEB_ENTRY_HTML=%PROJECT_ROOT%\index.html"

set "MODE=auto"
if defined BOXBOY_KONSOLE_PORT (set "KONSOLE_PORT=%BOXBOY_KONSOLE_PORT%") else (set "KONSOLE_PORT=48900")
if defined BOXBOY_KONSOLE_TICK_HZ (set "KONSOLE_TICK_HZ=%BOXBOY_KONSOLE_TICK_HZ%") else (set "KONSOLE_TICK_HZ=20")
set "ASCII_PREVIEW=0"
if defined BOXBOY_WEB_PORT (set "WEB_PORT=%BOXBOY_WEB_PORT%") else (set "WEB_PORT=18080")

:parse_args
if "%~1"=="" goto after_parse
if /I "%~1"=="--godot" (
    set "MODE=godot"
    shift
    goto parse_args
)
if /I "%~1"=="--server" (
    set "MODE=server"
    shift
    goto parse_args
)
if /I "%~1"=="--konsole" (
    set "MODE=server"
    shift
    goto parse_args
)
if /I "%~1"=="--web" (
    set "MODE=web"
    shift
    goto parse_args
)
if /I "%~1"=="--check" (
    set "MODE=check"
    shift
    goto parse_args
)
if /I "%~1"=="--port" (
    set "KONSOLE_PORT=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--tick-hz" (
    set "KONSOLE_TICK_HZ=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--ascii-preview" (
    set "ASCII_PREVIEW=1"
    shift
    goto parse_args
)
if /I "%~1"=="--help" (
    echo Usage: launch_windows.bat [--godot ^| --server ^| --web ^| --check] [--port ^<port^>] [--tick-hz ^<hz^>] [--ascii-preview]
    exit /b 0
)
shift
goto parse_args

:after_parse
if /I "%MODE%"=="check" (
    echo [BOXBOY-WIN] PROJECT_ROOT=%PROJECT_ROOT%
    echo [BOXBOY-WIN] GODOT_PROJECT=%GODOT_PROJECT_DIR%\project.godot
    echo [BOXBOY-WIN] WEB_ENTRYPOINT=%WEB_ENTRY_HTML%
    echo [BOXBOY-WIN] KONSOLE_PORT=%KONSOLE_PORT% TICK_HZ=%KONSOLE_TICK_HZ% ASCII_PREVIEW=%ASCII_PREVIEW%
    if not exist "%GODOT_PROJECT_DIR%\project.godot" exit /b 1
    if not exist "%WEB_ENTRY_HTML%" exit /b 1
    echo [BOXBOY-WIN] CHECK=PASS
    exit /b 0
)

if /I "%MODE%"=="server" (
    set "PREVIEW_FLAG="
    if "%ASCII_PREVIEW%"=="1" set "PREVIEW_FLAG=--ascii-preview"
    echo [BOXBOY-WIN] Launching HeadlessKonsoleServer on 127.0.0.1:%KONSOLE_PORT% ^(%KONSOLE_TICK_HZ%Hz^)
    if exist "%PROJECT_ROOT%\build\libs\boxboy-konsole-server.jar" (
        java -cp "%PROJECT_ROOT%\build\libs\boxboy-konsole-server.jar" com.example.network.HeadlessKonsoleServer --port %KONSOLE_PORT% --tick-hz %KONSOLE_TICK_HZ% --daemon %PREVIEW_FLAG%
        exit /b %ERRORLEVEL%
    ) else (
        java -cp "%PROJECT_ROOT%\app\build\tmp\kotlin-classes\debug" com.example.network.HeadlessKonsoleServer --port %KONSOLE_PORT% --tick-hz %KONSOLE_TICK_HZ% --daemon %PREVIEW_FLAG%
        exit /b %ERRORLEVEL%
    )
)

if /I "%MODE%"=="godot" (
    if exist "%PROJECT_ROOT%\bin\windows\BoxBoyZombies.exe" (
        "%PROJECT_ROOT%\bin\windows\BoxBoyZombies.exe" --path "%GODOT_PROJECT_DIR%"
        exit /b %ERRORLEVEL%
    )
    where godot4.exe >nul 2>nul
    if %ERRORLEVEL%==0 (
        godot4.exe --path "%GODOT_PROJECT_DIR%"
        exit /b %ERRORLEVEL%
    )
    godot.exe --path "%GODOT_PROJECT_DIR%"
    exit /b %ERRORLEVEL%
)

echo [BOXBOY-WIN] Serving BoxBoy Zombies Web Capsule on http://127.0.0.1:%WEB_PORT%/index.html
python -B -m http.server %WEB_PORT% --bind 127.0.0.1 --directory "%PROJECT_ROOT%"
endlocal
