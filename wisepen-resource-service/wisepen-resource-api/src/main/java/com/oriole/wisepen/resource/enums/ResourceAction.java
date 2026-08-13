package com.oriole.wisepen.resource.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum ResourceAction {
    DISCOVER(1 << 0),           // 1  : 列表可见（发现权）
    VIEW(1 << 1),               // 2  : 在线阅读
    LOAD(1 << 2),               // 4  : 装载（仅AI类资源有此权限）
    EDIT(1 << 3),               // 8  : 协同编辑
    INLINE_COMMENT(1 << 4),     // 16 : 行内评论
    DOWNLOAD_WATERMARK(1 << 5), // 32 : 导出/下载带水印
    DOWNLOAD_ORIGINAL(1 << 6),  // 64 : 下载源文件
    FORK(1 << 7),               // 128: 复制资源
    COMMENT(1 << 8),            // 256: 评论
    MARKET_VIEW(1 << 9);        // 512: 市场阅读（仅能从 MARKET 市场获取该权限）

    public static final int ALL_ACTIONS = DISCOVER.code | VIEW.code | LOAD.code | EDIT.code | INLINE_COMMENT.code | DOWNLOAD_WATERMARK.code | DOWNLOAD_ORIGINAL.code | FORK.code | COMMENT.code;

    public static final int DEFAULT_MEMBER_ACTIONS = DISCOVER.code | VIEW.code | DOWNLOAD_WATERMARK.code;

    public static final int MARKET_BASE_ACTIONS = DISCOVER.code | MARKET_VIEW.code | COMMENT.code;
    public static final int MARKET_FORBIDDEN_ACTIONS_MASK = EDIT.code;

    private final int code;

    public int getImpliedMask() {
        int mask = this.code;
        switch (this) {
            case COMMENT:
                // 评论 必须包含 阅读和可见
                mask |= VIEW.code | DISCOVER.code;
                break;
            case INLINE_COMMENT:
                // 行内评论 必须包含 阅读和可见
                mask |= VIEW.code | DISCOVER.code;
                break;
            case EDIT:
                // 编辑 必须包含 阅读和可见
                mask |= VIEW.code | DISCOVER.code;
                break;
            case FORK:
                // 复制 必须包含 带水印下载、阅读和可见
                mask |= DOWNLOAD_WATERMARK.code | VIEW.code | DISCOVER.code;
                break;
            case DOWNLOAD_ORIGINAL:
                // 下载源文件 必须包含 带水印下载、阅读和可见
                mask |= DOWNLOAD_WATERMARK.code | VIEW.code | DISCOVER.code;
                break;
            case DOWNLOAD_WATERMARK:
                // 下载带水印 必须包含 阅读和可见
                mask |= VIEW.code | DISCOVER.code;
                break;
            case LOAD:
                // 装载 必须包含 阅读和可见（仅AI类资源有此权限）
                mask |= VIEW.code | DISCOVER.code;
                break;
            case MARKET_VIEW:
                // 市场阅读 必须包含 可见
                mask |= DISCOVER.code;
                break;
            case VIEW:
                // 阅读 必须包含 可见
                mask |= DISCOVER.code;
                break;
            case DISCOVER:
                // 列表可见 是最底层的，不隐含其他权限
            default:
                break;
        }
        return mask;
    }

    // 将权限掩码解析为枚举列表
    public static List<ResourceAction> permissionCodeToActions(int permissionCode) {
        return Arrays.stream(values())
                // 使用按位与(&)判断是否包含该权限
                .filter(action -> (permissionCode & action.code) != 0)
                .collect(Collectors.toList());
    }

    // 将枚举列表合并为一个权限掩码
    public static int actionsToPermissionCode(List<ResourceAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return 0;
        }
        return actions.stream()
                .mapToInt(ResourceAction::getImpliedMask)
                // 使用按位或(|)合并所有权限
                .reduce(0, (a, b) -> a | b);
    }

    // 快速校验是否拥有某个指定权限
    public static boolean hasAction(int currentPermissionCode, ResourceAction targetAction) {
        return (currentPermissionCode & targetAction.code) != 0;
    }
}
