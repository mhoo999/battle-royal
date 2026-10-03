param([Parameter(Mandatory)] [string]$Sql)
# Usage (PowerShell, after `aws login --profile battle-royal`):
#   scripts\db-query.ps1 -Sql "SELECT nickname, score FROM game_result ORDER BY score DESC LIMIT 10"
#
# It runs whatever it is given, DELETE included, on the live database. Look before
# you change anything: SELECT the rows first, delete by id rather than by a non-ASCII
# name, and remember there is no undo short of restoring a snapshot.
# Runs one SQL statement on the RDS database from the EC2 instance over SSM, with the
# app's own credentials from /etc/battle-royal/env, and prints the result.
# The env file is read as text, not sourced: DB_URL contains '&', which bash would run
# as a background job and leave the variable empty.
$script = @'
val() { grep "^$1=" /etc/battle-royal/env | cut -d= -f2-; }
host=$(val DB_URL | sed -E 's#jdbc:mysql://([^:/?]+).*#\1#')
MYSQL_PWD="$(val DB_PASSWORD)" mysql --ssl -h "$host" -u "$(val DB_USERNAME)" battleroyal --table -e "$SQL"
'@
$script = "SQL=`"$($Sql -replace '"', '\"')`"`n" + $script
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($script))
$params = "commands=echo $b64 | base64 -d | bash"
$id = aws ssm send-command --profile battle-royal --region ap-northeast-2 `
    --instance-ids i-02d65fab4965cb3c4 --document-name AWS-RunShellScript `
    --comment "db query" --parameters $params --query Command.CommandId --output text
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $status = aws ssm get-command-invocation --profile battle-royal --region ap-northeast-2 `
        --command-id $id --instance-id i-02d65fab4965cb3c4 --query Status --output text 2>$null
    if ($status -and $status -notin 'Pending', 'InProgress', 'Delayed') { break }
}
"status: $status"
aws ssm get-command-invocation --profile battle-royal --region ap-northeast-2 `
    --command-id $id --instance-id i-02d65fab4965cb3c4 `
    --query '[StandardOutputContent, StandardErrorContent]' --output text
