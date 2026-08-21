package com.oriole.wisepen.note.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSearchTextResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(contextId = "remoteNoteService", value = "wisepen-note-service")
public interface RemoteNoteService {

    @GetMapping("/internal/note/getNoteLatestVersion")
    R<NoteSnapshotResponse> getNoteLatestVersion(@RequestParam("resourceId") String resourceId);

    @GetMapping("/internal/note/getNoteSearchText")
    R<NoteSearchTextResponse> getNoteSearchText(@RequestParam("resourceId") String resourceId,
                                                @RequestParam(value = "version", required = false) Integer version);
}
