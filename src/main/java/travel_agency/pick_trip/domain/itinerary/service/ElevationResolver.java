package travel_agency.pick_trip.domain.itinerary.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.itinerary.client.ElevationClient;
import travel_agency.pick_trip.domain.itinerary.client.dto.ElevationResponse;
import travel_agency.pick_trip.domain.itinerary.scheduling.ElevationProfile;
import travel_agency.pick_trip.domain.itinerary.scheduling.SchedulingPlace;

/**
 * 일정에 포함된 장소들의 해발고도를 1콜로 조회해 {@link ElevationProfile}로 만든다.
 *
 * <p>고도는 도보 leg 의 경사 페널티를 매기는 보조 정보라, 조회에 실패하더라도 예외를 던지지 않고
 * 빈 프로필(= 모든 좌표 고도 미상)을 돌려준다. 그 경우 경사 페널티는 0이 되어 기존 일정과 같아진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElevationResolver {

    /**
     * 한 번에 조회하는 좌표 수 상한. OpenTopoData 공개 엔드포인트의 요청당 좌표 상한이 100 이다.
     * 넘치는 좌표는 잘라내고 고도 미상으로 둔다. 초당 1콜 제한이 있어 나눠 보내면 응답이 그만큼 늦어지는데,
     * 하루 100곳을 넘는 일정은 현실적으로 없으므로 절단이 분할 호출보다 낫다.
     */
    private static final int MAX_LOCATIONS_PER_CALL = 100;

    private final ElevationClient elevationClient;

    /**
     * @param places 좌표가 없는 장소는 건너뛴다. 같은 좌표는 한 번만 조회한다.
     * @return 조회에 성공한 좌표만 담긴 프로필. 실패하면 {@link ElevationProfile#unknown()}.
     */
    public ElevationProfile resolve(List<SchedulingPlace> places) {
        if (places == null || places.isEmpty()) {
            return ElevationProfile.unknown();
        }

        // 좌표 키로 중복을 걷어내되 요청 순서를 유지해야 응답을 인덱스로 되짚을 수 있다.
        Set<String> coordinateKeys = new LinkedHashSet<>();
        for (SchedulingPlace place : places) {
            if (place != null && place.hasCoordinates()) {
                coordinateKeys.add(ElevationProfile.key(place.latitude(), place.longitude()));
            }
        }
        if (coordinateKeys.isEmpty()) {
            return ElevationProfile.unknown();
        }

        List<String> targets = coordinateKeys.stream().limit(MAX_LOCATIONS_PER_CALL).toList();
        if (coordinateKeys.size() > MAX_LOCATIONS_PER_CALL) {
            log.warn("[고도] 좌표 {}개 중 상한 {}개만 조회합니다. 나머지는 고도 미상으로 둡니다.",
                    coordinateKeys.size(), MAX_LOCATIONS_PER_CALL);
        }

        List<ElevationResponse.Result> results = fetch(targets);
        if (results.isEmpty()) {
            return ElevationProfile.unknown();
        }

        Map<String, Double> metersByCoordinate = new LinkedHashMap<>();
        // 응답이 요청보다 짧게 오면(일부 좌표 누락) 그 뒤 좌표는 담기지 않아 고도 미상으로 남는다.
        int matched = Math.min(targets.size(), results.size());
        for (int i = 0; i < matched; i++) {
            Double elevation = results.get(i).elevation();
            // 데이터셋 범위 밖 좌표는 elevation 이 null 로 온다. 담지 않아야 "고도 미상"으로 읽힌다.
            if (elevation != null && Double.isFinite(elevation)) {
                metersByCoordinate.put(targets.get(i), elevation);
            }
        }
        return new ElevationProfile(metersByCoordinate);
    }

    /**
     * 좌표 키({@code "위도,경도"})가 곧 OpenTopoData 의 {@code locations} 항목 형식이라 그대로 이어 붙인다.
     * 형식이 갈라지면 응답을 프로필에 되꽂을 때 키가 어긋나므로 {@link ElevationProfile#key}만 쓴다.
     */
    private List<ElevationResponse.Result> fetch(List<String> targets) {
        try {
            ElevationResponse response = elevationClient.getElevations(String.join("|", targets));
            if (response == null || response.results() == null) {
                log.warn("[고도] 응답이 비어 있어 고도 미상으로 폴백합니다.");
                return List.of();
            }
            return response.results();
        } catch (RuntimeException e) {
            // 호출 실패·타임아웃·base-url 미설정 모두 여기로 떨어진다. 재시도 없이 경사 0으로 진행한다.
            log.warn("[고도] 조회 실패 - 고도 미상으로 폴백: {}", e.getMessage());
            return List.of();
        }
    }
}
