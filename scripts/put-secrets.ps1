# Puts the game server's secrets into Parameter Store, where a server reads them at
# every start (infra/modules/game-stack, fetch-env.sh). Run it yourself: the values are
# typed here, never shown, and never pass through Terraform or the repository.
#
#   aws login --profile battle-royal --region ap-northeast-2
#   ./scripts/put-secrets.ps1                 # production: /battle-royal/prod
#
# The values are the ones in /etc/battle-royal/env on the running server (Session
# Manager: sudo cat /etc/battle-royal/env) or in your password manager. Re-running it
# overwrites them; a running server picks a new value up at its next restart.
param(
    [string]$Environment = 'prod'
)

$ErrorActionPreference = 'Stop'
$path = "/battle-royal/$Environment"
$names = 'DB_PASSWORD', 'GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET'

foreach ($name in $names) {
    $secure = Read-Host -AsSecureString "$name"
    $value = [System.Net.NetworkCredential]::new('', $secure).Password
    if ([string]::IsNullOrEmpty($value)) {
        Write-Host "  skipped (empty)"
        continue
    }
    aws ssm put-parameter --profile battle-royal --region ap-northeast-2 `
        --name "$path/$name" --type SecureString --overwrite --value $value `
        --query Version --output text | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "put-parameter failed for $name" }
    Write-Host "  $path/$name stored"
}

# Names only, never values.
aws ssm get-parameters-by-path --profile battle-royal --region ap-northeast-2 `
    --path $path --query 'Parameters[].Name' --output text
