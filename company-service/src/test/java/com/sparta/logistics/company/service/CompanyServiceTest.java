package com.sparta.logistics.company.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.company.client.feign.HubCacheService;
import com.sparta.logistics.company.client.feign.HubFeignClient;
import com.sparta.logistics.company.client.model.HubResponse;
import com.sparta.logistics.company.dto.request.CreateRequest;
import com.sparta.logistics.company.dto.request.UpdateRequest;
import com.sparta.logistics.company.dto.response.CompanyResponse;
import com.sparta.logistics.company.entity.Company;
import com.sparta.logistics.company.enums.CompanyStatus;
import com.sparta.logistics.company.enums.CompanyType;
import com.sparta.logistics.company.exception.CompanyErrorCode;
import com.sparta.logistics.company.fixture.CompanyFixture;
import com.sparta.logistics.company.kafka.producer.CompanyEventProducer;
import com.sparta.logistics.company.repository.CompanyRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class CompanyServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private HubFeignClient hubFeignClient;
    @Mock private HubCacheService hubCacheService;
    @Mock private CompanyEventProducer companyEventProducer;
    @InjectMocks private CompanyService companyService;

    private UUID userId;
    private UUID hubId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        userId      = UUID.randomUUID();
        hubId       = UUID.randomUUID();
        companyId   = UUID.randomUUID();
    }

    // -------------------------------------------------------
    // 업체 생성
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 생성")
    class CreateCompany {

        @Test
        @DisplayName("MASTER는 업체를 생성할 수 있다")
        void createCompany_master_success() {
            // given
            Company company = CompanyFixture.create("테스트 업체", hubId);
            CreateRequest request = new CreateRequest(
                    "테스트 업체",
                    "PRODUCER",
                    hubId,
                    "서울시 송파구 송파대로 12",
                    null,
                    null);

            given(companyRepository.save(any(Company.class)))
                    .willReturn(company);

            // when
            CompanyResponse response
                    = companyService.createCompany(request, Role.MASTER, null);

            // then
            assertThat(response.name()).isEqualTo("테스트 업체");
            then(companyRepository).should().save(any(Company.class));
        }

        @Test
        @DisplayName("HUB_MANAGER는 본인 담당 허브 소속 업체만 생성할 수 있다")
        void createCompany_hubManager_success() {
            // given
            Company company = CompanyFixture.create("테스트 업체", hubId);
            CreateRequest request = new CreateRequest(
                    "테스트 업체",
                    "PRODUCER",
                    hubId,
                    "서울시 송파구 송파대로 12",
                    null,
                    null);

            given(companyRepository.save(any(Company.class))).willReturn(company);

            // when
            CompanyResponse response
                    = companyService.createCompany(request, Role.HUB_MANAGER, hubId);

            // then
            assertThat(response.name()).isEqualTo("테스트 업체");
        }

        @Test
        @DisplayName("HUB_MANAGER는 다른 허브 소속 업체를 생성할 수 없다")
        void createCompany_hubManager_differentHub_fail() {
            // given
            UUID otherHubId = UUID.randomUUID();

            CreateRequest request = new CreateRequest(
                    "테스트 업체",
                    "PRODUCER",
                    hubId,
                    "서울시 송파구 송파대로 12",
                    null,
                    null);

            // when & then
            assertThatThrownBy(() ->
                    companyService.createCompany(request, Role.HUB_MANAGER, otherHubId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.COMPANY_ACCESS_DENIED);

            then(companyRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 허브로 업체를 생성하면 예외가 발생한다")
        void createCompany_hubNotFound_fail() {
            // given
            CreateRequest request = new CreateRequest(
                    "테스트 업체",
                    "PRODUCER",
                    hubId,
                    "서울시 송파구 송파대로 12",
                    null,
                    null
            );

            FeignException exception = mock(FeignException.NotFound.class);

            doThrow(exception)
                    .when(hubFeignClient)
                    .checkHubExists(hubId);

            // when & then
            assertThatThrownBy(() ->
                    companyService.createCompany(request, Role.MASTER, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.EXTERNAL_HUB_NOT_FOUND);
        }

        @Test
        @DisplayName("Hub Service 장애 시 예외가 발생한다")
        void createCompany_hubServiceUnavailable_fail() {
            // given
            CreateRequest request = new CreateRequest(
                    "테스트 업체",
                    "PRODUCER",
                    hubId,
                    "서울시 송파구 송파대로 12",
                    null,
                    null);

            FeignException exception = mock(FeignException.ServiceUnavailable.class);

            doThrow(exception)
                    .when(hubFeignClient)
                    .checkHubExists(hubId);

            // when & then
            assertThatThrownBy(() ->
                    companyService.createCompany(request, Role.MASTER, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.EXTERNAL_HUB_SERVICE_UNAVAILABLE);
        }
    }

    // -------------------------------------------------------
    // 업체 단건 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 단건 조회")
    class GetCompany {

        @Test
        @DisplayName("존재하는 업체를 조회할 수 있다")
        void getCompany_success() {
            // given
            Company company = CompanyFixture.create("테스트 업체", hubId);
            given(companyRepository.findById(companyId))
                    .willReturn(Optional.of(company));

            given(hubCacheService.getHub(hubId))
                    .willReturn(new HubResponse(hubId, "서울특별시 센터"));

            // when
            CompanyResponse response = companyService.getCompany(companyId);

            // then
            assertThat(response.name()).isEqualTo("테스트 업체");
            assertThat(response.hubName()).isEqualTo("서울특별시 센터");
        }

        @Test
        @DisplayName("존재하지 않는 업체 조회 시 예외가 발생한다")
        void getCompany_notFound_fail() {
            // given
            given(companyRepository.findById(companyId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> companyService.getCompany(companyId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND);
        }
    }

    // -------------------------------------------------------
    // 업체 수정
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 수정")
    class UpdateCompany {

        @Test
        @DisplayName("MASTER는 모든 업체를 수정할 수 있다")
        void updateCompany_master_success() {
            //given
            // CompanyFixture는 @GeneratedValue 기반 id를 세팅할 수 없어 mock 사용
            // ReflectionTestUtils 대신 repository 조회 결과만 필요한 단위 테스트 구조로 검증
            Company company = mock(Company.class);
            given(company.getId()).willReturn(companyId);
            given(company.getHubId()).willReturn(hubId);
            given(company.getName()).willReturn("수정된 업체명");
            given(company.getType()).willReturn(CompanyType.PRODUCER);
            given(company.getStatus()).willReturn(CompanyStatus.ACTIVE);

            UpdateRequest request = new UpdateRequest(
                    "수정된 업체명",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            given(companyRepository.findById(companyId))
                    .willReturn(Optional.of(company));

            // when
            CompanyResponse response = companyService.updateCompany(
                    companyId,
                    request,
                    Role.MASTER,
                    null,
                    null);

            // then
            assertThat(response.name()).isEqualTo("수정된 업체명");
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 본인 업체만 수정할 수 있다")
        void updateCompany_companyManager_success() {
            // given
            Company company = mock(Company.class);
            given(company.getId()).willReturn(companyId);
            given(company.getHubId()).willReturn(hubId);
            given(company.getName()).willReturn("수정된 업체명");
            given(company.getType()).willReturn(CompanyType.PRODUCER);
            given(company.getStatus()).willReturn(CompanyStatus.ACTIVE);

            UUID userCompanyId = companyId;

            given(companyRepository.findById(companyId))
                    .willReturn(Optional.of(company));

            UpdateRequest request = new UpdateRequest(
                    "수정된 업체명",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            // when
            CompanyResponse response = companyService.updateCompany(
                    companyId,
                    request,
                    Role.COMPANY_MANAGER,
                    null,
                    userCompanyId);

            // then
            assertThat(response.name()).isEqualTo("수정된 업체명");
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 다른 업체를 수정할 수 없다")
        void updateCompany_companyManager_otherCompany_fail() {
            // given
            Company company = mock(Company.class);
            given(company.getId()).willReturn(companyId);

            UUID otherCompanyId = UUID.randomUUID();

            given(companyRepository.findById(companyId))
                    .willReturn(Optional.of(company));

            UpdateRequest request = new UpdateRequest(
                    "수정된 업체명",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            // when & then
            assertThatThrownBy(() ->
                    companyService.updateCompany(
                            companyId,
                            request,
                            Role.COMPANY_MANAGER,
                            null,
                            otherCompanyId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.COMPANY_ACCESS_DENIED);
        }
    }

    // -------------------------------------------------------
    // 업체 삭제
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 삭제 및 비동기 연동")
    class DeleteCompany {

        @Test
        @DisplayName("MASTER는 업체를 삭제(Soft Delete)하고 Kafka 이벤트를 발행한다")
        void deleteCompany_master_success() {
            // given
            Company company = CompanyFixture.create("테스트 업체", hubId);
            given(companyRepository.findById(companyId)).willReturn(Optional.of(company));

            // when
            companyService.deleteCompany(companyId, userId, Role.MASTER, null);

            // then
            assertThat(company.getDeletedAt()).isNotNull(); // Soft Delete 확인
            assertThat(company.getDeletedBy()).isEqualTo(userId);

            // Feign 대신 비동기 이벤트 발행 함수가 호출되었는지 행위 검증
            then(companyEventProducer).should().publishCompanyDeleted(companyId, userId);
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 업체를 삭제할 수 없으며 이벤트도 발행되지 않는다")
        void deleteCompany_companyManager_fail() {
            // given
            Company company = CompanyFixture.create("테스트 업체", hubId);
            given(companyRepository.findById(companyId)).willReturn(Optional.of(company));

            // when & then
            assertThatThrownBy(() -> companyService.deleteCompany(companyId, userId, Role.COMPANY_MANAGER, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CompanyErrorCode.COMPANY_ACCESS_DENIED);

            // 이벤트가 절대 발행되지 않아야 함
            then(companyEventProducer).should(never()).publishCompanyDeleted(any(), any());
        }
    }

}
