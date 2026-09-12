package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.entity.ContentCongestion;
import travel_agency.pick_trip.domain.content.entity.CongestionLevel;
import travel_agency.pick_trip.domain.content.repository.ContentCongestionRepository;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.content.repository.projection.ContentPopularityProjection;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("CongestionService")
class CongestionServiceTest {

    @Mock private TravelContentRepository travelContentRepository;
    @Mock private ContentCongestionRepository contentCongestionRepository;

    @InjectMocks private CongestionService congestionService;

    private static final Region REGION = Region.HADONG;

    private ContentPopularityProjection popularity(String contentId, String contentTypeId, long basketCount) {
        return new ContentPopularityProjection() {
            @Override
            public String getSourceContentId() {
                return contentId;
            }

            @Override
            public String getContentTypeId() {
                return contentTypeId;
            }

            @Override
            public long getBasketCount() {
                return basketCount;
            }
        };
    }

    @Test
    @DisplayName("지역 콘텐츠마다 방문 시간대 스냅샷을 다시 계산해 통째로 교체한다")
    void refreshRegion_스냅샷_교체() {
        // given: 가장 많이 담긴 관광지(c3)와 한 번도 담기지 않은 관광지(c1)
        given(travelContentRepository.findPopularityByRegion("HADONG")).willReturn(List.of(
                popularity("c1", "12", 0L),
                popularity("c2", "12", 3L),
                popularity("c3", "12", 10L)));

        // when
        int saved = congestionService.refreshRegion(REGION);

        // then
        assertThat(saved).isEqualTo(3 * CongestionCalculator.SNAPSHOT_HOURS.size());
        verify(contentCongestionRepository).deleteBySourceContentIds(List.of("c1", "c2", "c3"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContentCongestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentCongestionRepository).saveAll(captor.capture());
        List<ContentCongestion> snapshots = captor.getValue();

        // 지역에서 가장 많이 담긴 콘텐츠(백분위 1.0)는 비피크 시간대에도 HIGH 다.
        assertThat(levelOf(snapshots, "c3", 13)).isEqualTo(CongestionLevel.HIGH);
        assertThat(levelOf(snapshots, "c3", 19)).isEqualTo(CongestionLevel.HIGH);
        // 중간 인기(백분위 0.5) 관광지는 피크 시간대(13시)에만 MEDIUM 이고 비피크(19시)에는 LOW 로 내려간다.
        assertThat(levelOf(snapshots, "c2", 13)).isEqualTo(CongestionLevel.MEDIUM);
        assertThat(levelOf(snapshots, "c2", 19)).isEqualTo(CongestionLevel.LOW);
        // 아무도 담지 않은 콘텐츠는 시간대와 무관하게 LOW 다.
        assertThat(levelOf(snapshots, "c1", 13)).isEqualTo(CongestionLevel.LOW);
    }

    @Test
    @DisplayName("지역 콘텐츠가 없으면 스냅샷을 건드리지 않는다")
    void refreshRegion_콘텐츠없음_미변경() {
        // given
        given(travelContentRepository.findPopularityByRegion("HADONG")).willReturn(List.of());

        // when
        int saved = congestionService.refreshRegion(REGION);

        // then
        assertThat(saved).isZero();
        verify(contentCongestionRepository, never()).deleteBySourceContentIds(any());
        verify(contentCongestionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("스냅샷을 콘텐츠·시간대 맵으로 돌려준다")
    void findLevels_맵매핑() {
        // given
        given(contentCongestionRepository.findBySourceContentIdIn(List.of("c1"))).willReturn(List.of(
                snapshot("c1", 12, CongestionLevel.HIGH),
                snapshot("c1", 19, CongestionLevel.LOW)));

        // when
        Map<String, Map<Integer, CongestionLevel>> levels = congestionService.findLevels(List.of("c1"));

        // then
        assertThat(levels.get("c1")).containsExactlyInAnyOrderEntriesOf(
                Map.of(12, CongestionLevel.HIGH, 19, CongestionLevel.LOW));
    }

    @Test
    @DisplayName("조회할 콘텐츠가 없으면 저장소를 호출하지 않는다")
    void findLevels_빈입력_조회안함() {
        // when
        Map<String, Map<Integer, CongestionLevel>> levels = congestionService.findLevels(List.of());

        // then
        assertThat(levels).isEmpty();
        verify(contentCongestionRepository, never()).findBySourceContentIdIn(any());
    }

    private ContentCongestion snapshot(String contentId, int hourSlot, CongestionLevel level) {
        return ContentCongestion.builder()
                .sourceContentId(contentId)
                .hourSlot(hourSlot)
                .congestionLevel(level)
                .score(1.0)
                .computedAt(LocalDateTime.of(2026, 7, 2, 5, 30))
                .build();
    }

    private CongestionLevel levelOf(List<ContentCongestion> snapshots, String contentId, int hourSlot) {
        return snapshots.stream()
                .filter(row -> row.getSourceContentId().equals(contentId) && row.getHourSlot() == hourSlot)
                .map(ContentCongestion::getCongestionLevel)
                .findFirst()
                .orElse(null);
    }
}
