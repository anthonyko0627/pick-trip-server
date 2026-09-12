# 고도 데이터 소스 선정

이슈 #72 (도보 일정 경사 페널티) 기준. 도보 leg 의 누적 상승고도를 계산하려면 좌표 → 해발고도 조회가 필요하다.

## 1. 선정 기준

- **키 없이 동작**해야 한다. 운영 키 발급·주입은 나중 문제이고, 키가 없다고 일정 생성이 막히면 안 된다.
- **여러 좌표를 1콜로** 조회할 수 있어야 한다. 일정 하나에 장소가 10~20곳이라 좌표당 1콜은 감당할 수 없다.
- 하동(35.07N), 영주(36.81N), 예천(36.65N)을 덮어야 한다.
- 실패해도 일정 생성이 계속돼야 한다(고도 미상 → 경사 0 폴백).

## 2. 후보 비교 (2026-09 조사)

| 후보 | 인증키 | 배치 조회 | 제한 | 자체 호스팅 | 국내 커버리지 |
|------|--------|-----------|------|-------------|---------------|
| **OpenTopoData** | **불필요** | `locations=위도,경도\|위도,경도` | 1콜/초, 1,000콜/일, 좌표 100개/콜 | 가능 (Docker, MIT) | `srtm30m` 전 지구 30m |
| Open-Elevation | 공개 엔드포인트는 불필요 | GET(URL 1,024B 상한) / POST | 명시 없음, 무료 티어는 키 필요 | 가능 (Docker) | 전 지구 SRTM |
| VWorld (브이월드) | 필요 (+도메인 등록) | **고도 API 자체가 없음** | - | 불가 | - |
| Google Elevation | 필요 (+결제 계정) | 512개/콜 | 6,000 QPM, 월 무료분 초과 시 과금 | 불가 | 전 지구 |
| Kakao Local | - | **고도 API 미제공** | - | - | - |

### VWorld 를 쓸 수 없는 이유

이슈에 후보로 적혀 있었지만 **현재 오픈API 목록에 좌표→고도 REST 엔드포인트가 없다.** DEM 높이 격자를
내려주던 3D 데이터 Open API 는 2019년 7월에 종료됐고, 2025년 3단계 고도화로 추가된 지형 단면·가시면적 같은
분석 기능은 3D 지도 클라이언트용이라 서버에서 좌표만 던져 고도를 받는 용도가 아니다.
국토지리정보원 수치표고모델도 data.go.kr 파일 다운로드만 제공한다.

### Open-Elevation 을 쓰지 않는 이유

키 없이 배치 조회가 되지만, **데이터가 없는 좌표를 `null` 이 아니라 0m(해수면)로 내려준다.** 고도 미상과
"실제로 해발 0m" 를 구분할 수 없어 경사 계산이 조용히 틀어진다. 가용성 이슈 이력도 길다.

## 3. 결정: OpenTopoData `srtm30m`

- 인증키 불필요, `GET /v1/srtm30m?locations=...` 로 최대 100좌표를 1콜에 조회한다.
- 응답 `results` 는 **요청한 좌표 순서를 그대로 유지**하므로 인덱스로 좌표와 짝지운다.
- 데이터셋 범위 밖 좌표는 `elevation: null` 이라 고도 미상과 해발 0m 가 구분된다.
- SRTM 30m 격자는 관광 도보 구간(수백 m ~ 수 km)의 오르막 판단에 충분하다.

```json
{
  "results": [
    { "dataset": "srtm30m", "elevation": 153.0, "location": { "lat": 36.8056, "lng": 128.6241 } }
  ],
  "status": "OK"
}
```

### 운영 시 주의

공개 엔드포인트는 초당 1콜·일 1,000콜 제한이 있다. 일정 생성 1건이 1콜을 쓰므로 MVP 트래픽에서는 충분하지만,
동시 생성이 늘면 429 가 난다. 그때는 OpenTopoData 를 Docker 로 자체 호스팅하고 `elevation.base-url` 만 바꾼다
(데이터셋 이름은 그대로 `srtm30m`). 실패해도 예외를 올리지 않고 고도 미상으로 폴백하므로 장애가 일정 생성을 막지는 않는다.

## 4. 코드 위치

| 역할 | 클래스 |
|------|--------|
| Feign 클라이언트 | `domain/itinerary/client/ElevationClient` |
| 타임아웃·재시도 설정 | `domain/itinerary/client/ElevationFeignConfig` |
| 응답 DTO | `domain/itinerary/client/dto/ElevationResponse` |
| 조회·폴백·중복 제거 | `domain/itinerary/service/ElevationResolver` |
| 좌표별 고도 값 객체 | `domain/itinerary/scheduling/ElevationProfile` |
| 경사 페널티·휴식 판정 (순수) | `domain/itinerary/scheduling/WalkEffort` |
