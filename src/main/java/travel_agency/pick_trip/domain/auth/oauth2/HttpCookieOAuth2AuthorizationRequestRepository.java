package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

// STATELESS 세션 정책에서는 HttpSession에 state를 저장할 수 없으므로
// HttpOnly 쿠키에 직렬화해서 저장한다. state 기반 CSRF 방어를 유지하면서 세션 없이 동작한다.
// 하드닝: HttpOnly(스크립트 접근 차단) + SameSite=Lax(교차 사이트 전송 억제) + Secure(운영 HTTPS 강제).
// SameSite=Lax 는 최상위 이동(top-level redirect)에는 쿠키를 실어주므로 소셜 로그인 왕복은 정상 동작한다.
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    // state 유효 시간: 사용자가 3분 안에 소셜 로그인을 완료해야 한다.
    private static final Duration COOKIE_MAX_AGE = Duration.ofSeconds(180);

    // OAuth2AuthorizationRequest 와 그 필드(문자열·java.util 컬렉션·OAuth2 core 값 타입)만 허용하고
    // 마지막 "!*" 로 그 밖의 모든 클래스를 거부한다. 거부 시 InvalidClassException(IOException 하위)이 발생한다.
    private static final ObjectInputFilter DESERIALIZE_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=20;maxrefs=1000;maxarray=1000;"
                    + "org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;"
                    + "org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;"
                    + "org.springframework.security.oauth2.core.AuthorizationGrantType;"
                    + "java.util.*;java.lang.*;!*");

    // 운영(HTTPS)에서는 true, 로컬(HTTP)에서는 false. 프로퍼티로 환경별 제어한다.
    @Value("${app.oauth2.cookie-secure:false}")
    private boolean cookieSecure;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookieValue(request)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response);
            return;
        }
        ResponseCookie cookie = baseCookie(serialize(authorizationRequest))
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        deleteCookie(request, response);
        return authRequest;
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax");
    }

    private Optional<String> getCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return;
        boolean present = Arrays.stream(cookies).anyMatch(c -> COOKIE_NAME.equals(c.getName()));
        if (!present) return;
        // 만료 쿠키를 동일 속성으로 덮어써 즉시 제거한다.
        ResponseCookie cookie = baseCookie("").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(request);
            return Base64.getUrlEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest 직렬화 실패", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getUrlDecoder().decode(value));
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            // 쿠키 값은 클라이언트가 임의로 바꿔 보낼 수 있으므로, 인가요청 복원에 필요한 타입만 허용하고 나머지는 거부한다.
            ois.setObjectInputFilter(DESERIALIZE_FILTER);
            return (OAuth2AuthorizationRequest) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest 역직렬화 실패", e);
        }
    }
}
