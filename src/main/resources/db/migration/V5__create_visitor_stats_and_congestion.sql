-- 콘텐츠별 관광객수 노출·혼잡 기반 순서변경 제안 (#73).
-- 개별 장소 단위 관광객수는 공개 데이터에 없어, 지역 단위 방문자수 통계(공공데이터포털 15101972)와
-- PickTrip 자체 프록시(바구니에 담긴 횟수)를 조합해 근사한다.

-- 지역·기준 연월 단위 방문자수. 수집 배치가 (region, stat_month) 로 upsert 한다.
CREATE TABLE region_visitor_stats (
    id bigint NOT NULL AUTO_INCREMENT,
    region enum('HADONG','YECHEON','YEONGJU') NOT NULL,
    stat_month varchar(7) NOT NULL,
    visitor_count bigint NOT NULL,
    source varchar(100) NOT NULL,
    collected_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_region_visitor_stats_region_month (region, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 콘텐츠별 시간대 혼잡 스냅샷. 배치가 지역 단위로 다시 계산해 통째로 교체한다.
CREATE TABLE content_congestion (
    id bigint NOT NULL AUTO_INCREMENT,
    source_content_id varchar(50) NOT NULL,
    hour_slot int NOT NULL,
    congestion_level enum('HIGH','LOW','MEDIUM') NOT NULL,
    score double NOT NULL,
    computed_at datetime(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_congestion_content_hour (source_content_id, hour_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
