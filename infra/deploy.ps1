# PowerShell deploy script for Java CDK infra
# Usage: run from infra folder: .\deploy.ps1

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $scriptDir

Write-Host "1/6 - Building Java CDK (maven package)"

$mvnExec = $null
if (Test-Path (Join-Path $scriptDir 'mvnw')) {
  $mvnExec = (Join-Path $scriptDir 'mvnw')
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
  $mvnExec = 'mvn'
} elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) {
  $mvnExec = 'mvn.cmd'
} else {
  Write-Error "Maven not found. Install Maven or add mvnw to the project root."
  exit 1
}

& $mvnExec -DskipTests package
if ($LASTEXITCODE -ne 0) { Write-Error "Maven package failed"; exit 1 }

Write-Host "2/6 - Bootstrapping CDK (if required)"
$shadedJar = Get-ChildItem -Path (Join-Path $scriptDir 'target') -Filter '*-shaded.jar' | Select-Object -Last 1
if (-not $shadedJar) { Write-Error "Shaded jar not found"; exit 1 }
$appCmd = "java -jar `"$($shadedJar.FullName)`""

& cdk bootstrap --app $appCmd --require-approval never
if ($LASTEXITCODE -ne 0) { Write-Warning "cdk bootstrap returned non-zero, continuing" }

Write-Host "3/6 - Deploying EcrStack"
cdk deploy EcrStack --app $appCmd --outputs-file ecr-outputs.json --require-approval never
if ($LASTEXITCODE -ne 0) { Write-Error "CDK deploy EcrStack failed"; exit 1 }

Write-Host "4/6 - Parsing ECR repo URIs"
if (-Not (Test-Path ecr-outputs.json)) { Write-Error "ecr-outputs.json not found"; exit 1 }

$json = Get-Content ecr-outputs.json -Raw | ConvertFrom-Json

function Extract-RepoUri($text) {
  if ($text -match "(\d+\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com\/[a-zA-Z0-9\-\/]+)") {
    return $Matches[1]
  }
  return $null
}

$repoUris = @()
foreach ($stack in $json.PSObject.Properties) {
  foreach ($kv in $stack.Value.PSObject.Properties) {
    $uri = Extract-RepoUri $kv.Value
    if ($uri) { $repoUris += $uri }
  }
}

Write-Host "DEBUG repoUris detected:"
$repoUris | ForEach-Object { Write-Host " - $_" }

$productUri   = $repoUris | Where-Object { $_ -match "product-service" } | Select-Object -First 1
$inventoryUri = $repoUris | Where-Object { $_ -match "inventory-service" } | Select-Object -First 1
$orderUri     = $repoUris | Where-Object { $_ -match "order-service" } | Select-Object -First 1

Write-Host "`nDetected repos:"
Write-Host "Product: $productUri"
Write-Host "Inventory: $inventoryUri"
Write-Host "Order: $orderUri"

if (-not ($productUri -or $inventoryUri -or $orderUri)) {
  Write-Error "No valid ECR repo URIs found in outputs"
  exit 1
}

# Choose one repo to determine registry
if ($productUri) {
  $any = $productUri
} elseif ($inventoryUri) {
  $any = $inventoryUri
} else {
  $any = $orderUri
}

if ($any -match "(\d+\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com)") {
  $registry = $Matches[1]
} else {
  Write-Error "Could not parse ECR registry from: $any"
  exit 1
}

Write-Host "`nLogging into ECR registry $registry"
aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) { Write-Error "Docker login failed"; exit 1 }

function Build-And-Push($serviceName, $serviceRelPath, $repoUri) {
  if (-not $repoUri) {
    Write-Error "Repo URI missing for $serviceName"
    exit 1
  }

  $servicePath = Join-Path $scriptDir $serviceRelPath
  if (-not (Test-Path $servicePath)) {
    Write-Error "Service path not found: $servicePath"
    exit 1
  }

  $tag = "${repoUri}:latest"
  Write-Host "`nBuilding $serviceName -> $tag"
  docker build -t $tag $servicePath
  if ($LASTEXITCODE -ne 0) { Write-Error "Docker build failed for $serviceName"; exit 1 }

  Write-Host "Pushing $tag"
  docker push $tag
  if ($LASTEXITCODE -ne 0) { Write-Error "Docker push failed for $serviceName"; exit 1 }
}

Write-Host "`n5/6 - Building and pushing Docker images"
Build-And-Push "product-service" "..\services\product-service" $productUri
Build-And-Push "inventory-service" "..\services\inventory-service" $inventoryUri
Build-And-Push "order-service" "..\services\order-service" $orderUri

Write-Host "`n6/6 - Deploying remaining CDK stacks"
cdk deploy --all --app $appCmd --require-approval never
if ($LASTEXITCODE -ne 0) { Write-Error "CDK deploy failed"; exit 1 }

Write-Host "`n🚀 Deploy complete."
