[CmdletBinding()]
param(
    [string]$OutputPath = ".env",
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-RandomSecret {
    $bytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Get-EnvironmentOrRandomSecret {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    $configured = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($configured) -and
        -not $configured.StartsWith("replace_with_", [StringComparison]::Ordinal)) {
        return $configured
    }
    return New-RandomSecret
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $repositoryRoot ".env.example"
$resolvedOutputPath = if ([IO.Path]::IsPathRooted($OutputPath)) {
    [IO.Path]::GetFullPath($OutputPath)
} else {
    [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputPath))
}
$outputDirectory = Split-Path -Parent $resolvedOutputPath

if (-not (Test-Path -LiteralPath $examplePath -PathType Leaf)) {
    throw "Environment template '$examplePath' does not exist."
}
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    throw "Output directory '$outputDirectory' does not exist."
}
if ((Test-Path -LiteralPath $resolvedOutputPath) -and -not $Force) {
    throw "Refusing to overwrite '$resolvedOutputPath'. Use -Force only when intentional key/token invalidation is acceptable."
}

$privateKey = [Environment]::GetEnvironmentVariable("AUTH_JWT_PRIVATE_KEY_BASE64")
$publicKey = [Environment]::GetEnvironmentVariable("AUTH_JWT_PUBLIC_KEY_BASE64")
$hasPrivateKey = -not [string]::IsNullOrWhiteSpace($privateKey)
$hasPublicKey = -not [string]::IsNullOrWhiteSpace($publicKey)
if ($hasPrivateKey -ne $hasPublicKey) {
    throw "AUTH_JWT_PRIVATE_KEY_BASE64 and AUTH_JWT_PUBLIC_KEY_BASE64 must be supplied together."
}

if (-not $hasPrivateKey) {
    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $rsa.KeySize = 2048
        $privateKey = [Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())
        $publicKey = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
    } finally {
        $rsa.Dispose()
    }
}

try {
    $publicKeyBytes = [Convert]::FromBase64String($publicKey)
} catch {
    throw "AUTH_JWT_PUBLIC_KEY_BASE64 is not valid Base64."
}
$publicKeyHash = [System.Security.Cryptography.SHA256]::HashData($publicKeyBytes)
$fingerprint = [BitConverter]::ToString($publicKeyHash).Replace("-", "").Substring(0, 16).ToLowerInvariant()
$configuredKeyId = [Environment]::GetEnvironmentVariable("AUTH_JWT_KEY_ID")
$keyId = if ([string]::IsNullOrWhiteSpace($configuredKeyId)) {
    "ecommerce-local-$fingerprint"
} else {
    $configuredKeyId
}

$values = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
foreach ($secretName in @(
    "POSTGRES_SUPERUSER_PASSWORD",
    "AUTH_DB_PASSWORD",
    "USER_DB_PASSWORD",
    "PRODUCT_DB_PASSWORD",
    "INVENTORY_DB_PASSWORD",
    "ORDER_DB_PASSWORD",
    "PAYMENT_DB_PASSWORD",
    "NOTIFICATION_DB_PASSWORD",
    "GRAFANA_ADMIN_PASSWORD"
)) {
    $values[$secretName] = Get-EnvironmentOrRandomSecret -Name $secretName
}
$values["AUTH_JWT_KEY_ID"] = $keyId
$values["AUTH_JWT_PRIVATE_KEY_BASE64"] = $privateKey
$values["AUTH_JWT_PUBLIC_KEY_BASE64"] = $publicKey
$values["AUTH_JWT_REQUIRE_CONFIGURED_KEY"] = "true"

$outputLines = foreach ($line in [IO.File]::ReadAllLines($examplePath)) {
    if ($line -match '^(?<name>[A-Z][A-Z0-9_]*)=' -and $values.ContainsKey($Matches.name)) {
        "$($Matches.name)=$($values[$Matches.name])"
    } else {
        $line
    }
}

$temporaryPath = "$resolvedOutputPath.$([Guid]::NewGuid().ToString('N')).tmp"
try {
    $utf8WithoutBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllLines($temporaryPath, $outputLines, $utf8WithoutBom)
    Move-Item -LiteralPath $temporaryPath -Destination $resolvedOutputPath -Force:$Force
} finally {
    if (Test-Path -LiteralPath $temporaryPath) {
        Remove-Item -LiteralPath $temporaryPath -Force
    }
}

if ([Environment]::OSVersion.Platform -eq [PlatformID]::Unix) {
    & chmod 600 -- $resolvedOutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Could not restrict permissions on '$resolvedOutputPath'."
    }
}

Write-Output "Created '$resolvedOutputPath' with local secrets and stable JWT key id '$keyId'. Secret values were not printed."
