FROM openjdk:21-jdk-slim

WORKDIR /app

# 1. 接收构建参数
ARG JAR_FILE

# 2. 复制业务 Jar 包
COPY ${JAR_FILE} app.jar

# 3. [新增] 复制根目录下的探针 Jar 包到镜像中
# 注意：docker build 上下文是项目根目录，所以这里能直接读到
COPY opentelemetry-javaagent.jar agent.jar

# 暴露端口
EXPOSE 8080

# 4. [修改] 启动命令
# 使用环境变量来动态配置服务名，这样同一个镜像可以用在 User 和 System 上
ENTRYPOINT ["java", \
            "-javaagent:/app/agent.jar", \
            "-Dotel.traces.sampler=always_on", \
            "-jar", "/app/app.jar"]