package apu.saerok_admin.infra.dex;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.dex.dto.AdminBirdDetailResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignRequest;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdListResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AdminDexClientTest {

    private WireMockServer wireMockServer;
    private AdminDexClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        client = new AdminDexClient(restClient, new SaerokApiProps(wireMockServer.baseUrl(), "/api/v1"));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void listBirds_getsAdminDexBirdsEndpoint() {
        stubFor(get(urlPathEqualTo("/api/v1/admin/dex/birds"))
                .withQueryParam("q", equalTo("해오라기"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("20"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "birds": [
                                    {
                                      "id": 401,
                                      "koreanName": "해오라기",
                                      "scientificName": "Butorides striata",
                                      "conservationGrade": "NONE",
                                      "bodyLengthCm": 45.0,
                                      "habitats": ["RIVER_LAKE"],
                                      "thumbImageUrl": "https://cdn.example/dex/raw/bird.webp",
                                      "updatedAt": "2026-05-24T10:00:00Z"
                                    }
                                  ],
                                  "page": 1,
                                  "size": 20,
                                  "totalElements": 1,
                                  "totalPages": 1
                                }
                                """)));

        AdminBirdListResponse response = client.listBirds(" 해오라기 ", 1, 20);

        verify(getRequestedFor(urlPathEqualTo("/api/v1/admin/dex/birds"))
                .withQueryParam("q", equalTo("해오라기"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("20")));
        assertThat(response.birds()).hasSize(1);
        assertThat(response.birds().getFirst().scientificName()).isEqualTo("Butorides striata");
        assertThat(response.birds().getFirst().habitats()).containsExactly("RIVER_LAKE");
    }

    @Test
    void createBird_postsCreatePayload() {
        stubFor(post(urlEqualTo("/api/v1/admin/dex/birds"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 401,
                                  "name": {
                                    "koreanName": "해오라기",
                                    "scientificName": "Butorides striata",
                                    "scientificAuthor": "Linnaeus",
                                    "scientificYear": 1758
                                  },
                                  "taxonomy": null,
                                  "description": null,
                                  "bodyLengthCm": 45.0,
                                  "nibrUrl": null,
                                  "conservationGrade": "NONE",
                                  "habitats": ["RIVER_LAKE"],
                                  "seasonsWithRarity": [],
                                  "images": [],
                                  "createdAt": "2026-05-24T10:00:00Z",
                                  "updatedAt": "2026-05-24T10:00:00Z"
                                }
                                """)));
        AdminBirdUpsertRequest request = createRequest();

        AdminBirdDetailResponse response = client.createBird(request);

        verify(postRequestedFor(urlEqualTo("/api/v1/admin/dex/birds"))
                .withRequestBody(equalToJson("""
                        {
                          "name": {
                            "koreanName": "해오라기",
                            "scientificName": "Butorides striata",
                            "scientificAuthor": "Linnaeus",
                            "scientificYear": 1758
                          },
                          "taxonomy": {
                            "phylumEng": "Chordata",
                            "phylumKor": "척삭동물문",
                            "classEng": "Aves",
                            "classKor": "조강",
                            "orderEng": "Pelecaniformes",
                            "orderKor": "사다새목",
                            "familyEng": "Ardeidae",
                            "familyKor": "왜가리과",
                            "genusEng": "Butorides",
                            "genusKor": "해오라기속",
                            "speciesEng": "striata",
                            "speciesKor": "해오라기"
                          },
                          "description": {
                            "description": "설명",
                            "source": "출처",
                            "isAiGenerated": false
                          },
                          "bodyLengthCm": 45.0,
                          "nibrUrl": "https://species.example/bird",
                          "conservationGrade": "NONE",
                          "habitats": ["RIVER_LAKE"],
                          "residencies": [
                            {
                              "residencyType": "RESIDENT",
                              "rarity": "COMMON",
                              "monthBitmask": 4095
                            }
                          ],
                          "images": [
                            {
                              "objectKey": "raw/123.webp",
                              "originalUrl": "https://source.example/bird"
                            }
                          ]
                        }
                        """)));
        assertThat(response.id()).isEqualTo(401L);
    }

    @Test
    void updateBird_putsUpsertPayload() {
        stubFor(put(urlEqualTo("/api/v1/admin/dex/birds/401"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 401,
                                  "name": {
                                    "koreanName": "해오라기",
                                    "scientificName": "Butorides striata"
                                  },
                                  "habitats": ["RIVER_LAKE"],
                                  "seasonsWithRarity": [],
                                  "images": [],
                                  "createdAt": "2026-05-24T10:00:00Z",
                                  "updatedAt": "2026-05-24T11:00:00Z"
                                }
                                """)));
        AdminBirdUpsertRequest request = createRequest();

        AdminBirdDetailResponse response = client.updateBird(401L, request);

        verify(putRequestedFor(urlEqualTo("/api/v1/admin/dex/birds/401"))
                .withRequestBody(equalToJson("""
                        {
                          "name": {
                            "koreanName": "해오라기",
                            "scientificName": "Butorides striata",
                            "scientificAuthor": "Linnaeus",
                            "scientificYear": 1758
                          },
                          "conservationGrade": "NONE",
                          "habitats": ["RIVER_LAKE"],
                          "residencies": [
                            {
                              "residencyType": "RESIDENT",
                              "rarity": "COMMON",
                              "monthBitmask": 4095
                            }
                          ],
                          "images": [
                            {
                              "objectKey": "raw/123.webp",
                              "originalUrl": "https://source.example/bird"
                            }
                          ]
                        }
                        """, true, true)));
        assertThat(response.id()).isEqualTo(401L);
    }

    @Test
    void getBird_getsDetailIncludingOriginalResidency() {
        stubFor(get(urlEqualTo("/api/v1/admin/dex/birds/401"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 401,
                                  "name": {
                                    "koreanName": "해오라기",
                                    "scientificName": "Butorides striata"
                                  },
                                  "habitats": ["RIVER_LAKE"],
                                  "residencies": [
                                    {
                                      "residencyType": "RESIDENT",
                                      "rarity": "COMMON",
                                      "monthBitmask": null,
                                      "effectiveMonthBitmask": 4095
                                    }
                                  ],
                                  "seasonsWithRarity": [],
                                  "images": []
                                }
                                """)));

        AdminBirdDetailResponse response = client.getBird(401L);

        verify(getRequestedFor(urlEqualTo("/api/v1/admin/dex/birds/401")));
        assertThat(response.residencies()).singleElement().satisfies(residency -> {
            assertThat(residency.residencyType()).isEqualTo("RESIDENT");
            assertThat(residency.monthBitmask()).isNull();
            assertThat(residency.effectiveMonthBitmask()).isEqualTo(4095);
        });
    }

    @Test
    void generateImagePresignUrl_postsContentType() {
        stubFor(post(urlEqualTo("/api/v1/admin/dex/birds/image/presign"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "presignedUrl": "https://s3.example/upload",
                                  "objectKey": "raw/550e8400-e29b-41d4-a716-446655440000.png"
                                }
                                """)));

        AdminBirdImagePresignResponse response = client.generateImagePresignUrl(
                new AdminBirdImagePresignRequest("image/png")
        );

        verify(postRequestedFor(urlEqualTo("/api/v1/admin/dex/birds/image/presign"))
                .withRequestBody(equalToJson("""
                        {
                          "contentType": "image/png"
                        }
                        """)));
        assertThat(response.objectKey()).startsWith("raw/");
    }

    private AdminBirdUpsertRequest createRequest() {
        return new AdminBirdUpsertRequest(
                new AdminBirdUpsertRequest.Name("해오라기", "Butorides striata", "Linnaeus", 1758),
                new AdminBirdUpsertRequest.Taxonomy(
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
                new AdminBirdUpsertRequest.Description("설명", "출처", false),
                45.0,
                "https://species.example/bird",
                "NONE",
                List.of("RIVER_LAKE"),
                List.of(new AdminBirdUpsertRequest.Residency("RESIDENT", "COMMON", 4095)),
                List.of(new AdminBirdUpsertRequest.Image("raw/123.webp", "https://source.example/bird"))
        );
    }
}
