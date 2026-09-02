package travel_agency.pick_trip.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.dto.response.UserMeResponse;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.UserException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID uid) {
        User user = findUser(uid);

        return new UserMeResponse(
                user.getUid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                user.getCreatedAt()
        );
    }

    /**
     * 회원 탈퇴(소프트 삭제). 리프레시 토큰을 즉시 폐기해 재발급을 막는다.
     * 이미 발급된 액세스 토큰은 만료(최대 1시간)까지 유효하다.
     * 30일 뒤 userPurgeJob 이 소유 데이터와 함께 하드 삭제한다.
     */
    @Transactional
    public void withdraw(UUID uid) {
        User user = findUser(uid);
        user.withdraw();
        refreshTokenRepository.deleteById(uid);
    }

    private User findUser(UUID uid) {
        return userRepository.findById(uid)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }
}
