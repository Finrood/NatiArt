plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.github.ben-manes.versions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.h2database:h2")
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
}
