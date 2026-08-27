package travel_agency.pick_trip.domain.itinerary.scheduling;

/**
 * 두 좌표 사이의 대권 거리(haversine)를 계산한다.
 */
public final class GeoDistance {

    /** 지구 평균 반경(km). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    public static double kilometers(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);

        // 짧은 거리에서도 정밀도가 유지되도록 atan2 대신 asin 형태를 쓰되 a 가 1을 살짝 넘는 반올림 오차를 막는다.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private GeoDistance() {
    }
}
