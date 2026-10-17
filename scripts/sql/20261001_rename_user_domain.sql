-- MySQL: 기존 로컬 애플리케이션을 중지한 뒤 한 번 실행하고 새 버전을 시작한다.
-- 행 삭제/재생성 없이 테이블·인덱스·컬럼 이름만 변경하므로 ID와 데이터가 유지된다.
USE flow_commerce;
RENAME TABLE members TO users;
ALTER TABLE users RENAME INDEX uk_members_email TO uk_users_email;
ALTER TABLE refresh_tokens RENAME COLUMN member_id TO user_id;
