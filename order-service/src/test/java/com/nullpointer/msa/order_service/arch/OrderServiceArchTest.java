package com.nullpointer.msa.order_service.arch;

import com.nullpointer.msa.arch.CommonArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OrderServiceArchTest {

    private static final String BASE_PACKAGE = "com.nullpointer.msa.order_service";
    private JavaClasses importedClasses;

    @BeforeEach
    void setUp() {
        // 테스트 클래스 제외, 프로덕션 코드 import
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    @DisplayName("레이어드 아키텍처 의존성 방향 준수해야 합니다.")
    void layerDependencyTest() {
        CommonArchRules.layerDependencyRule(importedClasses, BASE_PACKAGE);
    }

    @Test
    @DisplayName("Controller 클래스명은 'Controller'로 끝나야 합니다.")
    void controllerNamingTest() {
        CommonArchRules.controllerNamingRule(BASE_PACKAGE)
                .check(importedClasses);
    }

    @Test
    @DisplayName("Service 클래스명은 'Service'로 끝나야 합니다.")
    void serviceNamingTest() {
        CommonArchRules.serviceNamingRule(BASE_PACKAGE)
                .check(importedClasses);
    }

    // Repository,Entity 생성 시 주석 해제 후 테스트 진행합니다.
//    @Test
//    @DisplayName("Repository 클래스명은 'Repository'로 끝나야 합니다.")
//    void repositoryNamingTest() {
//        CommonArchRules.repositoryNamingRule(BASE_PACKAGE)
//                .check(importedClasses);
//    }
//
//    @Test
//    @DisplayName("Entity 클래스명은 'Entity'로 끝나야 합니다.")
//    void entityNamingTest() {
//        CommonArchRules.entityNamingRule(BASE_PACKAGE)
//                .check(importedClasses);
//    }

    @Test
    @DisplayName("Controller는 @RestController 어노테이션이 있어야 합니다.")
    void controllerAnnotationTest() {
        CommonArchRules.controllerAnnotationRule(BASE_PACKAGE)
                .check(importedClasses);
    }

    @Test
    @DisplayName("Service는 @Service 어노테이션이 있어야 합니다.")
    void serviceAnnotationTest() {
        CommonArchRules.serviceAnnotationRule(BASE_PACKAGE)
                .check(importedClasses);
    }

    // Repository,Entity 생성 시 주석 해제 후 테스트 진행합니다.
//    @Test
//    @DisplayName("Repository는 @Repository 또는 JpaRepository를 상속해야 합니다.")
//    void repositoryAnnotationTest() {
//        CommonArchRules.repositoryAnnotationRule(BASE_PACKAGE)
//                .check(importedClasses);
//    }
//
//    @Test
//    @DisplayName("Entity는 @Entity 어노테이션이 있어야 합니다.")
//    void entityAnnotationTest() {
//        CommonArchRules.entityAnnotationRule(BASE_PACKAGE)
//                .check(importedClasses);
//    }
//
//    @Test
//    @DisplayName("Controller는 Entity를 직접 참조할 수 없습니다.")
//    void entityNotUsedInControllerTest() {
//        CommonArchRules.entityNotUsedInController(BASE_PACKAGE)
//                .check(importedClasses);
//    }

    @Test
    @DisplayName("user-service는 order-service 패키지를 직접 참조할 수 없습니다.")
    void noDirectCrossServiceImportTest() {
        CommonArchRules.noDirectServiceCrossImport(
                "com.nullpointer.msa.order_service", "com.nullpointer.msa.user_service")
                .check(importedClasses);
    }
}
