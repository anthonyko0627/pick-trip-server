package travel_agency.pick_trip.domain.content.client;

import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Kakao Mobility 전용 Feign 설정.
 * REST API 키는 기존 Kakao 앱의 것을 재사용한다({@code KAKAO_CLIENT_ID}).
 */
public class KakaoMobilityFeignConfig {

    @Bean
    public RequestInterceptor kakaoMobilityRequestInterceptor(
            @Value("${kakao-mobility.rest-api-key}") String restApiKey) {
        return template -> {
            template.header("Authorization", "KakaoAK " + restApiKey);
            template.header("Content-Type", "application/json");
        };
    }

    /** 무한 대기 방지: connect 3s / read 5s. 근처 조회는 응답성이 중요하다. */
    @Bean
    public Request.Options kakaoMobilityRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    /** 재시도 없음: 실패하면 즉시 직선거리로 폴백하므로 사용량을 아낀다. */
    @Bean
    public Retryer kakaoMobilityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
