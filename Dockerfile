# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
# Tests use PostgreSQL and run separately before releasing the image.
RUN ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN groupadd --gid 10001 grownupsvet \
    && useradd --uid 10001 --gid grownupsvet --no-create-home --shell /usr/sbin/nologin grownupsvet \
    && mkdir -p /app /app/tmp \
    && chown -R grownupsvet:grownupsvet /app
WORKDIR /app
COPY --from=build --chown=grownupsvet:grownupsvet /workspace/target/grownupsvet-backend-0.0.1-SNAPSHOT.jar /app/application.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Djava.io.tmpdir=/app/tmp -XX:MaxRAMPercentage=35 -XX:+ExitOnOutOfMemoryError -XX:-HeapDumpOnOutOfMemoryError"
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
