package com.docconv.converter.internal.normalizer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 8-phase normalization pipeline that transforms raw parser output
 * into clean, consistent Markdown.
 *
 * Phases:
 * 1. Encoding Cleanup
 * 2. Structural Detection (PDF only)
 * 3. Heading Normalization
 * 4. List Normalization
 * 5. Table Normalization
 * 6. Pandoc Artifact Cleanup
 * 7. Whitespace & Flow
 * 8. Final Validation
 */
@Component
public class MarkdownNormalizer {

    // ---- Phase 2: Heading detection patterns ----
    private static final Pattern HEADING_PART_PATTERN =
        Pattern.compile("^Part\\s+([A-Z]|I{1,3}V?|V?I{0,3})\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING_SECTION_PATTERN =
        Pattern.compile("^Section\\s+\\d+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING_QUESTION_PATTERN =
        Pattern.compile("^(Question|Q\\.?)\\s*\\d+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING_INSTRUCTIONS_PATTERN =
        Pattern.compile("^Instructions?\\s*:?.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING_ANSWER_KEY_PATTERN =
        Pattern.compile("^(Answer\\s+Key|Marking\\s+Scheme)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERING_PATTERN =
        Pattern.compile("^(\\d+\\.|\\d+\\)|I\\.|I\\)|IV\\.|IV\\)|V\\.|V\\)).*");

    // ---- Phase 2: List detection patterns ----
    private static final Pattern BULLET_LINE =
        Pattern.compile("^(?:\\s*)([\\u2022\\u25CB\\u25CF\\u2023\\u25AA\\-\\*\\+])\\s+(.*)");
    private static final Pattern ORDERED_LIST_LINE =
        Pattern.compile("^(?:\\s*)(\\d+)[)\\.]\\s+(.*)");
    private static final Pattern PAREN_LETTER_LINE =
        Pattern.compile("^(?:\\s*)\\(([a-zA-Z])\\)\\s+(.*)");
    private static final Pattern ANSWER_OPTION_LINE =
        Pattern.compile("^(?:\\s*)\\(?([A-D])\\)?[.):]?\\s+(.*)");

    // ---- Phase 5: Grid table detection ----
    private static final Pattern GRID_TABLE_BORDER =
        Pattern.compile("^\\+[\\-=+]+\\+$");
    private static final Pattern PIPE_TABLE_LINE =
        Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern PIPE_SEPARATOR_LINE =
        Pattern.compile("^\\s*\\|[\\s\\-:|]+\\|\\s*$");

    // ---- Phase 6: Pandoc artifacts ----
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PANDOC_CLASS_ATTR = Pattern.compile("\\s*\\{\\.[\\w\\-]+(?:\\s+\\.[\\w\\-]+)*\\}");
    private static final Pattern PANDOC_ANCHOR_SPAN = Pattern.compile("\\[\\]\\{#[\\w\\-]+\\}");
    private static final Pattern PANDOC_IMAGE = Pattern.compile("!\\[.*?\\]\\(.*?\\)");
    private static final Pattern PANDOC_NEWPAGE = Pattern.compile("\\\\newpage");
    private static final Pattern RAW_HTML_BLOCK =
        Pattern.compile("</?(?:div|span|table|tr|td|th|tbody|thead|p|br)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNNECESSARY_ESCAPE = Pattern.compile("\\\\([^\\w\\s*#\\[\\]()`{}>~_+\\-=!|\\\\])");

    // ---- Phase 7: Whitespace ----
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    // ---- Phase 1: Encoding ----
    private static final Pattern LIGATURE_FI = Pattern.compile("\\uFB01");
    private static final Pattern LIGATURE_FL = Pattern.compile("\\uFB02");
    private static final Pattern LIGATURE_FFI = Pattern.compile("\\uFB03");
    private static final Pattern LIGATURE_FFL = Pattern.compile("\\uFB04");
    private static final Pattern SMART_OPEN_QUOTE = Pattern.compile("\\u201C");
    private static final Pattern SMART_CLOSE_QUOTE = Pattern.compile("\\u201D");
    private static final Pattern SMART_SINGLE_OPEN = Pattern.compile("\\u2018");
    private static final Pattern SMART_SINGLE_CLOSE = Pattern.compile("\\u2019");
    private static final Pattern NON_BREAKING_SPACE = Pattern.compile("\\u00A0");
    private static final Pattern BOM = Pattern.compile("\\uFEFF");
    private static final Pattern REPLACEMENT_CHAR = Pattern.compile("\\uFFFD");

    public String normalize(String content, String sourceFormat) {
        String text = content;
        boolean isPdf = "pdf".equals(sourceFormat);

        // Phase 1: Encoding Cleanup
        text = phase1EncodingCleanup(text);

        // Phase 2: Structural Detection (PDF only)
        if (isPdf) {
            text = phase2StructuralDetection(text);
        }

        // Phase 3: Heading Normalization
        text = phase3HeadingNormalization(text);

        // Phase 4: List Normalization
        text = phase4ListNormalization(text);

        // Phase 5: Table Normalization
        text = phase5TableNormalization(text);

        // Phase 6: Pandoc Artifact Cleanup (Word/Pandoc source)
        if (!isPdf) {
            text = phase6PandocCleanup(text);
        }

        // Phase 7: Whitespace & Flow
        text = phase7WhitespaceAndFlow(text);

        // Phase 8: Final Validation
        text = phase8FinalValidation(text);

        return text;
    }

    // ========== PHASE 1: Encoding Cleanup ==========

    String phase1EncodingCleanup(String text) {
        // Strip BOM
        text = BOM.matcher(text).replaceAll("");

        // Decompose typographic ligatures
        text = LIGATURE_FFI.matcher(text).replaceAll("ffi");
        text = LIGATURE_FFL.matcher(text).replaceAll("ffl");
        text = LIGATURE_FI.matcher(text).replaceAll("fi");
        text = LIGATURE_FL.matcher(text).replaceAll("fl");

        // Replace replacement character with visible marker
        text = REPLACEMENT_CHAR.matcher(text).replaceAll("[?]");

        // Convert non-breaking spaces to regular spaces
        text = NON_BREAKING_SPACE.matcher(text).replaceAll(" ");

        // Convert smart quotes to straight quotes
        text = SMART_OPEN_QUOTE.matcher(text).replaceAll("\"");
        text = SMART_CLOSE_QUOTE.matcher(text).replaceAll("\"");
        text = SMART_SINGLE_OPEN.matcher(text).replaceAll("'");
        text = SMART_SINGLE_CLOSE.matcher(text).replaceAll("'");

        // Convert tabs to 2 spaces
        text = text.replace("\t", "  ");

        // Decode common HTML entities and remove HTML tags
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&quot;", "\"");

        return text;
    }

    // ========== PHASE 2: Structural Detection (PDF) ==========

    String phase2StructuralDetection(String text) {
        String[] lines = text.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();

            // Skip blank lines and page separators
            if (stripped.isEmpty() || stripped.equals("---")) {
                result.add(line);
                continue;
            }

            // Skip lines that already have Markdown heading prefix
            if (stripped.startsWith("#")) {
                result.add(line);
                continue;
            }

            // Check if this line should be a heading
            if (detectHeading(lines, i, stripped)) {
                String level = assignHeadingLevel(stripped);
                result.add(level + " " + stripped);
            }
            // Check if this is an answer option pattern: (A) text, (B) text, etc.
            else if (ANSWER_OPTION_LINE.matcher(stripped).matches()) {
                Matcher m = ANSWER_OPTION_LINE.matcher(stripped);
                if (m.matches()) {
                    result.add("- (" + m.group(1) + ") " + m.group(2));
                }
            }
            // Check for letter-labeled list: A) text, B) text, etc. (3+ consecutive)
            else if (isLetterListPattern(lines, i, stripped)) {
                String label = stripped.substring(0, stripped.indexOf(')') > 0 ? stripped.indexOf(')') + 1 : 2);
                String content = stripped.substring(label.length()).strip();
                result.add("- " + label + " " + content);
            }
            else {
                result.add(line);
            }
        }

        return String.join("\n", result);
    }

    private boolean detectHeading(String[] lines, int index, String stripped) {
        // Negative heuristics first
        if (stripped.length() > 100) return false;
        if (stripped.endsWith(",") || stripped.endsWith(";") || stripped.endsWith(" and") ||
            stripped.endsWith(" or") || stripped.endsWith(" but")) return false;
        if (BULLET_LINE.matcher(stripped).matches()) return false;
        if (ORDERED_LIST_LINE.matcher(stripped).matches()) return false;

        int signals = 0;

        // Signal 1: Structural position
        boolean precededByBlank = (index == 0) || lines[index - 1].strip().isEmpty();
        boolean followedByBlank = (index >= lines.length - 1) || lines[index + 1].strip().isEmpty();
        if (precededByBlank && followedByBlank) signals++;

        // Signal 2: Typographic convention
        boolean allCaps = stripped.length() >= 3 && stripped.equals(stripped.toUpperCase()) &&
                          stripped.chars().anyMatch(Character::isLetter);
        if (allCaps) signals++;

        boolean titleCase = isTitleCase(stripped);
        if (titleCase && stripped.length() < 80 && !stripped.endsWith(".")) signals++;

        if (NUMBERING_PATTERN.matcher(stripped).matches()) signals++;

        // Signal 3: Content patterns
        if (HEADING_PART_PATTERN.matcher(stripped).matches()) signals += 2;
        if (HEADING_SECTION_PATTERN.matcher(stripped).matches()) signals += 2;
        if (HEADING_QUESTION_PATTERN.matcher(stripped).matches()) signals += 2;
        if (HEADING_INSTRUCTIONS_PATTERN.matcher(stripped).matches()) signals += 2;
        if (HEADING_ANSWER_KEY_PATTERN.matcher(stripped).matches()) signals += 2;

        // Require at least 2 signals
        return signals >= 2;
    }

    private boolean isTitleCase(String text) {
        String[] words = text.split("\\s+");
        if (words.length < 2) return false;
        int titleCaseCount = 0;
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (Character.isUpperCase(word.charAt(0))) titleCaseCount++;
        }
        return titleCaseCount >= words.length * 0.7;
    }

    private String assignHeadingLevel(String text) {
        if (HEADING_PART_PATTERN.matcher(text).matches()) return "##";
        if (HEADING_SECTION_PATTERN.matcher(text).matches()) return "###";
        if (HEADING_QUESTION_PATTERN.matcher(text).matches()) return "####";
        if (HEADING_INSTRUCTIONS_PATTERN.matcher(text).matches()) return "###";
        if (HEADING_ANSWER_KEY_PATTERN.matcher(text).matches()) return "##";
        if (NUMBERING_PATTERN.matcher(text).matches()) return "###";
        return "###"; // default for detected headings
    }

    private boolean isLetterListPattern(String[] lines, int index, String stripped) {
        if (!Pattern.matches("^[A-Z]\\)\\s+.*", stripped)) return false;
        // Look for 2+ more consecutive letter options below
        int count = 1;
        for (int j = index + 1; j < lines.length && j < index + 6; j++) {
            if (Pattern.matches("^[A-Z]\\)\\s+.*", lines[j].strip())) {
                count++;
            } else if (!lines[j].strip().isEmpty()) {
                break;
            }
        }
        return count >= 3;
    }

    // ========== PHASE 3: Heading Normalization ==========

    String phase3HeadingNormalization(String text) {
        String[] lines = text.split("\n", -1);
        List<String> result = new ArrayList<>();

        // First pass: collect heading levels used and build heading info
        List<HeadingInfo> headings = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int level = getHeadingLevel(line);
            if (level > 0) {
                String content = line.substring(level).strip();
                headings.add(new HeadingInfo(i, level, content));
            }
        }

        if (headings.isEmpty()) return text;

        // Ensure single H1: promote first heading to H1 if none exists at level 1
        boolean hasH1 = headings.stream().anyMatch(h -> h.level == 1);
        if (!hasH1 && !headings.isEmpty()) {
            HeadingInfo first = headings.get(0);
            headings.set(0, new HeadingInfo(first.lineIndex, 1, first.content));
        }

        // Collapse heading gaps: collect levels used, remap to contiguous
        Set<Integer> usedLevels = new LinkedHashSet<>();
        for (HeadingInfo h : headings) {
            usedLevels.add(h.level);
        }
        List<Integer> sortedLevels = new ArrayList<>(usedLevels);
        Map<Integer, Integer> levelMap = new HashMap<>();
        for (int i = 0; i < sortedLevels.size(); i++) {
            levelMap.put(sortedLevels.get(i), i + 1);
        }

        // Apply remapping
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo h = headings.get(i);
            int newLevel = levelMap.getOrDefault(h.level, h.level);
            headings.set(i, new HeadingInfo(h.lineIndex, newLevel, h.content));
        }

        // Trim trailing punctuation from headings
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo h = headings.get(i);
            String content = h.content;
            content = content.replaceAll("[:\\-\\.]+$", "").strip();
            headings.set(i, new HeadingInfo(h.lineIndex, h.level, content));
        }

        // Remove duplicate consecutive headings
        List<HeadingInfo> deduped = new ArrayList<>();
        for (HeadingInfo h : headings) {
            if (deduped.isEmpty() || !deduped.get(deduped.size() - 1).content.equalsIgnoreCase(h.content)) {
                deduped.add(h);
            }
        }

        // Build output: replace heading lines, ensure blank lines around headings
        Map<Integer, HeadingInfo> headingByLine = new HashMap<>();
        for (HeadingInfo h : deduped) {
            headingByLine.put(h.lineIndex, h);
        }

        for (int i = 0; i < lines.length; i++) {
            HeadingInfo h = headingByLine.get(i);
            if (h != null) {
                String prefix = "#".repeat(h.level) + " ";
                // Ensure blank line before heading
                if (!result.isEmpty() && !result.get(result.size() - 1).isBlank()) {
                    result.add("");
                }
                result.add(prefix + h.content);
                // Ensure blank line after heading (will be cleaned in phase 7)
                result.add("");
            } else {
                // Skip lines that were headings in the original but got deduped
                int originalLevel = getHeadingLevel(lines[i]);
                if (originalLevel > 0) {
                    String originalContent = lines[i].substring(originalLevel).strip();
                    final int lineIdx = i;
                    boolean isDuplicate = deduped.stream()
                        .anyMatch(d -> d.content.equalsIgnoreCase(originalContent) && d.lineIndex != lineIdx);
                    if (isDuplicate) continue;
                }
                result.add(lines[i]);
            }
        }

        return String.join("\n", result);
    }

    private int getHeadingLevel(String line) {
        // Must check longest prefix first — ##### starts with ####
        if (line.startsWith("######")) return 6;
        if (line.startsWith("#####")) return 5;
        if (line.startsWith("####")) return 4;
        if (line.startsWith("###")) return 3;
        if (line.startsWith("##")) return 2;
        if (line.startsWith("#")) return 1;
        return 0;
    }

    private record HeadingInfo(int lineIndex, int level, String content) {}

    // ========== PHASE 4: List Normalization ==========

    String phase4ListNormalization(String text) {
        String[] lines = text.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (String line : lines) {
            String stripped = line.strip();

            // Normalize unordered list bullets to -
            Matcher bulletMatcher = BULLET_LINE.matcher(stripped);
            if (bulletMatcher.matches()) {
                String content = bulletMatcher.group(2);
                result.add("- " + content);
                continue;
            }

            // Normalize ordered lists to 1.
            Matcher orderedMatcher = ORDERED_LIST_LINE.matcher(stripped);
            if (orderedMatcher.matches()) {
                String content = orderedMatcher.group(2);
                result.add("1. " + content);
                continue;
            }

            // Convert (A), (B), etc. answer options to list items
            Matcher parenMatcher = PAREN_LETTER_LINE.matcher(stripped);
            if (parenMatcher.matches()) {
                String letter = parenMatcher.group(1);
                String content = parenMatcher.group(2);
                result.add("- (" + letter.toUpperCase() + ") " + content);
                continue;
            }

            result.add(line);
        }

        return String.join("\n", result);
    }

    // ========== PHASE 5: Table Normalization ==========

    String phase5TableNormalization(String text) {
        String[] lines = text.split("\n", -1);
        List<String> result = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            // Detect grid table
            if (GRID_TABLE_BORDER.matcher(lines[i].strip()).matches() && i + 2 < lines.length) {
                List<String> gridBlock = new ArrayList<>();
                while (i < lines.length && (GRID_TABLE_BORDER.matcher(lines[i].strip()).matches()
                        || lines[i].strip().startsWith("|"))) {
                    gridBlock.add(lines[i]);
                    i++;
                }
                result.addAll(convertGridTableToPipe(gridBlock));
                continue;
            }

            // Detect pipe table and clean padding
            if (PIPE_TABLE_LINE.matcher(lines[i].strip()).matches()) {
                List<String> tableBlock = new ArrayList<>();
                while (i < lines.length && PIPE_TABLE_LINE.matcher(lines[i].strip()).matches()) {
                    tableBlock.add(lines[i]);
                    i++;
                }
                result.addAll(cleanPipeTable(tableBlock));
                continue;
            }

            // Detect tabular data in plain text (PDF fallback)
            if (isTabularPattern(lines, i)) {
                result.add("<!-- Table: could not be converted to Markdown table. Preserved as text. -->");
                result.add("```");
                while (i < lines.length && !lines[i].strip().isEmpty()) {
                    result.add(lines[i]);
                    i++;
                }
                result.add("```");
                continue;
            }

            result.add(lines[i]);
            i++;
        }

        return String.join("\n", result);
    }

    private List<String> convertGridTableToPipe(List<String> gridBlock) {
        List<String> result = new ArrayList<>();
        for (String line : gridBlock) {
            if (GRID_TABLE_BORDER.matcher(line.strip()).matches()) {
                continue;
            }
            String stripped = line.strip();
            if (stripped.startsWith("|") && stripped.endsWith("|")) {
                String content = stripped.substring(1, stripped.length() - 1);
                String[] cells = content.split("\\|");
                StringBuilder pipeRow = new StringBuilder("|");
                for (String cell : cells) {
                    pipeRow.append(" ").append(cell.strip()).append(" |");
                }
                result.add(pipeRow.toString());
            }
        }

        if (!result.isEmpty()) {
            String firstRow = result.get(0);
            int colCount = (int) firstRow.chars().filter(c -> c == '|').count() - 1;
            if (colCount > 0) {
                StringBuilder sep = new StringBuilder("|");
                for (int c = 0; c < colCount; c++) {
                    sep.append("--|");
                }
                result.add(1, sep.toString());
            }
        }

        return result;
    }

    private List<String> cleanPipeTable(List<String> tableBlock) {
        List<String> result = new ArrayList<>();
        for (int j = 0; j < tableBlock.size(); j++) {
            String line = tableBlock.get(j).strip();

            if (PIPE_SEPARATOR_LINE.matcher(line).matches()) {
                String[] cols = line.split("\\|");
                StringBuilder sep = new StringBuilder("|");
                for (int c = 1; c < cols.length - 1; c++) {
                    sep.append("--|");
                }
                result.add(sep.toString());
                continue;
            }

            String[] parts = line.split("\\|", -1);
            StringBuilder cleaned = new StringBuilder("|");
            for (int c = 1; c < parts.length - 1; c++) {
                String cell = parts[c].strip();
                if (cell.isEmpty()) cell = " ";
                cleaned.append(" ").append(cell).append(" |");
            }
            result.add(cleaned.toString());
        }
        return result;
    }

    private boolean isTabularPattern(String[] lines, int index) {
        int consecutiveTabular = 0;
        for (int j = index; j < lines.length && j < index + 3; j++) {
            String s = lines[j].strip();
            if (s.isEmpty()) break;
            if (s.contains("\t") || s.matches(".*\\S{2,}\\s{3,}\\S{2,}.*")) {
                consecutiveTabular++;
            }
        }
        return consecutiveTabular >= 3;
    }

    // ========== PHASE 6: Pandoc Artifact Cleanup ==========

    String phase6PandocCleanup(String text) {
        text = HTML_COMMENT.matcher(text).replaceAll("");
        text = PANDOC_CLASS_ATTR.matcher(text).replaceAll("");
        text = PANDOC_ANCHOR_SPAN.matcher(text).replaceAll("");
        // 保留图片引用，不移除
        // text = PANDOC_IMAGE.matcher(text).replaceAll("<!-- image removed -->");
        text = PANDOC_NEWPAGE.matcher(text).replaceAll("");
        text = RAW_HTML_BLOCK.matcher(text).replaceAll("");
        // Remove <br> tags (case-insensitive) and convert to newline
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        // Also handle HTML entities for br
        text = text.replaceAll("(?i)&lt;br\\s*/?&gt;", "\n");
        text = UNNECESSARY_ESCAPE.matcher(text).replaceAll("$1");
        return text;
    }

    // ========== PHASE 7: Whitespace & Flow ==========

    String phase7WhitespaceAndFlow(String text) {
        text = TRAILING_WHITESPACE.matcher(text).replaceAll("");
        text = MULTIPLE_BLANK_LINES.matcher(text).replaceAll("\n\n");
        text = removePageBreakMarkers(text);
        // Final cleanup: remove any remaining <br> tags
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)&lt;br\\s*/?&gt;", "\n");
        text = text.strip();
        return text;
    }

    private String removePageBreakMarkers(String text) {
        String[] lines = text.split("\n", -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].strip();
            if (stripped.equals("---") || stripped.equals("***") || stripped.equals("___")) {
                boolean afterHeading = false;
                boolean beforeHeading = false;

                for (int j = result.size() - 1; j >= 0; j--) {
                    String prev = result.get(j).strip();
                    if (prev.isEmpty()) continue;
                    afterHeading = prev.startsWith("#");
                    break;
                }

                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].strip();
                    if (next.isEmpty()) continue;
                    beforeHeading = next.startsWith("#");
                    break;
                }

                if (afterHeading && beforeHeading) {
                    result.add(lines[i]);
                }
            } else {
                result.add(lines[i]);
            }
        }

        return String.join("\n", result);
    }

    // ========== PHASE 8: Final Validation ==========

    String phase8FinalValidation(String text) {
        if (text.isBlank()) {
            return "<!-- Document contains no extractable text -->\n";
        }

        text = text.replaceAll("(?m)^#{1,6}\\s*$", "");

        while (text.startsWith("\n")) {
            text = text.substring(1);
        }

        text = text.strip() + "\n";

        return text;
    }
}
