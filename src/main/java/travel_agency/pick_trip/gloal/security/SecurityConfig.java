package travel_agency.pick_trip.gloal.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import travel_agency.pick_trip.domain.auth.oauth2.CustomOAuth2UserService;
import travel_agency.pick_trip.domain.auth.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import travel_agency.pick_trip.domain.auth.oauth2.NonceCapturingAuthorizationRequestResolver;
import travel_agency.pick_trip.domain.auth.oauth2.OAuth2AuthenticationFailureHandler;
import travel_agency.pick_trip.domain.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import travel_agency.pick_trip.gloal.filter.JwtFilter;
import travel_agency.pick_trip.gloal.filter.TraceIdFilter;

/**
 * 보안 필터 체인을 두 개로 분리한다.
 *
 * <ul>
 *   <li><b>API 체인</b>(<code>/api/v1/**</code>): 순수 스테이트리스 Bearer(JWT) 인증. 세션·쿠키 인증
 *       경로가 없으므로 CSRF 필터를 명시적으로 끈다. Bearer 헤더는 브라우저가 자동 첨부하지 않아
 *       CSRF 에 안전하며, 이 체인에 쿠키/세션 기반 인증 우회로는 존재하지 않는다.</li>
 *   <li><b>oauth2Login 체인</b>(그 외 전체): 소셜 로그인 핸드셰이크. 로그인 CSRF 방어는 OAuth
 *       {@code state} 파라미터 + 인가요청 쿠키 저장소가 담당하며, 이는 서블릿 CsrfFilter 와 독립적으로
 *       동작한다. MCP(POST)/문서 엔드포인트가 같은 체인에 있어 CsrfFilter 는 끄되, state 검증은 유지한다.</li>
 * </ul>
 */
@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final TraceIdFilter traceIdFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final NonceCapturingAuthorizationRequestResolver nonceCapturingAuthorizationRequestResolver;

    /**
     * 데이터 API 체인. {@code /api/v1/**} 만 처리하며 Bearer 토큰으로만 인증한다.
     * 스테이트리스이고 쿠키/세션 인증이 없으므로 CSRF 필터를 끄는 것이 정답이다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth - 인증 불필요 (토큰 발급/교환)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/oauth/exchange").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/refresh").permitAll()
                        // Content - 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents/**").permitAll()
                        // Home - 홈 화면 조회 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/home").permitAll()
                        // Share - 공유 토큰 조회 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/share/**").permitAll()
                        // Basket - 여행 바구니는 로그인 필요
                        .requestMatchers("/api/v1/baskets/**").authenticated()
                        // Favorite - 찜하기는 로그인 필요
                        .requestMatchers("/api/v1/favorites/**").authenticated()
                        // Itinerary - 일정 생성·저장·조회·수정·공유 생성은 로그인 필요
                        .requestMatchers("/api/v1/itineraries/**").authenticated()
                        // 그 외 /api/v1/** 는 아직 개발 중이라 잠시 허용한다.
                        // TODO: 개발 완료 후 authenticated() 로 좁힌다.
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(traceIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * oauth2Login 및 기타(문서·MCP) 체인. 소셜 로그인 시작·콜백과 read-only 문서/ MCP 를 처리한다.
     * state 파라미터 기반 CSRF 보호는 인가요청 쿠키 저장소가 유지한다.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 소셜 로그인 시작·콜백 경로는 oauth2Login 이 처리한다.
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        // API 문서 / MCP 서버 - 인증 불필요 (read-only 문서 제공)
                        .requestMatchers("/sse", "/mcp/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // actuator 등 나머지는 현재 개발 중이라 잠시 허용한다.
                        // TODO: 개발 완료 후 필요한 경로만 좁힌다.
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                // 표준 인가요청 생성에 위임하되, 로그인 시작 시 nonce 를 state 에 바인딩한다.
                                .authorizationRequestResolver(nonceCapturingAuthorizationRequestResolver)
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .addFilterBefore(traceIdFilter, SecurityContextHolderFilter.class)
                .build();
    }
}
