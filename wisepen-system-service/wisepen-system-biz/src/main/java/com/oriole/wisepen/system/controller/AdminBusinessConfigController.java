package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.system.api.domain.dto.req.BusinessConfigApplyRequest;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigApplyResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigServiceResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigSummaryResponse;
import com.oriole.wisepen.system.service.BusinessConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理员 - 业务配置", description = "管理员查询并应用各微服务的 Nacos 业务配置")
@RestController
@RequestMapping("/system/admin/businessConfig")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminBusinessConfigController {

    private final BusinessConfigService businessConfigService;

    @Operation(
            summary = "查询业务配置目录",
            description = """
                    - 用途：查询当前 System 版本声明的全部可管理业务配置。
                    - 请求：无。
                    - 约束：当前身份必须是平台管理员。
                    - 处理：读取版本化静态目录并返回配置摘要；不读取 Nacos 配置内容。
                    - 失败：无业务失败项。
                    - 响应：返回配置编码、名称、描述、所属微服务及是否需要重启。
                    """
    )
    @GetMapping("/listConfigs")
    public R<List<BusinessConfigSummaryResponse>> listConfigs() {
        return R.ok(businessConfigService.listConfigs());
    }

    @Operation(
            summary = "查询服务业务配置",
            description = """
                    - 用途：一次读取指定微服务的全部受管 Nacos YAML 节点及其 JSON Schema，供管理端生成表单和编辑。
                    - 请求：service 是静态目录中登记的微服务名。
                    - 约束：当前身份必须是平台管理员；微服务目录和 Nacos Data ID 必须存在；目标节点可以不存在。
                    - 处理：只读取并解析一次该服务的完整 Nacos YAML，逐项提取目录节点；缺失节点返回 exists=false 和空 value，不修改配置。
                    - 失败：微服务目录或 Data ID 不存在 -> SysError.BUSINESS_CONFIG_NOT_FOUND；Nacos YAML 无法解析或已有父路径不是对象 -> SysError.BUSINESS_CONFIG_CONTENT_INVALID；Nacos 查询失败 -> SysError.BUSINESS_CONFIG_NACOS_FAILED。
                    - 响应：返回微服务名、完整 Nacos 文档 MD5 revision，以及全部配置项的元数据、是否需要重启、Schema、存在状态和当前 JSON 值。
                    """
    )
    @GetMapping("/listServiceConfigs")
    public R<BusinessConfigServiceResponse> listServiceConfigs(
            @RequestParam @NotBlank String service) {
        return R.ok(businessConfigService.listServiceConfigs(service));
    }

    @Operation(
            summary = "应用业务配置",
            description = """
                    - 用途：将管理端编辑完成的 JSON 节点应用到对应微服务的 Nacos YAML。
                    - 请求：code 是业务配置编码；revision 是查询时取得的完整文档 MD5；value 是符合目录 JSON Schema 的新节点值。
                    - 约束：当前身份必须是平台管理员；value 必须通过 Schema 校验；revision 必须与 Nacos 当前完整文档一致；Nacos Data ID 必须已存在。
                    - 处理：重新读取完整 YAML，自动创建缺失的对象父路径并设置目标节点，规范化整份 YAML 后通过 Nacos CAS 发布；不会覆盖已有的标量或数组父节点，也不确认业务实例已刷新。
                    - 失败：配置编码或 Data ID 不存在 -> SysError.BUSINESS_CONFIG_NOT_FOUND；Schema、YAML 或已有父路径类型无效 -> SysError.BUSINESS_CONFIG_CONTENT_INVALID；文档版本变化或 CAS 失败 -> SysError.BUSINESS_CONFIG_REVISION_CONFLICT；Nacos 查询或发布失败 -> SysError.BUSINESS_CONFIG_NACOS_FAILED。
                    - 响应：返回发布成功标记和规范化后完整文档的新 revision。
                    """
    )
    @Log(title = "应用业务配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/applyConfig")
    public R<BusinessConfigApplyResponse> applyConfig(
            @Valid @RequestBody BusinessConfigApplyRequest request) {
        return R.ok(businessConfigService.applyConfig(request));
    }
}
