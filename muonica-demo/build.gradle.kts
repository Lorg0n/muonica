plugins {
    id("org.springframework.boot")
    java
}

dependencies {
    implementation(project(":muonica-spring"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
