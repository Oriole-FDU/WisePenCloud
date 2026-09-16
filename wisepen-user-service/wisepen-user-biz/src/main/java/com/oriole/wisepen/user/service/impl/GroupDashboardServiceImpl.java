package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.resource.domain.dto.ResourceItemInfoResDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.user.api.constant.GroupDashboardMetricConstants;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDashboardActorMetricResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDashboardResourceMetricResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupResourceDailyMetricResponse;
import com.oriole.wisepen.user.api.enums.GroupDashboardSubjectType;
import com.oriole.wisepen.user.api.enums.ResourceGroupDashboardMetricType;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.GroupResourceDailyMetricEntity;
import com.oriole.wisepen.user.domain.entity.GroupResourceSummaryDailyMetricEntity;
import com.oriole.wisepen.user.domain.entity.GroupResourceUserDailyMetricEntity;
import com.oriole.wisepen.user.mapper.GroupResourceDailyMetricMapper;
import com.oriole.wisepen.user.mapper.GroupResourceSummaryDailyMetricMapper;
import com.oriole.wisepen.user.mapper.GroupResourceUserDailyMetricMapper;
import com.oriole.wisepen.user.service.IDisplayService;
import com.oriole.wisepen.user.service.IGroupDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDashboardServiceImpl implements IGroupDashboardService {

    private final RedisCacheManager redisCacheManager;
    private final GroupResourceSummaryDailyMetricMapper groupResourceSummaryDailyMetricMapper;
    private final GroupResourceDailyMetricMapper groupResourceDailyMetricMapper;
    private final GroupResourceUserDailyMetricMapper groupResourceUserDailyMetricMapper;
    private final RemoteResourceService remoteResourceService;
    private final IDisplayService displayService;

    @Override
    public void aggregateRecentResourceMetrics() {
        // 聚合今天和昨天，覆盖 Redis TTL 内最容易出现延迟写入的统计窗口
        List<LocalDate> statDates = List.of(LocalDate.now().minusDays(1), LocalDate.now());
        for (LocalDate statDate : statDates) {
            // 顶层小组每日汇总先在内存累加，再一次性写入汇总表
            Map<String, GroupResourceSummaryDailyMetricEntity> summaryMetricMap = new LinkedHashMap<>();
            // 资源层小组每日汇总由 actor key 推导，避免同时维护两套 Redis counter
            Map<String, GroupResourceDailyMetricEntity> resourceMetricMap = new LinkedHashMap<>();
            // 只读取 actor 维度 Redis counter，最细粒度数据可以推导出资源层和顶层统计
            redisCacheManager.listGroupDashboardMetricCounters(statDate).forEach((metricKey, metricValue) -> {
                String[] parts = metricKey.split(":", -1);
                // 检查 actor metricKey 是否满足要求
                if (parts.length != 10) {
                    log.warn("group resource dashboard actor key skipped. key={}", metricKey);
                    return;
                }
                // 当前任务只落 RESOURCE 的用户贡献统计
                if (!GroupDashboardSubjectType.RESOURCE.getValue().equals(parts[6])) {
                    return;
                }
                ResourceGroupDashboardMetricType metricType;
                try {
                    // 从 actor key 中解析资源看板指标类型
                    metricType = ResourceGroupDashboardMetricType.valueOf(parts[9]);
                } catch (IllegalArgumentException e) {
                    log.warn("group resource dashboard actor metric skipped. key={}", metricKey);
                    return;
                }

                // actor 维度比资源维度多一个 actorUserId，用于管理员查看是谁贡献了指标
                LocalDate parsedDate = LocalDate.parse(parts[4], GroupDashboardMetricConstants.DATE_FORMATTER);
                Long groupId = Long.valueOf(parts[5]);
                String resourceId = parts[7];
                Long actorUserId = Long.valueOf(parts[8]);

                // 同一个小组同一天会有多个资源和用户贡献同一类指标，这里累加到顶层汇总
                String summaryMetricKey = parsedDate + ":" + groupId;
                GroupResourceSummaryDailyMetricEntity summaryMetric = summaryMetricMap.computeIfAbsent(summaryMetricKey, key -> {
                    return GroupResourceSummaryDailyMetricEntity.builder().groupId(groupId).statDate(parsedDate).build();
                });
                summaryMetric.addMetricValue(metricType, metricValue);

                // 同一个资源同一天会有多个用户贡献同一类指标，这里累加到资源层汇总
                String resourceMetricKey = parsedDate + ":" + groupId + ":" + resourceId;
                GroupResourceDailyMetricEntity resourceMetric = resourceMetricMap.computeIfAbsent(resourceMetricKey, key -> {
                    return GroupResourceDailyMetricEntity.builder().groupId(groupId).statDate(parsedDate).resourceId(resourceId).build();
                });
                resourceMetric.addMetricValue(metricType, metricValue);

                // 用户层统计按小组、日期、资源、行为人定位
                GroupResourceUserDailyMetricEntity userMetric = groupResourceUserDailyMetricMapper.selectOne(Wrappers.<GroupResourceUserDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceUserDailyMetricEntity::getGroupId, groupId)
                        .eq(GroupResourceUserDailyMetricEntity::getStatDate, parsedDate)
                        .eq(GroupResourceUserDailyMetricEntity::getResourceId, resourceId)
                        .eq(GroupResourceUserDailyMetricEntity::getActorUserId, actorUserId));
                if (userMetric == null) {
                    // 首次聚合该用户当天对该资源的行为时创建用户层统计行
                    userMetric = GroupResourceUserDailyMetricEntity.builder()
                            .groupId(groupId)
                            .statDate(parsedDate)
                            .resourceId(resourceId)
                            .actorUserId(actorUserId)
                            .build();
                    userMetric.setMetricValue(metricType, metricValue);
                    groupResourceUserDailyMetricMapper.insert(userMetric);
                } else {
                    userMetric.setMetricValue(metricType, metricValue);
                    groupResourceUserDailyMetricMapper.updateById(userMetric);
                }
            });
            // Redis actor counter 是累计快照，资源层表按推导后的快照 upsert，重复调度不会重复加 DB
            resourceMetricMap.values().forEach(resourceMetric -> {
                GroupResourceDailyMetricEntity existed = groupResourceDailyMetricMapper.selectOne(Wrappers.<GroupResourceDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceDailyMetricEntity::getGroupId, resourceMetric.getGroupId())
                        .eq(GroupResourceDailyMetricEntity::getStatDate, resourceMetric.getStatDate())
                        .eq(GroupResourceDailyMetricEntity::getResourceId, resourceMetric.getResourceId()));
                if (existed == null) {
                    groupResourceDailyMetricMapper.insert(resourceMetric);
                } else {
                    BeanUtil.copyProperties(resourceMetric, existed, "id", "groupId", "statDate", "resourceId", "createTime", "updateTime");
                    groupResourceDailyMetricMapper.updateById(existed);
                }
            });
            // Redis actor counter 是累计快照，顶层表按推导后的快照 upsert，重复调度不会重复加 DB
            summaryMetricMap.values().forEach(summaryMetric -> {
                GroupResourceSummaryDailyMetricEntity existed = groupResourceSummaryDailyMetricMapper.selectOne(Wrappers.<GroupResourceSummaryDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceSummaryDailyMetricEntity::getGroupId, summaryMetric.getGroupId())
                        .eq(GroupResourceSummaryDailyMetricEntity::getStatDate, summaryMetric.getStatDate()));
                if (existed == null) {
                    groupResourceSummaryDailyMetricMapper.insert(summaryMetric);
                } else {
                    BeanUtil.copyProperties(summaryMetric, existed, "id", "groupId", "statDate", "createTime", "updateTime");
                    groupResourceSummaryDailyMetricMapper.updateById(existed);
                }
            });
        }
    }

    @Override
    public List<GroupResourceDailyMetricResponse> listResourceDailyMetrics(Long groupId, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, GroupResourceDailyMetricResponse> dailyMetricMap = new LinkedHashMap<>();
        // 先按日期补齐空数据，保证前端拿到连续日期序列
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            GroupResourceDailyMetricResponse dailyMetric = new GroupResourceDailyMetricResponse();
            dailyMetric.setStatDate(date);
            dailyMetricMap.put(date, dailyMetric);
        }
        // 汇总趋势直接读顶层表
        groupResourceSummaryDailyMetricMapper.selectList(Wrappers.<GroupResourceSummaryDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceSummaryDailyMetricEntity::getGroupId, groupId)
                        .between(GroupResourceSummaryDailyMetricEntity::getStatDate, startDate, endDate)
                        .orderByAsc(GroupResourceSummaryDailyMetricEntity::getStatDate))
                .forEach(entity -> dailyMetricMap.get(entity.getStatDate()).addMetricValue(entity));

        // 返回按日期排列的资源看板指标列表
        return new ArrayList<>(dailyMetricMap.values());
    }

    @Override
    public PageR<GroupDashboardResourceMetricResponse> listResourceMetrics(Long groupId, LocalDate statDate, int page, int size) {
        // 资源层列表按单日分页
        IPage<GroupResourceDailyMetricEntity> resourceMetricPage = groupResourceDailyMetricMapper.selectPage(new Page<>(page, size),
                Wrappers.<GroupResourceDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceDailyMetricEntity::getGroupId, groupId)
                        .eq(GroupResourceDailyMetricEntity::getStatDate, statDate)
                        .orderByDesc(GroupResourceDailyMetricEntity::getResourceReadCount)
                        .orderByAsc(GroupResourceDailyMetricEntity::getResourceId));
        PageR<GroupDashboardResourceMetricResponse> pageR = new PageR<>(resourceMetricPage.getTotal(), page, size);
        // 没有资源统计时直接返回空分页
        if (resourceMetricPage.getRecords().isEmpty()) {
            return pageR;
        }

        // 批量获取资源基础信息
        List<String> resourceIds = resourceMetricPage.getRecords().stream().map(GroupResourceDailyMetricEntity::getResourceId).toList();
        List<ResourceItemInfoResDTO> resourceInfos = remoteResourceService.listResourceBaseInfo(resourceIds).getData();
        Map<String, ResourceItemInfoResDTO> resourceInfoMap = Collections.emptyMap();
        if (resourceInfos != null && !resourceInfos.isEmpty()) {
            // 按 resourceId 建索引
            resourceInfoMap = resourceInfos.stream().collect(Collectors.toMap(
                    ResourceItemInfoResDTO::getResourceId,
                    resourceInfo -> resourceInfo,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new));
        }

        Map<String, ResourceItemInfoResDTO> finalResourceInfoMap = resourceInfoMap;
        // 指标来自统计表，资源展示字段来自 Resource 服务补充
        pageR.addAll(resourceMetricPage.getRecords().stream().map(entity -> {
            GroupDashboardResourceMetricResponse response = new GroupDashboardResourceMetricResponse();
            response.setResourceId(entity.getResourceId());
            response.addMetricValue(entity);
            ResourceItemInfoResDTO resourceInfo = finalResourceInfoMap.get(entity.getResourceId());
            if (resourceInfo != null) {
                response.setResourceName(resourceInfo.getResourceName());
                response.setResourceType(resourceInfo.getResourceType() == null ? null : resourceInfo.getResourceType().getValue());
            }
            return response;
        }).toList());
        return pageR;
    }

    @Override
    public PageR<GroupDashboardActorMetricResponse> listResourceActorMetrics(Long groupId, String resourceId,
                                                                            LocalDate statDate, int page, int size) {
        // 用户层列表按单日单资源分页，展示小组成员对该资源的贡献
        IPage<GroupResourceUserDailyMetricEntity> userMetricPage = groupResourceUserDailyMetricMapper.selectPage(new Page<>(page, size),
                Wrappers.<GroupResourceUserDailyMetricEntity>lambdaQuery()
                        .eq(GroupResourceUserDailyMetricEntity::getGroupId, groupId)
                        .eq(GroupResourceUserDailyMetricEntity::getResourceId, resourceId)
                        .eq(GroupResourceUserDailyMetricEntity::getStatDate, statDate)
                        .orderByDesc(GroupResourceUserDailyMetricEntity::getResourceReadCount)
                        .orderByAsc(GroupResourceUserDailyMetricEntity::getActorUserId));
        PageR<GroupDashboardActorMetricResponse> pageR = new PageR<>(userMetricPage.getTotal(), page, size);
        // 没有用户贡献统计时直接返回空分页
        if (userMetricPage.getRecords().isEmpty()) {
            return pageR;
        }

        // 批量获取行为人展示信息，避免逐个查用户
        Set<Long> actorUserIds = userMetricPage.getRecords().stream()
                .map(GroupResourceUserDailyMetricEntity::getActorUserId)
                .collect(Collectors.toSet());
        Map<Long, UserDisplayBase> userInfoMap = displayService.getUserDisplayInfoByIds(actorUserIds);
        // 组装用户维度指标和行为人展示信息
        pageR.addAll(userMetricPage.getRecords().stream().map(entity -> {
            GroupDashboardActorMetricResponse response = new GroupDashboardActorMetricResponse();
            response.setActorUserId(entity.getActorUserId());
            response.setActorInfo(userInfoMap.get(entity.getActorUserId()));
            response.addMetricValue(entity);
            return response;
        }).toList());
        return pageR;
    }
}
