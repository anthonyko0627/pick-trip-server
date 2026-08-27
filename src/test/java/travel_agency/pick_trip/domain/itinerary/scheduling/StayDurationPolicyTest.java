package travel_agency.pick_trip.domain.itinerary.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StayDurationPolicyTest {

    @ParameterizedTest(name = "contentTypeId={0} -> {1}분")
    @CsvSource({
            "12, 120",
            "14, 90",
            "15, 150",
            "28, 150",
            "38, 60",
            "39, 60"
    })
    @DisplayName("콘텐츠 타입별로 정해진 체류 시간을 반환한다.")
    void returnStayMinutesByContentType(int contentTypeId, int expected) {
        // given & when
        int stayMinutes = StayDurationPolicy.stayMinutes(contentTypeId);

        // then
        assertThat(stayMinutes).isEqualTo(expected);
    }

    @Test
    @DisplayName("콘텐츠 타입이 없으면 기본 체류 시간을 반환한다.")
    void returnDefaultWhenContentTypeIsNull() {
        // given & when
        int stayMinutes = StayDurationPolicy.stayMinutes(null);

        // then
        assertThat(stayMinutes).isEqualTo(SchedulingPolicy.DEFAULT_STAY_MINUTES);
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 0, -1, 25, 32})
    @DisplayName("정의되지 않은 콘텐츠 타입이면 기본 체류 시간을 반환한다.")
    void returnDefaultWhenContentTypeIsUnknown(int contentTypeId) {
        // given & when
        int stayMinutes = StayDurationPolicy.stayMinutes(contentTypeId);

        // then
        assertThat(stayMinutes).isEqualTo(SchedulingPolicy.DEFAULT_STAY_MINUTES);
    }
}
