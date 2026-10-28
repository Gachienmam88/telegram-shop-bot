#!/usr/bin/env bash
# Backup PostgreSQL của shop bot, giữ 7 bản gần nhất.
# Cron hằng ngày lúc 3h sáng:
#   0 3 * * * /opt/telegram-shop-bot/deploy/backup-db.sh >> /var/log/shopbot-backup.log 2>&1
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
KEEP="${KEEP:-7}"
COMPOSE="docker compose -f $APP_DIR/docker-compose.prod.yml --project-directory $APP_DIR"

# Đọc DB_USER / DB_NAME từ .env (mặc định shopbot)
DB_USER="$(grep -E '^DB_USER=' "$APP_DIR/.env" | cut -d= -f2- || true)"
DB_NAME="$(grep -E '^DB_NAME=' "$APP_DIR/.env" | cut -d= -f2- || true)"
DB_USER="${DB_USER:-shopbot}"
DB_NAME="${DB_NAME:-shopbot}"

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/shopbot-$(date +%Y%m%d-%H%M%S).sql.gz"

$COMPOSE exec -T postgres pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner | gzip > "$FILE"
echo "$(date '+%F %T') backup OK: $FILE ($(du -h "$FILE" | cut -f1))"

# Xóa bản cũ, chỉ giữ $KEEP bản mới nhất
ls -1t "$BACKUP_DIR"/shopbot-*.sql.gz | tail -n +"$((KEEP + 1))" | xargs -r rm --
