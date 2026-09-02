package travel_agency.pick_trip.domain.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.projection.NearbyContentProjection;
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
}
