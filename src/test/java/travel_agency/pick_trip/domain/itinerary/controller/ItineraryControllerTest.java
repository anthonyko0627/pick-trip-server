package travel_agency.pick_trip.domain.itinerary.controller;

import io.jsonwebtoken.Claims;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.service.ItineraryService;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryController")
class ItineraryControllerTest {

    @Mock private ItineraryService itineraryService;
    @InjectMocks private ItineraryController itineraryController;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final UUID ITINERARY_ID = UUID.randomUUID();

    // standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    private SaveItineraryRequest saveRequest() {
        return new SaveItineraryRequest(
                "하동 1박 2일", Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                List.of(new SaveItineraryRequest.DayRequest(1, List.of(
                        new SaveItineraryRequest.ItemRequest("c1", "쌍계사", 1, "오전 배치", true)
                )))
        );
    }

    private ItineraryResponse itineraryResponse(String title) {
        return new ItineraryResponse(
                ITINERARY_ID, title, Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                LocalDateTime.of(2026, 6, 21, 12, 0),
                List.of(new ItineraryResponse.Day(UUID.randomUUID(), 1, List.of(
                        new ItineraryResponse.Item(UUID.randomUUID(), "c1", "쌍계사", 1, "오전 배치", true)
                )))
        );
    }

    @Nested
    @DisplayName("POST /api/v1/itineraries/generate")
    class Generate {

        @Test
        @DisplayName("바구니 기준 AI 일정을 생성하면 200과 미리보기를 반환한다")
        void generate_returns200() {
            // given
            ItineraryGenerateResponse expected = new ItineraryGenerateResponse(
                    "하동 1박 2일 가족 여행", Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                    List.of(new ItineraryGenerateResponse.Day(1, List.of(
                            new ItineraryGenerateResponse.Item("c1", "쌍계사", 1, "오전 배치")
                    )))
            );
            given(itineraryService.generate(USER_UID)).willReturn(expected);

            // when
            ResponseEntity<ItineraryGenerateResponse> result = itineraryController.generate(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(result.getBody().days()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/itineraries")
    class Save {

        @Test
        @DisplayName("일정을 저장하면 201과 저장된 일정을 반환한다")
        void save_returns201() {
            // given
            SaveItineraryRequest request = saveRequest();
            given(itineraryService.save(USER_UID, request)).willReturn(itineraryResponse("하동 1박 2일"));

            // when
            ResponseEntity<ItineraryResponse> result = itineraryController.save(principal(), request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().itineraryId()).isEqualTo(ITINERARY_ID);
            assertThat(result.getBody().title()).isEqualTo("하동 1박 2일");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/itineraries/{itineraryId}")
    class GetItinerary {

        @Test
        @DisplayName("저장된 일정을 조회하면 200과 일정을 반환한다")
        void getItinerary_returns200() {
            // given
            given(itineraryService.getItinerary(USER_UID, ITINERARY_ID)).willReturn(itineraryResponse("하동 1박 2일"));

            // when
            ResponseEntity<ItineraryResponse> result = itineraryController.getItinerary(principal(), ITINERARY_ID);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().itineraryId()).isEqualTo(ITINERARY_ID);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/itineraries/{itineraryId}")
    class Modify {

        @Test
        @DisplayName("일정을 수정하면 200과 수정된 일정을 반환한다")
        void modify_returns200() {
            // given
            SaveItineraryRequest request = saveRequest();
            given(itineraryService.modify(USER_UID, ITINERARY_ID, request))
                    .willReturn(itineraryResponse("수정된 일정"));

            // when
            ResponseEntity<ItineraryResponse> result =
                    itineraryController.modify(principal(), ITINERARY_ID, request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().title()).isEqualTo("수정된 일정");
        }
    }

    @Nested
    @DisplayName("POST /api/v1/itineraries/{itineraryId}/regenerate")
    class Regenerate {

        @Test
        @DisplayName("일정을 재생성하면 200과 덮어쓴 일정을 반환한다")
        void regenerate_returns200() {
            // given
            given(itineraryService.regenerate(USER_UID, ITINERARY_ID))
                    .willReturn(itineraryResponse("재생성된 일정"));

            // when
            ResponseEntity<ItineraryResponse> result =
                    itineraryController.regenerate(principal(), ITINERARY_ID);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().title()).isEqualTo("재생성된 일정");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/itineraries")
    class GetMyItineraries {

        @Test
        @DisplayName("저장된 일정 목록을 조회하면 200과 일정 요약 목록을 반환한다")
        void getMyItineraries_returns200() {
            // given
            ItinerarySummaryResponse summary = new ItinerarySummaryResponse(
                    ITINERARY_ID, "하동 1박 2일", Region.HADONG,
                    LocalDate.of(2026, 7, 1), 2, LocalDateTime.of(2026, 6, 21, 12, 0)
            );
            given(itineraryService.getMyItineraries(USER_UID)).willReturn(List.of(summary));

            // when
            ResponseEntity<List<ItinerarySummaryResponse>> result =
                    itineraryController.getMyItineraries(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody()).hasSize(1);
            ItinerarySummaryResponse body = result.getBody().get(0);
            assertThat(body.itineraryId()).isEqualTo(ITINERARY_ID);
            assertThat(body.title()).isEqualTo("하동 1박 2일");
            assertThat(body.region()).isEqualTo(Region.HADONG);
            assertThat(body.lastModifiedAt()).isEqualTo(LocalDateTime.of(2026, 6, 21, 12, 0));
        }

        @Test
        @DisplayName("저장된 일정이 없으면 빈 배열을 반환한다")
        void getMyItineraries_returnsEmptyListWhenNoItinerary() {
            // given
            given(itineraryService.getMyItineraries(USER_UID)).willReturn(List.of());

            // when
            ResponseEntity<List<ItinerarySummaryResponse>> result =
                    itineraryController.getMyItineraries(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/itineraries/{itineraryId}")
    class Delete {

        @Test
        @DisplayName("일정을 삭제하면 204를 반환하고 서비스의 삭제를 호출한다")
        void delete_returns204() {
            // given & when
            ResponseEntity<Void> result = itineraryController.delete(principal(), ITINERARY_ID);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            then(itineraryService).should().delete(USER_UID, ITINERARY_ID);
        }

        @Test
        @DisplayName("타인의 일정을 삭제하려 하면 ITINERARY_NOT_FOUND 예외를 던진다")
        void delete_throwsItineraryNotFoundWhenOwnedByOther() {
            // given
            willThrow(new ItineraryException(ErrorCode.ITINERARY_NOT_FOUND))
                    .given(itineraryService).delete(USER_UID, ITINERARY_ID);

            // when
            ThrowableAssert.ThrowingCallable action =
                    () -> itineraryController.delete(principal(), ITINERARY_ID);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(ItineraryException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        }
    }
}
