package com.docconv.converter.support.kit;

import com.docconv.converter.dto.UploadFile;
import com.docconv.converter.support.exception.Errors;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class MultipartFileKit {

    public static UploadFile toUploadFile(MultipartFile multipartFile) {
        try {
            return UploadFile.builder()
                    .filename(multipartFile.getOriginalFilename())
                    .contentType(multipartFile.getContentType())
                    .size(multipartFile.getSize())
                    .inputStream(multipartFile.getInputStream())
                    .build();
        } catch (IOException e) {
            throw Errors.INTERNAL_ERROR.toException(e, "无法读取上传文件: {}", e.getMessage());
        }
    }
}
