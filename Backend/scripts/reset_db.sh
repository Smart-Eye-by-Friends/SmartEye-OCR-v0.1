#!/usr/bin/env bash
#
# 개발/테스트용 SmartEyeSsen MySQL 스키마 초기화 스크립트
# -------------------------------------------------------
# - docker mysql 컨테이너에서 사용하는 smarteyessen_db 스키마를 드롭 후 재생성합니다.
# - 이후 FastAPI ORM 모델 기반으로 테이블을 초기화합니다.
# - 환경 변수(DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME)가 설정되어 있어야 합니다.
#
# 사용 예시:
#   chmod +x Backend/scripts/reset_db.sh
#   Backend/scripts/reset_db.sh
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PYTHONPATH="${ROOT_DIR}"

DB_HOST="${DB_HOST:-10.255.255.254}"
DB_PORT="${DB_PORT:-3308}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4r}"
DB_NAME="${DB_NAME:-smarteyessen_db}"

MYSQL_CMD=(
  mysql
  -h "${DB_HOST}"
  -P "${DB_PORT}"
  -u "${DB_USER}"
)

if [[ -n "${DB_PASSWORD}" ]]; then
  MYSQL_CMD+=(-p"${DB_PASSWORD}")
fi

echo "🔄 Dropping and recreating schema \`${DB_NAME}\` on ${DB_HOST}:${DB_PORT}..."
"${MYSQL_CMD[@]}" <<SQL
DROP DATABASE IF EXISTS \`${DB_NAME}\`;
CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

echo "✅ Schema recreated."

echo "📦 Initializing tables via Backend.app.database.init_db()..."
python - <<'PYTHON'
from Backend.app.database import init_db

if __name__ == "__main__":
    init_db()
PYTHON

echo "✅ Table initialization complete."

echo "🎉 Database reset finished. You can now rerun backend services or seed data as needed."
