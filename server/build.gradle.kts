plugins {
	java
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.docconv"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	maven { url = uri("https://maven.aliyun.com/repository/public") }
	maven { url = uri("https://maven.aliyun.com/repository/spring") }
	maven { url = uri("https://artifactory.openpreservation.org/artifactory/vera-dev") }
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// .env file support
	implementation("io.github.cdimascio:java-dotenv:5.2.2")

	// LangChain4j for AI
	implementation("dev.langchain4j:langchain4j-open-ai:1.5.0")
	implementation("dev.langchain4j:langchain4j:1.5.0")

	// PDF parsing - OpenDataLoader
	implementation("org.opendataloader:opendataloader-pdf-core:2.3.0")

	// Swagger / OpenAPI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

	// Hutool
	implementation("cn.hutool:hutool-core:5.8.42")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// MapStruct
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

	// Test
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
