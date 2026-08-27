package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 일정 스케줄링에 필요한 최소 정보만 담은 장소 값 객체다.
 * Entity 대신 이 타입을 쓰는 이유는 스케줄링 패키지를 JPA/외부 의존성 없는 순수 코드로 유지하기 위함이다.
 */
public record SchedulingPlace(
        String contentId,
        String title,
        Integer contentTypeId,
        Double latitude,
        Double longitude,
        OperatingHours operatingHours,
        int stayMinutes
) {
    public SchedulingPlace {
        // 호출부마다 null/0 방어를 반복하지 않도록 생성 시점에 한 번만 정규화한다.
        if (operatingHours == null) {
            operatingHours = OperatingHours.unknown();
        }
        // 하한만 막고 상한을 열어두면 누적 시각 계산이 int 오버플로로 음수가 되어
        // 클램프를 통과해버린다. 하루를 넘는 체류는 의미가 없으므로 24시간으로 자른다.
        stayMinutes = stayMinutes <= 0
                ? SchedulingPolicy.DEFAULT_STAY_MINUTES
                : Math.min(stayMinutes, 24 * 60);
    }

    /** 좌표가 모두 있어야 이동 거리·시간을 계산할 수 있다. */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
