package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HttpCookieOAuth2AuthorizationRequestRepository")
class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String COOKIE_NAME = "oauth2_auth_request";

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository();

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("openid", "profile"))
                .state("test-state")
                .additionalParameters(Map.of("nonce", "browser-nonce"))
                .attributes(Map.of("registration_id", "google"))
                .build();
    }

    @Test
    @DisplayName("저장한 인가요청은 쿠키에서 그대로 복원된다")
    void saveThenLoad_restoresAuthorizationRequest() {
        // given
        OAuth2AuthorizationRequest original = authorizationRequest();
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookie(COOKIE_NAME));

        // when
        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(loadRequest);

        // then
        assertThat(restored).isNotNull();
        assertThat(restored.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(restored.getClientId()).isEqualTo(original.getClientId());
        assertThat(restored.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(restored.getScopes()).isEqualTo(original.getScopes());
        assertThat(restored.getState()).isEqualTo(original.getState());
        assertThat(restored.getGrantType()).isEqualTo(original.getGrantType());
        assertThat(restored.getResponseType()).isEqualTo(original.getResponseType());
        assertThat(restored.getAdditionalParameters()).isEqualTo(original.getAdditionalParameters());
        assertThat(restored.getAttributes()).isEqualTo(original.getAttributes());
        assertThat(restored.getAuthorizationRequestUri()).isEqualTo(original.getAuthorizationRequestUri());
    }

    @Test
    @DisplayName("허용 목록 밖 클래스가 담긴 쿠키는 역직렬화하지 않고 예외를 던진다")
    void loadAuthorizationRequest_disallowedClass_throws() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, serialized(new File("/etc/passwd"))));

        // when & then
        assertThatThrownBy(() -> repository.loadAuthorizationRequest(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OAuth2AuthorizationRequest 역직렬화 실패");
    }

    private String serialized(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return Base64.getUrlEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
