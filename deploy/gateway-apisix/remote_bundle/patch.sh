#!/bin/bash

# ================= 环境变量定义 =================
CONTAINER_NAME="apisix" # 容器名

# ================= 补丁修复逻辑 =================

function apply_patches() {
    echo ">>> [Patch] 正在应用 APISIX 补丁..."
    echo "    源文件: $PATCH_SRC"
    echo "    目标位: $CONTAINER_TARGET"

    # 定义补丁源文件和目标路径
    local PATCH_SRC="$1"
    local CONTAINER_TARGET="$2"

    # 检查本地补丁文件是否存在
    if [ ! -f "$PATCH_SRC" ]; then
        echo "错误: 补丁文件 $PATCH_SRC 不存在！请检查路径。"
        exit 1
    fi

    # 检查容器是否运行
    if ! docker ps | grep -q "$CONTAINER_NAME"; then
        echo "错误: 容器 $CONTAINER_NAME 未运行，无法打补丁。"
        exit 1
    fi

    # 备份原文件
    echo "   └─ 正在备份容器内原文件..."
    docker exec "$CONTAINER_NAME" cp "$CONTAINER_TARGET" "${CONTAINER_TARGET}.bak" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "      备份成功: ${CONTAINER_TARGET}.bak"
    else
        echo "      (提示: 首次备份或文件不存在，继续执行)"
    fi

    # 强制覆盖容器内文件
    # patches 目录就在脚本旁边
    echo "   └─ 正在覆盖新文件..."
    docker cp "$PATCH_SRC" "$CONTAINER_NAME:$CONTAINER_TARGET"

    if [ $? -ne 0 ]; then
        echo "错误: 补丁应用（docker cp）失败！请检查容器名称或路径。"
        exit 1
    fi

    echo "   └─ 覆盖成功。正在重启 APISIX (Restarting)..."

    # 重启容器 (Lua文件修改后必须重启或reload才能生效)
    # restart，比 reload 更彻底，防止内存残留
    docker restart "$CONTAINER_NAME"

    # 3. 等待健康检查 (可选，但推荐)
    echo "补丁应用完成！网关正在重启中..."
    sleep 5
}

# ================= 🚀 执行逻辑 =================

echo "========================================="
echo "   WisePen 网关 APISIX 热修复工具"
echo "========================================="

# 应用补丁
apply_patches "./patches/nacos.lua" "/usr/local/apisix/apisix/discovery/nacos/init.lua"

echo -e "\n========================================="
echo "APISIX 补丁修复已完成!"