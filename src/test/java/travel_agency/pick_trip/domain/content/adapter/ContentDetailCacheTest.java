package travel_agency.pick_trip.domain.content.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

/**
 * 상세 조회 캐시 배선 검증. 상세 1건이 TourAPI 3콜을 쓰므로 재조회가 실제로 차단되는지 확인한다.
 *
 * <p>전체 컨텍스트({@code @SpringBootTest})는 MySQL 을 요구해 캐시 프록시 확인에 비해 과하므로
 * 캐시에 필요한 빈만 올린 최소 컨텍스트를 쓴다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ContentDetailCacheTest.CacheTestConfig.class)
@DisplayName("TourApiContentAdapter 상세 조회 캐시")
class ContentDetailCacheTest {

    private static final String CACHE_NAME = "contentDetail";

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        TourApiClient tourApiClient() {
            return mock(TourApiClient.class);
        }

        @Bean
        TourApiContentMapper tourApiContentMapper() {
            return mock(TourApiContentMapper.class);
        }

        @Bean
        TourApiContentAdapter tourApiContentAdapter(TourApiClient client, TourApiContentMapper mapper) {
            return new TourApiContentAdapter(client, mapper);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CACHE_NAME);
        }
    }

    @Autowired private TourApiContentAdapter adapter;
    @Autowired private TourApiClient tourApiClient;
    @Autowired private TourApiContentMapper mapper;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void clearState() {
        Mockito.reset(tourApiClient, mapper);
        cacheManager.getCache(CACHE_NAME).clear();
    }

    @Test
    @DisplayName("같은 contentId를 다시 조회하면 TourAPI를 호출하지 않는다")
    void sameContentId_callsTourApiOnce() {
        // given
        String contentId = "2748010";
        ContentDetailResponse expected = detailResponse(contentId);
        givenDetailAvailable(contentId, expected);

        // when
        ContentDetailResponse first = adapter.fetchDetail(contentId);
        ContentDetailResponse second = adapter.fetchDetail(contentId);

        // then
        assertThat(first).isEqualTo(expected);
        assertThat(second).isEqualTo(expected);
        verify(tourApiClient, times(1)).getDetailCommon(contentId);
        verify(tourApiClient, times(1)).getDetailIntro(eq(contentId), any());
        verify(tourApiClient, times(1)).getDetailImage(contentId);
    }

    @Test
    @DisplayName("다른 contentId는 각각 TourAPI를 호출한다")
    void differentContentId_callsTourApiEachTime() {
        // given
        String first = "2748010";
        String second = "127434";
        givenDetailAvailable(first, detailResponse(first));
        givenDetailAvailable(second, detailResponse(second));

        // when
        adapter.fetchDetail(first);
        adapter.fetchDetail(second);

        // then
        verify(tourApiClient, times(1)).getDetailCommon(first);
        verify(tourApiClient, times(1)).getDetailCommon(second);
    }

    @Test
    @DisplayName("호출 실패는 캐시되지 않아 다음 조회에서 다시 시도한다")
    void failure_isNotCached() {
        // given - TourAPI 일일 한도 초과(429) 같은 일시적 실패가 캐시에 굳으면 안 된다
        String contentId = "2748010";
        given(tourApiClient.getDetailCommon(contentId)).willThrow(FeignException.class);

        // when
        assertThatThrownBy(() -> adapter.fetchDetail(contentId))
                .isInstanceOf(ContentException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        assertThatThrownBy(() -> adapter.fetchDetail(contentId))
                .isInstanceOf(ContentException.class);

        // then
        verify(tourApiClient, times(2)).getDetailCommon(contentId);
        assertThat(cacheManager.getCache(CACHE_NAME).get(contentId)).isNull();
    }

    @Test
    @DisplayName("조회 결과가 없으면 캐시하지 않고 CONTENT_NOT_FOUND를 던진다")
    void notFound_isNotCached() {
        // given
        String contentId = "9999999";
        given(tourApiClient.getDetailCommon(contentId)).willReturn(emptyCommonResponse());

        // when
        assertThatThrownBy(() -> adapter.fetchDetail(contentId))
                .isInstanceOf(ContentException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);

        // then
        assertThat(cacheManager.getCache(CACHE_NAME).get(contentId)).isNull();
        verify(tourApiClient, never()).getDetailIntro(any(), any());
    }

    private void givenDetailAvailable(String contentId, ContentDetailResponse response) {
        TourApiDetailCommonResponse common = commonResponse(contentId);
        given(tourApiClient.getDetailCommon(contentId)).willReturn(common);
        given(mapper.toDetailResponse(eq(common), any(), any())).willReturn(response);
    }

    private TourApiDetailCommonResponse commonResponse(String contentId) {
        TourApiDetailCommonResponse.Item item = new TourApiDetailCommonResponse.Item(
                contentId, "12", "쌍계사", "경남 하동군", null, null, null,
                "127.0", "35.0", null, null, null, null, null, "36", "13"
        );
        return new TourApiDetailCommonResponse(
                new TourApiDetailCommonResponse.Response(
                        new TourApiDetailCommonResponse.Body(
                                new TourApiDetailCommonResponse.Items(List.of(item))
                        )
                )
        );
    }

    private TourApiDetailCommonResponse emptyCommonResponse() {
        return new TourApiDetailCommonResponse(
                new TourApiDetailCommonResponse.Response(
                        new TourApiDetailCommonResponse.Body(
                                new TourApiDetailCommonResponse.Items(List.of())
                        )
                )
        );
    }

    private ContentDetailResponse detailResponse(String contentId) {
        return new ContentDetailResponse(
                contentId, "쌍계사", 12, "경남 하동군", null, null, 35.0, 127.0, null,
                null, null, null, null, null, null, null, null, null,
                List.of(), null, false, "HADONG"
        );
    }
}
