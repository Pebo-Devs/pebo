<#
.SYNOPSIS
    Autonomous parity loop that drives the Pebo desktop -> Android port to completion.

.DESCRIPTION
    Reads a unit ledger (android-ledger.json), and for each "ready" unit (status pending,
    attempts remaining, all dependencies done) it:
      1. builds a focused prompt from unit-prompt.md,
      2. runs the GitHub Copilot CLI non-interactively to implement that single unit,
      3. runs the unit's gradle verify task,
      4. marks the unit done (verify exit 0) or schedules a retry / marks it failed.

    Build verification -- not the agent's self-report -- is the source of truth, mirroring
    the user's established parity-ledger workflow.

.EXAMPLE
    ./android-parity-loop.ps1 -ListOnly
    ./android-parity-loop.ps1 -DryRun
    ./android-parity-loop.ps1 -MaxIterations 6
    ./android-parity-loop.ps1 -OnlyUnit android-gradle-target
#>
[CmdletBinding()]
param(
    [string]$RepoRoot        = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$LedgerPath      = (Join-Path $PSScriptRoot 'android-ledger.json'),
    [string]$PromptTemplate  = (Join-Path $PSScriptRoot 'unit-prompt.md'),
    [string]$LogDir          = (Join-Path $PSScriptRoot 'logs'),
    [string]$Model           = 'claude-opus-4.8',
    [int]$MaxIterations      = 20,
    [int]$MaxConsecutiveFailures = 3,
    [string]$OnlyUnit        = '',
    [string[]]$CopilotExtraArgs = @(),
    [switch]$SkipVerify,
    [switch]$DryRun,
    [switch]$ListOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ---------------------------------------------------------------------------- helpers
function Write-Stamp([string]$msg, [string]$color = 'Gray') {
    $ts = (Get-Date).ToString('HH:mm:ss')
    Write-Host "[$ts] $msg" -ForegroundColor $color
}

function Read-Ledger([string]$path) {
    if (-not (Test-Path $path)) { throw "Ledger not found: $path" }
    $raw = Get-Content -LiteralPath $path -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) { throw "Ledger is empty: $path" }
    # PS7: '[]' | ConvertFrom-Json returns $null -- guard before use.
    $obj = $raw | ConvertFrom-Json
    if ($null -eq $obj)        { throw "Ledger parsed to null: $path" }
    if ($null -eq $obj.units)  { throw "Ledger has no 'units' array: $path" }
    return $obj
}

function Save-Ledger($ledger, [string]$path) {
    $json = $ledger | ConvertTo-Json -Depth 12
    # Atomic-ish write so a crash mid-write can't corrupt the ledger.
    $tmp = "$path.tmp"
    Set-Content -LiteralPath $tmp -Value $json -Encoding UTF8
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

function Set-Field($obj, [string]$name, $value) {
    if ($obj.PSObject.Properties[$name]) { $obj.$name = $value }
    else { $obj | Add-Member -NotePropertyName $name -NotePropertyValue $value }
}

function Get-Attempts($u)    { if ($u.PSObject.Properties['attempts'])    { [int]$u.attempts }    else { 0 } }
function Get-MaxAttempts($u) { if ($u.PSObject.Properties['maxAttempts']) { [int]$u.maxAttempts } else { 3 } }
function Get-DependsOn($u)   { if ($u.PSObject.Properties['dependsOn'] -and $u.dependsOn) { @($u.dependsOn) } else { @() } }

function Test-DepsDone($unit, $unitsById) {
    foreach ($dep in (Get-DependsOn $unit)) {
        $d = $unitsById[$dep]
        if ($null -eq $d -or $d.status -ne 'done') { return $false }
    }
    return $true
}

function Get-ReadyUnit($ledger, $unitsById) {
    foreach ($u in $ledger.units) {
        # Resume a unit interrupted mid-flight.
        if ($u.status -eq 'in_progress' -and (Get-Attempts $u) -lt (Get-MaxAttempts $u)) { return $u }
    }
    foreach ($u in $ledger.units) {
        if ($u.status -eq 'pending' -and (Get-Attempts $u) -lt (Get-MaxAttempts $u) -and (Test-DepsDone $u $unitsById)) {
            return $u
        }
    }
    return $null
}

function Show-Ledger($ledger) {
    Write-Host ''
    Write-Host ("{0,-32} {1,-12} {2,-8} {3}" -f 'UNIT', 'STATUS', 'ATTEMPTS', 'TITLE') -ForegroundColor Cyan
    Write-Host ('-' * 92) -ForegroundColor DarkGray
    foreach ($u in $ledger.units) {
        $color = switch ($u.status) {
            'done'        { 'Green' }
            'in_progress' { 'Yellow' }
            'failed'      { 'Red' }
            default       { 'Gray' }
        }
        Write-Host ("{0,-32} {1,-12} {2,-8} {3}" -f `
            $u.id, $u.status, ("{0}/{1}" -f (Get-Attempts $u), (Get-MaxAttempts $u)), $u.title) -ForegroundColor $color
    }
    $done  = @($ledger.units | Where-Object { $_.status -eq 'done' }).Count
    $total = @($ledger.units).Count
    Write-Host ('-' * 92) -ForegroundColor DarkGray
    Write-Host ("Progress: {0}/{1} units done" -f $done, $total) -ForegroundColor Cyan
    Write-Host ''
}

function Build-Prompt($template, $ledger, $unit) {
    $ctx = (@($ledger.globalContext) | ForEach-Object { "- $_" }) -join "`n"
    $deps = (Get-DependsOn $unit) -join ', '
    if ([string]::IsNullOrWhiteSpace($deps)) { $deps = '(none)' }
    $verify = if ($unit.PSObject.Properties['verify'] -and $unit.verify) { $unit.verify } else { $ledger.defaultVerify }
    $out = $template
    $out = $out.Replace('{{GLOBAL_CONTEXT}}',   $ctx)
    $out = $out.Replace('{{UNIT_ID}}',          [string]$unit.id)
    $out = $out.Replace('{{UNIT_TITLE}}',       [string]$unit.title)
    $out = $out.Replace('{{DEPENDS_ON}}',       $deps)
    $out = $out.Replace('{{UNIT_DESCRIPTION}}', [string]$unit.description)
    $out = $out.Replace('{{UNIT_ACCEPTANCE}}',  [string]$unit.acceptance)
    $out = $out.Replace('{{VERIFY}}',           $verify)
    return $out
}

function Invoke-Agent([string]$copilot, [string]$prompt, [string]$logFile) {
    $argList = @(
        '-p', $prompt,
        '--allow-all-tools',
        '--no-color',
        '--log-level', 'none',
        '-C', $RepoRoot,
        '--model', $Model
    ) + $CopilotExtraArgs
    Write-Stamp "Running agent ($Model)..." 'Yellow'
    & $copilot @argList 2>&1 | Tee-Object -FilePath $logFile
    $code = $LASTEXITCODE
    Write-Stamp "Agent exited with code $code" 'DarkGray'
    return $code
}

function Invoke-Verify([string]$verifyTask, [string]$logFile) {
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    $tasks = $verifyTask -split '\s+' | Where-Object { $_ }
    Write-Stamp "Verifying: gradlew $($tasks -join ' ')" 'Yellow'
    & $gradlew @tasks '--console=plain' 2>&1 | Tee-Object -FilePath $logFile
    $code = $LASTEXITCODE
    Write-Stamp "Verify exit code $code" 'DarkGray'
    return $code
}

# ---------------------------------------------------------------------------- preflight
$copilotCmd = (Get-Command copilot -ErrorAction SilentlyContinue)
$copilot = if ($copilotCmd) { $copilotCmd.Source } else { 'copilot' }
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$template = Get-Content -LiteralPath $PromptTemplate -Raw

$ledger = Read-Ledger $LedgerPath

if ($ListOnly) { Show-Ledger $ledger; return }

Write-Stamp "RepoRoot : $RepoRoot"
Write-Stamp "Ledger   : $LedgerPath"
Write-Stamp "Model    : $Model"
Write-Stamp "Copilot  : $copilot"
Show-Ledger $ledger

# ---------------------------------------------------------------------------- main loop
$iteration = 0
$consecutiveFailures = 0

while ($iteration -lt $MaxIterations) {
    # Reload each pass so external edits to the ledger are honored.
    $ledger = Read-Ledger $LedgerPath
    $unitsById = @{}
    foreach ($u in $ledger.units) { $unitsById[$u.id] = $u }

    if ($OnlyUnit) {
        $unit = $unitsById[$OnlyUnit]
        if ($null -eq $unit) { throw "Unit not found: $OnlyUnit" }
        if ($unit.status -eq 'done') { Write-Stamp "Unit '$OnlyUnit' already done." 'Green'; break }
    } else {
        $unit = Get-ReadyUnit $ledger $unitsById
    }

    if ($null -eq $unit) {
        Write-Stamp 'No ready units remain. Loop complete.' 'Green'
        break
    }

    $iteration++
    $verify = if ($unit.PSObject.Properties['verify'] -and $unit.verify) { $unit.verify } else { $ledger.defaultVerify }
    Write-Host ''
    Write-Stamp ("===== Iteration {0}/{1} :: unit '{2}' =====" -f $iteration, $MaxIterations, $unit.id) 'Cyan'
    Write-Stamp $unit.title 'Cyan'

    $prompt = Build-Prompt $template $ledger $unit
    $stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
    $agentLog  = Join-Path $LogDir ("{0}_{1}_agent.log"  -f $stamp, $unit.id)
    $verifyLog = Join-Path $LogDir ("{0}_{1}_verify.log" -f $stamp, $unit.id)

    if ($DryRun) {
        Write-Stamp 'DryRun: would run the agent with this prompt:' 'Magenta'
        Write-Host '----------------------------------------------------------------' -ForegroundColor DarkGray
        Write-Host $prompt
        Write-Host '----------------------------------------------------------------' -ForegroundColor DarkGray
        Write-Stamp ("DryRun: would then verify with 'gradlew {0}'" -f $verify) 'Magenta'
        if ($OnlyUnit) { break }
        # In dry run, don't mutate; avoid infinite loop by stopping after first.
        break
    }

    # Mark in-progress + bump attempts, persist immediately (crash-safe).
    Set-Field $unit 'status' 'in_progress'
    Set-Field $unit 'attempts' ((Get-Attempts $unit) + 1)
    Set-Field $unit 'lastStartedUtc' ((Get-Date).ToUniversalTime().ToString('o'))
    Save-Ledger $ledger $LedgerPath

    $agentExit = Invoke-Agent $copilot $prompt $agentLog

    $verifyExit = 0
    if ($SkipVerify) {
        Write-Stamp 'SkipVerify set -- trusting agent run.' 'DarkYellow'
    } else {
        $verifyExit = Invoke-Verify $verify $verifyLog
    }

    # Reload + relocate the unit (the agent may have changed nothing in the ledger,
    # but reloading keeps us consistent if it did).
    $ledger = Read-Ledger $LedgerPath
    $unitsById = @{}
    foreach ($u in $ledger.units) { $unitsById[$u.id] = $u }
    $unit = $unitsById[$unit.id]

    if ($verifyExit -eq 0) {
        Set-Field $unit 'status' 'done'
        Set-Field $unit 'notes' ("Verified OK via '{0}' (agent exit {1})." -f $verify, $agentExit)
        Set-Field $unit 'lastVerifiedUtc' ((Get-Date).ToUniversalTime().ToString('o'))
        Save-Ledger $ledger $LedgerPath
        $consecutiveFailures = 0
        Write-Stamp ("UNIT DONE: {0}" -f $unit.id) 'Green'
    } else {
        $attempts = Get-Attempts $unit
        $max = Get-MaxAttempts $unit
        if ($attempts -ge $max) {
            Set-Field $unit 'status' 'failed'
            Set-Field $unit 'notes' ("Verify failed after {0} attempts. See {1}" -f $attempts, $verifyLog)
            Write-Stamp ("UNIT FAILED (max attempts): {0}" -f $unit.id) 'Red'
        } else {
            Set-Field $unit 'status' 'pending'
            Set-Field $unit 'notes' ("Verify failed (attempt {0}/{1}). Will retry. See {2}" -f $attempts, $max, $verifyLog)
            Write-Stamp ("UNIT RETRY LATER: {0} ({1}/{2})" -f $unit.id, $attempts, $max) 'DarkYellow'
        }
        Save-Ledger $ledger $LedgerPath
        $consecutiveFailures++
        if ($consecutiveFailures -ge $MaxConsecutiveFailures) {
            Write-Stamp ("Stopping: {0} consecutive verify failures." -f $consecutiveFailures) 'Red'
            break
        }
    }

    if ($OnlyUnit) { break }
}

Write-Host ''
$ledger = Read-Ledger $LedgerPath
Show-Ledger $ledger
Write-Stamp 'Loop finished.' 'Cyan'
