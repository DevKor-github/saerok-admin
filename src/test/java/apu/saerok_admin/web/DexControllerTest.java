package apu.saerok_admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import apu.saerok_admin.infra.CurrentAdminClient;
import apu.saerok_admin.infra.dex.AdminDexClient;
import apu.saerok_admin.infra.dex.dto.AdminBirdDetailResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignRequest;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdListResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import apu.saerok_admin.web.support.DexFormMapper;
import apu.saerok_admin.web.support.DexOptions;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClientResponseException;

@WebMvcTest(DexController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DexFormMapper.class, DexOptions.class})
class DexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDexClient adminDexClient;

    @MockBean
    private CurrentAdminClient currentAdminClient;

    @MockBean
    private LoginSessionManager loginSessionManager;

    @BeforeEach
    void setUp() {
        given(currentAdminClient.fetchCurrentAdminProfile()).willReturn(Optional.of(profile()));
    }

    @Test
    void list_rendersActualDexListTemplate() throws Exception {
        given(adminDexClient.listBirds(null, 1, 20))
                .willReturn(new AdminBirdListResponse(
                        List.of(new AdminBirdListResponse.Item(
                                401L,
                                "해오라기",
                                "Butorides striata",
                                "NONE",
                                45.0,
                                List.of("RIVER_LAKE"),
                                "https://cdn.example/bird.webp",
                                OffsetDateTime.parse("2026-05-24T10:00:00Z")
                        )),
                        1,
                        20,
                        1,
                        1
                ));

        mockMvc.perform(get("/dex"))
                .andExpect(status().isOk())
                .andExpect(view().name("dex/list"));
    }

    @Test
    void createForm_rendersActualDexFormTemplate() throws Exception {
        mockMvc.perform(get("/dex/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("dex/form"));
    }

    @Test
    void detail_rendersActualDexDetailTemplate() throws Exception {
        given(adminDexClient.getBird(401L)).willReturn(detailResponse());

        mockMvc.perform(get("/dex/401"))
                .andExpect(status().isOk())
                .andExpect(view().name("dex/detail"));
    }

    @Test
    void create_withBlankRequiredFieldRendersSameFormWithInputAndMessage() throws Exception {
        mockMvc.perform(validCreateRequest(" "))
                .andExpect(status().isOk())
                .andExpect(view().name("dex/form"))
                .andExpect(model().attribute("flashStatus", "error"))
                .andExpect(model().attribute("flashMessage", "국문명과 학명을 입력해 주세요."))
                .andExpect(model().attributeExists("form"));

        verify(adminDexClient, never()).createBird(any(AdminBirdUpsertRequest.class));
    }

    @Test
    void create_whenBackendReturnsConflictShowsBackendMessage() throws Exception {
        given(adminDexClient.createBird(any(AdminBirdUpsertRequest.class)))
                .willThrow(new RestClientResponseException(
                        "Conflict",
                        409,
                        "Conflict",
                        new HttpHeaders(),
                        "{\"status\":409,\"message\":\"이미 등록된 학명입니다: Butorides striata\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                ));

        mockMvc.perform(validCreateRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("dex/form"))
                .andExpect(model().attribute("flashStatus", "error"))
                .andExpect(model().attribute("flashMessage", "이미 등록된 학명입니다: Butorides striata"))
                .andExpect(model().attribute("form", allOf(
                        hasProperty("scientificName", is("Butorides striata")),
                        hasProperty("objectKey", is("raw/123.webp"))
                )));
    }

    @Test
    void editForm_rendersFormPrefilledFromBirdDetail() throws Exception {
        given(adminDexClient.getBird(401L)).willReturn(detailResponse());

        mockMvc.perform(get("/dex/401/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("dex/form"))
                .andExpect(model().attribute("submitLabel", "도감 수정"))
                .andExpect(model().attribute("formAction", "/dex/401/edit"))
                .andExpect(model().attribute("form", allOf(
                        hasProperty("koreanName", is("해오라기")),
                        hasProperty("scientificName", is("Butorides striata")),
                        hasProperty("objectKey", is("raw/123.webp"))
                )));
    }

    @Test
    void update_onSuccessRedirectsToDetail() throws Exception {
        given(adminDexClient.updateBird(org.mockito.ArgumentMatchers.eq(401L), any(AdminBirdUpsertRequest.class)))
                .willReturn(detailResponse());

        mockMvc.perform(validUpdateRequest())
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/dex/401"));

        verify(adminDexClient).updateBird(org.mockito.ArgumentMatchers.eq(401L), any(AdminBirdUpsertRequest.class));
    }

    @Test
    void update_whenBackendReturnsConflictShowsBackendMessage() throws Exception {
        given(adminDexClient.updateBird(org.mockito.ArgumentMatchers.eq(401L), any(AdminBirdUpsertRequest.class)))
                .willThrow(new RestClientResponseException(
                        "Conflict",
                        409,
                        "Conflict",
                        new HttpHeaders(),
                        "{\"status\":409,\"message\":\"이미 등록된 학명입니다: Butorides striata\"}"
                                .getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                ));

        mockMvc.perform(validUpdateRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("dex/form"))
                .andExpect(model().attribute("flashStatus", "error"))
                .andExpect(model().attribute("flashMessage", "이미 등록된 학명입니다: Butorides striata"))
                .andExpect(model().attribute("submitLabel", "도감 수정"));
    }

    @Test
    void presignImage_proxiesDexPresignResponse() throws Exception {
        given(adminDexClient.generateImagePresignUrl(any(AdminBirdImagePresignRequest.class)))
                .willReturn(new AdminBirdImagePresignResponse(
                        "https://s3.example/upload",
                        "raw/550e8400-e29b-41d4-a716-446655440000.png"
                ));

        mockMvc.perform(post("/dex/image/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").value("https://s3.example/upload"))
                .andExpect(jsonPath("$.objectKey").value("raw/550e8400-e29b-41d4-a716-446655440000.png"));

        verify(adminDexClient).generateImagePresignUrl(any(AdminBirdImagePresignRequest.class));
    }

    private MockHttpServletRequestBuilder validCreateRequest() {
        return validCreateRequest("해오라기");
    }

    private MockHttpServletRequestBuilder validUpdateRequest() {
        return withValidBirdParams(post("/dex/401/edit"))
                .param("koreanName", "해오라기");
    }

    private MockHttpServletRequestBuilder validCreateRequest(String koreanName) {
        return withValidBirdParams(post("/dex"))
                .param("koreanName", koreanName);
    }

    private MockHttpServletRequestBuilder withValidBirdParams(MockHttpServletRequestBuilder builder) {
        return builder
                .param("scientificName", "Butorides striata")
                .param("scientificAuthor", "Linnaeus")
                .param("scientificYear", "1758")
                .param("bodyLengthCm", "45.0")
                .param("nibrUrl", "https://species.example/bird")
                .param("conservationGrade", "NONE")
                .param("phylumEng", "Chordata")
                .param("phylumKor", "척삭동물문")
                .param("classEng", "Aves")
                .param("classKor", "조강")
                .param("orderEng", "Pelecaniformes")
                .param("orderKor", "사다새목")
                .param("familyEng", "Ardeidae")
                .param("familyKor", "왜가리과")
                .param("genusEng", "Butorides")
                .param("genusKor", "해오라기속")
                .param("speciesEng", "striata")
                .param("speciesKor", "해오라기")
                .param("habitats", "RIVER_LAKE")
                .param("residencies[0].residencyType", "RESIDENT")
                .param("residencies[0].rarity", "COMMON")
                .param("residencies[0].months", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
                .param("objectKey", "raw/123.webp")
                .param("contentType", "image/webp")
                .param("originalUrl", "https://source.example/bird")
                .param("description", "설명")
                .param("descriptionSource", "출처");
    }

    private CurrentAdminProfile profile() {
        return new CurrentAdminProfile(
                "운영자",
                "admin@saerok.app",
                null,
                List.of("운영자"),
                List.of("ADMIN_EDITOR"),
                List.of("ADMIN_LOGIN")
        );
    }

    private AdminBirdDetailResponse detailResponse() {
        return new AdminBirdDetailResponse(
                401L,
                new AdminBirdDetailResponse.BirdName("해오라기", "Butorides striata", "Linnaeus", 1758),
                new AdminBirdDetailResponse.BirdTaxonomy(
                        "Chordata",
                        "척삭동물문",
                        "Aves",
                        "조강",
                        "Pelecaniformes",
                        "사다새목",
                        "Ardeidae",
                        "왜가리과",
                        "Butorides",
                        "해오라기속",
                        "striata",
                        "해오라기"
                ),
                new AdminBirdDetailResponse.BirdDescription("설명", "출처", false),
                45.0,
                "https://species.example/bird",
                "NONE",
                List.of("RIVER_LAKE"),
                List.of(new AdminBirdDetailResponse.Residency("RESIDENT", "COMMON", null, 4095)),
                List.of(new AdminBirdDetailResponse.SeasonWithRarity("SPRING", "COMMON", 1)),
                List.of(new AdminBirdDetailResponse.Image(
                        "raw/123.webp",
                        "https://cdn.example/bird.webp",
                        "https://source.example/bird",
                        0,
                        true
                )),
                OffsetDateTime.parse("2026-05-24T10:00:00Z"),
                OffsetDateTime.parse("2026-05-24T10:00:00Z")
        );
    }
}
