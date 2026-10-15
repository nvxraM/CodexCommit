param([Parameter(Mandatory=$true)][string]$IdeHome)
$ErrorActionPreference = 'Stop'
$resolvedIde = (Resolve-Path -LiteralPath $IdeHome).Path
$java = Join-Path $resolvedIde 'jbr\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'The selected IDE has no bundled JDK; use a full JDK with tools/LocalBuild.java.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
& $java (Join-Path $PSScriptRoot 'LocalBuild.java') $projectRoot $resolvedIde
if ($LASTEXITCODE -ne 0) { throw 'Local build failed.' }
