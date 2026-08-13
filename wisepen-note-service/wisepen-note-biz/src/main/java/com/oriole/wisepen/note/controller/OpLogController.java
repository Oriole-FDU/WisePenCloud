package com.oriole.wisepen.note.controller;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.note.api.domain.dto.res.NoteOperationLogResponse;
import com.oriole.wisepen.note.service.INoteOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "笔记日志", description = "查看笔记的细粒度操作日志")
@RestController
@RequestMapping("/note/log")
@RequiredArgsConstructor
@CheckRole
public class OpLogController {

    private final INoteOperationLogService noteOperationLogService;

    @Operation(
            summary = "分页查询笔记操作日志",
            description = """
                    - 用途：查看指定笔记的协同编辑操作日志。
                    - 请求：resourceId 指定笔记资源；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；本接口当前不单独校验资源查看权限。
                    - 处理：按 resourceId 和时间倒序分页读取操作日志；不返回笔记快照，也不修改日志记录。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；日志查询发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回分页操作日志列表和总数。
                    """
    )
    @GetMapping("/getNoteOpLogs")
    public R<PageR<NoteOperationLogResponse>> listNoteOperationLogs(
            @RequestParam String resourceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(noteOperationLogService.listOperationLogs(resourceId, page, size));
    }
}
