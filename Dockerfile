ARG BUILD_IMAGE=eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269

FROM ${BUILD_IMAGE} AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Resolve dependencies before the network-isolated prod Bean definition generation.
FROM builder AS aot-builder
COPY scripts/spring-aot/generation.properties scripts/spring-aot/generation.properties
COPY scripts/appcds/training-credentials.json scripts/appcds/training-credentials.json
RUN ./gradlew -PplimapAot=true resolveAotDependencies --no-daemon
RUN --network=none ./gradlew -PplimapAot=true verifyAotArtifact --offline --no-daemon

FROM ${RUNTIME_IMAGE} AS runtime-base

RUN useradd --system --uid 10001 --user-group --create-home spring \
    && install -d -o spring -g spring /app

WORKDIR /app

USER spring

ENV PORT=8080
EXPOSE 8080

# Optional packaging control: docker build --target baseline ...
FROM runtime-base AS baseline
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Train with the exact same JRE, paths and application classpath as runtime.
# No production environment, credentials or network is available to this RUN.
FROM runtime-base AS appcds-training
COPY --from=aot-builder --chown=spring:spring /workspace/build/libs/*.jar /tmp/application.jar
RUN java -Djarmode=tools -jar /tmp/application.jar extract --destination /app --application-filename app.jar
COPY --from=aot-builder --chown=spring:spring /workspace/build/spring-aot/profile /app/spring-aot-profile
COPY --chown=spring:spring scripts/appcds/training.properties /tmp/appcds-training.properties
COPY --chown=spring:spring scripts/appcds/training-credentials.json /tmp/appcds-training-credentials.json
RUN --network=none export GOOGLE_APPLICATION_CREDENTIALS=/tmp/appcds-training-credentials.json \
    GOOGLE_CLOUD_PROJECT=appcds-training \
    && java -XX:ArchiveClassesAtExit=/app/application.jsa -Dspring.context.exit=onRefresh \
    -jar /app/app.jar --spring.profiles.active=prod \
    --spring.config.additional-location=file:/tmp/appcds-training.properties \
    && test -s /app/application.jsa \
    && java -Xshare:on -XX:SharedArchiveFile=/app/application.jsa \
    -Xlog:class+load=info:file=/tmp/appcds-validation.log -Dspring.context.exit=onRefresh \
    -jar /app/app.jar --spring.profiles.active=prod \
    --spring.config.additional-location=file:/tmp/appcds-training.properties \
    && grep -F 'com.example.plimap.PlimapApplication source: shared objects file (top)' /tmp/appcds-validation.log \
    && sha256sum /app/application.jsa > /app/application.jsa.sha256 \
    && java -version 2> /app/java-version.txt

FROM runtime-base AS runtime
COPY --from=appcds-training --chown=spring:spring /app/ /app/
COPY --chown=spring:spring --chmod=755 scripts/appcds/entrypoint.sh /app/entrypoint.sh
ENV PLIMAP_APP_CDS_ENABLED=true
ENV PLIMAP_SPRING_AOT_ENABLED=false
ENTRYPOINT ["/app/entrypoint.sh"]
