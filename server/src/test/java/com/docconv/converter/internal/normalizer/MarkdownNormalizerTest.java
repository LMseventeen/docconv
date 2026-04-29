package com.docconv.converter.internal.normalizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownNormalizerTest {

    private MarkdownNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new MarkdownNormalizer();
    }

    // ========== Phase 1: Encoding Cleanup ==========

    @Nested
    @DisplayName("Phase 1: Encoding Cleanup")
    class EncodingCleanup {

        @Test
        void convertsSmartQuotesToStright() {
            String input = "smart\u201Cquotes\u201D and \u2018single\u2019";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("smart\"quotes\" and 'single'");
        }

        @Test
        void decomposesLigatures() {
            String input = "\uFB01nish \uFB02ow \uFB03nal \uFB04y";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("finish flow ffinal ffly");
        }

        @Test
        void replacesReplacementCharacter() {
            String input = "bad\uFFFDchar";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("bad[?]char");
        }

        @Test
        void convertsNonBreakingSpaces() {
            String input = "hello\u00A0world";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        void stripsBOM() {
            String input = "\uFEFFcontent";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("content");
        }

        @Test
        void convertsTabsToSpaces() {
            String input = "col1\tcol2\tcol3";
            String result = normalizer.phase1EncodingCleanup(input);
            assertThat(result).isEqualTo("col1  col2  col3");
        }
    }

    // ========== Phase 2: Structural Detection ==========

    @Nested
    @DisplayName("Phase 2: Structural Detection (PDF)")
    class StructuralDetection {

        @Test
        void detectsUpperCaseHeading() {
            String input = "\nALL CAPS TITLE\n\nSome text below";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("### ALL CAPS TITLE");
        }

        @Test
        void detectsPartHeading() {
            String input = "\nPart A\n\nQuestions here";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("## Part A");
        }

        @Test
        void detectsQuestionHeading() {
            String input = "\nQuestion 1\n\nWhat is...?";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("#### Question 1");
        }

        @Test
        void detectsSectionHeading() {
            String input = "\nSection 2\n\nContent here";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("### Section 2");
        }

        @Test
        void doesNotDetectLongLineAsHeading() {
            String input = "This is a very long line that should not be detected as a heading because it exceeds one hundred characters in total length by far";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).doesNotContain("#");
        }

        @Test
        void detectsAnswerOptionsPattern() {
            String input = "(A) Paris\n(B) London\n(C) Berlin\n(D) Madrid";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("- (A) Paris");
            assertThat(result).contains("- (B) London");
        }

        @Test
        void skipsAlreadyHeadedLines() {
            String input = "## Already a heading\n\nSome text";
            String result = normalizer.phase2StructuralDetection(input);
            assertThat(result).contains("## Already a heading");
        }
    }

    // ========== Phase 3: Heading Normalization ==========

    @Nested
    @DisplayName("Phase 3: Heading Normalization")
    class HeadingNormalization {

        @Test
        void collapsesHeadingGaps() {
            String input = "# Title\n\n### Sub\n\n##### Detail";
            String result = normalizer.phase3HeadingNormalization(input);
            assertThat(result).contains("# Title");
            assertThat(result).contains("## Sub");
            assertThat(result).contains("### Detail");
        }

        @Test
        void ensuresBlankLineBeforeHeading() {
            String input = "Some text\n## Heading\nMore text";
            String result = normalizer.phase3HeadingNormalization(input);
            assertThat(result).contains("Some text\n\n# Heading");
        }

        @Test
        void ensuresBlankLineAfterHeading() {
            String input = "## Heading\nMore text";
            String result = normalizer.phase3HeadingNormalization(input);
            assertThat(result).contains("# Heading\n\nMore text");
        }

        @Test
        void removesTrailingPunctuationFromHeadings() {
            String input = "# Section 1:\n\nContent";
            String result = normalizer.phase3HeadingNormalization(input);
            assertThat(result).contains("# Section 1");
            assertThat(result).doesNotContain("Section 1:");
        }

        @Test
        void removesDuplicateHeadings() {
            String input = "# Title\n\n# Title\n\nContent";
            String result = normalizer.phase3HeadingNormalization(input);
            long count = result.lines().filter(l -> l.startsWith("# Title")).count();
            assertThat(count).isEqualTo(1);
        }
    }

    // ========== Phase 4: List Normalization ==========

    @Nested
    @DisplayName("Phase 4: List Normalization")
    class ListNormalization {

        @Test
        void normalizesAsteriskBulletsToDash() {
            String input = "* item one\n* item two";
            String result = normalizer.phase4ListNormalization(input);
            assertThat(result).isEqualTo("- item one\n- item two");
        }

        @Test
        void normalizesPlusBulletsToDash() {
            String input = "+ item one\n+ item two";
            String result = normalizer.phase4ListNormalization(input);
            assertThat(result).isEqualTo("- item one\n- item two");
        }

        @Test
        void normalizesOrderedLists() {
            String input = "1) First\n2) Second\n3) Third";
            String result = normalizer.phase4ListNormalization(input);
            assertThat(result).isEqualTo("1. First\n1. Second\n1. Third");
        }

        @Test
        void preservesDashBullets() {
            String input = "- already dash";
            String result = normalizer.phase4ListNormalization(input);
            assertThat(result).isEqualTo("- already dash");
        }

        @Test
        void convertsParenLetterOptions() {
            String input = "(a) Option one\n(b) Option two";
            String result = normalizer.phase4ListNormalization(input);
            assertThat(result).isEqualTo("- (A) Option one\n- (B) Option two");
        }
    }

    // ========== Phase 5: Table Normalization ==========

    @Nested
    @DisplayName("Phase 5: Table Normalization")
    class TableNormalization {

        @Test
        void convertsGridTableToPipe() {
            String input = "+-----+-----+\n| a | b |\n+-----+-----+\n| c | d |\n+-----+-----+";
            String result = normalizer.phase5TableNormalization(input);
            assertThat(result).contains("| a | b |");
            assertThat(result).contains("| c | d |");
            assertThat(result).contains("|--|--|");
        }

        @Test
        void cleansPipeTablePadding() {
            String input = "|  hello  |  world  |\n|---------|---------|\n|  foo  |  bar  |";
            String result = normalizer.phase5TableNormalization(input);
            assertThat(result).contains("| hello | world |");
            assertThat(result).contains("| foo | bar |");
        }
    }

    // ========== Phase 6: Pandoc Artifact Cleanup ==========

    @Nested
    @DisplayName("Phase 6: Pandoc Artifact Cleanup")
    class PandocCleanup {

        @Test
        void removesHtmlComments() {
            String input = "before <!-- comment --> after";
            String result = normalizer.phase6PandocCleanup(input);
            assertThat(result).doesNotContain("<!--");
            assertThat(result).contains("before");
            assertThat(result).contains("after");
        }

        @Test
        void removesPandocClassAttributes() {
            String input = "text {.unstyled} more";
            String result = normalizer.phase6PandocCleanup(input);
            assertThat(result).doesNotContain("{.unstyled}");
        }

        @Test
        void removesAnchorSpans() {
            String input = "text []{#anchor} more";
            String result = normalizer.phase6PandocCleanup(input);
            assertThat(result).doesNotContain("{#anchor}");
        }

        @Test
        void removesImages() {
            String input = "![alt text](image.png)";
            String result = normalizer.phase6PandocCleanup(input);
            assertThat(result).doesNotContain("![");
            assertThat(result).contains("<!-- image removed -->");
        }

        @Test
        void removesNewpage() {
            String input = "text\\newpage\nmore";
            String result = normalizer.phase6PandocCleanup(input);
            assertThat(result).doesNotContain("\\newpage");
        }
    }

    // ========== Phase 7: Whitespace & Flow ==========

    @Nested
    @DisplayName("Phase 7: Whitespace & Flow")
    class WhitespaceAndFlow {

        @Test
        void collapsesMultipleBlankLines() {
            String input = "line1\n\n\n\nline2";
            String result = normalizer.phase7WhitespaceAndFlow(input);
            assertThat(result).isEqualTo("line1\n\nline2");
        }

        @Test
        void removesTrailingWhitespace() {
            String input = "line1   \nline2\t  \n";
            String result = normalizer.phase7WhitespaceAndFlow(input);
            assertThat(result).isEqualTo("line1\nline2");
        }

        @Test
        void removesPageBreakMarkers() {
            String input = "text\n\n---\n\nmore text";
            String result = normalizer.phase7WhitespaceAndFlow(input);
            assertThat(result).doesNotContain("---");
            assertThat(result).contains("text");
            assertThat(result).contains("more text");
        }

        @Test
        void keepsStructuralSeparators() {
            String input = "## Part A\n\n---\n\n## Part B";
            String result = normalizer.phase7WhitespaceAndFlow(input);
            assertThat(result).contains("---");
        }
    }

    // ========== Full Pipeline Integration ==========

    @Nested
    @DisplayName("Full normalization pipeline")
    class FullPipeline {

        @Test
        void pdfSimpleExam() {
            String input = "EXAM TITLE\n\nPart A\n\nQuestion 1\n\nWhat is the capital of France?\n\n(A) Paris\n(B) London\n(C) Berlin\n(D) Madrid";
            String result = normalizer.normalize(input, "pdf");

            assertThat(result).contains("#");
            assertThat(result).contains("Part A");
            assertThat(result).contains("Question 1");
            assertThat(result).contains("(A) Paris");
            assertThat(result).doesNotContain("\uFFFD");
            assertThat(result).doesNotContain("\t");
        }

        @Test
        void docxWithPandocArtifacts() {
            String input = "# Title {.unstyled}\n\nSome text <!-- comment -->\n\n![img](pic.png)\n\n## Section\n\nContent";
            String result = normalizer.normalize(input, "docx");

            assertThat(result).doesNotContain("{.unstyled}");
            assertThat(result).doesNotContain("<!-- comment -->");
            assertThat(result).doesNotContain("![img]");
            assertThat(result).contains("# Title");
            assertThat(result).contains("## Section");
        }

        @Test
        void emptyContentProducesPlaceholder() {
            String result = normalizer.normalize("", "pdf");
            assertThat(result).contains("no extractable text");
        }

        @Test
        void doesNotOverNormalize() {
            String input = "## Question 1\n\nWhat is 2 + 2?\n\n- 3\n- 4\n- 5\n- 6";
            String result = normalizer.normalize(input, "docx");

            assertThat(result).contains("# Question 1");
            assertThat(result).contains("What is 2 + 2?");
            assertThat(result).contains("- 3");
            assertThat(result).contains("- 4");
        }
    }
}
