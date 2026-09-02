package travel_agency.pick_trip.domain.user.service;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.dto.response.UserMeResponse;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private UserService userService;

    private static final UUID USER_UID = UUID.randomUUID();

    private User newUser() {
        return User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId("kakao-123")
                .email("test@test.com")
                .nickname("테스트유저")
                .profileImageUrl("https://profile.com/img.jpg")
                .build();
    }

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("유효한 uid로 조회하면 사용자 정보를 담은 UserMeResponse를 반환한다")
        void validUid_returnsUserMeResponse() {
            // given
            User user = newUser();
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(user));

            // when
            UserMeResponse response = userService.getMe(USER_UID);

            // then
            assertThat(response.email()).isEqualTo("test@test.com");
            assertThat(response.nickname()).isEqualTo("테스트유저");
            assertThat(response.profileImageUrl()).isEqualTo("https://profile.com/img.jpg");
            assertThat(response.provider()).isEqualTo("KAKAO");
        }

        @Test
        @DisplayName("존재하지 않는 uid로 조회하면 USER_NOT_FOUND 예외를 던진다")
        void invalidUid_throwsUserNotFoundException() {
            // given
            given(userRepository.findById(USER_UID)).willReturn(Optional.empty());

            // when
            ThrowableAssert.ThrowingCallable action = () -> userService.getMe(USER_UID);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("유효한 uid로 탈퇴하면 소프트 삭제되고 리프레시 토큰이 폐기된다")
        void validUid_softDeletesUserAndRevokesRefreshToken() {
            // given
            User user = newUser();
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(user));

            // when
            userService.withdraw(USER_UID);

            // then
            assertThat(user.isDeleted()).isTrue();
            assertThat(user.getDeletedAt()).isNotNull();
            then(refreshTokenRepository).should().deleteById(USER_UID);
        }

        @Test
        @DisplayName("이미 탈퇴한 사용자가 다시 탈퇴해도 탈퇴 시각은 갱신되지 않는다")
        void alreadyWithdrawnUser_keepsOriginalDeletedAt() {
            // given
            User user = newUser();
            user.withdraw();
            LocalDateTime firstDeletedAt = user.getDeletedAt();
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(user));

            // when
            userService.withdraw(USER_UID);

            // then
            assertThat(user.isDeleted()).isTrue();
            assertThat(user.getDeletedAt()).isEqualTo(firstDeletedAt);
            then(refreshTokenRepository).should().deleteById(USER_UID);
        }

        @Test
        @DisplayName("존재하지 않는 uid로 탈퇴하면 USER_NOT_FOUND 예외를 던지고 토큰을 건드리지 않는다")
        void invalidUid_throwsUserNotFoundException() {
            // given
            given(userRepository.findById(USER_UID)).willReturn(Optional.empty());

            // when
            ThrowableAssert.ThrowingCallable action = () -> userService.withdraw(USER_UID);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
            then(refreshTokenRepository).shouldHaveNoInteractions();
        }
    }
}
