FROM openjdk:25-jdk-slim

WORKDIR /app

# 接收构建参数：传入模块的相对路径
ARG MODULE_NAME

# 复制业务 Jar 包
COPY ${MODULE_NAME}/app.jar app.jar

COPY opentelemetry-javaagent.jar /app/agent.jar

# 暴露端口
EXPOSE 8080

# 使用环境变量来动态配置服务名
ENTRYPOINT ["java", \
            "-javaagent:/app/agent.jar", \
            "-Dotel.traces.sampler=always_on", \
            "-jar", "/app/app.jar"]