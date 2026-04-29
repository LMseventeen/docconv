package com.docconv.converter.internal.api;

import com.docconv.converter.ConvertService;
import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.ConvertResponse;
import com.docconv.converter.support.kit.MultipartFileKit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/convert")
@RequiredArgsConstructor
@Tag(name = "convert/文档转换", description = "文档格式转换相关接口")
public class ConvertController {

    private final ConvertService convertService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "转换文档", description = "上传文档文件，解析并转换为 Markdown 格式。支持 .docx 和 .pdf 格式。")
    public ConvertResponse convert(@RequestParam("file") MultipartFile file) {
        log.info("Received upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        var uploadFile = MultipartFileKit.toUploadFile(file);
        ConvertDocumentParseResult result = convertService.convert(uploadFile);

        return ConvertResponse.builder()
                .markdown(result.getContent())
                .originalFilename(file.getOriginalFilename())
                .build();
    }
}
