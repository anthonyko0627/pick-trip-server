package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiFestivalResponse;
import travel_agency.pick_trip.domain.content.entity.ContentDetail;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 축제 수집 (수집 5단계). {@code /searchFestival2}로 여행 날짜 기준 축제를 모아
 * {@link TravelContent}(타입 15)와 {@link ContentDetail}의 행사 기간에 upsert한다.
 *
 * <p>저장한 {@code eventStartDate}/{@code eventEndDate}는 AI 일정 생성의 제약 조건으로 사용된다.
 * 호출 실패 시 예외를 전파하지 않고 기존 데이터를 유지하며 로그를 남긴다.
 *
 * <p>외부 조회는 트랜잭션 밖에서 수행하고, upsert(save)만 짧은 트랜잭션으로 감싼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalCollectService {

    private static final String FESTIVAL_CONTENT_TYPE_ID = "15";
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TravelContentRepository travelContentRepository;
    private final ContentCollectMapper mapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 지역의 축제를 수집한다. {@code eventStartDate}(yyyyMMdd) 이후 진행 중·예정 축제가 대상.
     * 반환값은 적재 건수.
     */
    public int collectFestivals(Region region, String eventStartDate) {
        TourApiFestivalResponse response;
        try {
            response = tourApiClient.searchFestival(
                    eventStartDate, region.getLDongRegnCd(), region.getLDongSignguCd(), 1, PAGE_SIZE);
        } catch (FeignException e) {
            log.warn("[수집] 축제 조회 실패 region={} from={} - 건너뜀: {}", region, eventStartDate, e.getMessage());
            return 0;
        }
        if (response != null && response.isError()) {
            log.warn("[수집] 축제 TourAPI 오류 응답 region={} code={} msg={} - 건너뜀",
                    region, response.resultCode(), response.resultMsg());
            return 0;
        }

        List<TourApiFestivalResponse.Item> festivals = response.festivals();
        Integer count = transactionTemplate.execute(status -> {
            int c = 0;
            for (TourApiFestivalResponse.Item item : festivals) {
                if (upsertFestival(region, item)) {
                    c++;
                }
            }
            return c;
        });
        int result = count != null ? count : 0;
        log.info("[수집] {} 지역 축제 {}건 적재", region, result);
        return result;
    }

    private boolean upsertFestival(Region region, TourApiFestivalResponse.Item item) {
        String contentId = item.contentid();
        if (contentId == null || contentId.isBlank()) {
            return false;
        }

        TravelContent content = travelContentRepository.findById(contentId)
                .orElseGet(() -> newFestival(region, item));

        content.updateSourceData(
                FESTIVAL_CONTENT_TYPE_ID,
                item.title(),
                null,
                null,
                mapper.buildAddress(item.addr1(), item.addr2()),
                mapper.parseLatitude(item.mapy()),
                mapper.parseLongitude(item.mapx()),
                item.tel(),
                null,
                item.firstimage(),
                null
        );

        ContentDetail detail = content.getDetail();
        if (detail == null) {
            detail = ContentDetail.builder().build();
            content.assignDetail(detail);
        }
        detail.updateEventPeriod(item.eventstartdate(), item.eventenddate());

        travelContentRepository.save(content);
        return true;
    }

    private TravelContent newFestival(Region region, TourApiFestivalResponse.Item item) {
        return TravelContent.builder()
                .sourceContentId(item.contentid())
                .contentTypeId(FESTIVAL_CONTENT_TYPE_ID)
                .title(item.title())
                .region(region)
                .latitude(mapper.parseLatitude(item.mapy()))
                .longitude(mapper.parseLongitude(item.mapx()))
                .firstImage(item.firstimage())
                .dataStatus(DataStatus.ACTIVE)
                .build();
    }
}
