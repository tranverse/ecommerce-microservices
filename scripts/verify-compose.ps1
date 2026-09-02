[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$ProductBaseUrl = "http://localhost:8083",
    [string]$InventoryBaseUrl = "http://localhost:8084",
    [string]$ComposeEnvFile = ".env",
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-DatabaseValue {
    param(
        [Parameter(Mandatory)]
        [string]$Database,
        [Parameter(Mandatory)]
        [string]$Query
    )

    $output = & docker compose --env-file $ComposeEnvFile exec -T postgres `
        psql -U postgres -d $Database -tAc $Query 2>&1

    if ($LASTEXITCODE -ne 0) {
        throw "Database verification failed for '$Database': $($output -join [Environment]::NewLine)"
    }

    return ($output -join [Environment]::NewLine).Trim()
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot

try {
    if (-not (Test-Path -LiteralPath $ComposeEnvFile)) {
        throw "Compose env file '$ComposeEnvFile' does not exist. Run scripts/bootstrap-local-env.ps1 first."
    }

    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 10)
    $customerPassword = "Compose-$suffix-Aa1!"

    $product = Invoke-RestMethod `
        -Method Post `
        -Uri "$ProductBaseUrl/api/v1/products" `
        -ContentType "application/json" `
        -Body (@{
            sku = "COMPOSE-$suffix"
            name = "Compose Verification Product"
            description = "Created by scripts/verify-compose.ps1"
            price = 49.90
            currency = "USD"
            status = "ACTIVE"
        } | ConvertTo-Json)

    Invoke-RestMethod `
        -Method Put `
        -Uri "$InventoryBaseUrl/api/v1/inventory/items/$($product.id)" `
        -ContentType "application/json" `
        -Body (@{ totalQuantity = 10 } | ConvertTo-Json) | Out-Null

    $tokens = Invoke-RestMethod `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/v1/auth/register" `
        -ContentType "application/json" `
        -Body (@{
            email = "compose-$suffix@example.test"
            password = $customerPassword
        } | ConvertTo-Json)

    $authorization = @{ Authorization = "Bearer $($tokens.accessToken)" }
    $orderHeaders = @{
        Authorization = $authorization.Authorization
        "Idempotency-Key" = "compose-$suffix"
    }

    $order = Invoke-RestMethod `
        -Method Post `
        -Uri "$GatewayBaseUrl/api/v1/orders" `
        -Headers $orderHeaders `
        -ContentType "application/json" `
        -Body (@{
            items = @(
                @{
                    productId = $product.id
                    quantity = 2
                }
            )
        } | ConvertTo-Json -Depth 4)

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $order = Invoke-RestMethod `
            -Method Get `
            -Uri "$GatewayBaseUrl/api/v1/orders/$($order.id)" `
            -Headers $authorization

        if ($order.status -in @("CONFIRMED", "CANCELLED")) {
            break
        }

        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    if ($order.status -ne "CONFIRMED") {
        throw "Expected Order CONFIRMED but found '$($order.status)' with reason '$($order.failureReason)'."
    }

    $orderId = [Guid]::Parse($order.id)
    do {
        $inventoryStatus = Get-DatabaseValue `
            -Database "inventory_db" `
            -Query "SELECT status FROM inventory_reservations WHERE order_id = '$orderId';"
        $paymentStatus = Get-DatabaseValue `
            -Database "payment_db" `
            -Query "SELECT status FROM payments WHERE order_id = '$orderId';"
        $notificationStatus = Get-DatabaseValue `
            -Database "notification_db" `
            -Query "SELECT status FROM notifications WHERE order_id = '$orderId';"

        if ($inventoryStatus -eq "CONFIRMED" -and
            $paymentStatus -eq "COMPLETED" -and
            $notificationStatus -eq "SENT") {
            break
        }

        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    if ($inventoryStatus -ne "CONFIRMED") {
        throw "Expected Inventory CONFIRMED but found '$inventoryStatus'."
    }
    if ($paymentStatus -ne "COMPLETED") {
        throw "Expected Payment COMPLETED but found '$paymentStatus'."
    }
    if ($notificationStatus -ne "SENT") {
        throw "Expected Notification SENT but found '$notificationStatus'."
    }

    [ordered]@{
        productId = $product.id
        orderId = $order.id
        orderStatus = $order.status
        inventoryStatus = $inventoryStatus
        paymentStatus = $paymentStatus
        notificationStatus = $notificationStatus
        totalAmount = $order.totalAmount
        currency = $order.currency
    } | ConvertTo-Json
}
finally {
    Pop-Location
}
