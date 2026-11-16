package com.oriole.wisepen.questionnaire.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问卷内部接口预留入口，具体 Feign 契约随内部调用场景后续补充。
 */
@Tag(name = "内部 - 问卷", description = "供资源等业务微服务按版本读取问卷信息")
@RestController
@RequestMapping("/internal/questionnaire")
public class InternalQuestionnaireController {
}
