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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeCodeStore;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeData;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationSuccessHandler")
class OAuth2AuthenticationSuccessHandlerTest {

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OAuthExchangeCodeStore exchangeCodeStore;
    @Mock private OAuthNonceStore nonceStore;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final String REDIRECT_URI = "http://localhost:3000/auth/callback";
    private static final String APP_REDIRECT_URI = "picktrip://auth/callback";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN_VALUE = "refresh-token";
    private static final String EXCHANGE_CODE = "opaque-exchange-code";
    private static final long REFRESH_EXPIRE_DAYS = 14L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
        ReflectionTestUtils.setField(successHandler, "appRedirectUri", APP_REDIRECT_URI);
        ReflectionTestUtils.setField(successHandler, "refreshTokenExpireTimeDays", REFRESH_EXPIRE_DAYS);
    }

    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .email("test@gmail.com")
                .nickname("테스트")
                .profileImageUrl(null)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        return user;
    }

    private Authentication mockAuthentication(User user) {
        OAuth2UserAdapter adapter = mock(OAuth2UserAdapter.class);
        given(adapter.getUser()).willReturn(user);
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(adapter);
        return authentication;
    }

    private void stubTokenIssue() {
        given(jwtUtil.generateAccessToken(any(JwtUserInfo.class))).willReturn(ACCESS_TOKEN);
        given(jwtUtil.generateRefreshToken(any(JwtUserInfo.class))).willReturn(REFRESH_TOKEN_VALUE);
        given(exchangeCodeStore.issue(any(OAuthExchangeData.class))).willReturn(EXCHANGE_CODE);
    }

    @Nested
    @DisplayName("리다이렉트 URL (토큰 비노출)")
    class RedirectUrl {

        @Test
        @DisplayName("리다이렉트 URL에는 code만 실리고 accessToken/refreshToken은 노출되지 않는다")
        void onSuccess_redirectUrlContainsCodeOnly() throws Exception {
            // given
            stubTokenIssue();
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), response, mockAuthentication(userWithUid(USER_UID)));

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).startsWith(REDIRECT_URI + "?");
            assertThat(redirectedUrl).contains("code=" + EXCHANGE_CODE);
            assertThat(redirectedUrl).doesNotContain("accessToken");
            assertThat(redirectedUrl).doesNotContain("refreshToken");
            assertThat(redirectedUrl).doesNotContain(ACCESS_TOKEN);
            assertThat(redirectedUrl).doesNotContain(REFRESH_TOKEN_VALUE);
        }

        @Test
        @DisplayName("발급된 교환 코드에 실제 토큰이 서버 측 저장소로 담긴다")
        void onSuccess_storesTokensInExchangeStore() throws Exception {
            // given
            stubTokenIssue();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(userWithUid(USER_UID)));

            // then
            ArgumentCaptor<OAuthExchangeData> captor = ArgumentCaptor.forClass(OAuthExchangeData.class);
            then(exchangeCodeStore).should().issue(captor.capture());
            OAuthExchangeData data = captor.getValue();
            assertThat(data.userId()).isEqualTo(USER_UID);
            assertThat(data.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(data.refreshToken()).isEqualTo(REFRESH_TOKEN_VALUE);
        }
    }

    @Nested
    @DisplayName("클라이언트별 콜백 분기")
    class RedirectTarget {

        @Test
        @DisplayName("앱에서 시작한 로그인(state에 .app 표식)은 커스텀 스킴으로 리다이렉트한다")
        void appClient_redirectsToCustomScheme() throws Exception {
            // given
            stubTokenIssue();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("state", "random-state" + NonceCapturingAuthorizationRequestResolver.APP_STATE_SUFFIX);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(request, response, mockAuthentication(userWithUid(USER_UID)));

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo(APP_REDIRECT_URI + "?code=" + EXCHANGE_CODE);
        }

        @Test
        @DisplayName("표식 없는 state는 웹 콜백으로 리다이렉트한다")
        void webClient_redirectsToWebCallback() throws Exception {
            // given
            stubTokenIssue();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("state", "random-state");
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(request, response, mockAuthentication(userWithUid(USER_UID)));

            // then
            assertThat(response.getRedirectedUrl()).startsWith(REDIRECT_URI + "?");
        }

        @Test
        @DisplayName("앱 콜백이 설정되지 않은 환경에서는 웹 콜백으로 폴백한다")
        void appClient_withoutAppRedirectUri_fallsBackToWeb() throws Exception {
            // given
            stubTokenIssue();
            ReflectionTestUtils.setField(successHandler, "appRedirectUri", "");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("state", "random-state" + NonceCapturingAuthorizationRequestResolver.APP_STATE_SUFFIX);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(request, response, mockAuthentication(userWithUid(USER_UID)));

            // then
            assertThat(response.getRedirectedUrl()).startsWith(REDIRECT_URI + "?");
        }
    }

    @Nested
    @DisplayName("nonce 바인딩")
    class NonceBinding {

        @Test
        @DisplayName("콜백의 state로 회수한 nonce가 교환 코드에 바인딩된다")
        void onSuccess_bindsNonceFromState() throws Exception {
            // given
            stubTokenIssue();
            String state = "state-value";
            String nonce = "browser-nonce";
            given(nonceStore.consume(state)).willReturn(nonce);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("state", state);

            // when
            successHandler.onAuthenticationSuccess(
                    request, new MockHttpServletResponse(), mockAuthentication(userWithUid(USER_UID)));

            // then
            ArgumentCaptor<OAuthExchangeData> captor = ArgumentCaptor.forClass(OAuthExchangeData.class);
            then(exchangeCodeStore).should().issue(captor.capture());
            assertThat(captor.getValue().nonce()).isEqualTo(nonce);
        }

        @Test
        @DisplayName("state 파라미터가 없으면 nonce는 null로 바인딩된다")
        void onSuccess_noState_bindsNullNonce() throws Exception {
            // given
            stubTokenIssue();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(userWithUid(USER_UID)));

            // then
            ArgumentCaptor<OAuthExchangeData> captor = ArgumentCaptor.forClass(OAuthExchangeData.class);
            then(exchangeCodeStore).should().issue(captor.capture());
            assertThat(captor.getValue().nonce()).isNull();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 처리")
    class RefreshTokenHandling {

        @Test
        @DisplayName("로그인 시 새 리프레시 토큰으로 save를 호출해 기존 행을 덮어쓴다")
        void reLogin_savesNewRefreshToken() throws Exception {
            // given
            stubTokenIssue();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(userWithUid(USER_UID)));

            // then
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            then(refreshTokenRepository).should().save(captor.capture());
            RefreshToken saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_UID);
            assertThat(saved.getToken()).isEqualTo(REFRESH_TOKEN_VALUE);
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("JWT 발급 검증")
    class JwtIssuing {

        @Test
        @DisplayName("JwtUtil에 DB 사용자 정보가 올바르게 전달된다")
        void onSuccess_passesCorrectUserInfoToJwtUtil() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            stubTokenIssue();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(user));

            // then
            JwtUserInfo expected = new JwtUserInfo(
                    USER_UID, user.getNickname(), user.getEmail(), user.getRole().name());
            then(jwtUtil).should().generateAccessToken(expected);
            then(jwtUtil).should().generateRefreshToken(expected);
        }
    }
}
