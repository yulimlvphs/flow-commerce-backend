-- 기존 DB에서 애플리케이션을 중지한 뒤 한 번 실행한다.
-- 신규 DB는 JPA가 컬럼을 생성하므로 이 스크립트를 실행하지 않는다.
-- 먼저 기존 products/deals.created_at의 실제 저장 시간대를 확인한다.
-- UTC 이외의 로컬 시각이라면 아래 SQL 실행 전에 created_at을 UTC로 변환한다.
-- 변환한 created_at을 updated_at에 복사해야 두 시각의 기준이 일치한다.
-- 이미 이 스크립트를 실행했다면 필요한 시간대 보정은 두 컬럼 모두에 적용한다.
-- 실제 저장 시간대를 추측해 자동 변환하지 않는다.
-- 과거 수정 시각은 알 수 없으므로 기존 행은 생성 시각으로 초기화한다.
ALTER TABLE products ADD COLUMN updated_at DATETIME(6) NULL;
UPDATE products SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE products MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE deals ADD COLUMN updated_at DATETIME(6) NULL;
UPDATE deals SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE deals MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
