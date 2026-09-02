package travel_agency.pick_trip.domain.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.domain.user.repository.UserRepository;

@DisplayName("UserPurgeService")
@ExtendWith(MockitoExtension.class)
class UserPurgeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private BasketRepository basketRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ItineraryRepository itineraryRepository;

    @Mock
    private ShareTokenRepository shareTokenRepository;

    @InjectMocks
    private UserPurgeService userPurgeService;

    @Test
    @DisplayName("일정·바구니·찜·토큰을 지운 뒤 사용자 행을 삭제한다.")
    void purgeDeletesOwnedDataBeforeUserRow() {
        // given
        UUID uid = UUID.randomUUID();
        Itinerary first = itinerary(uid, "일정1");
        Itinerary second = itinerary(uid, "일정2");
        List<Itinerary> itineraries = List.of(first, second);
        Basket basket = Basket.builder().userId(uid).build();

        given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(uid)).willReturn(itineraries);
        given(basketRepository.findByUserId(uid)).willReturn(Optional.of(basket));

        // when
        userPurgeService.purge(uid);

        // then
        InOrder inOrder = inOrder(
                shareTokenRepository,
                itineraryRepository,
                basketRepository,
                favoriteRepository,
                refreshTokenRepository,
                userRepository
        );
        inOrder.verify(shareTokenRepository)
                .deleteAllByItineraryIdIn(List.of(first.getItineraryId(), second.getItineraryId()));
        inOrder.verify(itineraryRepository).deleteAll(itineraries);
        inOrder.verify(basketRepository).delete(basket);
        inOrder.verify(favoriteRepository).deleteAllByUserId(uid);
        inOrder.verify(refreshTokenRepository).deleteById(uid);
        inOrder.verify(userRepository).deleteById(uid);
    }

    @Test
    @DisplayName("일정과 바구니가 없어도 사용자 행은 삭제한다.")
    void purgeDeletesUserRowWhenNoOwnedData() {
        // given
        UUID uid = UUID.randomUUID();
        given(itineraryRepository.findByUserIdOrderByLastModifiedAtDesc(uid)).willReturn(List.of());
        given(basketRepository.findByUserId(uid)).willReturn(Optional.empty());

        // when
        userPurgeService.purge(uid);

        // then
        verifyNoInteractions(shareTokenRepository);
        verify(itineraryRepository, never()).deleteAll(anyList());
        verify(basketRepository, never()).delete(any(Basket.class));
        verify(favoriteRepository).deleteAllByUserId(uid);
        verify(refreshTokenRepository).deleteById(uid);
        verify(userRepository).deleteById(uid);
    }

    private static Itinerary itinerary(UUID uid, String title) {
        Itinerary itinerary = Itinerary.builder()
                .userId(uid)
                .title(title)
                .region(Region.HADONG)
                .travelDate(LocalDate.now())
                .duration(1)
                .build();
        ReflectionTestUtils.setField(itinerary, "itineraryId", UUID.randomUUID());
        return itinerary;
    }
}
