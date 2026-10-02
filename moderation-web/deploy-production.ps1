param(
    [Parameter(Mandatory = $true)]
    [string] $TokenFile,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$webDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$wrangler = Join-Path $webDirectory 'node_modules/.bin/wrangler.cmd'
$secretsFile = Join-Path ([IO.Path]::GetTempPath()) ("enthusia-web-keys-{0}.json" -f [guid]::NewGuid().ToString('N'))

if (-not (Test-Path -LiteralPath $wrangler -PathType Leaf)) {
    throw 'Install moderation-web dependencies with npm ci first.'
}
if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) {
    throw 'Production bot token file is unavailable.'
}

function Get-DerivedKeyHex([string] $domain, [string] $token) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($domain + [char]0 + $token)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

try {
    $botToken = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $TokenFile).Path).Trim()
    if ([string]::IsNullOrWhiteSpace($botToken) -or $botToken -match '\s') {
        throw 'Production bot token file format is invalid.'
    }

    $secrets = @{
        LAUNCH_SIGNING_KEY_HEX = Get-DerivedKeyHex 'enthusia-staff-moderation-launch-v1' $botToken
        READ_API_SIGNING_KEY_HEX = Get-DerivedKeyHex 'enthusia-staff-moderation-read-api-v1' $botToken
    }
    [IO.File]::WriteAllText($secretsFile, ($secrets | ConvertTo-Json -Compress),
        (New-Object Text.UTF8Encoding($false)))
    $botToken = $null
    $secrets = $null

    Push-Location $webDirectory
    try {
        $env:MODERATION_WEB_ENVIRONMENT = 'production'
        $env:CLOUDFLARE_ACCOUNT_ID = '83982f6d6277634f57d216e6a7f24125'
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Production website build failed.' }
        if ($DryRun) {
            & $wrangler deploy --config wrangler.production.jsonc --secrets-file $secretsFile --dry-run
        } else {
            & $wrangler deploy --config wrangler.production.jsonc --secrets-file $secretsFile
        }
        if ($LASTEXITCODE -ne 0) { throw 'Cloudflare Worker deployment failed.' }
    } finally {
        Pop-Location
        Remove-Item Env:MODERATION_WEB_ENVIRONMENT -ErrorAction SilentlyContinue
        Remove-Item Env:CLOUDFLARE_ACCOUNT_ID -ErrorAction SilentlyContinue
    }
} finally {
    Remove-Item -LiteralPath $secretsFile -Force -ErrorAction SilentlyContinue
    $botToken = $null
    $secrets = $null
}
