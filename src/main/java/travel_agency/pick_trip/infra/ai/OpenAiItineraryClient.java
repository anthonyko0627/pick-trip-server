package travel_agency.pick_trip.infra.ai;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ItineraryException;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;
import travel_agency.pick_trip.infra.ai.dto.AiPlace;

/**
 * OpenAI 기반 AI 일정 생성 구현체 (Spring AI {@link ChatClient} 사용).
 * 사용 모델은 {@code spring.ai.openai.chat.options.model} 설정으로 주입한다.
 *
 * <p>일정 JSON 스키마는 {@code entity()} 의 structured output 변환으로 강제해 파싱 실패를 최소화하고,
 * 호출 실패·타임아웃·파싱 실패는 {@link ItineraryException} 으로 변환해 던진다.
 * 보안 규칙상 프롬프트 전문과 AI 응답 원문은 운영 로그에 남기지 않는다(디버그 레벨만 허용).
 */
@Slf4j
@Component
public class OpenAiItineraryClient implements AiItineraryClient {

    /** 모드별 한 문단만 갈아끼운다. 나머지 제약은 두 모드가 동일해야 하므로 본문을 복제하지 않는다. */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 경상도 소도시(하동, 영주, 예천) 여행 일정을 설계하는 전문 플래너입니다.
            %s

            다음 제약을 반드시 지키세요.
            - 각 장소의 운영시간(useTime)과 휴무일(restDate)을 고려해 방문 시간대를 배치합니다.
            - 지리적으로 가까운 장소끼리 같은 일차에 묶습니다. 세부 동선과 순서는 서버가 좌표로 다시 최적화합니다.
            - 우선순위가 "꼭 가기"인 장소는 반드시 포함하고 우선 배치합니다.
            - 동행·여행 스타일 조건을 고려해 걷기 부담·실내외 비율을 조정합니다.
            - 여행 기간(duration)에 맞춰 일차(dayIndex)를 1부터 나눕니다.

            각 장소 배치마다 한국어로 배치 이유(reason)를 작성하세요. reason 규칙:
            - 한국어 완결 문장 한 개로, 문장부호로 끝맺습니다.
              예: "축제 운영시간이 오전 10시부터라서 1일차 오전에 배치했습니다."
            - contentId, 영문 코드, 괄호 안 숫자 ID를 절대 포함하지 마세요.
            - 다른 장소를 언급할 때는 장소 이름만 씁니다. 예: "슬로시티"(O), "슬로시티(773075)"(X).
            - 동행·스타일 조건은 입력에 제공된 한국어 표현만 씁니다. 예: "걷기 적게"(O), "LESS_WALKING"(X).
            """;

    /** STRICT: 바구니 밖 장소를 아예 만들지 못하게 막는다. */
    private static final String STRICT_RULE = """
            사용자가 선택한 장소만으로 현실적인 일정을 만드세요. 임의의 장소를 추가하지 마세요.
            응답의 각 항목 contentId 에는 입력으로 받은 contentId 값만 사용하세요.""";

    /** AUGMENT: 추가 제안을 허용하되 후보 목록으로 한정한다. 실제 채택 여부는 서버 화이트리스트가 정한다. */
    private static final String AUGMENT_RULE = """
            사용자가 선택한 장소를 모두 포함하되, 빈 시간을 채우기 위해 같은 지역의 장소를 추가로 제안해도 됩니다.
            추가하는 장소는 반드시 "[추가 제안 가능한 지역 장소]" 목록에서만 고르고, 그 장소의 contentId 를 목록에 적힌 값 그대로 씁니다.
            목록에 없는 장소는 추가하지 마세요. 응답의 모든 contentId 는 입력으로 받은 값이어야 합니다.""";

    private final ChatClient chatClient;

    public OpenAiItineraryClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiItineraryResult generate(AiItineraryRequest request) {
        try {
            AiItineraryResult result = chatClient.prompt()
                    .system(buildSystemPrompt(request))
                    .user(buildUserPrompt(request))
                    .call()
                    .entity(AiItineraryResult.class);

            if (result == null || result.days() == null || result.days().isEmpty()) {
                log.warn("AI 일정 생성 응답이 비어 있습니다.");
                throw new ItineraryException(ErrorCode.ITINERARY_PROVIDER_FAILED);
            }
            return result;
        } catch (ItineraryException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // 연결/응답 타임아웃 등 네트워크 계열 실패
            log.warn("AI 일정 생성 타임아웃이 발생했습니다.");
            throw new ItineraryException(ErrorCode.ITINERARY_GENERATION_TIMEOUT);
        } catch (Exception e) {
            // 제공자 장애, 응답 파싱 실패 등 (원문은 로그에 남기지 않음)
            log.warn("AI 일정 생성에 실패했습니다. type={}", e.getClass().getSimpleName());
            throw new ItineraryException(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }
    }

    /**
     * "추가 제안 허용/금지" 문단만 바꿔 시스템 프롬프트를 만든다.
     * 제시할 후보가 없으면 허용 문구가 가리킬 목록도 없으므로 STRICT 로 수렴시킨다.
     */
    String buildSystemPrompt(AiItineraryRequest request) {
        return SYSTEM_PROMPT_TEMPLATE.formatted(
                request.extraCandidates().isEmpty() ? STRICT_RULE : AUGMENT_RULE);
    }

    /**
     * 여행 조건과 장소 목록을 사용자 프롬프트 텍스트로 직렬화한다.
     * 운영시간·휴무일·좌표 등 상세가 없는 장소는 해당 항목을 생략한다.
     * 동행·우선순위는 한국어 라벨로 전달되며, 장소명과 contentId 는 별도 줄로 분리해
     * 모델이 "이름(contentId)" 형태로 인용하지 못하게 한다.
     * 추가 후보 목록은 AUGMENT 모드에서만 채워지므로, STRICT 프롬프트는 종전과 동일하다.
     */
    String buildUserPrompt(AiItineraryRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("[여행 조건]\n");
        sb.append("- 지역: ").append(nullToDash(request.regionName())).append('\n');
        sb.append("- 여행 시작일: ").append(request.travelDate() == null ? "-" : request.travelDate()).append('\n');
        sb.append("- 기간(일): ").append(nullToDash(request.duration())).append('\n');
        sb.append("- 동행·스타일 조건: ")
                .append(request.companions() == null || request.companions().isEmpty()
                        ? "-" : String.join(", ", request.companions()))
                .append('\n');

        sb.append("\n[선택한 장소 목록]\n");
        appendPlaces(sb, request.places());

        if (!request.extraCandidates().isEmpty()) {
            sb.append("\n[추가 제안 가능한 지역 장소]\n");
            appendPlaces(sb, request.extraCandidates());
        }
        return sb.toString();
    }

    /** 후보 목록은 id·이름·분류만 채워져 있어, 없는 항목은 그대로 생략된다. */
    private void appendPlaces(StringBuilder sb, List<AiPlace> places) {
        int index = 1;
        for (AiPlace place : places) {
            sb.append(index++).append(". ").append(nullToDash(place.title())).append('\n');
            appendIfPresent(sb, "   - contentId", place.contentId());
            appendIfPresent(sb, "   - 분류", place.category());
            appendIfPresent(sb, "   - 우선순위", place.priority());
            appendCoordinates(sb, place);
            appendIfPresent(sb, "   - 운영시간", place.useTime());
            appendIfPresent(sb, "   - 휴무일", place.restDate());
            appendIfPresent(sb, "   - 권장 체류시간", place.stayDuration());
        }
    }

    private void appendCoordinates(StringBuilder sb, AiPlace place) {
        if (place.latitude() != null && place.longitude() != null) {
            sb.append("   - 좌표: ").append(place.latitude()).append(", ").append(place.longitude()).append('\n');
        }
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }
}
