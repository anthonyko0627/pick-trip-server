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

    /**
     * 도보 속도. 성인 평지 보행은 3~4km/h 구간이며, 짐·사진 촬영·신호 대기를 감안해 중간값인 3.5km/h 로 잡는다.
     */
    public static final double WALK_SPEED_KMH = 3.5;

    /**
     * 이 거리 이하 구간은 걸어서 이동한다고 본다(3.5km/h 로 약 34분).
     * 초과하면 도보로 감당할 수 없다고 보고 시내버스로 환산한다.
     * ponytail: 교통비 계산(#71)도 같은 경계를 쓰므로 정책 상수로 둔다.
     */
    public static final double WALK_MAX_KM = 2.0;

    /**
     * 시내버스 표정속도(정차·신호 포함한 실효 속도). 소도시 노선 기준 25km/h 로 잡는다.
     * ponytail: 노선·배차를 모르는 근사. ODsay 등 대중교통 길찾기 API 를 붙이면 실측으로 교체한다.
     */
    public static final double TRANSIT_SPEED_KMH = 25.0;

    /**
     * 버스 한 번을 타기 위한 평균 대기 시간. 소도시 시내버스 배차가 30분 안팎이라 그 절반을 평균 대기로 본다.
     * ponytail: 노선·배차를 모르는 근사. ODsay 등 대중교통 길찾기 API 를 붙이면 실측으로 교체한다.
     */
    public static final int TRANSIT_WAIT_MINUTES = 15;

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

    /**
     * Naismith 규칙 근사에서 쓰는 시간당 상승고도(m). 원 규칙은 "수평 5km/h + 상승 600m/h" 이므로
     * 오르막 600m 마다 +60분(= 300m 마다 +30분)이 붙는다. 이슈 #72 에 적힌 근사치를 그대로 쓴다.
     */
    public static final double NAISMITH_CLIMB_METERS_PER_HOUR = 600.0;

    /**
     * 휴식 스톱을 권하는 누적 도보 시간(분). 성인 보행자가 쉬지 않고 걷는 한계를 1시간 30분으로 본다.
     * 관광 도보는 중간에 관람 체류가 끼므로 통근·등산 기준(보통 60분)보다 여유를 뒀다.
     */
    public static final int REST_WALK_MINUTES = 90;

    /**
     * 휴식 스톱을 권하는 누적 상승고도(m). 소도시 관광 코스에서 200m 는 20~30층 계단에 해당하며,
     * 이 지점을 넘으면 평지 도보와 체감 피로가 확연히 달라진다.
     */
    public static final double REST_CLIMB_METERS = 200.0;

    private SchedulingPolicy() {
    }
}
