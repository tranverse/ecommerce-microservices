[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "inventory.commands.v1",
        "inventory.events.v1",
        "payment.commands.v1",
        "payment.events.v1",
        "order.events.v1"
    )]
    [string]$OriginalTopic,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 2147483647)]
    [int]$Partition,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 9223372036854775807)]
    [long]$Offset,

    [string]$ComposeEnvFile = ".env.example",

    [switch]$Execute
)

$ErrorActionPreference = "Stop"

$dltTopic = "$OriginalTopic.DLT"
$separator = "<|ECOMMERCE-DLT-KEY|>"

$runningServices = @(& docker compose --env-file $ComposeEnvFile ps --services --filter status=running)
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect the Docker Compose environment"
}
if ($runningServices -notcontains "kafka") {
    throw "The Compose Kafka service is not running"
}

Write-Output "Inspecting $dltTopic partition=$Partition offset=$Offset"
& docker compose --env-file $ComposeEnvFile exec -T kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server kafka:19092 `
    --topic $dltTopic `
    --partition $Partition `
    --offset $Offset `
    --max-messages 1 `
    --timeout-ms 10000 `
    --property print.key=true `
    --property print.value=false `
    --property print.headers=false `
    --property print.partition=true `
    --property print.offset=true
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect the requested DLT record"
}

if (-not $Execute) {
    Write-Output "Dry run only. The payload and failure headers were not printed. Investigate and fix the cause, then rerun with -Execute to replay exactly this record."
    exit 0
}

$recordLines = @(& docker compose --env-file $ComposeEnvFile exec -T kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server kafka:19092 `
    --topic $dltTopic `
    --partition $Partition `
    --offset $Offset `
    --max-messages 1 `
    --timeout-ms 10000 `
    --property print.key=true `
    --property print.value=true `
    --property print.headers=false `
    --property "key.separator=$separator")
if ($LASTEXITCODE -ne 0) {
    throw "Could not read the requested DLT record for replay"
}

$records = @($recordLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($records.Count -ne 1) {
    throw "Expected exactly one DLT record but read $($records.Count)"
}
if (-not $records[0].Contains($separator)) {
    throw "The DLT record does not contain the expected key/value separator"
}

$records[0] | & docker compose --env-file $ComposeEnvFile exec -T kafka `
    /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server kafka:19092 `
    --topic $OriginalTopic `
    --sync `
    --producer-property acks=all `
    --property parse.key=true `
    --property "key.separator=$separator"
if ($LASTEXITCODE -ne 0) {
    throw "Kafka rejected the replayed record; the DLT record remains unchanged"
}

Write-Output "Replayed $dltTopic partition=$Partition offset=$Offset to $OriginalTopic. The DLT record was retained for audit."
