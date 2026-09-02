package travel_agency.pick_trip.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import travel_agency.pick_trip.domain.user.dto.response.UserMeResponse;
import travel_agency.pick_trip.domain.user.service.UserService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getMe(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(userService.getMe(principal.getUid()));
    }

    /** 회원 탈퇴. 소프트 삭제 후 30일이 지나면 소유 데이터와 함께 하드 삭제된다. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal JwtUserPrincipal principal) {
        userService.withdraw(principal.getUid());
        return ResponseEntity.noContent().build();
    }
}
