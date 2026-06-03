# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# JVM 기본 타임존을 KST 로 고정한다. JPA @LastModifiedDate(기본 DateTimeProvider)와
# LocalDateTime 직렬화가 모두 한국시 기준이 되어, 프론트 표시 시각과 일치한다.
ENV TZ=Asia/Seoul

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
