package com.docconv.converter.internal.support.documentparser;

import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;

/// 文档解析器接口
public interface IDocumentParser {

    /// 解析文档
    ///
    /// @param document 上传的文档文件
    /// @return 解析结果，包含解析后的 Markdown 内容
    ConvertDocumentParseResult parse(UploadFile document);
}
