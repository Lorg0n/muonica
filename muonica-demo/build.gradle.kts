plugins {
    id("org.springframework.boot")
    java
}

dependencies {
    implementation(project(":muonica-spring"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
