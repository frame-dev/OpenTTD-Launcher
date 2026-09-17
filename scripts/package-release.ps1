#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & mvn --batch-mode clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed' }
    [xml]$pom = Get-Content -LiteralPath 'pom.xml'
    $name = "openttd-launcher-$($pom.project.version)"
    $release = New-Item -ItemType Directory -Path 'target/release'
    $binary = New-Item -ItemType Directory -Path "target/bundle/$name"
    $source = New-Item -ItemType Directory -Path "target/source/$name-source"
    & mvn --batch-mode org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$($binary.FullName)/third-party"
    if ($LASTEXITCODE -ne 0) { throw 'Dependency packaging failed' }
    & mvn --batch-mode org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies '-DincludeScope=runtime' '-Dclassifier=sources' '-DfailOnMissingClassifierArtifact=true' "-DoutputDirectory=$($source.FullName)/dependency-sources"
    if ($LASTEXITCODE -ne 0) { throw 'Dependency source packaging failed' }
    Copy-Item -LiteralPath "target/$name.jar" -Destination "$binary/launcher.jar"
    foreach ($file in @('README.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'RELEASE_NOTES.md')) {
        Copy-Item -LiteralPath $file -Destination $binary
        Copy-Item -LiteralPath $file -Destination $source
    }
    foreach ($file in @('src', 'scripts', '.github', 'pom.xml', 'RELEASING.md', '.gitignore')) {
        Copy-Item -LiteralPath $file -Destination $source -Recurse
    }
    "@echo off`r`ncd /d `"%~dp0`"`r`njava -jar launcher.jar`r`nif errorlevel 1 pause`r`n" | Set-Content -LiteralPath "$binary/launch.bat" -Encoding ascii
    '#!/bin/sh' + "`n" + 'cd -- "$(dirname -- "$0")" || exit 1' + "`n" + 'exec java -jar launcher.jar' + "`n" | Set-Content -LiteralPath "$binary/launch.sh" -NoNewline -Encoding utf8
    foreach ($platform in @('windows', 'macos', 'linux')) {
        $platformRoot = New-Item -ItemType Directory -Path "target/platform-$platform/$name"
        Get-ChildItem -LiteralPath $binary.FullName | Where-Object { $_.Name -notin @('launch.bat', 'launch.sh') } | Copy-Item -Destination $platformRoot -Recurse
        if ($platform -eq 'windows') {
            Copy-Item -LiteralPath "$binary/launch.bat" -Destination $platformRoot
        } else {
            Copy-Item -LiteralPath "$binary/launch.sh" -Destination $platformRoot
            if ($platform -eq 'macos') { Copy-Item -LiteralPath "$binary/launch.sh" -Destination "$platformRoot/launch.command" }
        }
        $zipPath = "$release/$name-$platform.zip"
        [System.IO.Compression.ZipFile]::CreateFromDirectory($platformRoot.Parent.FullName, $zipPath)
        if ($platform -ne 'windows') {
            $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Update)
            try {
                foreach ($entry in $zip.Entries) {
                    if ($entry.FullName.EndsWith('.sh') -or $entry.FullName.EndsWith('.command')) { $entry.ExternalAttributes = (0x81ED -shl 16) }
                }
            } finally { $zip.Dispose() }
        }
    }
    # ZipFile includes .github and .gitignore on Unix, unlike Compress-Archive.
    [System.IO.Compression.ZipFile]::CreateFromDirectory($source.FullName, "$release/$name-source.zip")
    Get-ChildItem -LiteralPath $release -Filter '*.zip' | Sort-Object Name | ForEach-Object {
        "$( (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"
    } | Set-Content -LiteralPath "$release/SHA256SUMS.txt" -Encoding ascii
    Write-Host "Release files: $($release.FullName)"
} finally { Pop-Location }
