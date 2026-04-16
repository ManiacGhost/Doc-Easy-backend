@echo off
REM ChatApp - Easy Start Script
REM This script automatically sets up the environment and starts the application

setlocal enabledelayedexpansion

echo.
echo ========================================
echo ChatApp - Startup Script
echo ========================================
echo.

REM Check if OpenJDK exists (preferred)
if exist "C:\Program Files\OpenJDK\jdk-17.0.2\bin\java.exe" (
    set JAVA_HOME=C:\Program Files\OpenJDK\jdk-17.0.2
    set JAVA_BIN=%JAVA_HOME%\bin\java.exe
    echo Using OpenJDK from %JAVA_HOME%
) else if exist "C:\Program Files\DBeaver\jre\bin\java.exe" (
    set JAVA_HOME=C:\Program Files\DBeaver\jre
    set JAVA_BIN=%JAVA_HOME%\bin\java.exe
    echo Using DBeaver Java from %JAVA_HOME%
) else (
    echo ERROR: Java not found
    echo.
    echo Please install one of:
    echo 1. OpenJDK 17 at: C:\Program Files\OpenJDK\jdk-17.0.2
    echo 2. DBeaver at: C:\Program Files\DBeaver\jre
    echo.
    pause
    exit /b 1
)

REM Add Java to PATH
set PATH=%JAVA_HOME%\bin;%PATH%

REM Verify Java version
echo Verifying Java installation...
"%JAVA_BIN%" -version
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java not working properly
    pause
    exit /b 1
)
echo ✓ Java verified
echo.

REM Navigate to project directory
cd /d "E:\chatapp\chatapp"

REM Check if Maven wrapper exists
if not exist "mvnw.cmd" (
    echo ERROR: Maven wrapper not found
    pause
    exit /b 1
)

REM Option menu
echo Select startup option:
echo.
echo 1. Clean build and run with Spring Boot Maven plugin (RECOMMENDED)
echo 2. Build JAR only
echo 3. Run existing JAR
echo 4. Clean only
echo.
set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" (
    echo.
    echo Building and starting application...
    echo.
    call mvnw clean spring-boot:run
    if %ERRORLEVEL% neq 0 (
        echo.
        echo BUILD/RUN FAILED!
        echo Check the error messages above.
        pause
        exit /b 1
    )
) else if "%choice%"=="2" (
    echo.
    echo Building JAR...
    echo.
    call mvnw clean package -DskipTests
    if %ERRORLEVEL% equ 0 (
    echo.
    echo ✓ Build successful!
    echo JAR created at: target\chatapp-0.0.1-SNAPSHOT.jar
    echo.
    echo To run the JAR, execute:
    echo   "%JAVA_BIN%" -jar target\chatapp-0.0.1-SNAPSHOT.jar
    ) else (
        echo.
        echo BUILD FAILED!
        echo Check the error messages above.
        pause
        exit /b 1
    )
) else if "%choice%"=="3" (
    echo.
    echo Checking if JAR exists...
    if not exist "target\chatapp-0.0.1-SNAPSHOT.jar" (
        echo ERROR: JAR not found at target\chatapp-0.0.1-SNAPSHOT.jar
        echo Build the project first using option 2.
        pause
        exit /b 1
    )
    echo Running JAR...
    echo.
    "%JAVA_BIN%" -jar target\chatapp-0.0.1-SNAPSHOT.jar
) else if "%choice%"=="4" (
    echo.
    echo Cleaning project...
    echo.
    call mvnw clean
    echo ✓ Clean complete
) else (
    echo Invalid choice
    exit /b 1
)

echo.
echo ========================================
echo Startup script complete
echo ========================================
echo.
pause

