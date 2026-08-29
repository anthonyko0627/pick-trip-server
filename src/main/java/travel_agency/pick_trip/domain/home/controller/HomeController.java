package travel_agency.pick_trip.domain.home.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.home.dto.response.HomeResponse;
import travel_agency.pick_trip.domain.home.service.HomeService;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ResponseEntity<HomeResponse> getHome() {
        return ResponseEntity.ok(homeService.getHome());
    }
}
