<#
.SYNOPSIS
  Find duplicate JPMS module providers (ModLauncher ResolutionException cause).

.DESCRIPTION
  Scans jars under the given directories and reports every jar that declares a
  module name (manifest `Automatic-Module-Name`, or an explicit module-info.class),
  including jars embedded via JarJar (META-INF/jarjar/*.jar inside a mod).

  The typical failure this diagnoses:
    java.lang.module.ResolutionException: Module X reads more than one module
    named net.kyori.adventure
  means two jars in the module layer claim the same module name.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\find_module_conflict.ps1 -Root "D:\server"
  powershell -ExecutionPolicy Bypass -File .\find_module_conflict.ps1 -Root "D:\server" -Module net.kyori.adventure
  powershell -ExecutionPolicy Bypass -File .\find_module_conflict.ps1 -Root "C:\Users\Administrator\Downloads\emoney" -Module net.kyori.examination.string -Dirs mods,libraries
#>
[CmdletBinding()]
param(
    [string]$Root = ".",
    [string]$Module = "",
    [string[]]$Dirs = @("mods", "libraries"),
    [switch]$SkipJarJar,
    [string]$Output = ""
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-ManifestAttribute {
    param([System.IO.Compression.ZipArchive]$Zip, [string]$Name)
    $entry = $Zip.GetEntry('META-INF/MANIFEST.MF')
    if (-not $entry) { return $null }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { $text = $reader.ReadToEnd() } finally { $reader.Close() }
    $text = $text -replace "`r`n(?=\s)", "" -replace "`n(?=\s)", ""
    foreach ($line in ($text -split "`r?`n")) {
        if ($line -match ("^\s*" + [regex]::Escape($Name) + "\s*:\s*(.+?)\s*$")) {
            return $Matches[1].Trim()
        }
    }
    return $null
}

function Get-JarModules {
    param([string]$JarPath, [string]$Owner)
    $found = New-Object System.Collections.Generic.List[object]
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $modName = Read-ManifestAttribute -Zip $zip -Name 'Automatic-Module-Name'
        $explicit = ($null -ne $zip.GetEntry('module-info.class'))
        $found.Add([pscustomobject]@{
            Owner       = $Owner
            Jar         = $JarPath
            Source      = if ($Owner) { 'jarjar-embedded' } else { 'jar' }
            Module      = $modName
            Explicit    = $explicit
        })
        if (-not $SkipJarJar) {
            $embedded = @($zip.Entries | Where-Object { $_.FullName -like 'META-INF/jarjar/*.jar' })
            foreach ($entry in $embedded) {
                $temp = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), ([guid]::NewGuid().ToString('N') + '.jar'))
                try {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $temp, $true)
                    $nz = [System.IO.Compression.ZipFile]::OpenRead($temp)
                    try {
                        $innerName = Read-ManifestAttribute -Zip $nz -Name 'Automatic-Module-Name'
                        $innerExplicit = ($null -ne $nz.GetEntry('module-info.class'))
                        $found.Add([pscustomobject]@{
                            Owner    = $JarPath
                            Jar      = $JarPath + '!/' + $entry.FullName
                            Source   = 'jarjar-embedded'
                            Module   = $innerName
                            Explicit = $innerExplicit
                        })
                    } finally { $nz.Dispose() }
                } finally {
                    Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
                }
            }
        }
    } finally { $zip.Dispose() }
    return $found
}

$rootPath = (Resolve-Path -LiteralPath $Root).Path
Write-Host "Root   : $rootPath"
Write-Host "Dirs   : $($Dirs -join ', ')"
Write-Host "Scanning jars ..."

$all = New-Object System.Collections.Generic.List[object]
foreach ($dir in $Dirs) {
    $base = Join-Path $rootPath $dir
    if (-not (Test-Path -LiteralPath $base)) { Write-Host "  (skip missing dir: $dir)"; continue }
    $jars = Get-ChildItem -LiteralPath $base -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue
    foreach ($jar in $jars) {
        foreach ($m in (Get-JarModules -JarPath $jar.FullName -Owner '')) { $all.Add($m) }
    }
}
Write-Host "Scanned: $($all.Count) module declarations"
Write-Host ""

$named = $all | Where-Object { $_.Module -or $_.Explicit }

$conflicts = @()
if ($Module) {
    $conflicts = @($named | Where-Object { $_.Module -eq $Module })
} else {
    $conflicts = @($named | Where-Object { $_.Module } | Group-Object Module | Where-Object { $_.Count -gt 1 } |
        ForEach-Object { $_.Group })
}
[pscustomobject]@{}

function Format-Providers($items) {
    foreach ($item in $items) {
        $flag = if ($item.Explicit) { ' [module-info]' } else { '' }
        if ($item.Source -eq 'jarjar-embedded') {
            "  - $($item.Jar)$flag"
            "      (embedded in: $($item.Owner))"
        } else {
            "  - $($item.Jar)$flag"
        }
    }
}

if ($Module) {
    Write-Host "=== Jars providing module '$Module' ==="
    if (@($conflicts).Count -eq 0) {
        Write-Host "  none found"
    } else {
        Format-Providers $conflicts
        Write-Host ""
        if (@($conflicts).Count -gt 1) {
            Write-Host ">>> CONFLICT: $($conflicts.Count) providers for '$Module' <<<"
        } else {
            Write-Host "(only one provider)"
        }
    }
} else {
    Write-Host "=== Modules with duplicate providers ==="
    $groups = $named | Where-Object { $_.Module } | Group-Object Module | Where-Object { $_.Count -gt 1 } | Sort-Object Count -Descending
    if ($groups.Count -eq 0) {
        Write-Host "  no duplicates found"
    } else {
        foreach ($g in $groups) {
            Write-Host ""
            Write-Host "[$($g.Count)x] $($g.Name)"
            Format-Providers $g.Group
        }
    }
}

$reportPath = $Output
if (-not $reportPath) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $reportPath = Join-Path $rootPath ("module-conflicts-$stamp.txt")
}
$report = @()
$report += "root: $rootPath"
$report += "dirs: $($Dirs -join ', ')"
$report += "scanned module declarations: $($all.Count)"
$report += ""
if ($Module) {
    $report += "module '$Module' providers: $(@($conflicts).Count)"
    $report += ($conflicts | ForEach-Object { "  $($_.Jar)  (embedded in: $($_.Owner))  explicit=$($_.Explicit)" })
} else {
    $groups = $named | Where-Object { $_.Module } | Group-Object Module | Where-Object { $_.Count -gt 1 } | Sort-Object Count -Descending
    foreach ($g in $groups) {
        $report += "module '$($g.Name)' providers: $($g.Count)"
        $report += ($g.Group | ForEach-Object { "  $($_.Jar)  (embedded in: $($_.Owner))  explicit=$($_.Explicit)" })
        $report += ""
    }
}
$report | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Host ""
Write-Host "report: $reportPath"
