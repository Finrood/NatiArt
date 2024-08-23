plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework:spring-test")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("com.h2database:h2")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.apache.commons:commons-lang3:3.16.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}
