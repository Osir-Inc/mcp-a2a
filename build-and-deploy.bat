@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: OSIR MCP + A2A Server — Build and Deploy
:: Builds both Quarkus modules and pushes Docker images to the registry.
::
:: Usage:
::   build-and-deploy.bat              Build and push both images
::   build-and-deploy.bat mcp          Build and push MCP server only
::   build-and-deploy.bat a2a          Build and push A2A server only
::   build-and-deploy.bat --no-push    Build images without pushing
::
:: Every image is pushed twice: as :latest AND as :<git-version> (the exact tag when HEAD
:: is tagged, e.g. v2.2.0, otherwise tag-distance+short SHA; "-dirty" = uncommitted tree).
:: To REVERT a bad deploy: on the server, point docker-compose at the previous version tag
:: (e.g. docker-registry.dev.osir.com/com-osir-mcp:v2.1.0) and restart - no rebuild needed.
:: List available versions: docker image ls, or the registry UI.
:: ============================================================================

set REGISTRY=docker-registry.dev.osir.com

:: Resolve the version tag from git for revertible, traceable images.
for /f "delims=" %%v in ('git describe --tags --always --dirty') do set VERSION=%%v
if "%VERSION%"=="" set VERSION=unknown

set MCP_IMAGE=%REGISTRY%/com-osir-mcp:latest
set A2A_IMAGE=%REGISTRY%/com-osir-a2a:latest
set MCP_IMAGE_VER=%REGISTRY%/com-osir-mcp:%VERSION%
set A2A_IMAGE_VER=%REGISTRY%/com-osir-a2a:%VERSION%

set TARGET=%1
set NO_PUSH=0
if "%1"=="--no-push" (
    set TARGET=all
    set NO_PUSH=1
)
if "%2"=="--no-push" set NO_PUSH=1
if "%TARGET%"=="" set TARGET=all

echo.
echo ========================================
echo  OSIR Build and Deploy
echo  Registry: %REGISTRY%
echo  Target:   %TARGET%
echo  Version:  %VERSION%
echo ========================================
echo.

:: ---------- Build with Gradle ----------
echo [1/4] Building Java modules with Gradle...
call gradlew.bat build -x test
if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed.
    exit /b 1
)
echo       Build successful.
echo.

:: ---------- MCP Server ----------
if "%TARGET%"=="all" goto build_mcp
if "%TARGET%"=="mcp" goto build_mcp
goto skip_mcp

:build_mcp
echo [2/4] Building Docker image: %MCP_IMAGE% (+ %VERSION%)
docker build -f mcp-server\src\main\docker\Dockerfile.jvm -t %MCP_IMAGE% -t %MCP_IMAGE_VER% mcp-server
if errorlevel 1 (
    echo ERROR: MCP Docker build failed.
    exit /b 1
)
echo       MCP image built.
echo.

if %NO_PUSH%==1 goto skip_mcp_push
echo [2b]  Pushing %MCP_IMAGE% and %MCP_IMAGE_VER%...
docker push %MCP_IMAGE%
if errorlevel 1 (
    echo ERROR: MCP push failed.
    exit /b 1
)
docker push %MCP_IMAGE_VER%
if errorlevel 1 (
    echo ERROR: MCP version-tag push failed.
    exit /b 1
)
echo       MCP image pushed.
:skip_mcp_push
:skip_mcp

:: ---------- A2A Server ----------
if "%TARGET%"=="all" goto build_a2a
if "%TARGET%"=="a2a" goto build_a2a
goto skip_a2a

:build_a2a
echo [3/4] Building Docker image: %A2A_IMAGE% (+ %VERSION%)
docker build -f a2a-server\src\main\docker\Dockerfile.jvm -t %A2A_IMAGE% -t %A2A_IMAGE_VER% a2a-server
if errorlevel 1 (
    echo ERROR: A2A Docker build failed.
    exit /b 1
)
echo       A2A image built.
echo.

if %NO_PUSH%==1 goto skip_a2a_push
echo [3b]  Pushing %A2A_IMAGE% and %A2A_IMAGE_VER%...
docker push %A2A_IMAGE%
if errorlevel 1 (
    echo ERROR: A2A push failed.
    exit /b 1
)
docker push %A2A_IMAGE_VER%
if errorlevel 1 (
    echo ERROR: A2A version-tag push failed.
    exit /b 1
)
echo       A2A image pushed.
:skip_a2a_push
:skip_a2a

:: ---------- Done ----------
echo.
echo ========================================
echo  Deploy complete!  Version: %VERSION%
echo.
if not "%TARGET%"=="a2a" echo  MCP Server: %MCP_IMAGE%  (also %MCP_IMAGE_VER%)
if not "%TARGET%"=="mcp" echo  A2A Server: %A2A_IMAGE%  (also %A2A_IMAGE_VER%)
echo.
echo  To revert: point compose at the previous :vX.Y.Z tag and restart.
echo.
echo  Run locally with:
if not "%TARGET%"=="a2a" echo    docker run -p 8081:8081 %MCP_IMAGE%
if not "%TARGET%"=="mcp" echo    docker run -p 8082:8082 %A2A_IMAGE%
echo.
echo  Or use docker-compose.yml for both.
echo ========================================
