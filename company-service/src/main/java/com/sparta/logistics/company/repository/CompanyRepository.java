package com.sparta.logistics.company.repository;

import com.sparta.logistics.company.entity.Company;
import com.sparta.logistics.company.enums.CompanyStatus;
import com.sparta.logistics.company.enums.CompanyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * deleted_at IS NULL인 데이터만 조회 (@SQLRestriction 적용)
 */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * 업체 목록 조회 Query Parameter 기반
     * - 검색 조건: 업체명(부분 일치), 타입, 허브ID, 상태
     */
    @Query("""
        SELECT c FROM Company c
        WHERE (:name IS NULL OR c.name LIKE %:name%)
         AND (:type IS NULL OR c.type = :type)
         AND (:hubId IS NULL OR c.hubId = :hubId)
         AND (:status IS NULL OR c.status = :status)
    """)
    Page<Company> searchCompanies(
            @Param("name") String name,
            @Param("type") CompanyType type,
            @Param("hubId") UUID hubId,
            @Param("status")CompanyStatus status,
            Pageable pageable
    );

    /**
     * 서비스 내부 통신용 : 존재 여부만 확인 (Product Service → FeignClient 호출)
     * - deleted_at IS NULL 조건은 @SQLRestriction 자동 적용
     */
    boolean existsById(UUID id);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE schema_company.p_company
        SET deleted_at = NULL, deleted_by = NULL
        WHERE id = :companyId
        """, nativeQuery = true)
    int restoreById(@Param("companyId") UUID companyId);
}