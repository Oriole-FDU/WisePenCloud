#!/bin/bash

# ================= 环境变量定义 =================
APISIX_ADMIN="http://127.0.0.1:9180"
ADMIN_KEY="edd1c9f034335f136f87ad84b625c8f1"

# 鉴权相关配置 (可从 Jenkins/GitLab CI 注入)
CONSUMER_NAME="wisepen_global_consumer"
CONSUMER_KEY="wisepen-app"
JWT_SECRET="wisepen-secret-888"

# 鉴权模版 ID
TPL_ID_PUBLIC=1      # 公开模板 (仅路由 + 监控)
TPL_ID_PROTECTED=2   # 保护模板 (路由 + 监控 + JWT + Header注入)

# ================= 工具函数 =================

# 检查 jq 是否安装
if ! command -v jq &> /dev/null; then
    echo "错误: 未检测到 'jq' 命令。请先安装它 (apt install jq / yum install jq)"
    exit 1
fi

# 优雅地读取 Lua 并转义为 JSON 字符串
function load_lua_script() {
    local filepath=$1
    if [ ! -f "$filepath" ]; then
        echo "Error: $filepath 文件不存在！"
        exit 1
    fi
    # 使用 jq -Rs . 可以将任意文件内容转义为安全的 JSON 字符串
    cat "$filepath" | jq -Rs .
}

# ================= 核心函数定义 =================
function init_infrastructure() {
echo ">>> [1/4] 加载 Lua 脚本..."
    # 获取转义后的 Lua 脚本字符串
    local LUA_ROUTE=$(load_lua_script "./scripts/route.lua")
    local LUA_AUTH=$(load_lua_script "./scripts/auth.lua")


echo ">>> [2/4] 初始化 Consumer..."
    # 使用 jq 构造 JSON body
    local body_consumer=$(jq -n \
        --arg user "$CONSUMER_NAME" \
        --arg key "$CONSUMER_KEY" \
        --arg secret "$JWT_SECRET" \
        '{
            username: $user,
            plugins: {
                "jwt-auth": { key: $key, secret: $secret }
            }
        }')

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/consumers" -X PUT \
          -H "X-API-KEY: ${ADMIN_KEY}" \
          -d "$body_consumer"

    echo ">>> [3/4] 初始化 [公开] 模板 (ID: ${TPL_ID_PUBLIC})..."
        # 公开模板：只有路由隔离，没有 JWT
        local body_public=$(jq -n \
            --argjson script_route "$LUA_ROUTE" \
            '{
                desc: "WisePen Public Template (Monitor + Routing)",
                plugins: {
                    "prometheus": {},
                    "opentelemetry": {},
                    "serverless-pre-function": {
                        phase: "rewrite",
                        functions: [$script_route] # route.lua 放在 rewrite 阶段
                    }
                }
            }')

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/plugin_configs/${TPL_ID_PUBLIC}" -X PUT \
          -H "X-API-KEY: ${ADMIN_KEY}" \
          -d "$body_public"

    echo ">>> [4/4] 初始化 [受保护] 模板 (ID: ${TPL_ID_PROTECTED})..."
        # 受保护模板：路由 -> JWT校验 -> Header注入
        # 注意顺序：serverless-pre (rewrite) 中的函数按数组顺序执行
        # 我们希望：1. 路由选择(route.lua)  2. 鉴权后注入Header(auth.lua)
        # 注意：JWT 插件会在 rewrite 阶段较早执行，所以 pre-function 通常在插件之后或并行
        # 修正逻辑：auth.lua 需要从请求头拿 Authorization，这通常在 access 阶段或者 rewrite 阶段

    local body_protected=$(jq -n \
            --argjson script_route "$LUA_ROUTE" \
            --argjson script_auth "$LUA_AUTH" \
            '{
                desc: "WisePen Protected Template (Monitor + Routing + JWT + AuthHeaders)",
                plugins: {
                    "jwt-auth": {
                        "cookie": "authorization"
                    },
                    "prometheus": {},
                    "opentelemetry": {},
                    "serverless-pre-function": {
                        phase: "rewrite",
                        functions: [$script_route, $script_auth]
                    }
                }
            }')
            # functions 数组里，先跑路由脚本，再跑 Header 注入脚本。
            # jwt-auth 插件本身优先级很高，通常会先确保 401，然后才轮到我们的脚本处理业务逻辑

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/plugin_configs/${TPL_ID_PROTECTED}" -X PUT \
          -H "X-API-KEY: ${ADMIN_KEY}" \
          -d "$body_protected"

    echo -e "\n基础设施初始化完成。"
}

# ================= 路由注册函数 =================

# 注册服务 (通用版)
# 参数：ID, Name, URI, NacosService, TemplateID
function _register_route_base() {
    local ID=$1
    local NAME=$2
    local URI=$3
    local SERVICE=$4
    local TPL_ID=$5

    echo ">>> 注册路由 [$NAME] -> $SERVICE (TPL: $TPL_ID)"

    local body=$(jq -n \
        --arg name "$NAME" \
        --arg uri "$URI" \
        --arg service "$SERVICE" \
        --argjson tpl "$TPL_ID" \
        '{
            name: $name,
            uri: $uri,
            plugin_config_id: $tpl,
            upstream: {
                type: "roundrobin",
                discovery_type: "nacos",
                service_name: $service
            }
        }')

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/routes/${ID}" -X PUT \
      -H "X-API-KEY: ${ADMIN_KEY}" \
      -d "$body"
}

# 对外暴露的快捷函数

# 1. 公开路由 (使用 TPL_ID_PUBLIC)
function register_public_route() {
    _register_route_base "$1" "$2" "$3" "$4" "$TPL_ID_PUBLIC"
}

# 2. 保护路由 (使用 TPL_ID_PROTECTED)
function register_service_route() {
    _register_route_base "$1" "$2" "$3" "$4" "$TPL_ID_PROTECTED"
}

# ================= 🚀 执行逻辑 =================

echo "========================================="
echo "   WisePen 网关自动化部署脚本"
echo "========================================="

init_infrastructure

echo -e "\n-----------------------------------------"

# 注册服务
# 格式: register_public_route / register_service_route  <ID>  <描述>  <路径>  <Nacos服务名>

# 注册鉴权服务 (公开接口，如登录)
# 使用 register_public_route，ID 建议用独立段 (如 100+)
register_public_route 100 "auth-service-public" "/auth/*" "wisepen-user-service"

# 注册业务服务 (受保护接口)
# 使用 register_service_route，绑定鉴权模版
register_service_route 1 "user-service-protected" "/user/*" "wisepen-user-service"

echo -e "\n========================================="
echo "所有配置已推送到 APISIX !"