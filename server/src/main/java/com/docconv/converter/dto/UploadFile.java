package com.docconv.converter.dto;

import lombok.*;

import java.io.InputStream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadFile {
    private String filename;
    private String contentType;
    private long size;
    private InputStream inputStream;
}
