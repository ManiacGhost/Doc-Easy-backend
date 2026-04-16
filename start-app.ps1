#!/usr/bin/env pwsh
<#
.SYNOPSIS
    ChatApp - Easy Start Script (PowerShell Version)
.DESCRIPTION
    This script automatically sets up the environment and starts the application
.EXAMPLE
    .\start-app.ps1
#>

$ErrorActionPreference = "Continue"

Write-Host "`n" -NoNewline
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ChatApp - Startup Script (PowerShell)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check for OpenJDK first (preferred)
$JavaPath = "C:\Program Files\OpenJDK\jdk-17.0.2\bin\java.exe"
if (-not (Test-Path $JavaPath)) {
    # Fallback to DBeaver Java if available
    $JavaPath = "C:\Program Files\DBeaver\jre\bin\java.exe"
    if (-not (Test-Path $JavaPath)) {
        Write-Host "ERROR: Java not found" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please install one of:" -ForegroundColor Yellow
        Write-Host "1. OpenJDK 17 at: C:\Program Files\OpenJDK\jdk-17.0.2" -ForegroundColor Yellow
        Write-Host "2. DBeaver at: C:\Program Files\DBeaver\jre" -ForegroundColor Yellow
        Read-Host "Press Enter to exit"
        exit 1
    }
}

# Set Java environment
$JavaHome = Split-Path -Parent (Split-Path -Parent $JavaPath)
$env:JAVA_HOME = $JavaHome
$env:PATH = (Split-Path -Parent $JavaPath) + ";" + $env:PATH

# Verify Java version
Write-Host "Verifying Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = & $JavaPath -version 2>&1
    Write-Host $javaVersion[0] -ForegroundColor Green
    Write-Host "✓ Java verified" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Java not working properly" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

# Navigate to project directory
$ProjectDir = "E:\chatapp\chatapp"
if (-not (Test-Path $ProjectDir)) {
    Write-Host "ERROR: Project directory not found: $ProjectDir" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Set-Location $ProjectDir

# Check if Maven wrapper exists
if (-not (Test-Path "mvnw.cmd")) {
    Write-Host "ERROR: Maven wrapper (mvnw.cmd) not found" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Option menu
Write-Host "Select startup option:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Clean build and run with Spring Boot Maven plugin (RECOMMENDED)" -ForegroundColor Green
Write-Host "2. Build JAR only" -ForegroundColor Cyan
Write-Host "3. Run existing JAR" -ForegroundColor Cyan
Write-Host "4. Clean only" -ForegroundColor Yellow
Write-Host "5. Exit" -ForegroundColor Gray
Write-Host ""

$choice = Read-Host "Enter your choice (1-5)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "Building and starting application..." -ForegroundColor Cyan
        Write-Host ""
        & ".\mvnw.cmd" clean spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "BUILD/RUN FAILED!" -ForegroundColor Red
            Write-Host "Check the error messages above." -ForegroundColor Yellow
            Read-Host "Press Enter to exit"
            exit 1
        }
    }
    "2" {
        Write-Host ""
        Write-Host "Building JAR..." -ForegroundColor Cyan
        Write-Host ""
        & ".\mvnw.cmd" clean package -DskipTests
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "✓ Build successful!" -ForegroundColor Green
            Write-Host "JAR created at: target\chatapp-0.0.1-SNAPSHOT.jar" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "To run the JAR, execute:" -ForegroundColor Yellow
            Write-Host "  & '$JavaPath' -jar target\chatapp-0.0.1-SNAPSHOT.jar" -ForegroundColor Cyan
        } else {
            Write-Host ""
            Write-Host "BUILD FAILED!" -ForegroundColor Red
            Write-Host "Check the error messages above." -ForegroundColor Yellow
            Read-Host "Press Enter to exit"
            exit 1
        }
    }
    "3" {
        Write-Host ""
        Write-Host "Checking if JAR exists..." -ForegroundColor Yellow
        $jarPath = "target\chatapp-0.0.1-SNAPSHOT.jar"
        if (-not (Test-Path $jarPath)) {
            Write-Host "ERROR: JAR not found at $jarPath" -ForegroundColor Red
            Write-Host "Build the project first using option 2." -ForegroundColor Yellow
            Read-Host "Press Enter to exit"
            exit 1
        }
        Write-Host "Running JAR..." -ForegroundColor Cyan
        Write-Host ""
        & $JavaPath -jar $jarPath
    }
    "4" {
        Write-Host ""
        Write-Host "Cleaning project..." -ForegroundColor Cyan
        Write-Host ""
        & ".\mvnw.cmd" clean
        Write-Host ""
        Write-Host "✓ Clean complete" -ForegroundColor Green
    }
    "5" {
        Write-Host "Exiting..." -ForegroundColor Yellow
        exit 0
    }
    default {
        Write-Host "Invalid choice" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Startup script complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

