pipeline {
    agent any

    // 环境变量配置中心
    environment {
        // 项目基础名称
        PROJECT_NAME = 'wisepencloud'
        // Docker 镜像仓库地址 (如果在单机内网部署，可以直接留空或使用 local)
        DOCKER_REGISTRY = 'local'
        // 动态获取 Git 提交的简短哈希作为镜像版本 Tag
        IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        // 部署脚本的绝对或相对路径
        COMPOSE_FILE_PATH = 'docker-compose-app.yml'
    }

    // 可选参数化构建，方便手动触发时选择分支
    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: '选择需要构建的 Git 分支')
        booleanParam(name: 'DEPLOY_USER_SERVICE', defaultValue: true, description: '构建并部署 user-service')
        booleanParam(name: 'DEPLOY_SYSTEM_SERVICE', defaultValue: true, description: '构建并部署 system-service')
        booleanParam(name: 'DEPLOY_DOCS_SERVICE', defaultValue: true, description: '构建并部署 docs-service')
        booleanParam(name: 'DEPLOY_RESOURCE_SERVICE', defaultValue: true, description: '构建并部署 resource-service')
        booleanParam(name: 'DEPLOY_DOCUMENT_SERVICE', defaultValue: true, description: '构建并部署 document-service')
        booleanParam(name: 'DEPLOY_NOTE_SERVICE', defaultValue: true, description: '构建并部署 note-service')
        booleanParam(name: 'DEPLOY_AI_ASSET_SERVICE', defaultValue: true, description: '构建并部署 ai-asset-service')
        booleanParam(name: 'DEPLOY_FILE_STORAGE_SERVICE', defaultValue: true, description: '构建并部署 file-storage-service')
        booleanParam(name: 'DEPLOY_FUDAN_EXTENSION_SERVICE', defaultValue: true, description: '构建并部署 fudan-extension-service')
    }

    stages {
        stage('1. 拉取代码 (Checkout)') {
            steps {
                echo "开始拉取 ${params.BRANCH_NAME} 分支代码..."
                checkout scm
                echo "✅ 代码拉取成功，当前构建版本 TAG: ${IMAGE_TAG}"
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
                    if (params.DEPLOY_FILE_STORAGE_SERVICE) {
                        selectedServices << 'file-storage-service'
                    }
                    if (params.DEPLOY_FUDAN_EXTENSION_SERVICE) {
                        selectedServices << 'fudan-extension-service'
                    }
                    if (selectedServices.isEmpty()) {
                        error '至少需要选择一个要构建并部署的服务。'
                    }
                    env.SELECTED_COMPOSE_SERVICES = selectedServices.join(' ')
                    echo "本次选择构建并部署的服务: ${env.SELECTED_COMPOSE_SERVICES}"
                }
            }
        }

        stage('2. 编译与打包 (Build)') {
            parallel {
                stage('Java Backend Build') {
                    steps {
                        echo "开始进行 Java 微服务 Maven 构建..."
                        // 赋予 maven wrapper 执行权限
                        sh 'chmod +x ./mvnw'
                        // 多模块统一编译打包，跳过测试，开启单核多线程编译加快速度
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

        stage('3. 并行构建并推送镜像 (Docker Build & Push)') {
            // failFast true：任何一个微服务构建失败，立即停止整个流水线，避免资源浪费
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

        stage('4. 自动化部署 (Deploy)') {
            environment {
                NACOS_USER = credentials('nacos-username')
                NACOS_PWD  = credentials('nacos-password')
            }
            steps {
                script {
                    echo "开始部署最新版本: ${IMAGE_TAG}，服务: ${env.SELECTED_COMPOSE_SERVICES} ..."
                    // Jenkins 和 运行服务器 在同一台宿主机
                    sh """
                    # 如果没有 docker-compose，则静默下载最新独立版
                    if ! command -v docker-compose &> /dev/null; then
                        echo "容器内缺失 docker-compose，正在自动下载..."
                        curl -L -# -o /usr/local/bin/docker-compose "https://github.com/docker/compose/releases/latest/download/docker-compose-\$(uname -s)-\$(uname -m)"
                        chmod +x /usr/local/bin/docker-compose
                    fi

                    # 导出所需环境变量
                    export APP_VERSION=${IMAGE_TAG}
                    export DOCKER_REGISTRY=${DOCKER_REGISTRY}
                    export NACOS_USERNAME=\${NACOS_USER}
                    export NACOS_PASSWORD=\${NACOS_PWD}

                    # 网络兼容
                    # 检测到遗留 cloud-infra_cloud-net 时叠加 legacy-net overlay
                    # 老中间件全部下线后，本脚本可以与docker-compose-app.legacy-net.yml文件、部署脚本中的探测分支一并删除
                    docker network create wisepen-net 2>/dev/null || true
                    COMPOSE_FILES="-f docker-compose-app.yml"
                    if docker network inspect cloud-infra_cloud-net >/dev/null 2>&1; then
                        echo "检测到遗留网络 cloud-infra_cloud-net，叠加 docker-compose-app.legacy-net.yml"
                        COMPOSE_FILES="\$COMPOSE_FILES -f docker-compose-app.legacy-net.yml"
                    fi

                    # 启动部署
                    docker-compose \$COMPOSE_FILES up -d --remove-orphans ${env.SELECTED_COMPOSE_SERVICES}
                    """
                }
            }
        }
    }

    // 后置处理钩子
    post {
        always {
            // 清理悬挂的无用镜像 (<none>:<none>)，防止长年累月撑爆 Jenkins 宿主机磁盘
            echo "执行 Docker 垃圾回收..."
            sh 'docker image prune -f'
        }
        success {
            echo "🎉 构建与部署大功告成！版本: ${IMAGE_TAG}"
            // 可以在这里增加 curl 命令发送钉钉/飞书 webhook 机器人通知
            // sh """
            // curl -X POST 'https://oapi.dingtalk.com/robot/send?access_token=xxx' \
            // -H 'Content-Type: application/json' \
            // -d '{"msgtype": "text", "text": {"content": "【WisePenCloud CI/CD】\n版本 ${IMAGE_TAG} 已成功部署！"}}'
            // """
        }
        failure {
            echo "❌ 流水线执行失败，请检查 Jenkins 控制台报错日志！"
        }
    }
}
