package travel_agency.pick_trip.gloal.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 활성화. 캐시 이름과 만료 정책은 {@code application.yaml} 의 {@code spring.cache} 에서 관리한다.
 *
 * <p>{@link org.springframework.context.annotation.Profile} 가드를 두지 않는 이유는, 로컬 개발에서도
 * TourAPI 일일 요청 한도를 아끼는 것이 목적이기 때문이다.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
