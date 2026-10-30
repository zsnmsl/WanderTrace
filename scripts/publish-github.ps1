#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$Message,
    [ValidatePattern('^$|^[A-Za-z0-9][A-Za-z0-9._/-]*$')][string]$Branch,
    [string]$UserName,
    [string]$Email,
    [string]$GitPath,
    [switch]$DryRun,
    [switch]$CommitOnly
)
$ErrorActionPreference = 'Stop'
$repositoryUrl = 'https://github.com/zsnmsl/WanderTrace.git'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Invoke-RepoGit {
    param([string[]]$Arguments)
    & $script:gitExecutable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($Arguments[0]) (exit $LASTEXITCODE)." }
}

try {
    if ($GitPath) {
        $gitExecutable = (Resolve-Path -LiteralPath $GitPath).Path
    } else {
        $command = Get-Command git -ErrorAction SilentlyContinue
        if ($command) { $gitExecutable = $command.Source }
        else {
            $candidates = @(
                (Join-Path $env:ProgramFiles 'Git\cmd\git.exe'),
                (Join-Path $env:LOCALAPPDATA 'Programs\Git\cmd\git.exe'),
                (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd\git.exe')
            )
            $gitExecutable = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        }
    }
    if (!$gitExecutable) { throw 'Install Git for Windows, or provide -GitPath with the path to git.exe.' }

    Push-Location -LiteralPath $projectRoot
    try {
        if (!(Test-Path -LiteralPath '.git')) {
            if (!$Branch) { $Branch = 'main' }
            if ($DryRun) {
                Write-Host "Preview: initialize this project, stage non-ignored source, commit, and push to $repositoryUrl ($Branch)."
                Write-Host 'No files staged, repository initialized, or network requests made.'
                return
            }
            Invoke-RepoGit -Arguments @('init', '-b', $Branch)
        }
        $top = (Invoke-RepoGit -Arguments @('rev-parse', '--show-toplevel')).Trim().Replace('/', '\')
        if (![string]::Equals($top, $projectRoot.Replace('/', '\'), [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Run this script from the WanderTrace repository, not a parent or nested repository.'
        }
        $currentBranch = (Invoke-RepoGit -Arguments @('symbolic-ref', '--quiet', '--short', 'HEAD')).Trim()
        if (!$Branch) { $Branch = $currentBranch }
        Invoke-RepoGit -Arguments @('check-ref-format', '--branch', $Branch) | Out-Null
        & $gitExecutable rev-parse --verify --quiet HEAD *> $null
        $hasHead = $LASTEXITCODE -eq 0
        if ($hasHead -and $currentBranch -ne $Branch) {
            throw "Current branch is $currentBranch. Switch branches yourself, or pass -Branch $currentBranch."
        }
        $origin = & $gitExecutable config --get remote.origin.url
        if ($LASTEXITCODE -notin @(0, 1)) { throw 'Cannot read origin configuration.' }
        $acceptedOrigins = @($repositoryUrl, 'https://github.com/zsnmsl/WanderTrace', 'git@github.com:zsnmsl/WanderTrace.git', 'ssh://git@github.com/zsnmsl/WanderTrace.git')
        if ($origin -and $origin.TrimEnd('/') -notin $acceptedOrigins) {
            throw 'Origin points to another repository. It will not be overwritten.'
        }

        # Include already tracked files: .gitignore alone does not protect them.
        $files = @(Invoke-RepoGit -Arguments @('-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard'))
        $blocked = @($files | Where-Object {
            ($_ -ne 'gradle/wrapper/gradle-wrapper.jar') -and (
                $_ -match '(^|/)(build[^/]*|\.gradle|\.tools|releases|jniLibs|\.cxx|__pycache__)/' -or
                $_ -match '(?i)\.(apk|aab|apks|aar|jar|so|a|o|obj|class|dex|idsig|log|jks|keystore|p12|pfx|pem|key|db|sqlite|sqlite3|zip|pyc)$' -or
                $_ -match '(^|/)(local\.properties|keystore\.properties|signing\.properties|\.env(?:\..*)?)$'
            ) -and $_ -notmatch '(^|/)\.env\.example$'
        })
        if ($blocked.Count) { throw ('Remove generated or credential files from the Git index before publishing: ' + ($blocked -join ', ')) }
        foreach ($file in $files) {
            if ((Test-Path -LiteralPath $file -PathType Leaf) -and (Get-Item -LiteralPath $file).Length -gt 95MB) {
                throw "File exceeds the script's upload size limit: $file"
            }
        }
        $changes = @(Invoke-RepoGit -Arguments @('--no-optional-locks', 'status', '--short'))
        $hasChanges = $changes.Count -gt 0
        if ($DryRun) {
            Write-Host "Preview passed: $($files.Count) source files; target $repositoryUrl; branch $Branch."
            Write-Host "Changed entries: $($changes.Count). Commit only: $CommitOnly."
            $changes | ForEach-Object { Write-Host $_ }
            Write-Host 'Release/build/credential paths are excluded. No staging, commits, remote changes, or network requests made.'
            return
        }

        if ($hasChanges) {
            Write-Host 'Changes to include in this commit:'
            $changes | ForEach-Object { Write-Host $_ }
            if ([string]::IsNullOrWhiteSpace($Message)) { $Message = Read-Host 'Commit message (describe what changed)' }
            if ([string]::IsNullOrWhiteSpace($Message)) { throw 'A non-empty commit message is required. No changes were staged.' }
        } elseif ($CommitOnly) {
            Write-Host 'No new source changes to commit. Nothing was pushed.'
            return
        }

        if ($hasChanges) {
            foreach ($identity in @(@('user.name', $UserName), @('user.email', $Email))) {
                $configured = & $gitExecutable config --get $identity[0]
                if (!$configured) {
                    $value = $identity[1]
                    if ([string]::IsNullOrWhiteSpace($value)) { $value = Read-Host "Enter Git $($identity[0]) (saved only in this repository)" }
                    if ([string]::IsNullOrWhiteSpace($value)) { throw 'Git author name and email are required.' }
                    Invoke-RepoGit -Arguments @('config', '--local', $identity[0], $value)
                }
            }
        }
        if (!$CommitOnly -and !$origin) { Invoke-RepoGit -Arguments @('remote', 'add', 'origin', $repositoryUrl) }
        if (!$hasHead -and $currentBranch -ne $Branch) { Invoke-RepoGit -Arguments @('symbolic-ref', 'HEAD', "refs/heads/$Branch") }

        # Never force-push, discard changes, or auto-merge unrelated histories.
        if (!$CommitOnly) {
            $remoteHead = & $gitExecutable ls-remote --exit-code --heads origin "refs/heads/$Branch"
            $remoteExit = $LASTEXITCODE
            if ($remoteExit -notin @(0, 2)) { throw 'Cannot access GitHub. Check your connection and Git credential manager login.' }
            if ($remoteExit -eq 0) {
                Invoke-RepoGit -Arguments @('fetch', '--no-tags', 'origin', $Branch)
                if (!$hasHead) { throw 'The remote branch already has history. Clone it and copy your source into that checkout before publishing.' }
                & $gitExecutable merge-base --is-ancestor FETCH_HEAD HEAD
                if ($LASTEXITCODE -ne 0) { throw 'Remote history is ahead or different. Integrate it manually before publishing; nothing will be forced.' }
            }
        }
        if ($hasChanges) {
            Invoke-RepoGit -Arguments @('add', '--all', '--', '.')
            Invoke-RepoGit -Arguments @('update-index', '--chmod=+x', '--', 'gradlew')
            & $gitExecutable diff --cached --quiet
            $diffExit = $LASTEXITCODE
            if ($diffExit -eq 1) {
                # A UTF-8 file preserves Chinese text and quotes on Windows PowerShell 5.1.
                $messageFile = [System.IO.Path]::GetTempFileName()
                try {
                    [System.IO.File]::WriteAllText($messageFile, $Message + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
                    Invoke-RepoGit -Arguments @('commit', '--file', $messageFile)
                } finally { [System.IO.File]::Delete($messageFile) }
            }
            elseif ($diffExit -ne 0) { throw 'Cannot inspect staged changes.' }
            else { Write-Host 'No new source changes to commit.' }
        } else { Write-Host 'No new source changes to commit; checking whether existing commits need pushing.' }
        if ($CommitOnly) {
            Write-Host "Committed locally on $Branch. Nothing was pushed."
            return
        }
        Invoke-RepoGit -Arguments @('push', '--set-upstream', 'origin', "HEAD:refs/heads/$Branch")
        Write-Host "Published to $repositoryUrl ($Branch). Local releases were not uploaded."
    } finally { Pop-Location }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
