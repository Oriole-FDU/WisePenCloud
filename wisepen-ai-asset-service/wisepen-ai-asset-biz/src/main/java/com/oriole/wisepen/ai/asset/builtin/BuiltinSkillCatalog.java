package com.oriole.wisepen.ai.asset.builtin;

import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.res.AIResourceMetaInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.enums.AIResourceSourceType;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class BuiltinSkillCatalog {
    public static final String NOTE_AI_DIFF_SKILL_ID = "wisepen-note-ai-diff";

    private static final String NOTE_AI_DIFF_NAME = "WisePen Note AI-Diff";
    private static final String NOTE_AI_DIFF_DESCRIPTION =
            "Strict workflow for editing the current WisePen note through AI-Diff tools.";
    private static final int NOTE_AI_DIFF_VERSION = 1;
    private static final String NOTE_AI_DIFF_SKILL_MD = """
            # WisePen Note AI-Diff

            Use this skill when the user asks to edit, polish, translate, shorten, expand,
            correct, restructure, or otherwise modify the currently opened WisePen note.

            ## Required workflow
            1. Call `read_note_aixml` before every note edit.
            2. If the application context includes `selected_text`, call
               `read_note_aixml` with `scope: "selected_note_scope"` first unless the
               user asks for broader whole-note context. This scope gives you the
               containing block context and may include multiple blocks. The selected
               text may be either the exact edit boundary or only the user's focus cue;
               infer which from the user's words.
            3. Use only ids that appear in the latest `<ai_xml>` returned by the tool.
            4. Build a strict AI-Diff JSON plan.
            5. Call `apply_current_note_ai_diff_plan` with the exact `export_handle`
               returned by `read_note_aixml`.
            6. After apply succeeds, briefly tell the user what was proposed. If the
               apply result contains conflicts or skipped operations, mention them.

            ## Selection and scope guidance
            `selected_text` is a focus signal from the application, not automatically a
            hard boundary. Decide the edit scope from the user's actual wording.

            - If the user explicitly says to only modify the selected text, keep other
              text unchanged, replace just this phrase/sentence, or equivalent, treat
              `selected_text` as an exact edit boundary.
            - If the user asks for a broader rewrite, continuation, structural edit,
              consistency pass, or improvement that naturally involves surrounding text,
              you may edit outside `selected_text` within the requested scope.
            - When the user's intended scope is ambiguous, prefer the smaller edit and
              state the scope you applied.
            - Do not edit a whole block merely because the selected text is inside that
               block; edit the whole block only when the user's request calls for it.
            - A selection may span multiple blocks or multiple `<text>` targets. This is
              allowed. Use multiple operations, one for each affected target, instead of
              saying the edit cannot be done.
            - Do not use `add_block` or `delete_block` for selected-text-only requests
               unless the user explicitly asks to add or delete whole blocks.
            - Prefer the smallest target operation that can express the user's request.

            In exact-boundary mode, remember that `replace_text` replaces the entire
            `<text>` target. If `selected_text` is only part of that target, the
            operation's `text` value must be the full current target text with only the
            selected span replaced. Do not keep the original selected span next to its
            translation, rewrite, or correction:

            ```text
            original target text = prefix + selected_text + suffix
            replacement result   = prefix + transformed_selected_text + suffix
            ```

            Example: if `<text id="b1:t1">AAA 中文 BBB</text>` and `selected_text` is
            `中文`, translating only the selection to English must produce:

            ```json
            { "opId": "op-1", "kind": "replace_text", "target": "b1:t1", "text": "AAA Chinese BBB" }
            ```

            not `"Chinese"`, and not a rewritten version of `AAA` or `BBB`.
            Also never produce `"AAA 中文 Chinese BBB"` for a translate-selected-text
            request; the original selected span must appear zero times inside the
            replaced span unless the user explicitly asks to keep it.
            Also never produce `"AAA Chinese BBB Chinese"` or `"AAA Chinese Chinese BBB"`;
            the transformed selected span must appear exactly once inside the affected
            target unless the user explicitly asks for repetition.

            If the selected text spans multiple targets, split the work across those
            targets. Preserve the unselected prefix in the first target and the
            unselected suffix in the last target. Fully selected middle targets may be
            replaced by their transformed content. Example:

            ```json
            [
              { "opId": "op-1", "kind": "replace_text", "target": "b1:t2", "text": "prefix translated first part" },
              { "opId": "op-2", "kind": "replace_text", "target": "b2:t1", "text": "translated second part suffix" }
            ]
            ```

            In exact-boundary mode, do not say you cannot edit merely because the
            selection crosses blocks. Ask the user to reselect only if the relevant
            selected fragments cannot be located anywhere in the latest `<ai_xml>`.

            ## Hard rules
            - Never submit `review_suggestions`.
            - Never use operation fields named `op`, `items`, `target_id`, `targetId`,
              `type`, `before`, `after`, or `explanation`.
            - Never invent block ids, text ids, links, math ids, hashes, paths, styles,
              content indexes, BlockNote JSON, or Yjs paths.
            - Do not call apply before reading the note.
            - The apply tool writes review suggestions. It does not directly accept or
              permanently rewrite final text.

            ## Formula operations
            Formulas are first-class note content. Do not represent formulas as plain
            text unless the user explicitly asks for plain text.

            - Inline formulas appear as `<inline-math id="...">...</inline-math>`.
              Modify them with `replace_inline_math`, insert them with
              `add_inline_math`, and delete them with `delete_target`.
            - Formula blocks appear as `<math-expression id="...">...</math-expression>`
              inside a `type="math"` block. Modify them with
              `replace_math_expression`.
            - To add a formula block, use `add_block` with `blockType: "math"` and an
              `expression` field.
            - To delete a formula block, use `delete_block` on the containing block id,
              not `delete_target` on the expression id.

            ## Plan schema
            The top-level plan must be exactly:

            ```json
            {
              "version": 1,
              "operations": []
            }
            ```

            Every operation must have:
            - `opId`: unique non-empty string, for example `op-1`
            - `kind`: one of `replace_text`, `replace_link`, `replace_inline_math`,
              `replace_math_expression`, `add_text`, `add_link`, `add_inline_math`,
              `add_block`, `delete_target`, `delete_block`

            Valid operation shapes:

            ```json
            { "opId": "op-1", "kind": "replace_text", "target": "b1:t1", "text": "New text" }
            { "opId": "op-2", "kind": "replace_link", "target": "b1:l1", "text": "OpenAI", "href": "https://openai.com" }
            { "opId": "op-3", "kind": "replace_inline_math", "target": "b1:m1", "expression": "x^2" }
            { "opId": "op-4", "kind": "replace_math_expression", "target": "b2:expr", "expression": "E=mc^2" }
            { "opId": "op-5", "kind": "add_text", "anchor": "b1:t1", "position": "after", "text": " more text" }
            { "opId": "op-6", "kind": "add_link", "anchor": "b1:t1", "position": "after", "text": "source", "href": "https://example.com" }
            { "opId": "op-7", "kind": "add_inline_math", "anchor": "b1:t1", "position": "after", "expression": "a+b" }
            { "opId": "op-8", "kind": "add_block", "anchor": "b1", "position": "after", "blockType": "paragraph", "text": "New paragraph" }
            { "opId": "op-9", "kind": "add_block", "anchor": "b1", "position": "after", "blockType": "math", "expression": "E=mc^2" }
            { "opId": "op-10", "kind": "delete_target", "target": "b1:t1" }
            { "opId": "op-11", "kind": "delete_block", "target": "b1" }
            ```

            `position` must be `before` or `after`. `blockType` must be one of
            `paragraph`, `heading`, `quote`, `bulletListItem`, `numberedListItem`, or
            `math`. URLs must be absolute `http` or `https` URLs.

            ## Error handling
            - If apply returns `invalid_ai_diff_plan`, fix the JSON shape using this
              skill and retry once with the same latest XML.
            - If apply returns `export_handle_expired` or `export_handle_mismatch`,
              call `read_note_aixml` again and rebuild the plan from the new
              XML.
            - If targets are missing or stale, read again before retrying.
            """;

    public Optional<SkillInfoResponse> findSkill(String resourceId) {
        if (!NOTE_AI_DIFF_SKILL_ID.equals(resourceId)) {
            return Optional.empty();
        }
        return Optional.of(buildNoteAiDiffSkill());
    }

    public List<AIResourceMetaInfoResponse> listMetas(List<String> resourceIds) {
        if (resourceIds == null || !resourceIds.contains(NOTE_AI_DIFF_SKILL_ID)) {
            return List.of();
        }
        return List.of(buildNoteAiDiffMeta());
    }

    private SkillInfoResponse buildNoteAiDiffSkill() {
        SkillInfoResponse response = new SkillInfoResponse();
        response.setResourceId(NOTE_AI_DIFF_SKILL_ID);
        response.setName(NOTE_AI_DIFF_NAME);
        response.setDescription(NOTE_AI_DIFF_DESCRIPTION);
        response.setVersion(NOTE_AI_DIFF_VERSION);
        response.setSourceType(AIResourceSourceType.MANUAL);
        response.setSkillVersionBundle(buildNoteAiDiffBundle());
        return response;
    }

    private AIResourceMetaInfoResponse buildNoteAiDiffMeta() {
        AIResourceMetaInfoResponse response = new AIResourceMetaInfoResponse();
        response.setResourceId(NOTE_AI_DIFF_SKILL_ID);
        response.setName(NOTE_AI_DIFF_NAME);
        response.setDescription(NOTE_AI_DIFF_DESCRIPTION);
        response.setVersion(NOTE_AI_DIFF_VERSION);
        response.setSourceType(AIResourceSourceType.MANUAL);
        return response;
    }

    private SkillVersionBundleInfoResponse buildNoteAiDiffBundle() {
        SkillVersionBundleInfoResponse response = new SkillVersionBundleInfoResponse();
        response.setResourceId(NOTE_AI_DIFF_SKILL_ID);
        response.setVersion(NOTE_AI_DIFF_VERSION);
        response.setStatus(VersionStatus.PUBLISHED);
        response.setSkillMd(NOTE_AI_DIFF_SKILL_MD);
        response.setAssets(List.of(AssetInfoBase.builder()
                .id("builtin-skill-md")
                .name("SKILL.md")
                .path("/")
                .objectKey("builtin://wisepen-note-ai-diff/SKILL.md")
                .assetResourceType(AssetResourceType.MD)
                .uploadStatus(AssetUploadStatus.AVAILABLE)
                .size((long) NOTE_AI_DIFF_SKILL_MD.getBytes(StandardCharsets.UTF_8).length)
                .build()));
        return response;
    }
}
