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
:: ============================================================================

set REGISTRY=registry.example.com
set MCP_IMAGE=%REGISTRY%/com-osir-mcp:latest
set A2A_IMAGE=%REGISTRY%/com-osir-a2a:latest

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
echo [2/4] Building Docker image: %MCP_IMAGE%
docker build -f mcp-server\src\main\docker\Dockerfile.jvm -t %MCP_IMAGE% mcp-server
if errorlevel 1 (
    echo ERROR: MCP Docker build failed.
    exit /b 1
)
echo       MCP image built.
echo.

if %NO_PUSH%==1 goto skip_mcp_push
echo [2b]  Pushing %MCP_IMAGE%...
docker push %MCP_IMAGE%
if errorlevel 1 (
    echo ERROR: MCP push failed.
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
echo [3/4] Building Docker image: %A2A_IMAGE%
docker build -f a2a-server\src\main\docker\Dockerfile.jvm -t %A2A_IMAGE% a2a-server
if errorlevel 1 (
    echo ERROR: A2A Docker build failed.
    exit /b 1
)
echo       A2A image built.
echo.

if %NO_PUSH%==1 goto skip_a2a_push
echo [3b]  Pushing %A2A_IMAGE%...
docker push %A2A_IMAGE%
if errorlevel 1 (
    echo ERROR: A2A push failed.
    exit /b 1
)
echo       A2A image pushed.
:skip_a2a_push
:skip_a2a

:: ---------- Done ----------
echo.
echo ========================================
echo  Deploy complete!
echo.
if not "%TARGET%"=="a2a" echo  MCP Server: %MCP_IMAGE%
if not "%TARGET%"=="mcp" echo  A2A Server: %A2A_IMAGE%
echo.
echo  Run locally with:
if not "%TARGET%"=="a2a" echo    docker run -p 8081:8081 %MCP_IMAGE%
if not "%TARGET%"=="mcp" echo    docker run -p 8082:8082 %A2A_IMAGE%
echo.
echo  Or use docker-compose.yml for both.
echo ========================================
