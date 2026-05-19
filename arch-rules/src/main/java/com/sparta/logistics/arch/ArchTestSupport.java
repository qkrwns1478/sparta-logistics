package com.sparta.logistics.arch;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

public abstract class ArchTestSupport {

    protected abstract String getBasePackage();

    @ArchTest
    public final ArchRule controllerAnnotation =
            CommonArchRules.controllerAnnotationRule(getBasePackage());

    @ArchTest
    public final ArchRule serviceAnnotation =
            CommonArchRules.serviceAnnotationRule(getBasePackage());

    @ArchTest
    public final ArchRule repositoryAnnotation =
            CommonArchRules.repositoryAnnotationRule(getBasePackage());

    @ArchTest
    public final ArchRule entityAnnotation =
            CommonArchRules.entityAnnotationRule(getBasePackage());

    @ArchTest
    public void layerDependencyArchRule(JavaClasses classes) {
        CommonArchRules.layerDependencyRule(classes, getBasePackage());
    }

}