-- 기존 일정 행을 보존해야 하고 방문 시각·이동 요약은 저장 시 선택 입력이므로 모두 nullable 로 추가한다.
ALTER TABLE itinerary_items
    ADD COLUMN visit_start time NULL,
    ADD COLUMN visit_end time NULL;

ALTER TABLE itinerary_days
    ADD COLUMN travel_minutes int NULL,
    ADD COLUMN travel_km decimal(6,2) NULL;
