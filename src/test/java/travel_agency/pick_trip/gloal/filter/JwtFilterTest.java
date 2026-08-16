package travel_agency.pick_trip.gloal.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtFilter")
class JwtFilterTest {

    @InjectMocks
    private JwtFilter jwtFilter;

    @Mock private ObjectMapper objectMapper;
    @Mock private JwtUtil jwtUtil;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final UUID USER_UID = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private Jws<Claims> mockJwsWithClaims() {
        Claims claims = mock(Claims.class);
        given(claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class)).willReturn(JwtUtil.TOKEN_TYPE_ACCESS);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");

        Jws<Claims> jws = mock(Jws.class);
        given(jws.getPayload()).willReturn(claims);
        return jws;
    }

    /** typ 클레임만 지정한 토큰. 액세스 토큰이 아닌 토큰을 흉내 낸다. */
    @SuppressWarnings("unchecked")
    private Jws<Claims> mockJwsWithTokenType(String tokenType) {
        Claims claims = mock(Claims.class);
        given(claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class)).willReturn(tokenType);

        Jws<Claims> jws = mock(Jws.class);
        given(jws.getPayload()).willReturn(claims);
        return jws;
    }

    @Nested
    @DisplayName("Authorization 헤더 없음")
    class NoAuthorizationHeader {

        @Test
        @DisplayName("Authorization 헤더가 없으면 필터 체인이 진행되고 SecurityContext는 비어있다")
        void noHeader_passesFilterChain() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(chain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Bearer 접두사 없는 Authorization 헤더이면 필터 체인이 진행된다")
        void noBearerPrefix_passesFilterChain() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(chain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("유효한 토큰")
    class ValidToken {

        @Test
        @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보가 등록되고 필터 체인이 진행된다")
        void validToken_setsAuthenticationAndPassesChain() throws Exception {
            // given: stubbing 중첩 호출을 피하기 위해 사전에 mock 생성
            Jws<Claims> jws = mockJwsWithClaims();
            given(jwtUtil.parseToken(VALID_TOKEN)).willReturn(jws);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("유효한 토큰이면 SecurityContext에 올바른 uid가 등록된다")
        void validToken_registersCorrectUid() throws Exception {
            // given
            Jws<Claims> jws = mockJwsWithClaims();
            given(jwtUtil.parseToken(VALID_TOKEN)).willReturn(jws);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertThat(principal).hasFieldOrPropertyWithValue("uid", USER_UID);
        }
    }

    @Nested
    @DisplayName("유효하지 않은 토큰")
    class InvalidToken {

        @Test
        @DisplayName("만료된 토큰이면 401을 응답하고 필터 체인이 중단된다")
        void expiredToken_returns401AndStopsChain() throws Exception {
            // given
            given(jwtUtil.parseToken(VALID_TOKEN)).willThrow(mock(ExpiredJwtException.class));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("위조된 토큰이면 401을 응답하고 필터 체인이 중단된다")
        void tamperedToken_returns401AndStopsChain() throws Exception {
            // given
            given(jwtUtil.parseToken(VALID_TOKEN)).willThrow(mock(JwtException.class));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("IllegalArgumentException이 발생해도 401을 응답하고 필터 체인이 중단된다")
        void illegalArgumentToken_returns401AndStopsChain() throws Exception {
            // given
            given(jwtUtil.parseToken(VALID_TOKEN)).willThrow(new IllegalArgumentException("empty token"));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 타입 검증")
    class TokenTypeValidation {

        @Test
        @DisplayName("리프레시 토큰을 Bearer로 제시하면 401을 응답하고 인증이 등록되지 않는다")
        void refreshToken_returns401AndDoesNotAuthenticate() throws Exception {
            // given
            // 헬퍼가 내부에서 스텁을 만들므로 given(...) 인자 자리에서 호출하면 중첩 스터빙이 된다.
            var jws = mockJwsWithTokenType("refresh");
            given(jwtUtil.parseToken(VALID_TOKEN)).willReturn(jws);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("typ 클레임이 없는 토큰이면 401을 응답하고 인증이 등록되지 않는다")
        void missingTokenType_returns401AndDoesNotAuthenticate() throws Exception {
            // given
            var jws = mockJwsWithTokenType(null);
            given(jwtUtil.parseToken(VALID_TOKEN)).willReturn(jws);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            jwtFilter.doFilterInternal(request, response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
