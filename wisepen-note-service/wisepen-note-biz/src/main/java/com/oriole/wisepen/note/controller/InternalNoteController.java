package com.oriole.wisepen.note.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSearchTextResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.feign.RemoteNoteService;
import com.oriole.wisepen.note.service.INoteVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "内部 - 笔记", description = "供 Node.js 协同服务和业务微服务读取笔记快照")
@RestController
@RequestMapping("/internal/note")
@RequiredArgsConstructor
public class InternalNoteController implements RemoteNoteService {

    private final INoteVersionService noteVersionService;

    @Operation(
            summary = "内部获取最新笔记快照",
            description = """
                    - 用途：供协同服务拉取笔记最新完整快照和后续增量版本，用于恢复协同编辑状态。
                    - 请求：resourceId 指定笔记资源。
                    - 约束：调用方必须通过内部服务调用边界；目标笔记必须存在。
                    - 处理：读取最近一个 FULL 快照，并追加其后的 DELTA 版本；如果没有 FULL 快照则返回空 fullSnapshot 和当前版本 0；不生成新版本。
                    - 失败：笔记不存在 -> NoteError.NOTE_NOT_FOUND；版本读取发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回资源 ID、最新版本号、完整快照和增量快照列表。
                    """
    )
    @GetMapping("/getNoteLatestVersion")
    @Override
    public R<NoteSnapshotResponse> getNoteLatestVersion(@RequestParam("resourceId") String resourceId) {
        return R.ok(noteVersionService.getLatestVersion(resourceId));
    }

    @GetMapping("/getNoteSearchText")
    @Override
    public R<NoteSearchTextResponse> getNoteSearchText(@RequestParam("resourceId") String resourceId,
                                                       @RequestParam(value = "version", required = false) Integer version) {
        return R.ok(noteVersionService.getSearchText(resourceId, version));
    }
}
