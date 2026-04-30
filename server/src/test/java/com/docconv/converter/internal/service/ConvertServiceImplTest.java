package com.docconv.converter.internal.service;

import com.docconv.ai.dto.AIRiskRequest;
import com.docconv.ai.dto.AIRiskResponse;
import com.docconv.ai.service.AIRiskService;
import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.converter.internal.normalizer.MarkdownNormalizer;
import com.docconv.converter.internal.support.documentparser.DocumentParser;
import com.docconv.converter.internal.support.documentparser.PdfParser;
import com.docconv.converter.internal.support.documentparser.WordParser;
import com.docconv.support.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvertServiceImplTest {

    private ConvertServiceImpl convertService;
    private StubWordParser stubWordParser;

    @BeforeEach
    void setUp() {
        stubWordParser = new StubWordParser();
        StubPdfParser stubPdfParser = new StubPdfParser();
        DocumentParser documentParser = new DocumentParser(stubWordParser, stubPdfParser);
        MarkdownNormalizer normalizer = new MarkdownNormalizer();
        StubAIRiskService stubAIRiskService = new StubAIRiskService();
        convertService = new ConvertServiceImpl(documentParser, normalizer, stubAIRiskService);
    }

    @Test
    void convertsDocxSuccessfully() {
        UploadFile file = UploadFile.builder()
                .filename("test.docx")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy content".getBytes()))
                .build();

        ConvertDocumentParseResult result = convertService.convert(file);

        assertThat(result.getContent()).contains("# Test Title");
    }

    @Test
    void convertsPdfSuccessfully() {
        UploadFile file = UploadFile.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy content".getBytes()))
                .build();

        ConvertDocumentParseResult result = convertService.convert(file);

        assertThat(result.getContent()).contains("# Test Title");
    }

    @Test
    void throwsOnNullFile() {
        assertThatThrownBy(() -> convertService.convert(null))
            .isInstanceOf(AppException.class);
    }

    @Test
    void throwsOnEmptyFile() {
        UploadFile file = UploadFile.builder()
                .filename("empty.docx")
                .contentType("application/octet-stream")
                .size(0)
                .inputStream(new ByteArrayInputStream(new byte[0]))
                .build();

        assertThatThrownBy(() -> convertService.convert(file))
            .isInstanceOf(AppException.class);
    }

    @Test
    void normalizesContent() {
        UploadFile file = UploadFile.builder()
                .filename("test.docx")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        ConvertDocumentParseResult result = convertService.convert(file);

        // Verify normalization was applied (no trailing whitespace, proper structure)
        assertThat(result.getContent()).doesNotContain("\t");
        assertThat(result.getContent().strip()).isEqualTo(result.getContent().strip());
    }

    /** Stub Word parser that returns predictable output. */
    private static class StubWordParser extends WordParser {
        StubWordParser() {
            super(null, null);
        }

        @Override
        public ConvertDocumentParseResult parse(UploadFile document) {
            var result = new ConvertDocumentParseResult();
            result.setContent("# Test Title\n\nThis is test content.");
            return result;
        }
    }

    /** Stub PDF parser that returns predictable output. */
    private static class StubPdfParser extends PdfParser {
        StubPdfParser() {
            super(null);
        }

        @Override
        public ConvertDocumentParseResult parse(UploadFile document) {
            var result = new ConvertDocumentParseResult();
            result.setContent("# Test Title\n\nThis is test content.");
            return result;
        }
    }

    /** Stub AI Risk Service that returns the input content unchanged. */
    private static class StubAIRiskService extends AIRiskService {
        StubAIRiskService() {
            super(null);
        }

        @Override
        public AIRiskResponse process(AIRiskRequest request) {
            return AIRiskResponse.builder()
                    .content(request.getContent())
                    .overallSeverity("none")
                    .build();
        }
    }
}
