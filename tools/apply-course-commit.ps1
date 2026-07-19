<#
.SYNOPSIS
Copies the full files changed by a GitHub commit into this repository.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\tools\apply-course-commit.ps1 https://github.com/EmbarkXOfficial/spring-boot-course/commit/<sha>

.EXAMPLE
powershell -ExecutionPolicy Bypass -File .\tools\apply-course-commit.ps1 https://github.com/EmbarkXOfficial/spring-boot-course/commit/<sha> -RemoveDeletedFiles
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Commit,

    [switch]$Force,

    [switch]$RemoveDeletedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-GitOutput {
    param(
        [string[]]$Arguments
    )

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed."
    }

    return @($output)
}

function Invoke-Git {
    param(
        [string[]]$Arguments
    )

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed."
    }
}

function Test-GitCommit {
    param([string]$Spec)

    & git cat-file -e "$Spec^{commit}" 2>$null
    return $LASTEXITCODE -eq 0
}

function Get-SafeFullPath {
    param(
        [string]$Root,
        [string]$RelativePath
    )

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        throw "Git returned an empty path."
    }

    if ($RelativePath -match '^[A-Za-z]:|^\\\\') {
        throw "Refusing to write outside the repository: $RelativePath"
    }

    $rootFull = [System.IO.Path]::GetFullPath($Root)
    $fullPath = [System.IO.Path]::GetFullPath((Join-Path $rootFull $RelativePath))
    $rootPrefix = $rootFull.TrimEnd('\') + '\'

    if (-not $fullPath.Equals($rootFull, [System.StringComparison]::OrdinalIgnoreCase) -and
        -not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to write outside the repository: $RelativePath"
    }

    return $fullPath
}

function Resolve-LocalRelativePath {
    param(
        [string]$Root,
        [string]$SourcePath
    )

    $normalizedPath = $SourcePath -replace '\\', '/'
    $rootName = Split-Path -Leaf ([System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/'))
    $prefix = "$rootName/"

    if ($normalizedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $normalizedPath.Substring($prefix.Length)
    }

    return $normalizedPath
}

function Select-UniqueStrings {
    param([string[]]$Items)

    $seen = @{}
    foreach ($item in $Items) {
        if (-not $seen.ContainsKey($item)) {
            $seen[$item] = $true
            $item
        }
    }
}

function ConvertTo-ProcessArgument {
    param([string]$Argument)

    if ($null -eq $Argument -or $Argument.Length -eq 0) {
        return '""'
    }

    if ($Argument -notmatch '[\s"]') {
        return $Argument
    }

    $quoted = '"'
    $backslashes = 0

    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq '\') {
            $backslashes++
        } elseif ($character -eq '"') {
            $quoted += ('\' * (($backslashes * 2) + 1))
            $quoted += '"'
            $backslashes = 0
        } else {
            if ($backslashes -gt 0) {
                $quoted += ('\' * $backslashes)
                $backslashes = 0
            }

            $quoted += $character
        }
    }

    if ($backslashes -gt 0) {
        $quoted += ('\' * ($backslashes * 2))
    }

    $quoted += '"'
    return $quoted
}

function Join-ProcessArguments {
    param([string[]]$Arguments)

    return (($Arguments | ForEach-Object { ConvertTo-ProcessArgument $_ }) -join ' ')
}

function Copy-GitFileFromCommit {
    param(
        [string]$CommitSha,
        [string]$SourceRelativePath,
        [string]$DestinationRelativePath,
        [string]$Root
    )

    $destinationPath = Get-SafeFullPath -Root $Root -RelativePath $DestinationRelativePath
    $destinationDirectory = Split-Path -Parent $destinationPath
    $destinationName = Split-Path -Leaf $destinationPath

    if (-not (Test-Path -LiteralPath $destinationDirectory)) {
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
    }

    $objectOutput = @(Get-GitOutput -Arguments @('rev-parse', '--verify', "${CommitSha}:$SourceRelativePath"))
    $objectId = $objectOutput[0].Trim()
    $tempPath = Join-Path $destinationDirectory ".$destinationName.apply-course-commit.$PID.tmp"

    if (Test-Path -LiteralPath $tempPath) {
        Remove-Item -LiteralPath $tempPath -Force
    }

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = 'git'
    $processInfo.Arguments = Join-ProcessArguments -Arguments @('cat-file', '--filters', "--path=$DestinationRelativePath", $objectId)
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.UseShellExecute = $false

    $process = $null
    $process = [System.Diagnostics.Process]::Start($processInfo)

    try {
        $fileStream = [System.IO.File]::Open($tempPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            $process.StandardOutput.BaseStream.CopyTo($fileStream)
        } finally {
            $fileStream.Dispose()
        }

        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        if ($process.ExitCode -ne 0) {
            throw "git cat-file failed for $SourceRelativePath. $stderr"
        }

        Move-Item -LiteralPath $tempPath -Destination $destinationPath -Force
    } finally {
        if (Test-Path -LiteralPath $tempPath) {
            Remove-Item -LiteralPath $tempPath -Force
        }

        if ($null -ne $process) {
            $process.Dispose()
        }
    }
}

function Get-ChangedFileRows {
    param([string]$CommitSha)

    return Get-GitOutput -Arguments @('diff-tree', '--root', '--no-commit-id', '--name-status', '-r', '-M', $CommitSha)
}

try {
    $repoRootOutput = @(Get-GitOutput -Arguments @('rev-parse', '--show-toplevel'))
    $repoRoot = $repoRootOutput[0].Trim()
} catch {
    throw "Run this script from inside a git repository."
}

Set-Location $repoRoot

$commitSpec = $Commit.Trim()
$sourceRepoUrl = $null

if ($commitSpec -match '^https?://github\.com/([^/]+)/([^/]+)/commits?/([0-9a-fA-F]{7,40})(?:[/?#].*)?$') {
    $owner = $Matches[1]
    $repo = $Matches[2] -replace '\.git$', ''
    $commitSpec = $Matches[3]
    $sourceRepoUrl = "https://github.com/$owner/$repo.git"
} elseif ($commitSpec -notmatch '^[0-9a-fA-F]{7,40}$') {
    throw "Pass a GitHub commit URL or a commit SHA that already exists locally."
}

if (-not (Test-GitCommit $commitSpec)) {
    if ($null -eq $sourceRepoUrl) {
        throw "Commit '$commitSpec' is not available locally. Use the full GitHub commit URL instead."
    }

    if ($commitSpec.Length -lt 40) {
        throw "This commit is not local yet. Use a GitHub URL containing the full 40-character commit SHA."
    }

    Write-Host "Fetching $commitSpec from $sourceRepoUrl..."
    Invoke-Git -Arguments @('fetch', '--depth=2', $sourceRepoUrl, $commitSpec)
}

$resolvedCommitOutput = @(Get-GitOutput -Arguments @('rev-parse', '--verify', "$commitSpec^{commit}"))
$resolvedCommit = $resolvedCommitOutput[0].Trim()

try {
    $statusLines = @(Get-ChangedFileRows $resolvedCommit)
} catch {
    if ($null -eq $sourceRepoUrl) {
        throw
    }

    Write-Host "Fetching commit parent data for the file list..."
    Invoke-Git -Arguments @('fetch', '--depth=2', $sourceRepoUrl, $commitSpec)
    $statusLines = @(Get-ChangedFileRows $resolvedCommit)
}

if ($statusLines.Count -eq 0) {
    Write-Host "No changed files found in $($resolvedCommit.Substring(0, 12))."
    exit 0
}

$restoreList = New-Object System.Collections.Generic.List[string]
$deleteList = New-Object System.Collections.Generic.List[string]
$skippedDeletes = New-Object System.Collections.Generic.List[string]

foreach ($line in $statusLines) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    $parts = $line -split "`t"
    $status = $parts[0]

    if ($status -like 'R*') {
        if ($parts.Count -lt 3) {
            throw "Could not parse rename row: $line"
        }

        if ($RemoveDeletedFiles) {
            $deleteList.Add($parts[1]) | Out-Null
        }

        $restoreList.Add($parts[2]) | Out-Null
    } elseif ($status -like 'C*') {
        if ($parts.Count -lt 3) {
            throw "Could not parse copy row: $line"
        }

        $restoreList.Add($parts[2]) | Out-Null
    } elseif ($status -eq 'D') {
        if ($RemoveDeletedFiles) {
            $deleteList.Add($parts[1]) | Out-Null
        } else {
            $skippedDeletes.Add($parts[1]) | Out-Null
        }
    } elseif ($status -eq 'A' -or $status -eq 'M' -or $status -eq 'T') {
        if ($parts.Count -lt 2) {
            throw "Could not parse file row: $line"
        }

        $restoreList.Add($parts[1]) | Out-Null
    } else {
        Write-Warning "Skipping unsupported git status '$status': $line"
    }
}

$restorePaths = @(Select-UniqueStrings $restoreList.ToArray())
$deletePaths = @(Select-UniqueStrings $deleteList.ToArray())
$restoreItems = @(
    foreach ($path in $restorePaths) {
        [PSCustomObject]@{
            SourcePath = $path
            LocalPath = Resolve-LocalRelativePath -Root $repoRoot -SourcePath $path
        }
    }
)
$deleteItems = @(
    foreach ($path in $deletePaths) {
        [PSCustomObject]@{
            SourcePath = $path
            LocalPath = Resolve-LocalRelativePath -Root $repoRoot -SourcePath $path
        }
    }
)

foreach ($item in @($restoreItems + $deleteItems)) {
    Get-SafeFullPath -Root $repoRoot -RelativePath $item.LocalPath | Out-Null

    if ($item.SourcePath -ne $item.LocalPath) {
        Write-Host "Mapped $($item.SourcePath) -> $($item.LocalPath)"
    }
}

if (-not $Force) {
    $blockedPaths = New-Object System.Collections.Generic.List[string]

    foreach ($item in @($restoreItems + $deleteItems)) {
        $pathStatus = @(Get-GitOutput -Arguments @('status', '--porcelain', '--', $item.LocalPath))
        if ($pathStatus.Count -gt 0) {
            $blockedPaths.Add($item.LocalPath) | Out-Null
        }
    }

    if ($blockedPaths.Count -gt 0) {
        Write-Host "These target paths already have uncommitted local changes:"
        foreach ($path in $blockedPaths) {
            Write-Host "  $path"
        }

        throw "Commit or stash those changes first, or rerun with -Force to overwrite these paths."
    }
}

foreach ($item in $restoreItems) {
    Copy-GitFileFromCommit -CommitSha $resolvedCommit -SourceRelativePath $item.SourcePath -DestinationRelativePath $item.LocalPath -Root $repoRoot
    Write-Host "Copied $($item.LocalPath)"
}

foreach ($item in $deleteItems) {
    $fullPath = Get-SafeFullPath -Root $repoRoot -RelativePath $item.LocalPath

    if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
        Remove-Item -LiteralPath $fullPath -Force
        Write-Host "Removed $($item.LocalPath)"
    } elseif (Test-Path -LiteralPath $fullPath) {
        throw "Refusing to remove a non-file path: $($item.LocalPath)"
    }
}

Write-Host ""
Write-Host "Done. Copied $($restorePaths.Count) file(s) from $($resolvedCommit.Substring(0, 12))."

if ($skippedDeletes.Count -gt 0) {
    Write-Host "Skipped $($skippedDeletes.Count) deleted file(s). Rerun with -RemoveDeletedFiles if you want deletions applied too."
}

Write-Host "Review the result with: git diff --stat"
