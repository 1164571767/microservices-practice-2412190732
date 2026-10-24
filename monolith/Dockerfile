# syntax=docker/dockerfile:1

# ---- 构建阶段：Maven + JDK 25，只负责产出可执行 JAR ----
FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /workspace

# 先只复制 pom，依赖层可被 Docker 缓存复用
COPY pom.xml ./
COPY docker/maven-settings.xml /root/.m2/settings.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

# ---- 运行阶段：仅保留 JRE 与 JAR，减小镜像体积与攻击面 ----
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S -G app -h /app -s /sbin/nologin app \
    && mkdir -p /app \
    && chown app:app /app

WORKDIR /app

COPY --from=build /workspace/target/monolith-*.jar /app/app.jar

USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
