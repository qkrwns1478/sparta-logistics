package com.sparta.logistics.product.repository;

import com.sparta.logistics.product.config.JpaAuditingConfig;
import com.sparta.logistics.product.entity.Product;
import com.sparta.logistics.product.enums.ProductStatus;
import com.sparta.logistics.product.fixture.ProductFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private EntityManager entityManager;

    private UUID companyId1;
    private UUID companyId2;
    private UUID hubId1;
    private UUID hubId2;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        companyId1 = UUID.randomUUID();
        companyId2 = UUID.randomUUID();
        hubId1 = UUID.randomUUID();
        hubId2 = UUID.randomUUID();

        product1 = productRepository.save(
                ProductFixture.builder(companyId1, hubId1)
                        .name("노트북A")
                        .price(1000000L)
                        .description("노트북 설명")
                        .build());

        product2 = productRepository.save(
                ProductFixture.builder(companyId1, hubId1)
                        .name("마우스B")
                        .price(50000L)
                        .description("마우스 설명")
                        .build());

        product3 = productRepository.save(
                ProductFixture.builder(companyId2, hubId2)
                        .name("키보드C")
                        .price(150000L)
                        .description("키보드 설명")
                        .build());
    }

    // -------------------------------------------------------
    // 검색 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("상품 검색 조회")
    class SearchProducts {

        @Test
        @DisplayName("상품명 부분일치로 검색할 수 있다")
        void searchByName() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    "노트북", null, null, null, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("노트북A");
        }

        @Test
        @DisplayName("업체 ID로 검색할 수 있다")
        void searchByCompanyId() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, companyId1, null, null, pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("허브 ID로 검색할 수 있다")
        void searchByHubId() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, null, hubId2, null, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("키보드C");
        }

        @Test
        @DisplayName("상태로 검색할 수 있다")
        void searchByStatus() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, null, null, ProductStatus.AVAILABLE, pageable);

            // then — 전체 3개 모두 기본값 AVAILABLE
            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("조건 없이 전체 조회할 수 있다")
        void searchAll() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, null, null, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("페이지 크기 제한이 정상 동작한다")
        void searchWithPageSize() {
            // given — SA 문서 기준 10/30/50 페이지 크기
            PageRequest pageable = PageRequest.of(0, 10,
                    Sort.by(Sort.Direction.DESC, "createdAt"));

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, null, null, null, pageable);

            // then
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(3);
        }
    }

    // -------------------------------------------------------
    // Soft Delete
    // -------------------------------------------------------
    @Nested
    @DisplayName("Soft Delete")
    class SoftDelete {

        @Test
        @DisplayName("삭제된 상품은 기본 검색에서 제외된다")
        void deletedProduct_notIncludedInSearch() {
            // given
            product1.delete(UUID.randomUUID());
            productRepository.save(product1);

            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Product> result = productRepository.searchProducts(
                    null, null, null, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("삭제된 상품은 findById에서 조회되지 않는다")
        void deletedProduct_notFoundById() {
            // given
            product1.delete(UUID.randomUUID());
            productRepository.save(product1);

            // Soft Delete(@SQLRestriction) 검증을 위해
            // Persistence Context를 초기화하여 DB 기준 조회가 되도록 함
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<Product> result = productRepository.findById(product1.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------
    // 업체 기준 일괄 삭제
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 기준 일괄 삭제")
    class BulkDelete {

        @Test
        @DisplayName("업체 ID로 해당 업체의 모든 상품을 일괄 삭제할 수 있다")
        void bulkDeleteByCompanyId() {
            // given
            UUID deletedBy = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            // when — clearAutomatically = true 에 의해 영속성 컨텍스트가 자동으로 초기화됨
            productRepository.bulkDeleteByCompanyId(companyId1, now, deletedBy);

            // then — companyId1 소속 상품 2개 삭제 확인
            Page<Product> result = productRepository.searchProducts(
                    null,
                    companyId1,
                    null,
                    null,
                    PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("다른 업체 상품은 삭제되지 않는다")
        void bulkDeleteByCompanyId_otherCompanyNotAffected() {
            // given
            UUID deletedBy = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            // when
            productRepository.bulkDeleteByCompanyId(companyId1, now, deletedBy);

            // then
            // companyId2 소속 상품은 유지
            Page<Product> result = productRepository.searchProducts(
                    null,
                    companyId2,
                    null,
                    null,
                    PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 삭제된 상품은 일괄 삭제에서 제외된다")
        void bulkDeleteByCompanyId_alreadyDeletedSkipped() {
            // given
            // product1 미리 삭제
            product1.delete(UUID.randomUUID());
            productRepository.save(product1);

            UUID deletedBy = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            // when
            productRepository.bulkDeleteByCompanyId(companyId1, now, deletedBy);

            // then — 이미 삭제된 product1의 deletedAt이 덮어쓰여지지 않아야 함
            // (쿼리 조건: deleted_at IS NULL)
            // companyId1 소속 상품 모두 조회 불가
            Page<Product> result = productRepository.searchProducts(
                    null,
                    companyId1,
                    null,
                    null,
                    PageRequest.of(0, 10));
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}
