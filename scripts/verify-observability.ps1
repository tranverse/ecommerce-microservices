param(
    [string]$ComposeEnvFile = ".env"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ComposeEnvFile -PathType Leaf)) {
    throw "Compose env file '$ComposeEnvFile' does not exist. Run scripts/bootstrap-local-env.ps1 first."
}

function Get-ComposeHttpUrl {
    param(
        [string]$Service,
        [int]$ContainerPort
    )

    $binding = & docker compose --env-file $ComposeEnvFile port $Service $ContainerPort 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Could not resolve the published port for $Service`: $($binding -join [Environment]::NewLine)"
    }

    $firstBinding = @($binding)[0].Trim()
    if ($firstBinding -notmatch ':(?<port>\d+)$') {
        throw "Docker Compose returned an invalid port binding for $Service`: $firstBinding"
    }

    return "http://localhost:$($Matches.port)"
}

function Wait-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$Attempts = 30,
        [int]$DelaySeconds = 2
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
            if ($response.StatusCode -eq 200) {
                return
            }

            $lastError = "$Name returned HTTP $($response.StatusCode)"
        }
        catch {
            $lastError = $_.Exception.Message
        }

        if ($attempt -lt $Attempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "$Name was not ready after $Attempts attempts. Last error: $lastError"
}

$prometheusUrl = Get-ComposeHttpUrl -Service "prometheus" -ContainerPort 9090
$tempoUrl = Get-ComposeHttpUrl -Service "tempo" -ContainerPort 3200
$lokiUrl = Get-ComposeHttpUrl -Service "loki" -ContainerPort 3100
$alloyUrl = Get-ComposeHttpUrl -Service "alloy" -ContainerPort 12345
$grafanaUrl = Get-ComposeHttpUrl -Service "grafana" -ContainerPort 3000

Wait-HttpOk -Name "Prometheus" -Url "$prometheusUrl/-/ready"
Wait-HttpOk -Name "Tempo" -Url "$tempoUrl/ready"
Wait-HttpOk -Name "Loki" -Url "$lokiUrl/ready"
Wait-HttpOk -Name "Alloy" -Url "$alloyUrl/-/ready"
Wait-HttpOk -Name "Grafana" -Url "$grafanaUrl/api/health"

$healthyTargets = 0
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $targets = Invoke-RestMethod -Uri "$prometheusUrl/api/v1/targets" -TimeoutSec 10
    $healthyTargets = @($targets.data.activeTargets | Where-Object { $_.health -eq "up" }).Count
    if ($healthyTargets -ge 8) {
        break
    }
    if ($attempt -lt 30) {
        Start-Sleep -Seconds 2
    }
}
if ($healthyTargets -lt 8) {
    throw "Prometheus reports only $healthyTargets healthy application targets after waiting; expected 8"
}

$traceCount = 0
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $traces = Invoke-RestMethod -Uri "$tempoUrl/api/search?limit=20" -TimeoutSec 10
    $traceCount = @($traces.traces).Count
    if ($traceCount -gt 0) {
        break
    }
    if ($attempt -lt 30) {
        Start-Sleep -Seconds 2
    }
}
if ($traceCount -eq 0) {
    throw "Tempo did not receive any application traces after waiting"
}

$centralizedLogServices = 0
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $serviceLabels = Invoke-RestMethod -Uri "$lokiUrl/loki/api/v1/label/service/values" -TimeoutSec 10
    $centralizedLogServices = @($serviceLabels.data).Count
    if ($centralizedLogServices -ge 8) {
        break
    }
    if ($attempt -lt 30) {
        Start-Sleep -Seconds 2
    }
}
if ($centralizedLogServices -lt 8) {
    throw "Loki reports logs for only $centralizedLogServices application services after waiting; expected 8"
}

$composeServices = docker compose --env-file $ComposeEnvFile ps --services --filter status=running
if ($LASTEXITCODE -ne 0) {
    throw "docker compose ps failed"
}

[PSCustomObject]@{
    prometheus = "UP"
    tempo = "UP"
    loki = "UP"
    alloy = "UP"
    grafana = "UP"
    healthyApplicationTargets = $healthyTargets
    searchableTraces = $traceCount
    centralizedLogServices = $centralizedLogServices
    runningComposeServices = @($composeServices).Count
} | ConvertTo-Json
