package travel_agency.pick_trip.domain.content.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 지역별 방문자수 API 전용 Feign 설정. 공공데이터포털 공통 쿼리 파라미터가 TourAPI 와 같아
 * {@link TourApiRequestInterceptor}와 인증키({@code tour-api.service-key}) 를 그대로 재사용한다.
 */
public class VisitorStatsFeignConfig {

    @Bean
    public RequestInterceptor visitorStatsRequestInterceptor(
            @Value("${tour-api.service-key}") String serviceKey) {
        return new TourApiRequestInterceptor(serviceKey);
    }

    /** 결과가 없을 때 items 를 빈 문자열("")로 내려주는 공공데이터포털 관례를 그대로 흡수한다. */
    @Bean
    public Decoder visitorStatsDecoder() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        return new JacksonDecoder(mapper);
    }

    /** 무한 대기로 인한 배치 행(hang) 방지: connect 5s / read 10s. */
    @Bean
    public Request.Options visitorStatsRequestOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }

    /** 재시도 없음: 수집 실패는 자체 프록시로 폴백하므로 사용량을 아낀다. */
    @Bean
    public Retryer visitorStatsRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
