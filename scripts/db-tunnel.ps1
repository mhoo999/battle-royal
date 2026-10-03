# Opens 127.0.0.1:13306 on this PC as a tunnel to the RDS database, through the EC2
# instance over SSM. No port is open on AWS for this; see docs/AWS_DEPLOYMENT.md §11.
#
#   scripts\db-tunnel.cmd            (double-click), or
#   powershell -File scripts\db-tunnel.ps1 [-LocalPort 13306]
#
# Then connect MySQL Workbench to 127.0.0.1:13306 as battleroyal, SSL Required.
# Keep this window open; Ctrl+C or closing it ends the tunnel.
param(
    [int]$LocalPort = 13306
)

$AwsProfile = 'battle-royal'
$Region     = 'ap-northeast-2'
$Instance   = 'i-02d65fab4965cb3c4'
$Database   = 'battle-royal-db.c1caasea602e.ap-northeast-2.rds.amazonaws.com'

foreach ($tool in 'aws', 'session-manager-plugin') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host "$tool not found. Install it (docs/AWS_DEPLOYMENT.md §11) and open a new window." -ForegroundColor Red
        exit 1
    }
}

# aws login credentials expire; sign in again only when they have.
aws sts get-caller-identity --profile $AwsProfile --region $Region *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Signing in to AWS (browser: admin user + MFA)..." -ForegroundColor Yellow
    aws login --profile $AwsProfile --region $Region
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Sign-in failed." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Tunnel: 127.0.0.1:$LocalPort -> $Database`:3306 (via $Instance)" -ForegroundColor Cyan
Write-Host "Wait for 'Waiting for connections...', then connect Workbench. Ctrl+C to stop." -ForegroundColor Cyan

# Shorthand parameters rather than JSON: PowerShell 5 and 7 quote JSON for native
# programs differently, and this form needs no quotes at all.
aws ssm start-session --profile $AwsProfile --region $Region `
    --target $Instance `
    --document-name AWS-StartPortForwardingSessionToRemoteHost `
    --parameters "host=$Database,portNumber=3306,localPortNumber=$LocalPort"
