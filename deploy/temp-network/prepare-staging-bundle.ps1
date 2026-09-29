param(
    [string]$OutputDirectory = '.deploy-work/artifacts'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$SourceSha = '6a2db9f9cfb2516cff57b323f1956ccc98cff5c8'
$PaperSha256 = 'eb42779b06fd2f40084e7dc7b65bd6525df16a5ba7a14d0da75fefb92e0a0057'
$VelocitySha256 = '6dc73e57ea12be142b2f0d8b7214a1b9810b4d2545aab7da586196a8cf8c8965'
$PaperUrl = 'https://github.com/wsg138/EnthusiaStaff/releases/download/staff-platform-staging/EnthusiaStaff-Paper.jar'
$VelocityUrl = 'https://github.com/wsg138/EnthusiaStaff/releases/download/staff-platform-staging/EnthusiaStaff-Velocity.jar'

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$paper = Join-Path $OutputDirectory 'EnthusiaStaff-Paper.jar'
$velocity = Join-Path $OutputDirectory 'EnthusiaStaff-Velocity.jar'

Invoke-WebRequest -UseBasicParsing -Uri $PaperUrl -OutFile $paper
Invoke-WebRequest -UseBasicParsing -Uri $VelocityUrl -OutFile $velocity

function Assert-Sha256([string]$Path, [string]$Expected) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path. Expected $Expected, got $actual"
    }
}

Assert-Sha256 $paper $PaperSha256
Assert-Sha256 $velocity $VelocitySha256

@"
source_sha=$SourceSha
paper_sha256=$PaperSha256
velocity_sha256=$VelocitySha256
"@ | Set-Content -Encoding UTF8 (Join-Path $OutputDirectory 'PROVENANCE.txt')

Write-Host "Prepared verified EnthusiaStaff artifacts from $SourceSha"
Write-Host $paper
Write-Host $velocity
