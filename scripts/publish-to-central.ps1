<#
.SYNOPSIS
    Publishes JsonUtils to Maven Central via the Sonatype Central Portal.

.DESCRIPTION
    Runs the "release" Maven profile (source+javadoc jars, GPG signing, Central Portal
    upload) with a few pre-flight checks so failures happen fast and locally instead of
    mid-upload:
      - gpg is on PATH and has at least one secret key
      - ~/.m2/settings.xml declares a <server> with id "central"
      - the POM version is not a -SNAPSHOT (Central only accepts releases)
      - the git working tree is clean (warns, does not block)

    Does NOT publish automatically: the plugin is configured with autoPublish=false, so
    this uploads a validated staging bundle only. You still click "Publish" yourself at
    https://central.sonatype.com/publishing/deployments after reviewing it.

.PARAMETER SkipChecks
    Skip the pre-flight checks and go straight to `mvn clean deploy -Prelease`.

.EXAMPLE
    ./scripts/publish-to-central.ps1
#>

[CmdletBinding()]
param(
    [switch]$SkipChecks
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  FAIL: $msg" -ForegroundColor Red }

if (-not $SkipChecks) {
    Write-Step "Pre-flight checks"
    $failed = $false

    # 1. gpg on PATH
    $gpg = Get-Command gpg -ErrorAction SilentlyContinue
    if (-not $gpg) {
        Write-Fail "gpg not found on PATH. Install Gpg4win (https://www.gpg4win.org/) or add Git's usr\bin to PATH."
        $failed = $true
    } else {
        Write-Ok "gpg found at $($gpg.Source)"

        # 2. at least one secret key available
        $keys = & gpg --list-secret-keys --with-colons 2>$null | Select-String '^sec'
        if (-not $keys) {
            Write-Fail "No GPG secret key found. Generate one with: gpg --full-generate-key"
            $failed = $true
        } else {
            Write-Ok "GPG secret key present"
        }
    }

    # 3. settings.xml has a <server id="central">
    $settingsPath = Join-Path $env:USERPROFILE '.m2\settings.xml'
    if (-not (Test-Path $settingsPath)) {
        Write-Fail "$settingsPath not found. Add a <server> block with id 'central' and your Central Portal user token."
        $failed = $true
    } else {
        $settingsContent = Get-Content $settingsPath -Raw
        if ($settingsContent -notmatch '<id>\s*central\s*</id>') {
            Write-Fail "$settingsPath has no <server><id>central</id></server> entry."
            $failed = $true
        } else {
            Write-Ok "settings.xml has a 'central' server entry"
        }
    }

    # 4. version is not a SNAPSHOT
    # Read directly from pom.xml rather than shelling out to `mvn help:evaluate` -- on some
    # Windows/PowerShell setups the -D property gets mis-tokenized when passed through to
    # mvn.cmd, so this is both simpler and more reliable.
    [xml]$pomXml = Get-Content (Join-Path $repoRoot 'pom.xml') -Raw
    $version = $pomXml.project.version
    if ($version -like '*SNAPSHOT*') {
        Write-Fail "Version '$version' is a SNAPSHOT. Central only accepts release versions."
        $failed = $true
    } else {
        Write-Ok "Version to publish: $version"
    }

    # 5. clean working tree (warning only)
    $gitStatus = git status --porcelain 2>$null
    if ($gitStatus) {
        Write-Host "  WARN: working tree has uncommitted changes." -ForegroundColor Yellow
    } else {
        Write-Ok "git working tree is clean"
    }

    if ($failed) {
        Write-Host ""
        Write-Host "Pre-flight checks failed. Fix the issues above, or re-run with -SkipChecks to bypass." -ForegroundColor Red
        exit 1
    }
}

Write-Step "Running mvn clean deploy -Prelease"
mvn clean deploy -Prelease
if ($LASTEXITCODE -ne 0) {
    Write-Fail "mvn deploy failed (exit $LASTEXITCODE)"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Upload complete. Review and publish the staging bundle at:" -ForegroundColor Green
Write-Host "  https://central.sonatype.com/publishing/deployments" -ForegroundColor Green
