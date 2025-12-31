-- =============================================================================
-- APISIX 智能元数据路由脚本 (Smart Metadata Routing)
-- 作用：根据请求头 X-Developer 动态过滤 Nacos 节点，实现开发环境隔离
-- =============================================================================

return function(conf, ctx)
    local core = require("apisix.core")

    -- 安全加载 Nacos 模块
    local has_nacos, nacos = pcall(require, "apisix.discovery.nacos")
    if not has_nacos or not nacos then
        core.log.error(">>> [Dev-Isolation] Nacos module NOT found! Check if APISIX discovery is enabled.")
        return
    end

    -- 获取请求头中的 X-Developer
    local target_dev = core.request.header(ctx, "X-Developer")

    -- 如果没有头，或者头为空，直接跳过，走默认负载均衡
    if not target_dev or target_dev == "" then
        core.log.warn(">>> [Dev-Isolation] No Header, Skip.")
        return
    end

    -- 获取当前匹配的路由配置
    local route = ctx.matched_route
    if not route then
        core.log.warn(">>> [Dev-Isolation] No matched route found.")
        return
    end

    local up_conf = route.value.upstream
    if not up_conf then
        core.log.warn(">>> [Dev-Isolation] No upstream config in route.")
        return
    end

    -- 确认是 Nacos 服务
    if up_conf.discovery_type ~= "nacos" then
        core.log.warn(">>> [Dev-Isolation] Not a Nacos upstream. Skip.")
        return
    end

    local service_name = up_conf.service_name
    if not service_name then
        core.log.warn(">>> [Dev-Isolation] No service_name in upstream.")
        return
    end

    -- 直接从 Nacos 缓存获取节点列表
    local nodes = nacos.nodes(service_name)
    if not nodes then
        core.log.warn(">>> [Dev-Isolation] Nacos returned NO nodes for: ", service_name)
        return
    end

    -- 寻找 metadata.developer == target_dev 的节点
    local match_nodes = {}
    for _, node in ipairs(nodes) do
        -- core.log.warn("Node Info: ", core.json.encode(node))

        if node.metadata and node.metadata.developer == target_dev then
            table.insert(match_nodes, node)
        end
    end

    -- 决策
    if #match_nodes > 0 then
        -- 找到了开发者的专属节点
        -- 强行指定本次请求的上游节点 (Pick Server)
        -- 这里简单粗暴取第一个，通常开发环境每个人也只起一个实例
        ctx.picked_server = match_nodes[1]

        local ip = match_nodes[1].host or match_nodes[1].ip -- 兼容不同版本的字段
        local port = match_nodes[1].port

        ctx.upstream_conf = {
            name = "dev-isolation-temp",
            type = "roundrobin",
            scheme = "http",
            nodes = {
                {
                    host = match_nodes[1].host or match_nodes[1].ip,
                    port = match_nodes[1].port,
                    weight = 1
                }
            },
            pass_host = "pass",
            retries = 0,
            timeout = {
                connect = 2,
                send = 2,
                read = 10
            }
        }
        core.log.warn("[Development Isolation] Traffic Hit: ", target_dev, " -> ", ctx.picked_server.host, ":", ctx.picked_server.port)
    else
        -- 没找到节点
        -- 打印警告，然后自动放行（Failover），流量会回退到正常的主干环境
        core.log.warn("[Development Isolation] Failed to locate developer node: ", target_dev, ", automatically reverted to main environment.")
    end
end