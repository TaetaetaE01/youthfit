-- users.role: RBAC(USER/ADMIN)용 컬럼 추가
-- 적용 절차: psql "$YOUTHFIT_DB_URL" -f backend/src/main/resources/sql/2026-05-05-users-role.sql
-- Spec: docs/superpowers/specs/2026-05-05-admin-foundation-design.md

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(10) NOT NULL DEFAULT 'USER';
