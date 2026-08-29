package travel_agency.pick_trip.domain.share.controller;

import io.jsonwebtoken.Claims;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.dto.response.ShareCreateResponse;
import travel_agency.pick_trip.domain.share.dto.response.SharedItineraryResponse;
import travel_agency.pick_trip.domain.share.service.ShareService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShareController")
class ShareControllerTest {

    @Mock private ShareService shareService;
    @InjectMocks private ShareController shareController;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final UUID ITINERARY_ID = UUID.randomUUID();

    // standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    @Nested
    @DisplayName("POST /api/v1/itineraries/{itineraryId}/share")
    class CreateShare {

        @Test
        @DisplayName("소유자가 공유 링크를 생성하면 201과 토큰을 반환한다")
        void createShare_returns201() {
            // given
            String token = "abc123def456";
            String shareUrl = "https://example.test/api/v1/share/" + token;
            ShareCreateResponse expected = new ShareCreateResponse(token, shareUrl);
            given(shareService.createShare(USER_UID, ITINERARY_ID)).willReturn(expected);

            // when
            ResponseEntity<ShareCreateResponse> result = shareController.createShare(principal(), ITINERARY_ID);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().token()).isEqualTo(token);
            assertThat(result.getBody().shareUrl()).isEqualTo(shareUrl);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/share/{token}")
    class GetSharedItinerary {

        @Test
        @DisplayName("공유 토큰으로 공개 일정을 조회하면 200과 일정을 반환한다 (인증 불필요)")
        void getSharedItinerary_returns200() {
            // given
            String token = "abc123def456";
            SharedItineraryResponse expected = new SharedItineraryResponse(
                    "하동 1박 2일", Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                    List.of(new SharedItineraryResponse.Day(1, List.of(
                            new SharedItineraryResponse.Item(
                                    "c1", "쌍계사", 1, "오전 배치",
                                    LocalTime.of(9, 0), LocalTime.of(10, 30))
                    ), 0, BigDecimal.ZERO))
            );
            given(shareService.getSharedItinerary(token)).willReturn(expected);

            // when
            ResponseEntity<SharedItineraryResponse> result = shareController.getSharedItinerary(token);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().title()).isEqualTo("하동 1박 2일");
            assertThat(result.getBody().days()).hasSize(1);
        }
    }
}
