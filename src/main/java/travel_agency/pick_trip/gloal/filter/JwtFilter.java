package travel_agency.pick_trip.gloal.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.ErrorResponse;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtUtil.parseToken(token).getPayload();

            // 리프레시 토큰을 Bearer 로 제시해 보호 API 를 통과하는 것을 막는다.
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class))) {
                sendErrorResponse(response, request, ErrorCode.AUTH_INVALID_TOKEN);
                return;
            }

            JwtUserPrincipal principal = JwtUserPrincipal.from(claims);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ExpiredJwtException e) {
            sendErrorResponse(response, request, ErrorCode.AUTH_EXPIRED_TOKEN);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            sendErrorResponse(response, request, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, HttpServletRequest request, ErrorCode errorCode)
            throws IOException {
        String traceId = (String) request.getAttribute("traceId");
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode, traceId));
    }
}
