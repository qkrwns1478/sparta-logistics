package com.sparta.logistics.user.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.validator.HubCompanyValidator;
import com.sparta.logistics.user.client.support.FeignClientsTestBootApp;
import com.sparta.logistics.user.exception.UserErrorCode;
import feign.FeignException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Eureka 없이 MockWebServer로 hub / company Feign 클라이언트 HTTP 계약을 검증한다.
 */
@SpringBootTest(classes = FeignClientsTestBootApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HubCompanyFeignClientIT {

    private static final MockWebServer MOCK_BACKEND = new MockWebServer();

    private static final AtomicReference<Dispatcher> currentDispatcher = new AtomicReference<>();

    static {
        MOCK_BACKEND.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Dispatcher d = currentDispatcher.get();
                if (d != null) {
                    try {
                        return d.dispatch(request);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new MockResponse().setResponseCode(500);
                    }
                }
                return new MockResponse().setResponseCode(500);
            }
        });
        try {
            MOCK_BACKEND.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void feignUseMockBackend(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + MOCK_BACKEND.getPort();
        registry.add("spring.cloud.discovery.enabled", () -> false);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.openfeign.circuitbreaker.enabled", () -> false);
        registry.add("spring.cloud.openfeign.client.config.hub-service.url", () -> base);
        registry.add("spring.cloud.openfeign.client.config.company-service.url", () -> base);
    }

    @Autowired
    private HubServiceClient hubServiceClient;

    @Autowired
    private CompanyServiceClient companyServiceClient;

    @Autowired
    private HubCompanyValidator hubCompanyValidator;

    @BeforeEach
    void dispatcherReset() {
        currentDispatcher.set(null);
    }

    @AfterAll
    static void shutdownServer() throws IOException {
        MOCK_BACKEND.shutdown();
    }

    @Test
    @DisplayName("HubServiceClient - 허브 존재 시 200 void")
    void hubClient_exists_returnsOk() {
        UUID hubId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/hubs/" + hubId + "/exists")) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatCode(() -> hubServiceClient.checkHubExists(hubId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HubServiceClient - 없으면 404 → FeignException.NotFound")
    void hubClient_notExists_throwsNotFound() {
        UUID hubId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/hubs/" + hubId + "/exists")) {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatThrownBy(() -> hubServiceClient.checkHubExists(hubId))
                .isInstanceOf(FeignException.NotFound.class);
    }

    @Test
    @DisplayName("HubServiceClient - 503 등은 일반 FeignException")
    void hubClient_serviceError_throwsFeignException() {
        UUID hubId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/hubs/" + hubId + "/exists")) {
                    return new MockResponse().setResponseCode(503);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatThrownBy(() -> hubServiceClient.checkHubExists(hubId))
                .isInstanceOf(FeignException.class)
                .isNotInstanceOf(FeignException.NotFound.class);
    }

    @Test
    @DisplayName("CompanyServiceClient - 업체 존재 시 200 void")
    void companyClient_exists_returnsOk() {
        UUID companyId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/companies/" + companyId + "/exists")) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatCode(() -> companyServiceClient.checkCompanyExists(companyId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CompanyServiceClient - 없으면 404 → FeignException.NotFound")
    void companyClient_notExists_throwsNotFound() {
        UUID companyId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/companies/" + companyId + "/exists")) {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatThrownBy(() -> companyServiceClient.checkCompanyExists(companyId))
                .isInstanceOf(FeignException.NotFound.class);
    }

    @Test
    @DisplayName("HubCompanyValidator - hub 404 → HUB_NOT_FOUND")
    void validator_hubNotFound_mapsToBusinessException() {
        UUID hubId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod())
                        && Objects.equals(request.getPath(), "/api/v1/hubs/" + hubId + "/exists")) {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatThrownBy(() -> hubCompanyValidator.validate(hubId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.HUB_NOT_FOUND);
    }

    @Test
    @DisplayName("HubCompanyValidator - hub·company 존재 시 통과")
    void validator_hubAndCompanyOk_noException() {
        UUID hubId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID companyId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        currentDispatcher.set(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if ("GET".equals(request.getMethod())
                        && path.equals("/api/v1/hubs/" + hubId + "/exists")) {
                    return new MockResponse().setResponseCode(200);
                }
                if ("GET".equals(request.getMethod())
                        && path.equals("/api/v1/companies/" + companyId + "/exists")) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(500);
            }
        });

        assertThatCode(() -> hubCompanyValidator.validate(hubId, companyId))
                .doesNotThrowAnyException();
    }
}
