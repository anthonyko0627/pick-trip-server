package travel_agency.pick_trip.domain.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
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
}
