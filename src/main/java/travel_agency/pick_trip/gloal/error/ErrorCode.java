package travel_agency.pick_trip.gloal.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해주세요."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_PROVIDER_ERROR(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AUTH_REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    AUTH_INVALID_EXCHANGE_CODE(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 코드입니다. 다시 로그인해주세요."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // Content
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다."),
    CONTENT_INVALID_REGION(HttpStatus.BAD_REQUEST, "지원하지 않는 지역입니다."),
    CONTENT_LOCATION_UNKNOWN(HttpStatus.NOT_FOUND, "위치 정보가 없어 주변 콘텐츠를 조회할 수 없습니다."),
    CONTENT_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "콘텐츠를 불러오지 못했습니다. 다시 시도해주세요."),

    // Itinerary
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    ITINERARY_INPUT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "일정 생성에 필요한 조건이 부족합니다."),
    ITINERARY_GENERATION_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "일정 생성에 실패했습니다. 다시 시도해주세요."),
    ITINERARY_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "일정 생성에 실패했습니다. 콘텐츠를 추가하거나 다시 시도해주세요."),
    ITINERARY_INVALID_CONTENT(HttpStatus.BAD_REQUEST, "유효하지 않은 콘텐츠가 포함되어 있습니다."),
    ITINERARY_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "저장에 실패했습니다. 다시 시도해주세요."),

    // Basket
    BASKET_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "바구니 항목을 찾을 수 없습니다."),
    BASKET_ITEM_DUPLICATE(HttpStatus.CONFLICT, "이미 바구니에 담은 콘텐츠입니다."),

    // Favorite
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "찜한 콘텐츠를 찾을 수 없습니다."),
    FAVORITE_DUPLICATE(HttpStatus.CONFLICT, "이미 찜한 콘텐츠입니다."),

    // Share
    SHARE_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "공유된 일정을 찾을 수 없습니다."),
    SHARE_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    SHARE_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "공유 링크 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // Validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),

    // Common
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
