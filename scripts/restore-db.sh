#!/usr/bin/env bash
# backup-db.sh로 만든 백업 파일로 MySQL 데이터베이스를 덮어쓴다.
# 사용법: scripts/restore-db.sh <백업파일.sql.gz>
set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -ne 1 ]; then
  echo "사용법: $0 <백업파일.sql.gz>"
  exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "$BACKUP_FILE" ]; then
  echo "파일을 찾을 수 없습니다: $BACKUP_FILE"
  exit 1
fi

set -a
source .env
set +a

read -r -p "'${DB_NAME}' 데이터베이스를 '${BACKUP_FILE}' 내용으로 덮어씁니다. 계속하려면 yes를 입력하세요: " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "취소했습니다."
  exit 1
fi

export MYSQL_PWD="$DB_PASSWORD"
gunzip -c "$BACKUP_FILE" | docker compose exec -T -e MYSQL_PWD mysql \
  mysql -u "$DB_USER" "$DB_NAME"
unset MYSQL_PWD

echo "복원 완료"
