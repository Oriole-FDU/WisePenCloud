-- =============================================================================
-- APISIX 鉴权前置脚本 (Opaque Token + L1/L2 多级缓存)
-- 作用：拦截 Cookie，查询本地内存/Redis，滑动续期，并往下游注入强类型 Header
-- =============================================================================

return function(conf, ctx)
    local core = require("apisix.core")
    local redis = require("resty.redis")

    -- 清洗 Header 抵御伪造
    core.request.set_header(ctx, "X-User-Id", nil)
    core.request.set_header(ctx, "X-Identity-Type", nil)
    core.request.set_header(ctx, "X-Group-Role-Map", nil)

    -- 请求来源标记
    core.request.set_header(ctx, "X-From-Source", "APISIX-wX0iR6tY")


    -- 提取名为 authorization 的 Cookie
    local session_id = ctx.var.cookie_authorization

    if not session_id or session_id == "" then
        -- 放行，后端处理
        return
    end

    if not ngx.re.match(session_id, "^[a-zA-Z0-9-]+$") then
        -- 格式非法，放行，后端处理
        return
    end

    -- ================= L1 缓存 (APISIX 极速内存字典) =================
    -- 注意：必须在 APISIX 的 config.yaml 中提前定义好这个共享内存块
    local local_cache = ngx.shared.session_cache
    local session_json = local_cache and local_cache:get(session_id)

    if session_json == "INVALID" then
        -- 放行，后端处理
        return
    end

    -- ================= L2 缓存 (Redis 兜底与同步) =================
    -- 未能命中缓存
    if not session_json then
        local red = redis:new()
        -- 设置极短的超时时间(1秒)，防止 Redis 阻塞导致网关雪崩
        red:set_timeouts(1000, 1000, 1000)

        -- 【注意！】开发服务器Docker网络中是redis/6379，部署到正式服务器需改动！
        -- 【注意！】开发服务器密码为root，部署到正式服务器需改动！
        local ok, err = red:connect("redis", 6379)
        if not ok then
            core.log.error(">>> [Auth] Redis Connect Failed: ", err)
            core.response.exit(500, {code = 500, msg = "网关内部错误：鉴权服务暂时不可用"})
            return
        end
        local ok, err = red:auth("root")
        if not ok then
            core.log.error(">>> [Auth] Redis Connect Failed: ", err)
            core.response.exit(500, {code = 500, msg = "网关内部错误：鉴权服务暂时不可用"})
            return
        end

        -- 查库
        local redis_key = "wisepen:user:auth:session:" .. session_id
        session_json, err = red:get(redis_key)

        if not session_json or session_json == ngx.null then
            -- 没查到，说明登录过期
            if local_cache then
                local_cache:set(session_id, "INVALID", 60)
            end

            red:set_keepalive(10000, 100) -- 归还连接池
            -- 放行，后端处理
            return
        end

        -- 将查到的结果写回 L1 内存缓存，存活 5 分钟 (300秒)
        -- 接下来的 5 分钟内，该用户的请求都不会再引发 Redis 网络 I/O
        if local_cache then
            local_cache:set(session_id, session_json, 300)
        end

        -- 滑动续期，把 Redis 中 Key 的寿命重新拉回 7 天
        -- 7 天 = 7 * 24 * 3600 = 604800 秒
        red:expire(redis_key, 604800)

        -- 释放连接到连接池 (最大空闲时间 10 秒，池子大小 100)
        red:set_keepalive(10000, 100)
    end

    -- ================= 解析数据并注入 Header =================
    -- 使用 APISIX 内置的 core.json 安全解析
    local session_data = core.json.decode(session_json)
    if not session_data then
        core.log.error(">>> [Auth] Session JSON Decode Failed for ID: ", session_id)
        -- 放行，后端处理
        return
    end

    if session_data.userId then
        core.request.set_header(ctx, "X-User-Id", tostring(session_data.userId))
    end

    if session_data.identityType then
        core.request.set_header(ctx, "X-Identity-Type", tostring(session_data.identityType))
    end

    if session_data.groupRoleMap then
        core.request.set_header(ctx, "X-Group-Role-Map", core.json.encode(session_data.groupRoleMap))
    end
end