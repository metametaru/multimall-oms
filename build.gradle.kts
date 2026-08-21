// Why not: Spring Initializr の既定は Boot 4.x だが、本プロジェクトは CLAUDE.md / SETUP.md の
// 技術スタック定義に従い 3.x 系の最新安定版 (3.5.16) に固定する。
// Initializr は 3.x の生成を提供しなくなったため、雛形(Wrapper・エントリポイント)のみ
// 4.1.0 生成物から流用し、依存宣言は 3.x のアーティファクト名で記述している。
plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.minioms"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWTの発行(NimbusJwtEncoder)と検証(NimbusJwtDecoder)。
    // Why not: JWTライブラリを直接依存に入れない。トークンの検証は実装を誤ると
    // 静かに認証が素通りする箇所であり、Spring Security が保証する経路に乗せる
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // モールBのレスポンスがXML。クライアント専用のXmlMapperとして使う
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // @ServiceConnection でコンテナ接続情報を Spring に自動連携させるために必要
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // 画面E2E。ブラウザ本体は初回実行時に取得される
    testImplementation("com.microsoft.playwright:playwright:1.62.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Why not: ブラウザの自動取得に任せない。既定では Chromium / Firefox / WebKit の
// 3種すべてが入り 1GB 近くを消費するが、画面E2Eに要るのは Chromium だけ。
// clone した人が追加の手順を踏まずに済むよう、取得自体はビルドに組み込む。
val installPlaywrightBrowser by tasks.registering(JavaExec::class) {
    description = "画面E2Eに使う Chromium を取得する(取得済みなら何もしない)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"

    // Why not: --with-deps を常に付けない。CI の素のUbuntuではブラウザが依存する
    // 共有ライブラリを入れる必要があるが、この指定は sudo を要求するため、
    // 開発者の手元で勝手に走らせるべきではない。CI だけが明示的に有効化する
    val withDeps = providers.gradleProperty("playwrightWithDeps").isPresent
    setArgs(if (withDeps) listOf("install", "--with-deps", "chromium") else listOf("install", "chromium"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn(installPlaywrightBrowser)
    // 取得は上のタスクが担うため、テスト実行時の自動取得は止める
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
}
