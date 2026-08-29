package travel_agency.pick_trip.infra.ai;

import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.ResourceAccessException;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiDay;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult.AiItem;
import travel_agency.pick_trip.infra.ai.dto.AiPlace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * {@link OpenAiItineraryClient} 단위 테스트.
 * 외부 AI 제공자를 실제 호출하지 않고 Spring AI {@link ChatClient} 플루언트 체인을 Mockito 대역으로 처리한다.
 */
@DisplayName("OpenAiItineraryClient")
class OpenAiItineraryClientTest {

    private ChatClient chatClient;
    private OpenAiItineraryClient client;

    @BeforeEach
    void setUp() {
        // ChatClient.Builder#build() 는 생성자에서 한 번 호출되므로, 생성 전에 대역 ChatClient를 반환하도록 설정한다
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        given(builder.build()).willReturn(chatClient);
        client = new OpenAiItineraryClient(builder);
    }

    private AiItineraryRequest request() {
        return new AiItineraryRequest(
                "하동",
                LocalDate.of(2026, 7, 1),
                2,
                List.of("아이와 함께", "걷기 적게"),
                List.of(new AiPlace(
                        "c1", "쌍계사", "12", 35.27, 127.58, "09:00~18:00", "연중무휴", "2시간", "꼭 가기"))
        );
    }

    private AiItineraryResult validResult() {
        return new AiItineraryResult(
                "하동 1박 2일 가족 여행",
                List.of(new AiDay(1, List.of(new AiItem("c1", 1, "오전 운영시간에 맞춰 배치했습니다."))))
        );
    }

    // 플루언트 체인(prompt -> system -> user -> call -> entity)의 마지막 단계가 주어진 동작을 하도록 설정한다
    private void givenEntity(AiItineraryResult result) {
        given(chatClient.prompt().system(anyString()).user(anyString()).call()
                .entity(AiItineraryResult.class)).willReturn(result);
    }

    @Nested
    @DisplayName("generate - 정상 흐름")
    class Success {

        @Test
        @DisplayName("AI가 유효한 일정을 반환하면 결과를 그대로 반환한다")
        void validResponse_returnsResult() {
            // given
            givenEntity(validResult());

            // when
            AiItineraryResult result = client.generate(request());

            // then
            assertThat(result.title()).isEqualTo("하동 1박 2일 가족 여행");
            assertThat(result.days()).hasSize(1);
            assertThat(result.days().get(0).items().get(0).contentId()).isEqualTo("c1");
        }
    }

    @Nested
    @DisplayName("buildUserPrompt")
    class BuildUserPrompt {

        @Test
        @DisplayName("동행·스타일 조건을 한국어 라벨로 렌더링한다")
        void rendersConditionsAsKoreanLabels() {
            // when
            String prompt = client.buildUserPrompt(request());

            // then
            assertThat(prompt).contains("아이와 함께").contains("걷기 적게");
        }

        @Test
        @DisplayName("장소명과 contentId를 별도 줄로 분리해 이름 옆에 ID가 붙지 않는다")
        void separatesContentIdFromTitle() {
            // when
            String prompt = client.buildUserPrompt(request());

            // then
            assertThat(prompt)
                    .doesNotContain("(contentId=")
                    .contains("쌍계사")
                    .contains("- contentId: c1");
        }
    }

    @Nested
    @DisplayName("generate - 실패 변환")
    class Failure {

        @Test
        @DisplayName("AI 응답이 null이면 ITINERARY_PROVIDER_FAILED 예외를 던진다")
        void nullResponse_throwsProviderFailed() {
            // given
            givenEntity(null);

            // when
            ThrowingCallable action = () -> client.generate(request());

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }

        @Test
        @DisplayName("AI 응답의 일자(days)가 비어 있으면 ITINERARY_PROVIDER_FAILED 예외를 던진다")
        void emptyDays_throwsProviderFailed() {
            // given
            givenEntity(new AiItineraryResult("제목만 있는 응답", List.of()));

            // when
            ThrowingCallable action = () -> client.generate(request());

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }

        @Test
        @DisplayName("네트워크 타임아웃(ResourceAccessException)이면 ITINERARY_GENERATION_TIMEOUT 예외를 던진다")
        void timeout_throwsGenerationTimeout() {
            // given
            given(chatClient.prompt().system(anyString()).user(anyString()).call()
                    .entity(AiItineraryResult.class))
                    .willThrow(new ResourceAccessException("connect timed out"));

            // when
            ThrowingCallable action = () -> client.generate(request());

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_GENERATION_TIMEOUT);
        }

        @Test
        @DisplayName("제공자 장애·파싱 실패 등 일반 예외면 ITINERARY_PROVIDER_FAILED 예외를 던진다")
        void genericException_throwsProviderFailed() {
            // given
            given(chatClient.prompt().system(anyString()).user(anyString()).call()
                    .entity(AiItineraryResult.class))
                    .willThrow(new RuntimeException("provider 500"));

            // when
            ThrowingCallable action = () -> client.generate(request());

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_PROVIDER_FAILED);
        }
    }
}
