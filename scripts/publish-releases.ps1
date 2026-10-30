#Requires -Version 5.1
[CmdletBinding()]
param([switch]$DryRun,[string]$PythonPath)
$ErrorActionPreference='Stop'
try {
    if($PythonPath) { $python=(Resolve-Path -LiteralPath $PythonPath).Path }
    else {
        $command=Get-Command python -ErrorAction SilentlyContinue
        if($command) { $python=$command.Source }
        else {
            $python=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
        }
    }
    if(!(Test-Path -LiteralPath $python -PathType Leaf)) {throw 'Install Python 3.9+ and Git for Windows first.'}
    $arguments=@((Join-Path $PSScriptRoot 'publish-releases.py'))
    if($DryRun) {$arguments+='--dry-run'}
    & $python @arguments
    exit $LASTEXITCODE
} catch {Write-Error $_ -ErrorAction Continue;exit 1}
