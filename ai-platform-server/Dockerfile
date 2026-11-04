# syntax=docker/dockerfile:1.7
# 构建上下文必须是仓库根目录：docker compose build
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY ai-dependencies/pom.xml ai-dependencies/pom.xml
COPY ai-common/pom.xml ai-common/pom.xml
COPY ai-module-model/pom.xml ai-module-model/pom.xml
COPY ai-module-chat/pom.xml ai-module-chat/pom.xml
COPY ai-module-agent/pom.xml ai-module-agent/pom.xml
COPY ai-module-tool/pom.xml ai-module-tool/pom.xml
COPY ai-module-prompt/pom.xml ai-module-prompt/pom.xml
COPY ai-module-rag/pom.xml ai-module-rag/pom.xml
COPY ai-module-multimodal/pom.xml ai-module-multimodal/pom.xml
COPY ai-module-integration/pom.xml ai-module-integration/pom.xml
COPY ai-module-monitor/pom.xml ai-module-monitor/pom.xml
COPY ai-module-audit/pom.xml ai-module-audit/pom.xml
COPY ai-platform-server/pom.xml ai-platform-server/pom.xml

COPY ai-common/src ai-common/src
COPY ai-module-model/src ai-module-model/src
COPY ai-module-chat/src ai-module-chat/src
COPY ai-module-agent/src ai-module-agent/src
COPY ai-module-tool/src ai-module-tool/src
COPY ai-module-prompt/src ai-module-prompt/src
COPY ai-module-rag/src ai-module-rag/src
COPY ai-module-multimodal/src ai-module-multimodal/src
COPY ai-module-integration/src ai-module-integration/src
COPY ai-module-monitor/src ai-module-monitor/src
COPY ai-module-audit/src ai-module-audit/src
COPY ai-platform-server/src ai-platform-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
       tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home app

WORKDIR /app
COPY --from=build /workspace/ai-platform-server/target/ai-platform-server.jar /app/app.jar
RUN mkdir -p /app/data/uploads /app/data/tessdata && chown -R app:app /app

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
