package travel_agency.pick_trip.domain.auth.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.oauth2.userinfo.OAuth2UserInfo;
import travel_agency.pick_trip.domain.auth.oauth2.userinfo.OAuth2UserInfoFactory;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.of(registrationId, oAuth2User.getAttributes());

        String providerUserId = userInfo.providerUserId();
        if (providerUserId == null) {
            log.error("OAuth2 사용자 정보에 식별자가 없습니다 - registrationId: {}", registrationId);
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }

        // 재로그인 시 탈퇴 유예 중이면 계정을 복구하고, 닉네임·프로필 이미지를 최신 공급자 정보로 동기화한다.
        // 이메일은 최초 가입 시만 저장하고 이후 변경하지 않는다 (동의 항목 변경 방어).
        User user = userRepository.findByProviderAndProviderUserId(userInfo.provider(), providerUserId)
                .map(existing -> {
                    // 탈퇴 유예 기간(30일) 안에 재로그인하면 탈퇴를 철회하고 계정을 복구한다.
                    if (existing.isDeleted()) {
                        existing.restore();
                        log.info("탈퇴 계정 복구 - uid: {}", existing.getUid());
                    }
                    existing.updateProfile(userInfo.nickname(), userInfo.profileImageUrl());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(userInfo.provider())
                        .providerUserId(providerUserId)
                        .email(userInfo.email())
                        .nickname(userInfo.nickname())
                        .profileImageUrl(userInfo.profileImageUrl())
                        .build()));

        return new OAuth2UserAdapter(user, oAuth2User.getAttributes());
    }
}
