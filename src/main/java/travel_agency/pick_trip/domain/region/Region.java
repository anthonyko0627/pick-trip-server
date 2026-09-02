package travel_agency.pick_trip.domain.region;

import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.Arrays;

/**
 * 서비스 대상 지역. 지역 코드는 TourAPI 4.0(KorService2)의 <b>법정동 코드</b>를 사용한다.
 *
 * <p>legacy {@code areaCode}/{@code sigunguCode}는 KorService2에서 신규·갱신 콘텐츠에 채워지지 않아
 * 콘텐츠가 30~50% 누락되고 {@code /searchFestival2}는 아예 동작하지 않는다. 법정동 코드 조회 결과가
 * legacy 결과의 완전한 상위집합임을 실측 확인해 legacy 코드는 제거했다.
 */
public enum Region {
  HADONG("하동", "48", "850"),    // 경남 하동군
  YEONGJU("영주", "47", "210"),   // 경북 영주시
  YECHEON("예천", "47", "900");   // 경북 예천군

  private final String name;
  private final String lDongRegnCd;
  private final String lDongSignguCd;

  Region(String name, String lDongRegnCd, String lDongSignguCd) {
    this.name = name;
    this.lDongRegnCd = lDongRegnCd;
    this.lDongSignguCd = lDongSignguCd;
  }

  public String getName() {
    return name;
  }

  /** 법정동 시도 코드 ({@code /ldongCode2}). */
  public String getLDongRegnCd() {
    return lDongRegnCd;
  }

  /** 법정동 시군구 코드 ({@code /ldongCode2}). */
  public String getLDongSignguCd() {
    return lDongSignguCd;
  }

  public static Region fromCode(String code) {
    return Arrays.stream(values())
        .filter(r -> r.name().equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new ContentException(ErrorCode.CONTENT_INVALID_REGION));
  }

  /** TourAPI 법정동 코드 원본 값으로 지역을 역매핑한다. 대상 지역 밖이면 null. */
  public static Region fromLdongCode(String lDongRegnCd, String lDongSignguCd) {
    return Arrays.stream(values())
        .filter(r -> r.lDongRegnCd.equals(lDongRegnCd) && r.lDongSignguCd.equals(lDongSignguCd))
        .findFirst()
        .orElse(null);
  }
}
