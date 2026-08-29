package travel_agency.pick_trip.domain.content.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.content.dto.request.CompanionType;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.service.ContentService;

@Validated
@RestController
@RequestMapping("/api/v1/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping
    public ResponseEntity<ContentListResponse> getContents(
            @RequestParam @NotBlank String region,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CompanionType companion,
            @RequestParam(required = false) Boolean indoorOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ContentListRequest request = new ContentListRequest(region, contentTypeId, keyword, companion, indoorOnly, page, size);
        return ResponseEntity.ok(contentService.getContents(request));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@PathVariable String contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }

    @GetMapping("/{contentId}/nearby")
    public ResponseEntity<NearbyContentResponse> getNearbyContents(
            @PathVariable String contentId,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(contentService.getNearbyContents(contentId, radiusKm, size));
    }
}
