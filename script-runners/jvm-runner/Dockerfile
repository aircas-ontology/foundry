# syntax=docker/dockerfile:1
# jvm-runner：一份镜像服务 groovy 或 java（由 RUNNER_LANGUAGE 决定），部署为 groovy-runner / java-runner 两个容器。
# 运行阶段必须用 JDK（java-runner 依赖 javax.tools.JavaCompiler 动态编译）。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jdk AS runtime
WORKDIR /app
# 低权限非 root 运行（沙箱基线之一）
RUN useradd --system --create-home runner
COPY --from=build /build/target/jvm-runner.jar /app/jvm-runner.jar
USER runner
EXPOSE 39103
ENV RUNNER_LANGUAGE=groovy \
    SERVER_PORT=39103
ENTRYPOINT ["sh", "-c", "java -jar /app/jvm-runner.jar --runner.language=${RUNNER_LANGUAGE} --server.port=${SERVER_PORT}"]
