package travel_agency.pick_trip.domain.itinerary.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 생성된(또는 수정된) 일정을 저장하는 요청.
 * 클라이언트는 미리보기 결과를 그대로, 혹은 사용자가 편집한 상태로 전달한다.
 */
public record SaveItineraryRequest(
        @NotBlank String title,
        @NotNull Region region,
        LocalDate travelDate,
        @NotNull Integer duration,
        @NotEmpty @Valid List<DayRequest> days
) {

    public record DayRequest(
            @NotNull Integer dayIndex,
            @NotEmpty @Valid List<ItemRequest> items,
            Integer totalTravelMinutes,
            BigDecimal totalTravelKm
    ) {
    }

    public record ItemRequest(
            @NotBlank String contentId,
            String title,
            int order,
            String reason,
            boolean pinned,
            // 미리보기 응답과 동일한 "HH:mm" 문자열을 그대로 되돌려받기 위해 역직렬화 형식을 고정한다.
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime
    ) {
    }
}
