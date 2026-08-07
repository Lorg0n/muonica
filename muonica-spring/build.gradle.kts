plugins {
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    api(project(":muonica-core"))
    implementation(project(":muonica-ui"))
    implementation(project(":muonica-openapi"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.boot:spring-boot-webmvc")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
