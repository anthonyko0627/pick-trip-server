package travel_agency.pick_trip.domain.content.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.content.entity.RegionVisitorStats;
import travel_agency.pick_trip.domain.region.Region;

public interface RegionVisitorStatsRepository extends JpaRepository<RegionVisitorStats, Long> {

    Optional<RegionVisitorStats> findByRegionAndStatMonth(Region region, String statMonth);

    /** 최근 연월부터 내림차순. 응답에 쓸 기간(최근 N개월)을 앞에서 잘라 쓴다. */
    List<RegionVisitorStats> findByRegionOrderByStatMonthDesc(Region region);
}
