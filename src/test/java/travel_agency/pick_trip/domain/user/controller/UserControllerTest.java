package travel_agency.pick_trip.domain.user.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.user.dto.response.UserMeResponse;
import travel_agency.pick_trip.domain.user.service.UserService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private UserController userController;

    private static final UUID USER_UID = UUID.randomUUID();

    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMe {

        @Test
        @DisplayName("인증된 사용자가 요청하면 200과 사용자 정보를 반환한다")
        void authenticatedUser_returns200WithUserInfo() {
            // given
            JwtUserPrincipal principal = principal();
            UserMeResponse expectedResponse = new UserMeResponse(
                    USER_UID,
                    "test@test.com",
                    "테스트유저",
                    "https://profile.com/img.jpg",
                    "KAKAO",
                    LocalDateTime.of(2025, 5, 16, 12, 0)
            );
            given(userService.getMe(USER_UID)).willReturn(expectedResponse);

            // when: standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
            ResponseEntity<UserMeResponse> result = userController.getMe(principal);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().email()).isEqualTo("test@test.com");
            assertThat(result.getBody().nickname()).isEqualTo("테스트유저");
            assertThat(result.getBody().provider()).isEqualTo("KAKAO");
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me")
    class Withdraw {

        @Test
        @DisplayName("인증된 사용자가 탈퇴를 요청하면 204를 반환하고 서비스에 위임한다")
        void authenticatedUser_returns204() {
            // given
            JwtUserPrincipal principal = principal();

            // when: standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
            ResponseEntity<Void> result = userController.withdraw(principal);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            then(userService).should().withdraw(USER_UID);
        }
    }
}
