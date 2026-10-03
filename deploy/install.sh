#!/usr/bin/env bash
# Installs one release on the EC2 instance. Sent by the deploy job through SSM and run
# as root; see docs/AWS_DEPLOYMENT.md Phase 10. The manual equivalent is Phase 9.
#
#   install.sh <bucket> <commit>
#
# Keeps the running jar as app.jar.prev, and puts it back if the new one does not
# answer on 127.0.0.1:8080 within two minutes. Exits non-zero in that case, so the
# deploy job fails even though the server is up again.
set -euo pipefail

BUCKET="$1"
COMMIT="$2"
REGION=ap-northeast-2
APP=/opt/battle-royal/app.jar
NEW="/tmp/battle-royal-$COMMIT.jar"

healthy() {
  for _ in $(seq 1 60); do
    curl -sf -o /dev/null http://127.0.0.1:8080/ && return 0
    sleep 2
  done
  return 1
}

put() {
  install -o battleroyal -g battleroyal -m 644 "$1" "$APP"
  systemctl restart battle-royal
}

aws s3 cp "s3://$BUCKET/releases/$COMMIT/app.jar" "$NEW" --region "$REGION" --only-show-errors

if [ -f "$APP" ]; then
  cp "$APP" "$APP.prev"
fi
put "$NEW"
rm -f "$NEW"

if healthy; then
  echo "deployed $COMMIT"
  exit 0
fi

echo "$COMMIT did not come up; rolling back" >&2
journalctl -u battle-royal -n 40 --no-pager >&2 || true
if [ -f "$APP.prev" ]; then
  put "$APP.prev"
  if healthy; then
    echo "rolled back to the previous jar" >&2
  else
    echo "the previous jar did not come up either" >&2
  fi
fi
exit 1
