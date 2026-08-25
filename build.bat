@echo off
rem ==============================================================================
rem Enterprise Edge Benchmark Plugin - Build Script
rem ==============================================================================

echo ==============================================================================
echo [INFO] Checking build environment...
echo ==============================================================================

where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven (mvn) command was not found in your system PATH.
    echo [HINT] Please install Apache Maven and JDK 21 to build locally,
    echo        OR use GitHub Actions to build the JAR online automatically.
    echo ==============================================================================
    pause
    exit /b 1
)

echo [INFO] Starting Maven Package build (Java 21 Paper Plugin)...
call mvn clean package

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven build failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================================================
echo [SUCCESS] Build completed successfully!
echo [INFO] Target JAR: target\enterprise-edge-benchmark-1.0.0.jar
echo ==============================================================================
pause
exit /b 0
