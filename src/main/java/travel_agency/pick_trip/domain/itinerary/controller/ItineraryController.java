package travel_agency.pick_trip.domain.itinerary.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.itinerary.dto.request.SaveItineraryRequest;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryGenerateResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItineraryResponse;
import travel_agency.pick_trip.domain.itinerary.dto.response.ItinerarySummaryResponse;
import travel_agency.pick_trip.domain.itinerary.service.ItineraryService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    /**
     * 바구니의 선택 콘텐츠·여행 조건으로 AI 일정을 생성한다 (저장 전 미리보기).
     */
    @PostMapping("/generate")
    public ResponseEntity<ItineraryGenerateResponse> generate(
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(itineraryService.generate(principal.getUid()));
    }

    /**
     * 생성된(또는 편집된) 일정을 저장한다.
     */
    @PostMapping
    public ResponseEntity<ItineraryResponse> save(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody SaveItineraryRequest request
    ) {
        ItineraryResponse response = itineraryService.save(principal.getUid(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 저장된 일정을 조회한다.
     */
    @GetMapping("/{itineraryId}")
    public ResponseEntity<ItineraryResponse> getItinerary(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itineraryId
    ) {
        return ResponseEntity.ok(itineraryService.getItinerary(principal.getUid(), itineraryId));
    }

    /**
     * 일정을 수정한다 (순서 변경·삭제·대체·고정).
     */
    @PatchMapping("/{itineraryId}")
    public ResponseEntity<ItineraryResponse> modify(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itineraryId,
            @Valid @RequestBody SaveItineraryRequest request
    ) {
        return ResponseEntity.ok(itineraryService.modify(principal.getUid(), itineraryId, request));
    }

    /**
     * 저장된 일정을 바구니 기준으로 다시 생성해 덮어쓴다.
     */
    @PostMapping("/{itineraryId}/regenerate")
    public ResponseEntity<ItineraryResponse> regenerate(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itineraryId
    ) {
        return ResponseEntity.ok(itineraryService.regenerate(principal.getUid(), itineraryId));
    }

    /**
     * 사용자가 저장한 일정 목록을 조회한다.
     */
    @GetMapping
    public ResponseEntity<List<ItinerarySummaryResponse>> getMyItineraries(
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(itineraryService.getMyItineraries(principal.getUid()));
    }

    /**
     * 저장된 일정을 삭제한다.
     */
    @DeleteMapping("/{itineraryId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable UUID itineraryId
    ) {
        itineraryService.delete(principal.getUid(), itineraryId);
        return ResponseEntity.noContent().build();
    }
}
