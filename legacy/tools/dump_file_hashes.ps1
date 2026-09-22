<#
.SYNOPSIS
  Dump all files (recursively) in a directory with hash values, for cross-machine diff.

.DESCRIPTION
  Produces a CSV (path/size/hash/mtime) and a sha256sum-style TXT (`HASH  relative\path`)
  so you can diff a server's mods folder against a local test server's mods folder.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\dump_file_hashes.ps1
  powershell -ExecutionPolicy Bypass -File .\dump_file_hashes.ps1 -Path ".\mods" -Filter "*.jar"
  powershell -ExecutionPolicy Bypass -File .\dump_file_hashes.ps1 -Path "D:\server\mods" -Algorithm MD5 -Output "D:\server-mod-hashes.csv"
#>
[CmdletBinding()]
param(
    [string]$Path = ".",
    [string]$Filter = "*",
    [ValidateSet("SHA256", "SHA1", "MD5")]
    [string]$Algorithm = "SHA256",
    [string]$Output = "",
    [switch]$NoRecurse
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path)) { throw "Path not found: $Path" }
$root = (Resolve-Path -LiteralPath $Path).Path

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $Output) {
    $safeRoot = ($root -replace '[:\\/\s()]', '_').Trim('_')
    $suffix = if ($Filter -ne '*') { '-' + ($Filter -replace '[*.\s]', '') } else { '' }
    $Output = Join-Path $root ("file-hashes$suffix-$stamp.csv")
}

$gciArgs = @{
    LiteralPath = $root
    File        = $true
    Filter      = $Filter
    ErrorAction = 'SilentlyContinue'
}
if (-not $NoRecurse) { $gciArgs.Recurse = $true }

$files = Get-ChildItem @gciArgs | Sort-Object -Property FullName
$total = @($files).Count
if ($total -eq 0) { Write-Warning "No files matched '$Filter' under $root"; return }

$rows = New-Object System.Collections.Generic.List[object]
$index = 0
foreach ($file in $files) {
    $index++
    if ($index % 50 -eq 0 -or $index -eq $total) {
        Write-Progress -Activity "Hashing ($Algorithm)" -Status "$index / $total" `
            -PercentComplete ([math]::Min(100, [int](($index / $total) * 100)))
    }
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm $Algorithm).Hash
    $rows.Add([pscustomobject]@{
        RelativePath = $file.FullName.Substring($root.Length).TrimStart('\')
        Size         = $file.Length
        Hash         = $hash
        LastWrite    = $file.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
    })
}
Write-Progress -Activity "Hashing ($Algorithm)" -Completed

$rows | Export-Csv -LiteralPath $Output -NoTypeInformation -Encoding UTF8
$txtPath = [IO.Path]::ChangeExtension($Output, '.txt')
$rows | ForEach-Object { '{0}  {1}' -f $_.Hash, $_.RelativePath } |
    Set-Content -LiteralPath $txtPath -Encoding UTF8

Write-Host ""
Write-Host "Files : $total"
Write-Host "Algo  : $Algorithm"
Write-Host "Root  : $root"
Write-Host "CSV   : $Output"
Write-Host "TXT   : $txtPath"
