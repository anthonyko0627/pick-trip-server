package travel_agency.pick_trip.domain.itinerary.client;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

/**
 * 고도 조회 전용 Feign 설정. 인증키가 없는 공개 API 라 헤더를 붙일 것이 없다.
 */
public class ElevationFeignConfig {

    /** 일정 생성 응답 경로에 끼는 호출이라 무한 대기를 막는다: connect 3s / read 5s. */
    @Bean
    public Request.Options elevationRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    /**
     * 재시도 없음: 실패하면 고도 미상(경사 페널티 0)으로 폴백하므로 응답을 더 늦출 이유가 없고,
     * 공개 엔드포인트의 초당 1콜 제한을 재시도로 스스로 깎아먹지 않는다.
     */
    @Bean
    public Retryer elevationRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
