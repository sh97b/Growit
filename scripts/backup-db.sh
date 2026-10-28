#!/usr/bin/env bash
# MySQL 컨테이너를 mysqldump로 백업해 gzip으로 압축하고, 7일 지난 백업은 삭제한다.
set -euo pipefail

cd "$(dirname "$0")/.."

set -a
source .env
set +a

BACKUP_DIR="$HOME/backups"
mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d-%H%M)
BACKUP_FILE="$BACKUP_DIR/growit-${TIMESTAMP}.sql.gz"

export MYSQL_PWD="$DB_PASSWORD"
docker compose exec -T -e MYSQL_PWD mysql \
  mysqldump --single-transaction --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
  | gzip > "$BACKUP_FILE"
unset MYSQL_PWD

echo "백업 완료: $BACKUP_FILE"

find "$BACKUP_DIR" -name "growit-*.sql.gz" -mtime +7 -delete
