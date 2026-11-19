package com.oriole.wisepen.note.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.note.api.domain.base.NoteSnapshotBase;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSearchTextResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteVersionInfoResponse;
import com.oriole.wisepen.resource.enums.ResourceType;

import java.util.List;

public interface INoteVersionService {

    void createVersion(NoteSnapshotBase noteSnapshotMessage, List<Long> authors, ResourceType resourceType);

    NoteSnapshotResponse getLatestVersion(String resourceId);

    NoteSnapshotResponse getVersion(String resourceId, Integer targetVersion);

    NoteSearchTextResponse getSearchText(String resourceId, Integer targetVersion);

    PageR<NoteVersionInfoResponse> listVersions(String resourceId, int page, int size);

    void deleteAllVersionsByResourceIds(List<String> resourceIds);
}
