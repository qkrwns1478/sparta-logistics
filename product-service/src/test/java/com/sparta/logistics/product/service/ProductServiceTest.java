package com.sparta.logistics.product.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.product.client.feign.CompanyFeignClient;
import com.sparta.logistics.product.client.model.CompanyClientResponse;
import com.sparta.logistics.product.dto.request.CreateRequest;
import com.sparta.logistics.product.dto.request.UpdateRequest;
import com.sparta.logistics.product.dto.response.ProductResponse;
import com.sparta.logistics.product.entity.Product;
import com.sparta.logistics.product.entity.ProductStatus;
import com.sparta.logistics.product.exception.ProductErrorCode;
import com.sparta.logistics.product.fixture.ProductFixture;
import com.sparta.logistics.product.repository.ProductRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import static org.mockito.Mockito.mock;

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

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock
    private CompanyFeignClient companyFeignClient;

    @InjectMocks private ProductService productService;

    private UUID userId;
    private UUID hubId;
    private UUID companyId;
    private UUID productId;
    private Product product;
    private CompanyClientResponse companyClientResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        hubId     = UUID.randomUUID();
        companyId = UUID.randomUUID();
        productId = UUID.randomUUID();

        product = ProductFixture.create("테스트 상품", companyId, hubId);

        companyClientResponse = new CompanyClientResponse(
                companyId,
                "테스트 업체",
                "PRODUCER",
                hubId,
                "서울특별시 센터",
                "서울시 송파구 송파대로 12",
                "ACTIVE");
    }

    // -------------------------------------------------------
    // 상품 생성
    // -------------------------------------------------------
    @Nested
    @DisplayName("상품 생성 권한 검증")
    class CreateProduct {

        @Test
        @DisplayName("MASTER는 상품을 생성할 수 있다")
        void createProduct_master_success() {
            // given
            CreateRequest request = new CreateRequest(
                    "테스트 상품",
                    companyId,
                    hubId,
                    10000L,
                    "설명");
            ApiResponse<CompanyClientResponse> apiResponse = mock(ApiResponse.class);
            given(apiResponse.data()).willReturn(companyClientResponse);
            given(companyFeignClient.getCompany(companyId)).willReturn(apiResponse);

            given(productRepository.save(any(Product.class))).willReturn(product);

            // when
            ProductResponse response = productService.createProduct(
                    request,
                    Role.MASTER,
                    null,
                    null);

            // then
            assertThat(response.name()).isEqualTo("테스트 상품");
            then(productRepository).should().save(any(Product.class));
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 본인 업체 상품만 수정할 수 있다")
        void updateProduct_companyManager_success() {
            // given — companyId 반환하는 Mock 사용
            Product product = mock(Product.class);
            given(product.getCompanyId()).willReturn(companyId);
            given(product.getName()).willReturn("수정된 상품명");
            given(product.getHubId()).willReturn(hubId);
            given(product.getStatus()).willReturn(ProductStatus.AVAILABLE);

            UpdateRequest request = new UpdateRequest("수정된 상품명", null, null, null);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse response =
                    productService.updateProduct(
                            productId,
                            request,
                            Role.COMPANY_MANAGER,
                            null,
                            companyId);  // companyId 일치

            // then
            assertThat(response.name()).isEqualTo("수정된 상품명");
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 다른 업체 상품을 수정할 수 없다")
        void updateProduct_companyManager_otherCompany_fail() {
            // given
            Product product = mock(Product.class);
            given(product.getCompanyId()).willReturn(companyId);

            UUID otherCompanyId = UUID.randomUUID();  // 다른 값

            UpdateRequest request = new UpdateRequest("수정된 상품명", null, null, null);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() ->
                    productService.updateProduct(
                            productId,
                            request,
                            Role.COMPANY_MANAGER,
                            null,
                            otherCompanyId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("업체와 허브 정보가 불일치하면 예외가 발생한다")
        void createProduct_companyHubMismatch_fail() {
            // given
            UUID differentHubId = UUID.randomUUID();
            CreateRequest request = new CreateRequest(
                    "테스트 상품",
                    companyId,
                    differentHubId,
                    10000L,
                    "설명");

            CompanyClientResponse mismatchCompany = new CompanyClientResponse(
                    companyId,
                    "테스트 업체",
                    "PRODUCER",
                    hubId,  // 업체의 실제 hubId
                    "서울특별시 센터",
                    "서울시 송파구 송파대로 12",
                    "ACTIVE");

            ApiResponse<CompanyClientResponse> apiResponse = mock(ApiResponse.class);

            given(apiResponse.data()).willReturn(mismatchCompany);

            given(companyFeignClient.getCompany(companyId)).willReturn(apiResponse);

            // when & then
            assertThatThrownBy(() ->
                    productService.createProduct(
                            request,
                            Role.MASTER,
                            null,
                            null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.COMPANY_HUB_MISMATCH);
        }

        @Test
        @DisplayName("존재하지 않는 업체로 상품 생성 시 예외가 발생한다")
        void createProduct_companyNotFound_fail() {
            // given
            CreateRequest request = new CreateRequest(
                    "테스트 상품",
                    companyId,
                    hubId,
                    10000L,
                    "설명");

            // mock() 대신 실제 FeignException.NotFound 인스턴스 생성
            doThrow(new FeignException.NotFound(
                    "Not Found",
                    mock(feign.Request.class),
                    null,
                    null))
                    .when(companyFeignClient)
                    .getCompany(companyId);

            // when & then
            assertThatThrownBy(() ->
                    productService.createProduct(
                            request,
                            Role.MASTER,
                            null,
                            null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.COMPANY_NOT_FOUND);
        }
    }

    // -------------------------------------------------------
    // 상품 단건 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("상품 단건 조회")
    class GetProduct {

        @Test
        @DisplayName("존재하는 상품을 조회할 수 있다")
        void getProduct_success() {
            // given
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse response = productService.getProduct(productId);

            // then
            assertThat(response.name()).isEqualTo("테스트 상품");
            assertThat(response.price()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 예외가 발생한다")
        void getProduct_notFound_fail() {
            // given
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.getProduct(productId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    // -------------------------------------------------------
    // 상품 수정
    // -------------------------------------------------------
    @Nested
    @DisplayName("상품 수정 권한 검증")
    class UpdateProduct {

        @Test
        @DisplayName("MASTER는 모든 상품을 수정할 수 있다")
        void updateProduct_master_success() {
            // given
            UpdateRequest request = new UpdateRequest("수정된 상품명", 20000L, null, null);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse response = productService.updateProduct(
                            productId,
                            request,
                            Role.MASTER,
                            null,
                            null);

            // then
            assertThat(response.name()).isEqualTo("수정된 상품명");
            assertThat(response.price()).isEqualTo(20000L);
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 본인 업체 상품만 수정할 수 있다")
        void updateProduct_companyManager_success() {
            // given
            UpdateRequest request = new UpdateRequest("수정된 상품명", null, null, null);

            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse response = productService.updateProduct(
                    productId,
                    request,
                    Role.COMPANY_MANAGER,
                    null,
                    companyId);

            // then
            assertThat(response.name()).isEqualTo("수정된 상품명");
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 다른 업체 상품을 수정할 수 없다")
        void updateProduct_companyManager_otherCompany_fail() {
            // given
            UUID otherCompanyId = UUID.randomUUID();
            UpdateRequest request = new UpdateRequest("수정된 상품명", null, null, null);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() ->
                    productService.updateProduct(
                            productId,
                            request,
                            Role.COMPANY_MANAGER,
                            null,
                            otherCompanyId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("상품을 HIDDEN 상태로 변경할 수 있다")
        void updateProduct_hidden_success() {
            // given
            UpdateRequest request = new UpdateRequest(null, null, null, "HIDDEN");
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse response = productService.updateProduct(
                    productId,
                    request,
                    Role.MASTER,
                    null,
                    null);

            // then
            assertThat(response.status()).isEqualTo("HIDDEN");
        }
    }

    // -------------------------------------------------------
    // 상품 삭제
    // -------------------------------------------------------
    @Nested
    @DisplayName("상품 삭제")
    class DeleteProduct {

        @Test
        @DisplayName("MASTER는 상품을 삭제할 수 있다")
        void deleteProduct_master_success() {
            // given
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            productService.deleteProduct(productId, userId, Role.MASTER, null);

            // then — Soft Delete 확인
            assertThat(product.getDeletedAt()).isNotNull();
            assertThat(product.getDeletedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("COMPANY_MANAGER는 상품을 삭제할 수 없다")
        void deleteProduct_companyManager_fail() {
            // given
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() ->
                    productService.deleteProduct(
                            productId,
                            userId,
                            Role.COMPANY_MANAGER,
                            null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("HUB_MANAGER는 본인 담당 허브 상품만 삭제할 수 있다")
        void deleteProduct_hubManager_success() {
            // given
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when
            productService.deleteProduct(productId, userId, Role.HUB_MANAGER, hubId);

            // then
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("HUB_MANAGER는 다른 허브 상품을 삭제할 수 없다")
        void deleteProduct_hubManager_differentHub_fail() {
            // given
            UUID otherHubId = UUID.randomUUID();
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() ->
                    productService.deleteProduct(
                            productId,
                            userId,
                            Role.HUB_MANAGER,
                            otherHubId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }
    }
}
