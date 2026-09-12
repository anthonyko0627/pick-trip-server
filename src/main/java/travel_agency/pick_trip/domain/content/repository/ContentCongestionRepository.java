package travel_agency.pick_trip.domain.content.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import travel_agency.pick_trip.domain.content.entity.ContentCongestion;

public interface ContentCongestionRepository extends JpaRepository<ContentCongestion, Long> {

    List<ContentCongestion> findBySourceContentIdIn(Collection<String> sourceContentIds);

    /**
     * 지역 스냅샷 재계산 전에 해당 콘텐츠들의 이전 스냅샷을 통째로 지운다.
     * 파생 삭제(deleteBy...)는 행마다 select + delete 를 내므로 벌크 삭제로 한 번에 끝낸다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ContentCongestion c where c.sourceContentId in :sourceContentIds")
    void deleteBySourceContentIds(@Param("sourceContentIds") Collection<String> sourceContentIds);
}
