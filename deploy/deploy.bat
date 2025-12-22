@echo off
setlocal enabledelayedexpansion
chcp 65001
echo ==========================================
echo       WisePen 部署脚本
echo ==========================================
:: 用法: deploy.bat [JAVA_HOME] [USER] [IP]
:: 示例: deploy.bat "C:\Program Files\Java\jdk-21.0.2" oriole wisepen-dev-server

cd /d "%~dp0.."
echo 当前工作目录已切换至: %cd%

if "%~1" neq "" (set "JAVA_HOME=%~1") else (
    sset "JAVA_HOME=C:\Program Files\Java\jdk-21.0.2"
)

:: --- [1. 核心配置区] ---
if "%~2" neq "" (set "SERVER_USER=%~2") else (
    set "SERVER_USER=oriole"
)

if "%~3" neq "" (set "SERVER_IP=%~3") else (
    set "SERVER_IP=wisepen-dev-server"
)
set REMOTE_ROOT=/opt/wisepen-cloud

:: 【重点】在这里定义你的服务列表 (用空格隔开核心名称)
:: 脚本会自动拼接成 wisepen-xxx-service 和 wisepen-xxx-biz
set SERVICES=user system

echo.
echo [1/4] 正在执行全量编译...
call .\mvnw.cmd clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] 编译失败，脚本终止。
    pause
    exit /b
)

echo [2/4] 正在上传辅助文件...
ssh %SERVER_USER%@%SERVER_IP% "mkdir -p %REMOTE_ROOT%/deploy"
echo    - 上传 Dockerfile...
scp -q Dockerfile %SERVER_USER%@%SERVER_IP%:%REMOTE_ROOT%/
echo    - 上传 Agent Jar...
scp -q opentelemetry-javaagent.jar %SERVER_USER%@%SERVER_IP%:%REMOTE_ROOT%/
echo    - 上传 Compose 配置...
scp -q deploy\docker-compose-app.yml %SERVER_USER%@%SERVER_IP%:%REMOTE_ROOT%/deploy/

echo.
echo [3/4] 正在批量上传 Jar 包...

for %%s in (%SERVICES%) do (
    echo -- 处理服务: [%%s]

    :: 拼接本地路径: wisepen-user-service\wisepen-user-biz\target\*.jar
    set "LOCAL_PATH=wisepen-%%s-service\wisepen-%%s-biz\target\*.jar"

    :: 拼接远程路径: /opt/wisepen-cloud/wisepen-user-service/wisepen-user-biz/target/
    set "REMOTE_PATH=%REMOTE_ROOT%/wisepen-%%s-service/wisepen-%%s-biz/target/"

    echo    正在创建远程目录...
    ssh %SERVER_USER%@%SERVER_IP% "mkdir -p !REMOTE_PATH!"

    :: 执行上传
    scp !LOCAL_PATH! %SERVER_USER%@%SERVER_IP%:!REMOTE_PATH!

    if !errorlevel! neq 0 (
        echo [ERROR] 服务 [%%s] 上传失败！
        pause
        exit /b
    )
)

echo.
echo [4/4] 正在重启服务...

:: 初始化一个变量用来存所有 docker 服务名 (如: user-service system-service)
set DOCKER_ARGS=

for %%s in (%SERVICES%) do (
    :: 假设 docker-compose 里的服务名是 user-service, system-service
    set "DOCKER_ARGS=!DOCKER_ARGS! %%s-service"
)

echo    - 目标容器: !DOCKER_ARGS!

echo.
echo 发送重启指令...
echo %SERVER_USER%@%SERVER_IP% "cd %REMOTE_ROOT%/deploy && docker compose -f docker-compose-app.yml up -d --build --no-deps !DOCKER_ARGS!"

echo.
echo ==========================================
echo             部署完成
echo ==========================================
pause