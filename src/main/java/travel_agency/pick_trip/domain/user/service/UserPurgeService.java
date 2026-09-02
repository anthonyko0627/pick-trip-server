package travel_agency.pick_trip.domain.user.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.domain.user.repository.UserRepository;

/**
 * 탈퇴 유예 기간(30일)이 지난 계정을 소유 데이터와 함께 하드 삭제한다.
 *
 * <p>소유 데이터는 FK 없이 {@code user_id} UUID 로만 연결되어 있어 DB cascade 를 쓸 수 없다.
 * 참조하는 쪽(share_tokens → itineraries, basket → user)부터 지워 고아 행이 남지 않게 한다.
 * 한 사용자의 삭제는 한 트랜잭션이며, {@code userPurgeJob} 의 chunk 트랜잭션에 참여한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPurgeService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BasketRepository basketRepository;
    private final FavoriteRepository favoriteRepository;
    private final ItineraryRepository itineraryRepository;
    private final ShareTokenRepository shareTokenRepository;

    @Transactional
    public void purge(UUID uid) {
        // ponytail: 일정·바구니는 엔티티를 로드해 cascade 로 지운다(일정당 days/items 쿼리 N회).
        // 탈퇴 사용자 수가 적어 충분하며, 볼륨이 커지면 bulk JPQL 로 바꾼다.
        List<Itinerary> itineraries = itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(uid);
        if (!itineraries.isEmpty()) {
            shareTokenRepository.deleteAllByItineraryIdIn(
                    itineraries.stream().map(Itinerary::getItineraryId).toList());
            itineraryRepository.deleteAll(itineraries);
        }
        basketRepository.findByUserId(uid).ifPresent(basketRepository::delete);
        favoriteRepository.deleteAllByUserId(uid);
        refreshTokenRepository.deleteById(uid);
        userRepository.deleteById(uid);
        log.info("탈퇴 계정 하드 삭제 - uid: {}, itineraries: {}", uid, itineraries.size());
    }
}
