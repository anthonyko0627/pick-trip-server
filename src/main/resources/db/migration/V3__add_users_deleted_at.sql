-- 회원 탈퇴 시각. deleted=true 인 행만 값을 가지며, 30일 경과 여부를 이 컬럼으로 판단한다 (#76).
ALTER TABLE users ADD COLUMN deleted_at datetime(6) NULL;
