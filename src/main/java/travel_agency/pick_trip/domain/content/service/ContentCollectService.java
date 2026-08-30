package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.entity.ContentDetail;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 콘텐츠 수집 (수집 4단계). {@code /areaBasedList2}로 후보를 모으고
 * {@code /detailCommon2}·{@code /detailIntro2}·{@code /detailImage2}로 보강해
 * {@link TravelContent}/{@link ContentDetail}/{@link ContentImage}에 upsert한다.
 *
 * <p>보조 캐시 적재이며 실시간 조회 흐름은 변경하지 않는다. 콘텐츠 단위로 {@link FeignException}을
 * 잡아 일부 실패가 전체 수집을 막지 않게 하고, 실패 항목은 건너뛰며 로그를 남긴다(수동 트리거 전제).
 *
 * <p>외부 상세 호출은 트랜잭션 밖에서 수행하고, 영속화(save)만 콘텐츠 1건 단위의 짧은 트랜잭션
 * ({@link TransactionTemplate})으로 감싼다. 외부 응답 대기 동안 DB 커넥션을 점유하지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCollectService {

    /** MVP 콘텐츠 타입: 관광지·문화시설·축제·여행코스·레포츠·쇼핑·음식 */
    private static final List<String> MVP_CONTENT_TYPE_IDS =
            List.of("12", "14", "15", "25", "28", "38", "39");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TravelContentRepository travelContentRepository;
    private final ContentCollectMapper mapper;
    private final TransactionTemplate transactionTemplate;

    /** 지역의 MVP 콘텐츠 타입을 모두 수집한다. 반환값은 성공 적재 건수. */
    public int collectRegion(Region region) {
        int collected = 0;
        for (String contentTypeId : MVP_CONTENT_TYPE_IDS) {
            collected += collectRegionByType(region, contentTypeId);
        }
        log.info("[수집] {} 지역 콘텐츠 {}건 적재", region, collected);
        return collected;
    }

    private int collectRegionByType(Region region, String contentTypeId) {
        TourApiListResponse response;
        try {
            response = tourApiClient.getAreaBasedList(
                    region.getLDongRegnCd(), region.getLDongSignguCd(), contentTypeId, 1, PAGE_SIZE);
        } catch (FeignException e) {
            log.warn("[수집] 목록 조회 실패 region={} type={} - 건너뜀: {}", region, contentTypeId, e.getMessage());
            return 0;
        }
        if (response != null && response.isError()) {
            log.warn("[수집] 목록 TourAPI 오류 응답 region={} type={} code={} msg={} - 건너뜀",
                    region, contentTypeId, response.resultCode(), response.resultMsg());
            return 0;
        }

        int count = 0;
        for (TourApiListResponse.Item item : mapper.listItems(response)) {
            if (upsertContent(region, item)) {
                count++;
            }
        }
        return count;
    }

    private boolean upsertContent(Region region, TourApiListResponse.Item listItem) {
        String contentId = listItem.contentid();
        if (contentId == null || contentId.isBlank()) {
            return false;
        }
        try {
            // 외부 상세 호출은 트랜잭션 밖에서 수행해 DB 커넥션을 점유하지 않는다.
            TourApiDetailCommonResponse.Item common =
                    mapper.firstCommon(tourApiClient.getDetailCommon(contentId));
            String contentTypeId = resolveContentTypeId(common, listItem);
            TourApiDetailIntroResponse.Item intro = null;
            if (contentTypeId != null && !contentTypeId.isBlank()) {
                intro = mapper.firstIntro(tourApiClient.getDetailIntro(contentId, contentTypeId));
            }
            List<ContentImage> images = mapper.toContentImages(tourApiClient.getDetailImage(contentId));

            // 영속화만 짧은 트랜잭션으로 감싼다.
            TourApiDetailCommonResponse.Item commonRef = common;
            TourApiDetailIntroResponse.Item introRef = intro;
            transactionTemplate.executeWithoutResult(status ->
                    persistContent(region, listItem, commonRef, introRef, images));
            return true;
        } catch (FeignException e) {
            log.warn("[수집] 콘텐츠 {} 상세 보강 실패 - 건너뜀: {}", contentId, e.getMessage());
            return false;
        }
    }

    /** 외부 호출로 모은 상세 데이터를 트랜잭션 안에서 엔티티에 반영하고 저장한다. */
    private void persistContent(Region region, TourApiListResponse.Item listItem,
            TourApiDetailCommonResponse.Item common, TourApiDetailIntroResponse.Item intro,
            List<ContentImage> images) {
        TravelContent content = travelContentRepository.findById(listItem.contentid())
                .orElseGet(() -> newContent(region, listItem));

        if (common != null) {
            content.updateSourceData(
                    common.contenttypeid(),
                    common.title(),
                    resolveCategory(common),
                    common.overview(),
                    mapper.buildAddress(common.addr1(), common.addr2()),
                    mapper.parseLatitude(common.mapy()),
                    mapper.parseLongitude(common.mapx()),
                    common.tel(),
                    common.homepage(),
                    common.firstimage(),
                    null
            );
        }
        if (intro != null) {
            ContentDetail detail = content.getDetail();
            if (detail == null) {
                detail = ContentDetail.builder().build();
                content.assignDetail(detail);
            }
            detail.updateIntro(
                    intro.resolvedUseTime(),
                    intro.resolvedRestDate(),
                    intro.resolvedParking(),
                    intro.resolvedBabyCarriage(),
                    intro.chkpet(),
                    intro.usefee()
            );
        }
        if (!images.isEmpty()) {
            content.replaceImages(images);
        }
        travelContentRepository.save(content);
    }

    /** TourAPI 신분류체계 원본 코드({@code lclsSystm1/2})를 내부 6종 카테고리로 변환한다. */
    private ContentCategory resolveCategory(TourApiDetailCommonResponse.Item common) {
        if (common == null) {
            return null;
        }
        return ContentCategory.from(common.lclsSystm1(), common.lclsSystm2());
    }

    /** 상세(common) 타입을 우선 사용하고, 없으면 목록 항목의 타입을 쓴다. */
    private String resolveContentTypeId(TourApiDetailCommonResponse.Item common,
            TourApiListResponse.Item listItem) {
        if (common != null && common.contenttypeid() != null && !common.contenttypeid().isBlank()) {
            return common.contenttypeid();
        }
        return listItem.contenttypeid();
    }

    private TravelContent newContent(Region region, TourApiListResponse.Item listItem) {
        return TravelContent.builder()
                .sourceContentId(listItem.contentid())
                .contentTypeId(listItem.contenttypeid())
                .title(listItem.title())
                .region(region)
                .latitude(mapper.parseLatitude(listItem.mapy()))
                .longitude(mapper.parseLongitude(listItem.mapx()))
                .firstImage(listItem.firstimage())
                .dataStatus(DataStatus.ACTIVE)
                .build();
    }
}
