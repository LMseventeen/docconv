package com.docconv.converter.internal.support.documentparser;

import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.support.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserTest {

    private DocumentParser documentParser;

    @BeforeEach
    void setUp() {
        WordParser wordParser = new StubWordParser();
        PdfParser pdfParser = new StubPdfParser();
        documentParser = new DocumentParser(wordParser, pdfParser);
    }

    @Test
    void resolvesDocxByExtension() {
        UploadFile file = UploadFile.builder()
                .filename("exam.docx")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        ConvertDocumentParseResult result = documentParser.parse(file);
        assertThat(result.getContent()).contains("word parsed");
    }

    @Test
    void resolvesPdfByExtension() {
        UploadFile file = UploadFile.builder()
                .filename("exam.pdf")
                .contentType("application/pdf")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        ConvertDocumentParseResult result = documentParser.parse(file);
        assertThat(result.getContent()).contains("pdf parsed");
    }

    @Test
    void resolvesCaseInsensitive() {
        UploadFile file = UploadFile.builder()
                .filename("exam.PDF")
                .contentType("application/pdf")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        ConvertDocumentParseResult result = documentParser.parse(file);
        assertThat(result.getContent()).contains("pdf parsed");
    }

    @Test
    void throwsOnUnsupportedExtension() {
        UploadFile file = UploadFile.builder()
                .filename("exam.xlsx")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        assertThatThrownBy(() -> documentParser.parse(file))
            .isInstanceOf(AppException.class);
    }

    @Test
    void throwsOnNoExtension() {
        UploadFile file = UploadFile.builder()
                .filename("exam")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        assertThatThrownBy(() -> documentParser.parse(file))
            .isInstanceOf(AppException.class);
    }

    @Test
    void throwsOnNullFilename() {
        UploadFile file = UploadFile.builder()
                .filename(null)
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        assertThatThrownBy(() -> documentParser.parse(file))
            .isInstanceOf(AppException.class);
    }

    @Test
    void throwsOnEmptyFilename() {
        UploadFile file = UploadFile.builder()
                .filename("")
                .contentType("application/octet-stream")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        assertThatThrownBy(() -> documentParser.parse(file))
            .isInstanceOf(AppException.class);
    }

    @Test
    void handlesDoubleExtension() {
        UploadFile file = UploadFile.builder()
                .filename("exam.docx.pdf")
                .contentType("application/pdf")
                .size(100)
                .inputStream(new ByteArrayInputStream("dummy".getBytes()))
                .build();

        ConvertDocumentParseResult result = documentParser.parse(file);
        assertThat(result.getContent()).contains("pdf parsed");
    }

    @Test
    void extractExtensionHandlesEdgeCases() {
        assertThat(DocumentParser.extractExtension("file.pdf")).isEqualTo("pdf");
        assertThat(DocumentParser.extractExtension("file.PDF")).isEqualTo("pdf");
        assertThat(DocumentParser.extractExtension("path/to/file.docx")).isEqualTo("docx");
    }

    /** Stub Word parser for testing. */
    private static class StubWordParser extends WordParser {
        StubWordParser() {
            super(null, null);
        }

        @Override
        public ConvertDocumentParseResult parse(UploadFile document) {
            var result = new ConvertDocumentParseResult();
            result.setContent("# word parsed\n\nStub content.");
            return result;
        }
    }

    /** Stub PDF parser for testing. */
    private static class StubPdfParser extends PdfParser {
        StubPdfParser() {
            super(null);
        }

        @Override
        public ConvertDocumentParseResult parse(UploadFile document) {
            var result = new ConvertDocumentParseResult();
            result.setContent("# pdf parsed\n\nStub content.");
            return result;
        }
    }
}
