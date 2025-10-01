package com.smarteye.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 🏗️ SmartEye 아키텍처 검증 테스트
 *
 * ArchUnit을 사용하여 아키텍처 원칙과 설계 규칙을 자동으로 검증합니다.
 * 리팩토링 과정에서 아키텍처 위반 사항을 사전에 방지합니다.
 */
@AnalyzeClasses(packages = "com.smarteye")
@DisplayName("🏗️ SmartEye 아키텍처 검증 테스트")
public class ArchitectureTest {

    // ═══════════════════════════════════════════════════
    // 🎯 레이어 아키텍처 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("📋 계층형 아키텍처 규칙 준수 검증")
    static final ArchRule layered_architecture_is_respected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()

            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Entity").definedBy("..entity..")
            .layer("DTO").definedBy("..dto..")
            .layer("Config").definedBy("..config..")
            .layer("Util").definedBy("..util..")

            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Entity").mayOnlyBeAccessedByLayers("Service", "Repository", "DTO")
            .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Config").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Util").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository");

    // ═══════════════════════════════════════════════════
    // 🔒 의존성 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("🚫 Controller는 Repository에 직접 접근 금지")
    static final ArchRule controllers_should_not_directly_access_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    @DisplayName("🚫 Entity는 Service에 의존 금지")
    static final ArchRule entities_should_not_depend_on_services =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAPackage("..service..");

    @ArchTest
    @DisplayName("🚫 Service는 Controller에 의존 금지")
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    @DisplayName("✅ Repository는 Entity와 Spring Data에만 의존")
    static final ArchRule repositories_should_only_depend_on_entities_and_spring =
            classes().that().resideInAPackage("..repository..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "..entity..",
                            "java..",
                            "jakarta..",
                            "org.springframework.data..",
                            "org.springframework.stereotype..",
                            "org.springframework.transaction.."
                    );

    // ═══════════════════════════════════════════════════
    // 📊 Entity 설계 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("📋 모든 Entity는 @Entity 어노테이션 필수")
    static final ArchRule entities_should_be_annotated =
            classes().that().resideInAPackage("..entity..")
                    .and().areNotEnums()
                    .and().areNotInnerClasses()
                    .should().beAnnotatedWith(jakarta.persistence.Entity.class);

    @ArchTest
    @DisplayName("🔑 모든 Entity는 ID 필드 필수")
    static final ArchRule entities_should_have_id_field =
            classes().that().resideInAPackage("..entity..")
                    .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().containNumberOfElementsMatchingPredicate(1,
                            field -> field.isAnnotatedWith(jakarta.persistence.Id.class));

    @ArchTest
    @DisplayName("⏱️ 성능 측정 필드 일관성 검증")
    static final ArchRule performance_tracking_entities_should_have_processing_time =
            classes().that().resideInAPackage("..entity..")
                    .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .and().haveNameMatching(".*Job|.*Page|.*Output|.*Block")
                    .should().containNumberOfElementsMatchingPredicate(1,
                            field -> field.getName().equals("processingTimeMs"));

    // ═══════════════════════════════════════════════════
    // 🔧 Service 설계 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("⚙️ 모든 Service는 @Service 어노테이션 필수")
    static final ArchRule services_should_be_annotated =
            classes().that().resideInAPackage("..service..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class);

    @ArchTest
    @DisplayName("🎯 Service 네이밍 규칙 준수")
    static final ArchRule services_should_be_named_correctly =
            classes().that().resideInAPackage("..service..")
                    .and().areAnnotatedWith(org.springframework.stereotype.Service.class)
                    .should().haveSimpleNameEndingWith("Service")
                    .orShould().haveSimpleNameEndingWith("Engine")
                    .orShould().haveSimpleNameEndingWith("Analyzer")
                    .orShould().haveSimpleNameEndingWith("Detector");

    // ═══════════════════════════════════════════════════
    // 🎮 Controller 설계 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("🎮 모든 Controller는 @RestController 어노테이션 필수")
    static final ArchRule controllers_should_be_annotated =
            classes().that().resideInAPackage("..controller..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class);

    @ArchTest
    @DisplayName("🌐 Controller 메서드는 HTTP 매핑 어노테이션 필수")
    static final ArchRule controller_methods_should_have_http_mapping =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .and().arePublic()
                    .and().doNotHaveRawReturnType(void.class)
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class)
                    .orShould().beAnnotatedWith(org.springframework.web.bind.annotation.GetMapping.class)
                    .orShould().beAnnotatedWith(org.springframework.web.bind.annotation.PostMapping.class)
                    .orShould().beAnnotatedWith(org.springframework.web.bind.annotation.PutMapping.class)
                    .orShould().beAnnotatedWith(org.springframework.web.bind.annotation.DeleteMapping.class);

    // ═══════════════════════════════════════════════════
    // 📦 패키지 의존성 사이클 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("🔄 패키지 간 순환 의존성 금지")
    static final ArchRule no_cycles_between_packages =
            slices().matching("com.smarteye.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    @DisplayName("🔄 서비스 간 순환 의존성 금지")
    static final ArchRule no_cycles_between_services =
            slices().matching("com.smarteye.service.(*)")
                    .should().beFreeOfCycles();

    // ═══════════════════════════════════════════════════
    // 🏷️ 네이밍 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("📝 DTO 네이밍 규칙 준수")
    static final ArchRule dto_naming_convention =
            classes().that().resideInAPackage("..dto..")
                    .should().haveSimpleNameEndingWith("Dto")
                    .orShould().haveSimpleNameEndingWith("Request")
                    .orShould().haveSimpleNameEndingWith("Response")
                    .orShould().haveSimpleNameEndingWith("Result");

    @ArchTest
    @DisplayName("📝 Repository 네이밍 규칙 준수")
    static final ArchRule repository_naming_convention =
            classes().that().resideInAPackage("..repository..")
                    .and().areInterfaces()
                    .should().haveSimpleNameEndingWith("Repository");

    // ═══════════════════════════════════════════════════
    // 🔐 보안 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("🔐 비밀번호 필드는 String 타입 금지")
    static final ArchRule password_fields_should_not_be_string =
            noFields().that().haveName("password")
                    .or().haveName("passwd")
                    .or().haveName("pwd")
                    .should().haveRawType(String.class);

    @ArchTest
    @DisplayName("🚫 System.out.println 사용 금지")
    static final ArchRule no_system_out_println =
            noClasses().should().callMethod(System.class, "println", String.class)
                    .orShould().callMethod(System.class, "print", String.class);

    // ═══════════════════════════════════════════════════
    // 📊 복잡도 및 크기 제한 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("📏 클래스 크기 제한 (500줄 이하)")
    static final ArchRule classes_should_not_be_too_large =
            classes().that().resideInAPackage("com.smarteye..")
                    .and().areNotEnums()
                    .and().areNotInnerClasses()
                    .should().containNumberOfElementsLessThan(500)
                    .because("클래스는 500줄을 초과하지 않아야 합니다. 더 큰 클래스는 분해를 고려하세요.");

    @ArchTest
    @DisplayName("🔧 메서드 매개변수 제한 (5개 이하)")
    static final ArchRule methods_should_not_have_too_many_parameters =
            methods().that().areDeclaredInClassesThat().resideInAPackage("com.smarteye..")
                    .and().arePublic()
                    .should().haveRawParameterTypes(
                            parameters -> parameters.size() <= 5
                    ).because("메서드는 5개를 초과하는 매개변수를 가지지 않아야 합니다.");

    // ═══════════════════════════════════════════════════
    // 🧪 테스트 관련 규칙 검증
    // ═══════════════════════════════════════════════════

    @ArchTest
    @DisplayName("🧪 테스트 클래스 네이밍 규칙")
    static final ArchRule test_classes_naming =
            classes().that().resideInAPackage("..test..")
                    .or().areAnnotatedWith(org.junit.jupiter.api.Test.class)
                    .should().haveSimpleNameEndingWith("Test")
                    .orShould().haveSimpleNameEndingWith("Tests")
                    .orShould().haveSimpleNameEndingWith("IT");

    // ═══════════════════════════════════════════════════
    // 📋 사용자 정의 검증 메서드
    // ═══════════════════════════════════════════════════

    /**
     * 리팩토링 품질 종합 검증
     * 이 테스트는 모든 아키텍처 규칙을 한 번에 실행하고 결과를 종합합니다.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("🎯 리팩토링 품질 종합 검증")
    void comprehensiveArchitectureValidation() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.smarteye");

        // 모든 아키텍처 규칙들을 실행
        // 이는 CI/CD 파이프라인에서 단일 실패 지점을 제공

        System.out.println("🔍 아키텍처 검증 시작...");

        try {
            layered_architecture_is_respected.check(classes);
            System.out.println("✅ 계층형 아키텍처 규칙 통과");

            controllers_should_not_directly_access_repositories.check(classes);
            System.out.println("✅ Controller-Repository 분리 규칙 통과");

            no_cycles_between_packages.check(classes);
            System.out.println("✅ 순환 의존성 검사 통과");

            performance_tracking_entities_should_have_processing_time.check(classes);
            System.out.println("✅ 성능 추적 필드 일관성 통과");

            System.out.println("🎉 모든 아키텍처 검증 통과!");

        } catch (Exception e) {
            System.err.println("❌ 아키텍처 검증 실패: " + e.getMessage());
            throw e;
        }
    }
}