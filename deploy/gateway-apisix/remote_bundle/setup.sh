#!/bin/bash

# ================= 环境变量定义 =================
APISIX_ADMIN="http://127.0.0.1:9180"
ADMIN_KEY="edd1c9f034335f136f87ad84b625c8f1"

# 全局模版
TPL_ID_GLOBAL=1

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
    echo ">>> [1/2] 加载 Lua 脚本..."
    # 获取转义后的 Lua 脚本字符串
    local LUA_ROUTE=$(load_lua_script "./scripts/route.lua")
    local LUA_AUTH=$(load_lua_script "./scripts/auth.lua")

    echo ">>> [2/2] 初始化全局模板 (ID: ${TPL_ID_GLOBAL})..."
    local body_global=$(jq -n \
        --argjson script_route "$LUA_ROUTE" \
        --argjson script_auth "$LUA_AUTH" \
        '{
            desc: "WisePen Global Template (Monitor + Routing + Auth)",
            plugins: {
                "prometheus": {},
                "opentelemetry": {},
                "serverless-pre-function": {
                    "phase": "rewrite",
                    "functions": [$script_route, $script_auth]
                }
            }
        }')

    curl -s -o /dev/null "${APISIX_ADMIN}/apisix/admin/plugin_configs/${TPL_ID_GLOBAL}" -X PUT \
          -H "X-API-KEY: ${ADMIN_KEY}" \
          -d "$body_global"

    echo -e "\n基础设施初始化完成"
}

# ================= 路由注册函数 =================

# 注册服务
# 参数：ID, Name, URI, NacosService, TemplateID
function register_route() {
    local ID=$1
    local NAME=$2
    local URI=$3
    local SERVICE=$4

    echo ">>> 注册路由 [$NAME] -> $SERVICE"

    local body=$(jq -n \
        --arg name "$NAME" \
        --arg uri "$URI" \
        --arg service "$SERVICE" \
        --argjson tpl "$TPL_ID_GLOBAL" \
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

# ================= 🚀 执行逻辑 =================

echo "========================================="
echo "   WisePen 网关自动化部署脚本"
echo "========================================="

init_infrastructure

echo -e "\n-----------------------------------------"

# 注册服务
# 格式: register_route  <ID>  <描述>  <路径>  <Nacos服务名>

# 注册服务
# user-service
register_route 1 "auth-service" "/auth/*" "wisepen-user-service"
register_route 2 "user-service" "/user/*" "wisepen-user-service"
register_route 3 "group-service" "/group/*" "wisepen-user-service"
# res-permission-service
register_route 4 "res-permission-service" "/resource/*" "wisepen-res-permission-service"

echo -e "\n========================================="
echo "所有配置已推送到 APISIX !"