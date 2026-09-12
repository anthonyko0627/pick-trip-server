package travel_agency.pick_trip.domain.itinerary.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.itinerary.scheduling.TravelMode;

@DisplayName("GenerateItineraryRequest")
class GenerateItineraryRequestTest {

    @Test
    @DisplayName("이동수단을 지정하지 않으면 자동차 단일안으로 정규화한다.")
    void defaultsToCarWhenModesMissing() {
        // when
        GenerateItineraryRequest request = GenerateItineraryRequest.defaults();

        // then
        assertThat(request.travelModes()).containsExactly(TravelMode.CAR);
        assertThat(request.mode()).isEqualTo(GenerateMode.STRICT);
    }

    @Test
    @DisplayName("빈 리스트나 null 만 담긴 리스트도 자동차 단일안으로 되돌린다.")
    void fallbackToCarForEmptyModes() {
        // given
        List<TravelMode> onlyNulls = Arrays.asList(null, null);

        // when
        GenerateItineraryRequest empty = new GenerateItineraryRequest(null, null, List.of());
        GenerateItineraryRequest nulls = new GenerateItineraryRequest(null, null, onlyNulls);

        // then
        assertThat(empty.travelModes()).containsExactly(TravelMode.CAR);
        assertThat(nulls.travelModes()).containsExactly(TravelMode.CAR);
    }

    @Test
    @DisplayName("중복 이동수단은 순서를 유지한 채 한 번만 남긴다.")
    void removeDuplicateModes() {
        // given
        List<TravelMode> requested = List.of(TravelMode.TRANSIT, TravelMode.CAR, TravelMode.TRANSIT);

        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(null, null, requested);

        // then
        assertThat(request.travelModes()).containsExactly(TravelMode.TRANSIT, TravelMode.CAR);
    }

    @Test
    @DisplayName("일정안 수는 상한을 넘지 않는다.")
    void limitVariantCount() {
        // given - 이동수단이 늘어나도 상한 안으로 잘린다. 지금은 두 종류뿐이라 중복 제거만으로도 상한 안이다.
        List<TravelMode> requested = List.of(
                TravelMode.CAR, TravelMode.TRANSIT, TravelMode.CAR, TravelMode.TRANSIT, TravelMode.CAR);

        // when
        GenerateItineraryRequest request = new GenerateItineraryRequest(null, null, requested);

        // then
        assertThat(request.travelModes())
                .hasSizeLessThanOrEqualTo(GenerateItineraryRequest.MAX_VARIANTS)
                .containsExactly(TravelMode.CAR, TravelMode.TRANSIT);
    }

    @Test
    @DisplayName("이동수단 목록은 수정할 수 없다.")
    void travelModesAreImmutable() {
        // given
        GenerateItineraryRequest request =
                new GenerateItineraryRequest(null, null, List.of(TravelMode.CAR));

        // when
        List<TravelMode> modes = request.travelModes();

        // then
        assertThat(modes).isUnmodifiable();
    }
}
