// Why not: Spring Initializr の既定は Boot 4.x だが、本プロジェクトは CLAUDE.md / SETUP.md の
// 技術スタック定義に従い 3.x 系の最新安定版 (3.5.16) に固定する。
// Initializr は 3.x の生成を提供しなくなったため、雛形(Wrapper・エントリポイント)のみ
// 4.1.0 生成物から流用し、依存宣言は 3.x のアーティファクト名で記述している。
plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.2"
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

// Why not: 性能計測を通常の test に含めない。1万件の保存に数十秒かかり、
// 毎回のCIに載せると、得られる情報の量に対して待ち時間が見合わない。
// 計測は結果を README に記録する行為であって、回帰を検出する仕組みではない
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("perf") }
}

val perfTest by tasks.registering(Test::class) {
    description = "性能を計測する(通常の test からは除外している)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("perf") }
    // 計測結果を標準出力で読みたい
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}

// Why not: google-java-format や palantir-java-format のような全面フォーマッタは入れない。
// このコードベースは整形そのものに意味を持たせている箇所があり、機械的な再配置で失われる。
//   - OrderStatus の ALLOWED_TRANSITIONS は列を揃えて「どこからどこへ遷移できるか」を一覧させている
//   - 日本語のコメントは、文節の切れ目で折り返して読ませている(全面フォーマッタは行長だけで折る)
// 整形の基準が要るのは「議論にならない部分」だけなので、未使用importの除去や
// 末尾空白のように、誰も異議を唱えない規則に絞る。
spotless {
    java {
        target("src/*/java/**/*.java")
        // 既存の並び(その他 → 空行 → java/javax → 空行 → static)に合わせる。
        // "\#" は static import のグループを指す
        importOrder("", "java|javax", "\\#")
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
