package travel_agency.pick_trip.domain.content.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.projection.ContentPopularityProjection;
import travel_agency.pick_trip.domain.content.repository.projection.NearbyContentProjection;
import travel_agency.pick_trip.domain.content.repository.projection.RegionContentProjection;
import travel_agency.pick_trip.domain.content.repository.projection.RegionImageProjection;
import travel_agency.pick_trip.domain.region.Region;

public interface TravelContentRepository extends JpaRepository<TravelContent, String> {

    List<TravelContent> findByRegion(Region region);

    List<TravelContent> findByRegionAndDataStatus(Region region, DataStatus dataStatus);

    long countByDataStatus(DataStatus dataStatus);

    /**
     * 홈 화면용 (지역, 대표 이미지) 목록. sourceContentId 오름차순이므로
     * 지역별 첫 행이 결정적인 대표 이미지가 된다.
     */
    @Query("""
            select new travel_agency.pick_trip.domain.content.repository.projection.RegionImageProjection(t.region, t.firstImage)
            from TravelContent t
            where t.dataStatus = :dataStatus
              and t.firstImage is not null
            order by t.sourceContentId asc
            """)
    List<RegionImageProjection> findRegionImages(@Param("dataStatus") DataStatus dataStatus);

    /**
     * AUGMENT 모드에서 AI 에게 제시할 같은 지역의 추가 후보를 조회한다.
     * 프롬프트에 실을 id·이름·분류만 뽑고, 개수는 {@code pageable} 로 자른다.
     * {@code excludedIds} 는 바구니에 이미 담긴 contentId 로, 호출부가 비어 있지 않음을 보장한다
     * (빈 컬렉션은 {@code not in} 이 SQL 로 번역되지 않는다).
     */
    @Query("""
            select new travel_agency.pick_trip.domain.content.repository.projection.RegionContentProjection(
                t.sourceContentId, t.title, t.contentTypeId)
            from TravelContent t
            where t.region = :region
              and t.dataStatus = :dataStatus
              and t.sourceContentId not in :excludedIds
            order by t.sourceContentId asc
            """)
    List<RegionContentProjection> findRegionCandidates(
            @Param("region") Region region,
            @Param("dataStatus") DataStatus dataStatus,
            @Param("excludedIds") Collection<String> excludedIds,
            Pageable pageable
    );

    /**
     * 주어진 contentId 중 해당 지역에 주어진 상태로 적재된 것만 골라낸다.
     * AUGMENT 모드에서 AI 가 제안한 추가 장소를 "같은 지역의 유효 콘텐츠"로 좁히는 데 쓴다.
     * 지역 콘텐츠 전체를 올리지 않도록, AI 응답에 등장한 id 만 넘겨 확인한다.
     */
    @Query("""
            select t.sourceContentId from TravelContent t
            where t.sourceContentId in :ids
              and t.region = :region
              and t.dataStatus = :dataStatus
            """)
    List<String> findIdsByRegion(
            @Param("ids") Collection<String> ids,
            @Param("region") Region region,
            @Param("dataStatus") DataStatus dataStatus
    );

    /**
     * 기준 좌표에서 반경({@code radiusKm}) 안의 ACTIVE 콘텐츠를 거리순으로 조회한다.
     * 거리는 Haversine 근사(지구 반경 6371km)이며, 기준 콘텐츠 자신과 좌표가 없거나 (0, 0)인 행은 제외한다.
     * {@code acos} 인자를 1로 클램프해 부동소수 오차로 인한 NaN 을 막는다.
     */
    @Query(value = """
            select * from (
                select
                    t.source_content_id as sourceContentId,
                    t.content_type_id   as contentTypeId,
                    t.title             as title,
                    t.address           as address,
                    t.first_image       as firstImage,
                    t.latitude          as latitude,
                    t.longitude         as longitude,
                    t.category          as category,
                    t.summary           as summary,
                    t.region            as region,
                    (6371 * acos(least(1.0,
                        cos(radians(:lat)) * cos(radians(t.latitude))
                          * cos(radians(t.longitude) - radians(:lng))
                        + sin(radians(:lat)) * sin(radians(t.latitude))
                    ))) as distanceKm
                from travel_contents t
                where t.data_status = 'ACTIVE'
                  and t.source_content_id <> :originId
                  and t.latitude is not null and t.longitude is not null
                  and t.latitude <> 0 and t.longitude <> 0
            ) as nearby
            where nearby.distanceKm <= :radiusKm
            order by nearby.distanceKm
            limit :size
            """, nativeQuery = true)
    List<NearbyContentProjection> findNearby(
            @Param("originId") String originId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("size") int size
    );

    /**
     * 혼잡 스냅샷 계산 입력. 지역 내 ACTIVE 콘텐츠와 그 콘텐츠가 바구니에 담긴 횟수를 함께 읽는다.
     * 개별 장소 단위 관광객수 공개 데이터가 없어 바구니 담긴 횟수를 인기도 프록시로 쓴다 (#73).
     * {@code basket_items} 는 콘텐츠와 FK 없이 {@code content_id} 문자열로만 연결돼 있어 네이티브 조인으로 센다.
     */
    @Query(value = """
            select
                t.source_content_id as sourceContentId,
                t.content_type_id   as contentTypeId,
                count(b.item_id)    as basketCount
            from travel_contents t
            left join basket_items b on b.content_id = t.source_content_id
            where t.region = :region
              and t.data_status = 'ACTIVE'
            group by t.source_content_id, t.content_type_id
            """, nativeQuery = true)
    List<ContentPopularityProjection> findPopularityByRegion(@Param("region") String region);
}
