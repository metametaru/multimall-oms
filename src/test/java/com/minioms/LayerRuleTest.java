package com.minioms;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 層の構造そのものの仕様。
 *
 * <p>CLAUDE.md に文章で書いてある「依存方向は presentation → application → domain」
 * 「domain はフレームワーク非依存を保つ」を、読み手の注意力ではなくテストで守る。
 *
 * <p>Why not: レビューでの指摘に任せない。層の侵食は 1 つの import から始まり、
 * 差分としては小さいので見落とされる。気づいた時には剥がすのが高くつく。
 */
@DisplayName("層の依存ルール")
@AnalyzeClasses(packages = "com.minioms", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRuleTest {

    /**
     * Why not: jakarta と Jackson も一緒に禁じる。Spring だけを見ていると、
     * JPA のアノテーションや JSON の項目名が業務ルールの側に付き、
     * 「永続化の都合で業務ルールの形が決まる」状態に静かに戻る。
     */
    private static final String[] フレームワーク = {
        "org.springframework..", "jakarta..", "com.fasterxml.jackson.."
    };

    @ArchTest
    static final ArchRule domain層はフレームワークに依存しない =
            noClasses()
                    .that()
                    .resideInAPackage("com.minioms.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(フレームワーク)
                    .because("業務ルールは Spring や JPA を持ち込まずに読めて試せる状態を保つ");

    /**
     * Why not: application にロギング(slf4j)まで禁じることはしない。ユースケースの
     * 実行記録は運用に要る情報であり、slf4j は実装を差し替えられる facade なので、
     * 特定のフレームワークに縛られる依存には当たらない。
     */
    @ArchTest
    static final ArchRule application層はフレームワークに依存しない =
            noClasses()
                    .that()
                    .resideInAPackage("com.minioms.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(フレームワーク)
                    .because("ユースケースは Spring の起動なしに単体テストできる状態を保つ"
                            + "(DI の組み立ては infrastructure.config が担う)");

    @ArchTest
    static final ArchRule 依存は内側を向く =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("presentation")
                    .definedBy("com.minioms.presentation..")
                    .layer("application")
                    .definedBy("com.minioms.application..")
                    .layer("domain")
                    .definedBy("com.minioms.domain..")
                    .layer("infrastructure")
                    .definedBy("com.minioms.infrastructure..")
                    // 外側の 2 層は誰からも参照されない。infrastructure は application が
                    // 宣言したインターフェースを実装する側であって、呼ばれる相手ではない
                    .whereLayer("presentation")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("infrastructure")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("application")
                    .mayOnlyBeAccessedByLayers("presentation", "infrastructure")
                    .whereLayer("domain")
                    .mayOnlyBeAccessedByLayers("presentation", "application", "infrastructure");
}
