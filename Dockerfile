# syntax=docker/dockerfile:1

ARG GRADLE_IMAGE=gradle:9.8.1-jdk21
ARG NODE_IMAGE=node:22-bookworm-slim
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${GRADLE_IMAGE} AS gradle-base
USER root
RUN apt-get update \
    && apt-get install --no-install-recommends -y git \
    && rm -rf /var/lib/apt/lists/*
USER gradle
RUN git config --global --add safe.directory /workspace

FROM ${NODE_IMAGE} AS node

FROM gradle-base AS dev
COPY --from=node /usr/local/ /usr/local/
WORKDIR /workspace
ENTRYPOINT []
CMD ["bash"]

FROM gradle-base AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
USER gradle
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    chmod +x ./gradlew \
    && ./gradlew --no-daemon jar -PskipHandbook=1 -Dorg.gradle.jvmargs=-Xmx4g

FROM ${JRE_IMAGE} AS runtime
WORKDIR /opt/astaps
COPY --from=build /workspace/grasscutter-7.1.0.jar ./grasscutter.jar
RUN mkdir -p /runtime \
    && chown 1000:1000 /runtime /opt/astaps
WORKDIR /runtime
USER 1000:1000

EXPOSE 8088/tcp 22101/tcp 22101/udp
ENTRYPOINT ["java", "-jar", "/opt/astaps/grasscutter.jar"]
