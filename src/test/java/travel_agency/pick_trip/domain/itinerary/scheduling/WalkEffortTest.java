package travel_agency.pick_trip.domain.itinerary.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import travel_agency.pick_trip.domain.itinerary.scheduling.WalkEffort.RestReason;

class WalkEffortTest {

    @Test
    @DisplayName("오르막 구간은 고도 차이만큼 상승고도로 계산한다.")
    void returnAltitudeDifferenceForAscent() {
        // given
        Double from = 50.0;
        Double to = 230.0;

        // when
        double climb = WalkEffort.climbMeters(from, to);

        // then
        assertThat(climb).isEqualTo(180.0);
    }

    @Test
    @DisplayName("내리막 구간의 상승고도는 0이다.")
    void returnZeroClimbForDescent() {
        // given
        Double from = 300.0;
        Double to = 120.0;

        // when
        double climb = WalkEffort.climbMeters(from, to);

        // then
        assertThat(climb).isZero();
    }

    @Test
    @DisplayName("해수면 아래 고도라도 차이만 쓰므로 상승분을 그대로 계산한다.")
    void handleNegativeElevation() {
        // given
        Double from = -12.0;
        Double to = 48.0;

        // when
        double climb = WalkEffort.climbMeters(from, to);

        // then
        assertThat(climb).isEqualTo(60.0);
    }

    @Test
    @DisplayName("한쪽이라도 고도를 모르면 상승고도는 0이다.")
    void returnZeroClimbWhenElevationUnknown() {
        // given
        Double known = 100.0;

        // when
        double fromUnknown = WalkEffort.climbMeters(null, known);
        double toUnknown = WalkEffort.climbMeters(known, null);
        double bothUnknown = WalkEffort.climbMeters(null, null);

        // then
        assertThat(fromUnknown).isZero();
        assertThat(toUnknown).isZero();
        assertThat(bothUnknown).isZero();
    }

    @Test
    @DisplayName("평균 경사는 상승고도를 수평거리로 나눈 값이다.")
    void calculateAverageGrade() {
        // given
        // 1km 를 걸으며 100m 올라가면 10% 경사다.
        double climb = 100.0;
        double horizontalKm = 1.0;

        // when
        double grade = WalkEffort.averageGrade(climb, horizontalKm);

        // then
        assertThat(grade).isCloseTo(0.1, within(1e-9));
    }

    @Test
    @DisplayName("수평거리가 0이면 경사를 정의할 수 없어 0으로 본다.")
    void returnZeroGradeForZeroHorizontalDistance() {
        // given
        double climb = 100.0;

        // when
        double grade = WalkEffort.averageGrade(climb, 0.0);

        // then
        assertThat(grade).isZero();
    }

    @Test
    @DisplayName("상승고도가 없으면 경사는 0이다.")
    void returnZeroGradeForFlatOrDescent() {
        // when
        double grade = WalkEffort.averageGrade(0.0, 2.0);

        // then
        assertThat(grade).isZero();
    }

    @Test
    @DisplayName("Naismith 근사에 따라 상승 300m 는 +30분이다.")
    void applyNaismithPenaltyForClimb() {
        // given
        double climb = 300.0;

        // when
        int penalty = WalkEffort.slopePenaltyMinutes(climb);

        // then
        assertThat(penalty).isEqualTo(30);
    }

    @Test
    @DisplayName("상승 600m 는 +60분이다.")
    void applyNaismithPenaltyForFullHourClimb() {
        // when
        int penalty = WalkEffort.slopePenaltyMinutes(600.0);

        // then
        assertThat(penalty).isEqualTo(60);
    }

    @Test
    @DisplayName("평지와 내리막에는 경사 페널티가 붙지 않는다.")
    void noPenaltyForFlatOrDescent() {
        // when
        int flat = WalkEffort.slopePenaltyMinutes(0.0);
        int descent = WalkEffort.slopePenaltyMinutes(-120.0);

        // then
        assertThat(flat).isZero();
        assertThat(descent).isZero();
    }

    @Test
    @DisplayName("고도 미상 구간은 상승고도가 0이라 페널티도 0분이다.")
    void noPenaltyWhenElevationUnknown() {
        // given
        double climb = WalkEffort.climbMeters(null, null);

        // when
        int penalty = WalkEffort.slopePenaltyMinutes(climb);

        // then
        assertThat(penalty).isZero();
    }

    @Test
    @DisplayName("누적 도보 시간만 임계를 넘으면 사유는 도보 시간이다.")
    void restReasonIsWalkTime() {
        // when
        RestReason reason = WalkEffort.restReason(SchedulingPolicy.REST_WALK_MINUTES, 10.0);

        // then
        assertThat(reason).isEqualTo(RestReason.WALK_TIME);
        assertThat(reason.needsRest()).isTrue();
    }

    @Test
    @DisplayName("누적 상승고도만 임계를 넘으면 사유는 상승고도다.")
    void restReasonIsClimb() {
        // when
        RestReason reason = WalkEffort.restReason(20, SchedulingPolicy.REST_CLIMB_METERS);

        // then
        assertThat(reason).isEqualTo(RestReason.CLIMB);
        assertThat(reason.needsRest()).isTrue();
    }

    @Test
    @DisplayName("도보 시간과 상승고도가 모두 임계를 넘으면 사유는 둘 다이다.")
    void restReasonIsBoth() {
        // when
        RestReason reason = WalkEffort.restReason(120, 350.0);

        // then
        assertThat(reason).isEqualTo(RestReason.BOTH);
    }

    @Test
    @DisplayName("둘 다 임계에 못 미치면 휴식을 삽입하지 않는다.")
    void noRestBelowThresholds() {
        // when
        RestReason reason = WalkEffort.restReason(
                SchedulingPolicy.REST_WALK_MINUTES - 1, SchedulingPolicy.REST_CLIMB_METERS - 0.1);

        // then
        assertThat(reason).isEqualTo(RestReason.NONE);
        assertThat(reason.needsRest()).isFalse();
    }
}
