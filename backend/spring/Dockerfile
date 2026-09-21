ARG JAVA_BUILD_IMAGE=eclipse-temurin:21-jdk
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21-jre
ARG NODE_RUNTIME_IMAGE=node:22-bookworm-slim
FROM ${JAVA_BUILD_IMAGE} AS build

WORKDIR /workspace
COPY spring/.mvn .mvn
COPY spring/mvnw mvnw
COPY spring/mvnw.cmd mvnw.cmd
COPY spring/pom.xml pom.xml
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY spring/src src
RUN ./mvnw -q -DskipTests package

# The deterministic DSVP engine (backend/dsvp) is a dependency-free Node script
# that runs as a long-lived child process of the API. Without the runtime image
# shipping both a node binary and the script, every animation request degrades to
# DSVP_ENGINE_UNAVAILABLE on the host.
FROM ${NODE_RUNTIME_IMAGE} AS node-runtime

FROM ${JAVA_RUNTIME_IMAGE}

RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /workspace/target/ds-agent-server-*.jar app.jar
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY dsvp /dsvp

USER appuser
EXPOSE 8792

ENTRYPOINT ["java", "-Xms64m", "-Xmx192m", "-Xss512k", "-XX:MaxMetaspaceSize=96m", "-XX:MaxDirectMemorySize=32m", "-XX:+UseSerialGC", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
