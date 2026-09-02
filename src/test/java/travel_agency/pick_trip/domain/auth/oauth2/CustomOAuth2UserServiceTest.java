package travel_agency.pick_trip.domain.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService")
class CustomOAuth2UserServiceTest {

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private UserRepository userRepository;

    private DefaultOAuth2UserService mockDelegate;
    private OAuth2UserRequest mockRequest;

    private static final String PROVIDER_ID = "google-sub-12345";
    private static final String EMAIL = "test@gmail.com";
    private static final String NICKNAME = "테스트유저";
    private static final String PROFILE_IMG = "https://img.google.com/profile.jpg";

    @BeforeEach
    void setUp() {
        // delegate는 final 필드로 직접 주입되므로 ReflectionTestUtils로 교체한다.
        mockDelegate = mock(DefaultOAuth2UserService.class);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", mockDelegate);
        mockRequest = mock(OAuth2UserRequest.class);
    }

    /** 어느 공급자로 들어온 요청인지 지정한다. registrationId로 attribute 해석 방식이 갈린다. */
    private void givenRegistrationId(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("test-client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
                .authorizationUri("https://example.com/authorize")
                .tokenUri("https://example.com/token")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName("id")
                .build();
        given(mockRequest.getClientRegistration()).willReturn(registration);
    }

    private OAuth2User mockOAuth2User(Map<String, Object> attributes) {
        OAuth2User oAuth2User = mock(OAuth2User.class);
        given(oAuth2User.getAttributes()).willReturn(attributes);
        return oAuth2User;
    }

    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(PROVIDER_ID)
                .email(EMAIL)
                .nickname(NICKNAME)
                .profileImageUrl(PROFILE_IMG)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        return user;
    }

    @Nested
    @DisplayName("신규 구글 유저 가입")
    class NewUser {

        @Test
        @DisplayName("최초 구글 로그인 시 유저가 저장되고 OAuth2UserAdapter가 반환된다")
        void firstLogin_savesUserAndReturnsAdapter() {
            // given
            UUID uid = UUID.randomUUID();
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", EMAIL,
                    "name", NICKNAME, "picture", PROFILE_IMG
            );
            User savedUser = userWithUid(uid);

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            givenRegistrationId("google");
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            OAuth2User result = customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(result).isInstanceOf(OAuth2UserAdapter.class);
            then(userRepository).should().save(any(User.class));
        }
    }

    @Nested
    @DisplayName("기존 구글 유저 재로그인")
    class ExistingUser {

        @Test
        @DisplayName("재로그인 시 닉네임과 프로필 이미지가 최신 구글 정보로 업데이트된다")
        void reLogin_updatesNicknameAndProfileImage() {
            // given
            String newNickname = "변경된닉네임";
            String newProfileImg = "https://img.google.com/new.jpg";
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", EMAIL,
                    "name", newNickname, "picture", newProfileImg
            );
            User existingUser = userWithUid(UUID.randomUUID());

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            givenRegistrationId("google");
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(existingUser.getNickname()).isEqualTo(newNickname);
            assertThat(existingUser.getProfileImageUrl()).isEqualTo(newProfileImg);
            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("재로그인 시 이메일은 변경하지 않는다")
        void reLogin_doesNotChangeEmail() {
            // given
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", "new-email@gmail.com",
                    "name", NICKNAME, "picture", PROFILE_IMG
            );
            User existingUser = userWithUid(UUID.randomUUID());
            String originalEmail = existingUser.getEmail();

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            givenRegistrationId("google");
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(existingUser.getEmail()).isEqualTo(originalEmail);
        }

        @Test
        @DisplayName("탈퇴 유예 중인 사용자가 재로그인하면 탈퇴가 철회된다")
        void reLogin_restoresWithdrawnUser() {
            // given
            Map<String, Object> attributes = Map.of(
                    "sub", PROVIDER_ID, "email", EMAIL,
                    "name", NICKNAME, "picture", PROFILE_IMG
            );
            User existingUser = userWithUid(UUID.randomUUID());
            existingUser.withdraw();

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            givenRegistrationId("google");
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            assertThat(existingUser.isDeleted()).isFalse();
            assertThat(existingUser.getDeletedAt()).isNull();
            then(userRepository).should(never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("구글 OAuth2 오류")
    class OAuthError {

        @Test
        @DisplayName("구글 응답에 sub 필드가 없으면 AUTH_PROVIDER_ERROR 예외를 던진다")
        void missingSubField_throwsOAuthProviderException() {
            // given: sub 필드가 없는 응답 (HashMap으로 null 값 허용)
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("email", EMAIL);
            attributes.put("name", NICKNAME);

            OAuth2User oAuth2User = mockOAuth2User(attributes);
            givenRegistrationId("google");
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);

            // when / then
            assertThatThrownBy(() -> customOAuth2UserService.loadUser(mockRequest))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("카카오 로그인")
    class KakaoLogin {

        private static final Long KAKAO_ID = 12345678L;
        private static final String KAKAO_NICKNAME = "카카오유저";
        private static final String KAKAO_EMAIL = "test@kakao.com";
        private static final String KAKAO_PROFILE_IMG = "https://img.kakao.com/profile.jpg";

        /** 카카오는 식별자만 최상위에 두고 나머지는 kakao_account 아래에 중첩한다. */
        private Map<String, Object> kakaoAttributes() {
            return Map.of(
                    "id", KAKAO_ID,
                    "kakao_account", Map.of(
                            "email", KAKAO_EMAIL,
                            "profile", Map.of(
                                    "nickname", KAKAO_NICKNAME,
                                    "profile_image_url", KAKAO_PROFILE_IMG
                            )
                    )
            );
        }

        @Test
        @DisplayName("최초 카카오 로그인 시 중첩된 kakao_account에서 정보를 꺼내 저장한다")
        void firstLogin_savesUserFromNestedAttributes() {
            // given
            givenRegistrationId("kakao");
            OAuth2User oAuth2User = mockOAuth2User(kakaoAttributes());
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, String.valueOf(KAKAO_ID)))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getProvider()).isEqualTo(OAuthProvider.KAKAO);
            // 카카오 id는 숫자로 내려오므로 문자열로 변환돼야 한다.
            assertThat(saved.getProviderUserId()).isEqualTo(String.valueOf(KAKAO_ID));
            assertThat(saved.getEmail()).isEqualTo(KAKAO_EMAIL);
            assertThat(saved.getNickname()).isEqualTo(KAKAO_NICKNAME);
            assertThat(saved.getProfileImageUrl()).isEqualTo(KAKAO_PROFILE_IMG);
        }

        @Test
        @DisplayName("동의하지 않아 kakao_account가 없어도 식별자만으로 가입된다")
        void firstLogin_succeedsWithoutConsentedFields() {
            // given
            // 카카오는 동의 항목 미동의 시 kakao_account 자체를 내려주지 않는다.
            Map<String, Object> attributes = Map.of("id", KAKAO_ID);
            givenRegistrationId("kakao");
            OAuth2User oAuth2User = mockOAuth2User(attributes);
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, String.valueOf(KAKAO_ID)))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            assertThat(captor.getValue().getProviderUserId()).isEqualTo(String.valueOf(KAKAO_ID));
            assertThat(captor.getValue().getEmail()).isNull();
        }

        @Test
        @DisplayName("카카오 응답에 id가 없으면 AUTH_PROVIDER_ERROR 예외를 던진다")
        void missingId_throwsOAuthProviderException() {
            // given
            givenRegistrationId("kakao");
            OAuth2User oAuth2User = mockOAuth2User(new HashMap<>());
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);

            // when / then
            assertThatThrownBy(() -> customOAuth2UserService.loadUser(mockRequest))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("구글 사용자와 카카오 사용자는 같은 식별자여도 다른 계정으로 구분된다")
        void sameProviderUserId_isolatedByProvider() {
            // given
            givenRegistrationId("kakao");
            OAuth2User oAuth2User = mockOAuth2User(kakaoAttributes());
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, String.valueOf(KAKAO_ID)))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            customOAuth2UserService.loadUser(mockRequest);

            // then
            // GOOGLE로 조회하면 카카오 사용자를 찾지 못해야 한다.
            then(userRepository).should()
                    .findByProviderAndProviderUserId(OAuthProvider.KAKAO, String.valueOf(KAKAO_ID));
            then(userRepository).should(never())
                    .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, String.valueOf(KAKAO_ID));
        }
    }

    @Nested
    @DisplayName("지원하지 않는 공급자")
    class UnsupportedProvider {

        @Test
        @DisplayName("등록되지 않은 registrationId면 AUTH_PROVIDER_ERROR 예외를 던진다")
        void unknownRegistrationId_throwsOAuthProviderException() {
            // given
            givenRegistrationId("naver");
            OAuth2User oAuth2User = mockOAuth2User(Map.of("id", 1L));
            given(mockDelegate.loadUser(mockRequest)).willReturn(oAuth2User);

            // when / then
            assertThatThrownBy(() -> customOAuth2UserService.loadUser(mockRequest))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
