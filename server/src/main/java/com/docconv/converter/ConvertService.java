package com.docconv.converter;

import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;

/// 文档转换服务接口
///
/// 提供文档格式转换相关的核心业务功能。
public interface ConvertService {

    /// 解析文档，将文档内容转换为 Markdown 格式
    ///
    /// @param document 上传的文档文件
    /// @return 解析结果，包含解析后的 Markdown 内容
    ConvertDocumentParseResult convert(UploadFile document);
}
