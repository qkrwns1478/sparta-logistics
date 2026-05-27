package com.sparta.logistics.company.repository;

import com.sparta.logistics.company.config.JpaAuditingConfig;
import com.sparta.logistics.company.entity.Company;
import com.sparta.logistics.company.enums.CompanyStatus;
import com.sparta.logistics.company.enums.CompanyType;
import com.sparta.logistics.company.fixture.CompanyFixture;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest
 * - JPA 관련 빈만 로드 (빠름)
 * - 인메모리 H2 DB 사용
 * - 각 테스트마다 트랜잭션 롤백
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
public class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private EntityManager entityManager;

    private UUID hubId1;
    private UUID hubId2;
    private Company producer;
    private Company receiver;

    @BeforeEach
    void setUp() {
        hubId1 = UUID.randomUUID();
        hubId2 = UUID.randomUUID();

        producer = companyRepository.save(
                CompanyFixture.create("생산업체A", hubId1));
        receiver = companyRepository.save(
                CompanyFixture.builder(hubId2)
                        .name("수령업체B")
                        .type(CompanyType.RECEIVER)
                        .build());
    }

    // -------------------------------------------------------
    // 검색 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 검색 조회")
    class SearchCompanies {

        @Test
        @DisplayName("업체명 부분일치로 검색할 수 있다")
        void searchByName() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    "생산",
                    null,
                    null,
                    null,
                    pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("생산업체A");
        }

        @Test
        @DisplayName("업체 타입으로 검색할 수 있다")
        void searchByType() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    CompanyType.RECEIVER,
                    null,
                    null,
                    pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo(CompanyType.RECEIVER);
        }

        @Test
        @DisplayName("허브 ID로 검색할 수 있다")
        void searchByHubId() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    null,
                    hubId1,
                    null,
                    pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getHubId()).isEqualTo(hubId1);
        }

        @Test
        @DisplayName("상태로 검색할 수 있다")
        void searchByStatus() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    null,
                    null,
                    CompanyStatus.ACTIVE,
                    pageable);

            // then — 두 업체 모두 기본값 ACTIVE
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("조건 없이 전체 조회할 수 있다")
        void searchAll() {
            // given
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    null,
                    null,
                    null,
                    pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("페이지네이션이 정상 동작한다")
        void searchWithPagination() {
            // given — size=1로 첫 번째 페이지
            PageRequest pageable = PageRequest.of(0, 1,
                    Sort.by(Sort.Direction.ASC, "name"));

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    null,
                    null,
                    null,
                    pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }

    // -------------------------------------------------------
    // Soft Delete
    // -------------------------------------------------------
    @Nested
    @DisplayName("Soft Delete")
    class SoftDelete {

        @Test
        @DisplayName("삭제된 업체는 기본 조회에서 제외된다")
        void deletedCompany_notIncludedInSearch() {
            // given
            UUID deletedBy = UUID.randomUUID();

            // producer 삭제
            producer.delete(deletedBy);
            companyRepository.save(producer);

            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Page<Company> result = companyRepository.searchCompanies(
                    null,
                    null,
                    null,
                    null,
                    pageable);

            // then
            // 삭제된 producer 제외, receiver만 조회
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("수령업체B");
        }

        @Test
        @DisplayName("삭제된 업체는 findById에서 조회되지 않는다")
        void deletedCompany_notFoundById() {
            // given
            UUID deletedBy = UUID.randomUUID();

            producer.delete(deletedBy);
            companyRepository.save(producer);

            // Soft Delete(@SQLRestriction) 검증을 위해
            // Persistence Context를 초기화하여 DB 기준 조회가 되도록 함
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<Company> result = companyRepository.findById(producer.getId());

            // then
            // @SQLRestriction("deleted_at IS NULL") 적용
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("삭제 시 deletedAt, deletedBy가 정상 저장된다")
        void softDelete_fieldsSet() {
            // given
            UUID deletedBy = UUID.randomUUID();

            // when
            producer.delete(deletedBy);
            companyRepository.save(producer);

            // then
            assertThat(producer.getDeletedAt()).isNotNull();
            assertThat(producer.getDeletedBy()).isEqualTo(deletedBy);
        }
    }

    // -------------------------------------------------------
    // existsById
    // -------------------------------------------------------
    @Nested
    @DisplayName("업체 존재 여부 확인")
    class ExistsById {

        @Test
        @DisplayName("존재하는 업체는 true를 반환한다")
        void existsById_true() {
            assertThat(companyRepository.existsById(producer.getId())).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 업체는 false를 반환한다")
        void existsById_false() {
            assertThat(companyRepository.existsById(UUID.randomUUID())).isFalse();
        }

        @Test
        @DisplayName("삭제된 업체는 false를 반환한다")
        void existsById_deleted_false() {
            // given
            producer.delete(UUID.randomUUID());
            companyRepository.save(producer);

            // then
            // @SQLRestriction 적용으로 삭제된 업체 제외
            assertThat(companyRepository.existsById(producer.getId())).isFalse();
        }
    }
}
