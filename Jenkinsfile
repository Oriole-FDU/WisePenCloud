pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        PROJECT_NAME = 'wisepencloud'
        DOCKER_REGISTRY = 'local'
        COMPOSE_FILE_PATH = 'docker-compose-app.yml'
    }

    // 可选参数化构建，方便手动触发时选择分支
    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: '选择需要构建的 Git 分支')
        booleanParam(name: 'DEPLOY_AFTER_BUILD', defaultValue: true, description: '构建成功后是否执行部署')
        booleanParam(name: 'PUSH_TO_REGISTRY', defaultValue: false, description: '是否额外上传镜像到用户指定的制品仓')
        string(name: 'REGISTRY_HOST', defaultValue: '', description: '制品仓主机名和可选端口，不包含协议或路径')
        string(name: 'REGISTRY_NAMESPACE', defaultValue: '', description: '制品仓命名空间，例如 wisepen')
        booleanParam(name: 'DEPLOY_USER_SERVICE', defaultValue: true, description: '构建并部署 user-service')
        booleanParam(name: 'DEPLOY_SYSTEM_SERVICE', defaultValue: true, description: '构建并部署 system-service')
        booleanParam(name: 'DEPLOY_DOCS_SERVICE', defaultValue: true, description: '构建并部署 docs-service')
        booleanParam(name: 'DEPLOY_RESOURCE_SERVICE', defaultValue: true, description: '构建并部署 resource-service')
        booleanParam(name: 'DEPLOY_DOCUMENT_SERVICE', defaultValue: true, description: '构建并部署 document-service')
        booleanParam(name: 'DEPLOY_NOTE_SERVICE', defaultValue: true, description: '构建并部署 note-service')
        booleanParam(name: 'DEPLOY_AI_ASSET_SERVICE', defaultValue: true, description: '构建并部署 ai-asset-service')
        booleanParam(name: 'DEPLOY_WEB_APP_SERVICE', defaultValue: true, description: '构建并部署 web-app-service')
        booleanParam(name: 'DEPLOY_FILE_STORAGE_SERVICE', defaultValue: true, description: '构建并部署 file-storage-service')
        booleanParam(name: 'DEPLOY_FUDAN_EXTENSION_SERVICE', defaultValue: true, description: '构建并部署 fudan-extension-service')
    }

    stages {
        stage('1. 拉取代码 (Checkout)') {
            steps {
                script {
                    def remoteUrl = scm.userRemoteConfigs[0].url
                    echo "开始从 ${remoteUrl} 拉取 ${params.BRANCH_NAME} 分支代码..."
                    deleteDir()
                    git branch: params.BRANCH_NAME, url: remoteUrl
                    env.GIT_COMMIT_FULL = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    sh 'git status --short --branch'
                    echo "代码拉取成功，Commit: ${env.GIT_COMMIT_FULL}，镜像 TAG: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('1.5 确认部署服务 (Select Services)') {
            steps {
                script {
                    def selectedServices = []
                    if (params.DEPLOY_USER_SERVICE) {
                        selectedServices << 'user-service'
                    }
                    if (params.DEPLOY_SYSTEM_SERVICE) {
                        selectedServices << 'system-service'
                    }
                    if (params.DEPLOY_DOCS_SERVICE) {
                        selectedServices << 'docs-service'
                    }
                    if (params.DEPLOY_RESOURCE_SERVICE) {
                        selectedServices << 'resource-service'
                    }
                    if (params.DEPLOY_DOCUMENT_SERVICE) {
                        selectedServices << 'document-service'
                    }
                    if (params.DEPLOY_NOTE_SERVICE) {
                        selectedServices << 'note-service'
                    }
                    if (params.DEPLOY_AI_ASSET_SERVICE) {
                        selectedServices << 'ai-asset-service'
                    }
                    if (params.DEPLOY_WEB_APP_SERVICE) {
                        selectedServices << 'web-app-service'
                    }
                    if (params.DEPLOY_FILE_STORAGE_SERVICE) {
                        selectedServices << 'file-storage-service'
                    }
                    if (params.DEPLOY_FUDAN_EXTENSION_SERVICE) {
                        selectedServices << 'fudan-extension-service'
                    }
                    if (selectedServices.isEmpty()) {
                        error '至少需要选择一个要构建的服务。'
                    }
                    env.SELECTED_COMPOSE_SERVICES = selectedServices.join(' ')
                    echo "本次选择构建的服务: ${env.SELECTED_COMPOSE_SERVICES}"
                }
            }
        }

        stage('2. 编译与打包 (Build)') {
            parallel {
                stage('Java Backend Build') {
                    steps {
                        echo "开始进行 Java 微服务 Maven 构建..."
                        sh 'chmod +x ./mvnw'
                        sh './mvnw clean package -Dmaven.test.skip=true -T 1C'
                        sh '''
                        echo "开始整理构建产物..."
                        # 遍历所有服务模块
                        for dir in wisepen-*-service/*-biz; do
                            if [ -d "$dir/target" ]; then
                                # 精确查找 .jar 结尾，并排除 .original 和 -sources.jar 的包，重命名为 app.jar 放至模块根目录
                                find "$dir/target" -maxdepth 1 -name "*.jar" ! -name "*.original" ! -name "*-sources.jar" -exec cp {} "$dir/app.jar" \\;
                                echo "已成功提取: $dir/app.jar"
                            fi
                        done
                        '''
                    }
                }
            }
        }

        stage('2.5 准备可观测性探针 (OTel Agent)') {
            steps {
                echo "检查并下载 OpenTelemetry Java Agent..."
                sh '''
                FILE="opentelemetry-javaagent.jar"

                # 如果文件不存在，或者文件大小小于10MB，说明下载损坏了
                if [ ! -f "$FILE" ] || [ $(wc -c < "$FILE") -lt 10000000 ]; then
                    echo "本地探针缺失或已损坏，开始从阿里云镜像极速下载..."
                    # 删除坏文件
                    rm -f "$FILE"
                    # 弃用 GitHub，改用阿里云 Maven 官方镜像仓库，解决国内网络阻断问题
                    curl -f -L -# -o "$FILE" https://maven.aliyun.com/repository/public/io/opentelemetry/javaagent/opentelemetry-javaagent/2.30.0/opentelemetry-javaagent-2.30.0.jar
                else
                    echo "探针文件已存在且大小正常，跳过下载。"
                fi
                '''
            }
        }

        stage('3. 构建 Docker 镜像 (Docker Build)') {
            failFast true

            parallel {
                stage('User Service') {
                    when {
                        expression { return params.DEPLOY_USER_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-user:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-user-service/wisepen-user-biz ."
                        }
                    }
                }
                stage('System Service') {
                    when {
                        expression { return params.DEPLOY_SYSTEM_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-system:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-system-service/wisepen-system-biz ."
                        }
                    }
                }
                stage('Docs Service') {
                    when {
                        expression { return params.DEPLOY_DOCS_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-docs:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-docs-service/wisepen-docs-biz ."
                        }
                    }
                }
                stage('Resource Service') {
                    when {
                        expression { return params.DEPLOY_RESOURCE_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-resource:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-resource-service/wisepen-resource-biz ."
                        }
                    }
                }
                stage('Document Service') {
                    when {
                        expression { return params.DEPLOY_DOCUMENT_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-document:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-document-service/wisepen-document-biz ."
                        }
                    }
                }
                stage('Note Service') {
                    when {
                        expression { return params.DEPLOY_NOTE_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-note:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-note-service/wisepen-note-biz ."
                        }
                    }
                }
                stage('AI Asset Service') {
                    when {
                        expression { return params.DEPLOY_AI_ASSET_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-ai-asset:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-ai-asset-service/wisepen-ai-asset-biz ."
                        }
                    }
                }
                stage('Web App Service') {
                    when {
                        expression { return params.DEPLOY_WEB_APP_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-web-app:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-web-app-service/wisepen-web-app-biz ."
                        }
                    }
                }
                stage('File Storage Service') {
                    when {
                        expression { return params.DEPLOY_FILE_STORAGE_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-file-storage:${IMAGE_TAG} -f Dockerfile --build-arg MODULE_NAME=wisepen-file-storage-service/wisepen-file-storage-biz ."
                        }
                    }
                }
                stage('Fudan Extension Service') {
                    when {
                        expression { return params.DEPLOY_FUDAN_EXTENSION_SERVICE }
                    }
                    steps {
                        script {
                            sh "docker build -t ${DOCKER_REGISTRY}/${PROJECT_NAME}-fudan-extension:${IMAGE_TAG} -f Dockerfile.playwright --build-arg MODULE_NAME=wisepen-fudan-extension-service/wisepen-fudan-extension-biz ."
                        }
                    }
                }
            }
        }

        stage('3.5 上传镜像到制品仓 (Push Registry)') {
            when {
                expression { return params.PUSH_TO_REGISTRY }
            }
            steps {
                script {
                    if (!params.REGISTRY_HOST?.trim() || params.REGISTRY_HOST.contains('://') || params.REGISTRY_HOST.contains('/')) {
                        error 'REGISTRY_HOST 必须填写主机名和可选端口，不能包含协议或路径。'
                    }
                    if (!params.REGISTRY_NAMESPACE?.trim()) {
                        error '启用制品仓上传时必须填写 REGISTRY_NAMESPACE。'
                    }
                    withCredentials([
                        usernamePassword(
                            credentialsId: 'docker-registry-credentials',
                            usernameVariable: 'REGISTRY_USERNAME',
                            passwordVariable: 'REGISTRY_PASSWORD'
                        )
                    ]) {
                        withEnv([
                            "REGISTRY_HOST=${params.REGISTRY_HOST.trim()}",
                            "REGISTRY_NAMESPACE=${params.REGISTRY_NAMESPACE.trim()}"
                        ]) {
                            sh '''
                            set -eu
                            DOCKER_CONFIG="$(mktemp -d)"
                            export DOCKER_CONFIG
                            trap 'rm -rf "$DOCKER_CONFIG"' EXIT
                            printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY_HOST" \
                                --username "$REGISTRY_USERNAME" --password-stdin

                            for service in $SELECTED_COMPOSE_SERVICES; do
                                case "$service" in
                                    user-service) image_name=user ;;
                                    system-service) image_name=system ;;
                                    docs-service) image_name=docs ;;
                                    resource-service) image_name=resource ;;
                                    document-service) image_name=document ;;
                                    note-service) image_name=note ;;
                                    ai-asset-service) image_name=ai-asset ;;
                                    web-app-service) image_name=web-app ;;
                                    file-storage-service) image_name=file-storage ;;
                                    fudan-extension-service) image_name=fudan-extension ;;
                                    *) echo "未知服务: $service"; exit 1 ;;
                                esac
                                source_image="${DOCKER_REGISTRY}/${PROJECT_NAME}-${image_name}:${IMAGE_TAG}"
                                target_image="${REGISTRY_HOST}/${REGISTRY_NAMESPACE}/${PROJECT_NAME}-${image_name}:${IMAGE_TAG}"
                                docker tag "$source_image" "$target_image"
                                docker push "$target_image"
                            done
                            '''
                        }
                    }
                }
            }
        }

        stage('4. 部署 (Deploy)') {
            when {
                expression { return params.DEPLOY_AFTER_BUILD }
            }
            environment {
                NACOS_USER = credentials('nacos-username')
                NACOS_PWD  = credentials('nacos-password')
            }
            steps {
                script {
                    echo "开始部署最新版本: ${IMAGE_TAG}，服务: ${env.SELECTED_COMPOSE_SERVICES} ..."
                    sh """
                    set -eu
                    docker compose version

                    export APP_VERSION=${IMAGE_TAG}
                    export DOCKER_REGISTRY=${DOCKER_REGISTRY}
                    export NACOS_USERNAME=\${NACOS_USER}
                    export NACOS_PASSWORD=\${NACOS_PWD}

                    docker network create wisepen-net 2>/dev/null || true

                    # 清理本轮选中服务的规范容器
                    for service in ${env.SELECTED_COMPOSE_SERVICES}; do
                        case "\$service" in
                            user-service) container_name=wisepen-user-service ;;
                            system-service) container_name=wisepen-system-service ;;
                            docs-service) container_name=wisepen-docs-service ;;
                            resource-service) container_name=wisepen-resource-service ;;
                            document-service) container_name=wisepen-document-service ;;
                            note-service) container_name=wisepen-note-service ;;
                            ai-asset-service) container_name=wisepen-ai-asset-service ;;
                            web-app-service) container_name=wisepen-web-app-service ;;
                            file-storage-service) container_name=wisepen-file-storage-service ;;
                            fudan-extension-service) container_name=wisepen-fudan-extension-service ;;
                            *)
                                echo "未知 Compose 服务: \$service"
                                exit 1
                                ;;
                        esac
                        docker rm -f "\$container_name" >/dev/null 2>&1 || true
                    done

                    docker compose \
                        -p wisepen-cloud \
                        -f ${COMPOSE_FILE_PATH} \
                        up -d --no-build --force-recreate --remove-orphans \
                        ${env.SELECTED_COMPOSE_SERVICES}
                    """
                }
            }
        }
    }

    post {
        always {
            echo "执行 Docker 垃圾回收..."
            sh 'docker image prune -f'
        }
        success {
            script {
                echo(params.DEPLOY_AFTER_BUILD
                    ? "构建与部署完成！版本: ${IMAGE_TAG}"
                    : "构建完成，已按 build-only 模式跳过部署！版本: ${IMAGE_TAG}")
            }
        }
        failure {
            echo "流水线执行失败，请检查 Jenkins 控制台报错日志！"
        }
    }
}
