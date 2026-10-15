param([Parameter(Mandatory=$true)][string]$IdeHome)
$ErrorActionPreference = 'Stop'
$resolvedIde = (Resolve-Path -LiteralPath $IdeHome).Path
$java = Join-Path $resolvedIde 'jbr\bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'The selected IDE has no bundled JDK; use a full JDK with tools/LocalBuild.java.' }
& $java (Join-Path $PSScriptRoot 'tools\LocalBuild.java') $PSScriptRoot $resolvedIde
if ($LASTEXITCODE -ne 0) { throw 'Local build failed.' }
