package travel_agency.pick_trip.domain.itinerary.scheduling;

import java.time.LocalTime;

/**
 * 일정 스케줄링 전반에서 공유하는 정책 상수.
 * 외부 의존성 없이 순수 값만 보유한다.
 */
public final class SchedulingPolicy {

    /** 숙소 체크아웃·아침 이동을 감안한 현실적인 첫 일정 시작 시각. */
    public static final LocalTime DAY_START = LocalTime.of(9, 0);

    /** 소도시 대부분의 시설·식당이 닫히는 시각. 넘어서면 경고만 남기고 강제 종료하지는 않는다. */
    public static final LocalTime DAY_SOFT_END = LocalTime.of(21, 0);

    /** 하동·영주·예천은 고속도로보다 국도 비중이 높아 실효 평균 속도를 35km/h로 잡는다. */
    public static final double AVG_SPEED_KMH = 35.0;

    /** 직선거리(haversine)와 실제 도로 거리의 차이를 보정하는 우회 계수. */
    public static final double DETOUR_FACTOR = 1.3;

    /** 체류 시간 정보가 없는 장소의 기본 관람 시간. */
    public static final int DEFAULT_STAY_MINUTES = 90;

    /** 좌표가 없어 거리를 계산할 수 없는 구간에 부여하는 이동 시간 추정치. */
    public static final int UNKNOWN_HOP_MINUTES = 20;

    /** 이 거리를 넘는 단일 이동은 하루 일정으로 무리라고 보고 경고 대상으로 삼는다. */
    public static final double MAX_SINGLE_HOP_KM = 40.0;

    /** 하루 총 이동이 3시간을 넘으면 관광보다 이동에 시간을 더 쓰게 되므로 경고한다. */
    public static final int MAX_DAY_TRAVEL_MINUTES = 180;

    /** 순서 재배치 탐색은 조합 폭발을 피하기 위해 하루 7개 장소까지만 수행한다. */
    public static final int MAX_REORDER_STOPS = 7;

    private SchedulingPolicy() {
    }
}
