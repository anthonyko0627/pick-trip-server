package travel_agency.pick_trip.gloal.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET_KEY = "test-secret-key-that-is-at-least-32-bytes!!";
    private static final long ACCESS_TOKEN_EXPIRE_MS = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14L;
    private static final UUID USER_UID = UUID.randomUUID();
    private static final String NICKNAME = "테스트유저";
    private static final String EMAIL = "test@example.com";
    private static final String ROLE = "USER";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireTimeMs", ACCESS_TOKEN_EXPIRE_MS);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpireTimeDays", REFRESH_TOKEN_EXPIRE_DAYS);
        ReflectionTestUtils.invokeMethod(jwtUtil, "init");
    }

    private JwtUserInfo userInfo() {
        return new JwtUserInfo(USER_UID, NICKNAME, EMAIL, ROLE);
    }

    @Nested
    @DisplayName("액세스 토큰 생성")
    class GenerateAccessToken {

        @Test
        @DisplayName("생성된 액세스 토큰에 uid, 닉네임, 이메일, 역할 클레임이 포함된다")
        void generateAccessToken_containsAllClaims() {
            // given
            JwtUserInfo info = userInfo();

            // when
            String token = jwtUtil.generateAccessToken(info);
            Claims claims = jwtUtil.parseToken(token).getPayload();

            // then
            assertThat(claims.getSubject()).isEqualTo(USER_UID.toString());
            assertThat(claims.get("name", String.class)).isEqualTo(NICKNAME);
            assertThat(claims.get("email", String.class)).isEqualTo(EMAIL);
            assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
            assertThat(claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtUtil.TOKEN_TYPE_ACCESS);
        }

    }

    @Nested
    @DisplayName("리프레시 토큰 생성")
    class GenerateRefreshToken {

        @Test
        @DisplayName("생성된 리프레시 토큰에는 uid만 포함되고 개인정보 클레임은 없다")
        void generateRefreshToken_containsOnlyUid() {
            // given
            JwtUserInfo info = userInfo();

            // when
            String token = jwtUtil.generateRefreshToken(info);
            Claims claims = jwtUtil.parseToken(token).getPayload();

            // then
            assertThat(claims.getSubject()).isEqualTo(USER_UID.toString());
            assertThat(claims.get("name")).isNull();
            assertThat(claims.get("email")).isNull();
            assertThat(claims.get("role")).isNull();
            // 타입 클레임이 없어야 JwtFilter 가 이 토큰을 액세스 토큰으로 오인하지 않는다.
            assertThat(claims.get(JwtUtil.CLAIM_TOKEN_TYPE)).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 파싱")
    class ParseToken {

        @Test
        @DisplayName("만료된 토큰 파싱 시 ExpiredJwtException이 발생한다")
        void parseToken_expiredToken_throwsExpiredJwtException() {
            // given: 만료 시간을 과거로 설정해 즉시 만료되는 토큰 생성
            ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireTimeMs", -1000L);
            String expiredToken = jwtUtil.generateAccessToken(userInfo());

            // when / then
            assertThatThrownBy(() -> jwtUtil.parseToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("위조된 토큰 파싱 시 JwtException이 발생한다")
        void parseToken_tamperedToken_throwsJwtException() {
            // given
            String validToken = jwtUtil.generateAccessToken(userInfo());
            String tamperedToken = validToken + "tampered";

            // when / then
            assertThatThrownBy(() -> jwtUtil.parseToken(tamperedToken))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰 파싱 시 JwtException이 발생한다")
        void parseToken_differentKeyToken_throwsJwtException() {
            // given: 다른 키로 서명된 JwtUtil로 토큰 생성
            JwtUtil otherUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherUtil, "secretKey", "other-secret-key-that-is-at-least-32bytes!!");
            ReflectionTestUtils.setField(otherUtil, "accessTokenExpireTimeMs", ACCESS_TOKEN_EXPIRE_MS);
            ReflectionTestUtils.setField(otherUtil, "refreshTokenExpireTimeDays", REFRESH_TOKEN_EXPIRE_DAYS);
            ReflectionTestUtils.invokeMethod(otherUtil, "init");
            String foreignToken = otherUtil.generateAccessToken(userInfo());

            // when / then
            assertThatThrownBy(() -> jwtUtil.parseToken(foreignToken))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    @DisplayName("초기화 검증")
    class Init {

        @Test
        @DisplayName("32바이트 미만 시크릿 키로 초기화하면 IllegalStateException이 발생한다")
        void init_shortSecretKey_throwsIllegalStateException() {
            // given
            JwtUtil shortKeyUtil = new JwtUtil();
            ReflectionTestUtils.setField(shortKeyUtil, "secretKey", "short-key");
            ReflectionTestUtils.setField(shortKeyUtil, "accessTokenExpireTimeMs", ACCESS_TOKEN_EXPIRE_MS);
            ReflectionTestUtils.setField(shortKeyUtil, "refreshTokenExpireTimeDays", REFRESH_TOKEN_EXPIRE_DAYS);

            // when / then
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(shortKeyUtil, "init"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32바이트");
        }
    }
}
