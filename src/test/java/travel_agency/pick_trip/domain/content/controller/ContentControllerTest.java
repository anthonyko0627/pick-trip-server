package travel_agency.pick_trip.domain.content.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.content.dto.response.NearbyContentResponse;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.content.service.ContentService;
import travel_agency.pick_trip.gloal.error.GlobalExceptionHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentController")
class ContentControllerTest {

    @Mock private ContentService contentService;
    @InjectMocks private ContentController contentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(contentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/contents")
    class GetContents {

        @Test
        @DisplayName("정상 요청이면 200과 ContentListResponse를 반환한다")
        void validRequest_returns200WithList() {
            // given
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58,
                            ContentCategory.ATTRACTION, null, false, "HADONG")
            ));
            given(contentService.getContents(any(ContentListRequest.class))).willReturn(expected);

            // when
            ResponseEntity<ContentListResponse> result = contentController.getContents(
                    "HADONG", null, null, null, null, 0, 20
            );

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }

        @Test
        @DisplayName("region 파라미터가 없으면 500이 아닌 400을 반환한다")
        void missingRegion_returns400NotInternalServerError() throws Exception {
            mockMvc.perform(get("/api/v1/contents"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("companion 값이 enum에 없으면 500이 아닌 400을 반환한다")
        void invalidCompanionEnum_returns400NotInternalServerError() throws Exception {
            mockMvc.perform(get("/api/v1/contents").param("region", "HADONG").param("companion", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}")
    class GetContentDetail {

        @Test
        @DisplayName("정상 요청이면 200과 ContentDetailResponse를 반환한다")
        void validContentId_returns200WithDetail() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가",
                    "약 2시간", null, "TourAPI", List.of(),
                    ContentCategory.CULTURE, true, "HADONG"
            );
            given(contentService.getContentDetail("2741429")).willReturn(expected);

            // when
            ResponseEntity<ContentDetailResponse> result = contentController.getContentDetail("2741429");

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().contentId()).isEqualTo("2741429");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/nearby")
    class GetNearbyContents {

        @Test
        @DisplayName("정상 요청이면 200과 NearbyContentResponse를 반환한다")
        void validRequest_returns200WithNearby() {
            // given
            NearbyContentResponse expected = new NearbyContentResponse("111", 5.0, List.of(
                    new NearbyContentResponse.NearbyContentItem("222", "최참판댁", "12", "하동군 악양면",
                            "https://img.jpg", 35.13, 127.57, ContentCategory.CULTURE, "토지 배경", "HADONG", 1.23)
            ));
            given(contentService.getNearbyContents("111", 5.0, 10)).willReturn(expected);

            // when
            ResponseEntity<NearbyContentResponse> result = contentController.getNearbyContents("111", 5.0, 10);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
            assertThat(result.getBody().items().get(0).contentId()).isEqualTo("222");
        }

        @Test
        @DisplayName("radiusKm·size 파라미터가 없으면 기본값 5km·10개로 위임한다")
        void missingParams_delegatesWithDefaults() throws Exception {
            given(contentService.getNearbyContents(eq("111"), eq(5.0), eq(10)))
                    .willReturn(new NearbyContentResponse("111", 5.0, List.of()));

            mockMvc.perform(get("/api/v1/contents/111/nearby"))
                    .andExpect(status().isOk());

            verify(contentService).getNearbyContents("111", 5.0, 10);
        }

        @Test
        @DisplayName("radiusKm 값이 숫자가 아니면 500이 아닌 400을 반환한다")
        void nonNumericRadius_returns400NotInternalServerError() throws Exception {
            mockMvc.perform(get("/api/v1/contents/111/nearby").param("radiusKm", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }
}
