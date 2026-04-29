package com.docconv.converter.workflow.internal.api;

import com.docconv.converter.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 工作流管理控制器
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "workflow/工作流管理", description = "工作流管理相关接口")
public class WorkflowController {

    private final WorkflowService workflowService;

}
