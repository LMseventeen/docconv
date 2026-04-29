# MarkdownNormalizer: Transformation Rules

> Author: Information Designer
> Date: 2026-04-29
> Status: Design (Pre-implementation)
> Depends on: ARCHITECTURE.md

---

## 0. Design Philosophy

The MarkdownNormalizer exists for one reason: **the reader should never have to think about where the document came from.** A teacher uploading an exam from Word and one from PDF should get Markdown that looks like it was written by the same careful human.

Three principles guide every rule below:

1. **Preserve meaning, not format.** We don't care if the original used 14pt Georgia or 12pt Arial. We care that "Section 2" was a section heading and "Question 5" was a question heading.
2. **Reduce cognitive load.** Every unnecessary blank line, inconsistent heading level, or mangled list is friction for the teacher who will read or edit this output. Remove friction.
3. **Be predictable.** If the normalizer makes a transformation, it should make the *same* transformation for the *same* input pattern every time. No heuristics that behave differently on Tuesday.

---

## 1. Heading Hierarchy Rules

### 1.1 The Problem

PDF gives us plain text. There are no `#` symbols — just lines that *look* like headings because they're short, bold, capitalized, or set apart by whitespace. Pandoc gives us Markdown headings, but they're often wrong: a document that uses Word's "Heading 1" for the title and "Heading 3" for sections produces `#` and `###` with nothing in between.

The normalizer must produce a **consistent, shallow heading hierarchy** that reflects the document's actual structure.

### 1.2 Target Hierarchy

For exam documents, the natural hierarchy is:

```
#  Document Title          (H1 — exactly one, from metadata or first detected heading)
##  Section / Part          (H2 — "Part A", "Section 1", major divisions)
### Question Group          (H3 — if questions are grouped, e.g., "Reading Comprehension")
#### Question               (H4 — individual question number, if styled as heading)
```

**Rule: H1 is reserved for the document title.** If the parser provides a title via `DocumentMetadata.title`, use it. If not, the normalizer may promote the first detected heading to H1. Never produce more than one H1.

**Rule: Never skip heading levels.** If the source has H1 and H3 but no H2, either promote H3 to H2 or insert a gap. The output must be a valid hierarchy — a reader using a document outline should never see a jump.

### 1.3 PDF Heading Detection Heuristics

Since PDF gives us plain text, the normalizer must detect headings. Apply these heuristics **in order of reliability**. A line must match at least two signals to be promoted to a heading.

#### Signal 1: Structural Position (strongest)

- Line is preceded by a blank line AND followed by a blank line.
- Line is the first non-blank line of a page (preceded by the `---` page separator).

#### Signal 2: Typographic Convention

- Line is entirely UPPERCASE (3+ characters, no lowercase).
- Line is Title Case AND shorter than 80 characters AND does not end with a period.
- Line starts with a numbering pattern: `1.`, `I.`, `A.`, `Part I`, `Section 2`, `Question 1`.

#### Signal 3: Content Pattern

- Line matches common exam structure patterns:
  - `Part [A-Z]` / `Part [IVX]+`
  - `Section \d+`
  - `Question \d+` / `Q\d+` / `Q\. \d+`
  - `Instructions?[:]`
  - `Answer Key` / `Marking Scheme`
  - Lines that are entirely bold-like (wrapped in `**` if the parser detected emphasis)

#### Signal 4: Negative Heuristics (do NOT treat as heading)

- Line ends with a comma, semicolon, or conjunction ("and", "or", "but") — it's a continuation, not a heading.
- Line is longer than 100 characters — it's a paragraph, not a heading.
- Line contains a question mark AND is longer than 40 characters — it's a question body, not a heading.
- Line starts with a bullet (`-`, `*`, `\u2022`) or number followed by `)` or `.` — it's a list item.

#### Heading Level Assignment

Once a line is identified as a heading, assign its level:

| Pattern                                          | Level |
|--------------------------------------------------|-------|
| First heading in document (if no metadata title) | H1    |
| `Part [A-Z/I-V]` or equivalent major division    | H2    |
| `Section \d+` or named subsection                | H3    |
| `Question \d+` or equivalent                     | H4    |
| Everything else                                  | H3    |

**Rule: Apply heading detection BEFORE any other normalization.** Headings inform the structure that other rules depend on.

### 1.4 Word/Pandoc Heading Normalization

Pandoc preserves Word's heading levels directly. The problem: most Word documents use heading levels inconsistently. A teacher might use "Heading 1" for the title, "Heading 3" for sections (skipping 2), and "Heading 5" for questions.

**Rule: Collapse the heading gap.** Walk the document, collect the set of heading levels actually used, and remap them to a contiguous sequence starting from H1.

Example:

```
Input levels used:  {1, 3, 5}
Remapped to:        {1 → 1, 3 → 2, 5 → 3}
```

**Rule: Preserve relative depth.** If the source has H1 > H3 > H5, the output has H1 > H2 > H3. The *distance* between levels is not preserved — only the *ordering*.

**Rule: If Pandoc produces H1 for the document title and the metadata also has a title, use the metadata title as H1 and demote all other H1s to H2.** This prevents duplicate titles.

### 1.5 Heading Cleanup

After detection and normalization, apply these cleanup rules:

- **Remove duplicate headings.** If two consecutive headings have identical text, keep only the first.
- **Trim trailing punctuation from headings.** Remove trailing colons, periods, and dashes. `Section 1:` becomes `Section 1`.
- **Normalize whitespace within headings.** Collapse multiple spaces to one. Trim leading/trailing whitespace.
- **Ensure blank line before every heading.** A heading without a preceding blank line is invisible to many Markdown parsers.
- **Ensure exactly one blank line after every heading.** No more, no less.

---

## 2. Table Conversion Rules

### 2.1 The Problem

Tables are the most fragile structure in document conversion. PDF tables are rarely extracted as tables at all — they come through as aligned columns of text, or worse, as a jumble of words. Word tables survive Pandoc, but Pandoc's default Markdown table syntax is verbose and hard to read by hand.

### 2.2 Input Handling

#### PDF Tables (Unsupported in MVP — Graceful Degradation)

The architecture confirms: PDF tables are not extracted in MVP. The normalizer should:

- **Detect tabular patterns.** Lines with 3+ consecutive spaces separating aligned columns, or lines containing `|` or `\t` characters that appear in repeated patterns.
- **Preserve as code block.** If a tabular pattern is detected, wrap it in a fenced code block (` ``` `) to preserve column alignment. This is better than collapsing it into a paragraph, which destroys all structure.
- **Add a comment.** Precede the code block with `<!-- Table: could not be converted to Markdown table. Preserved as text. -->`. This makes the limitation visible without disrupting the reading flow.

#### Word Tables (via Pandoc)

Pandoc produces pipe tables by default:

```
| Column A | Column B | Column C |
|----------|----------|----------|
| cell     | cell     | cell     |
```

This is correct but noisy. The normalizer should:

- **Preserve the structure.** Don't break it.
- **Clean up padding.** Pandoc sometimes over-pads cells. Normalize to single-space padding: `| cell |` not `|      cell      |`.
- **Remove grid tables.** Pandoc sometimes produces grid tables (using `+---+---+`). Convert these to pipe tables for consistency.
- **Handle empty cells.** Ensure empty cells contain at least one space: `| |` not `||`. This prevents parsing ambiguity.

### 2.3 Table Readability Rules

- **Align columns consistently.** Use left-alignment by default. Right-align numeric columns if the normalizer can detect them (column is >50% numbers).
- **Limit table width where possible.** If a table has more than 6 columns, consider whether it should be preserved as-is or if it's actually a list that was formatted as a table. (This is a heuristic for post-MVP.)
- **Ensure blank line before and after every table.** Same rule as headings — Markdown parsers need this.

### 2.4 Exam-Specific Table Patterns

Exam documents commonly use tables for:

- **Answer sheets** (`| Q | Answer |`): Preserve as-is.
- **Marking rubrics** (`| Criterion | Points | Description |`): Preserve as-is.
- **Multi-column layouts** (not real tables): If the document uses a table purely for visual layout (e.g., side-by-side questions), detect this by checking if cells contain multiple paragraphs or block-level elements. If so, flatten to sequential content.

For MVP, the last pattern is acknowledged but not implemented — it requires content-level heuristics that are out of scope.

---

## 3. List Handling Rules

### 3.1 The Problem

Lists in exam documents are everywhere: answer options, instruction steps, question sets. PDF loses list structure entirely. Word preserves it through Pandoc, but Pandoc's output is inconsistent — sometimes it uses `-`, sometimes `*`, and nested lists use varying indentation.

### 3.2 List Detection (PDF)

For plain text from PDF, detect lists by these patterns:

#### Unordered List Items

- Line starts with a bullet character: `\u2022` (•), `\u25CB` (○), `\u25CF` (●), `\u2023` (‣), `\u25AA` (▪), `-`, `*`, `+`
- Line starts with a letter in parentheses: `(a)`, `(b)`, `(A)`, `(B)`
- Line starts with a checkbox: `[ ]`, `[x]`, `[X]`

#### Ordered List Items

- Line starts with a number followed by `)` or `.`: `1)`, `1.`, `01.`
- Line starts with a Roman numeral followed by `)` or `.`: `i)`, `ii)`, `I.`, `IV.`

#### Nested Lists

- A list item that is indented (2+ spaces or 1+ tab) relative to the previous list item is a nested item.
- The normalizer should normalize indentation to exactly 2 spaces per nesting level.

### 3.3 List Normalization (Both Sources)

**Rule: Use `-` for all unordered lists.** Pandoc may produce `*` or `+`. Normalize all to `-` for consistency.

**Rule: Use `1.` for all ordered lists.** Even if the source used `1)`, `01)`, or Roman numerals. Markdown renders `1. 2. 3.` identically to `1. 1. 1.` (auto-numbering), so use `1.` throughout for simplicity.

**Exception: Preserve Roman numeral lists.** If the source used `I.`, `II.`, `III.` and these represent meaningful structure (e.g., "Part I", "Part II"), convert to headings instead of list items. Roman numerals in exam documents almost always denote major sections, not list items.

**Rule: Preserve answer option labels.** Exam answer options are often labeled `(A)`, `(B)`, `(C)`, `(D)`. These should be preserved as list items with the label intact:

```
- (A) Paris
- (B) London
- (C) Berlin
- (D) Madrid
```

Do NOT strip the `(A)` prefix. It carries meaning — the teacher and students refer to options by these labels.

### 3.4 List Continuation

- A list item that spans multiple lines should have continuation lines indented to align with the text after the bullet:
  ```
  - This is a long list item that
    continues on the next line.
  ```
- Ensure a blank line before and after every list block.
- Ensure NO blank line between list items unless the source explicitly had one (indicating a paragraph-level break within the list).

### 3.5 Edge Case: Answer Options as Paragraphs

Some exam documents don't format answer options as lists at all. They appear as:

```
A) Paris
B) London
C) Berlin
D) Madrid
```

or:

```
A. Paris
B. London
C. Berlin
D. Madrid
```

The normalizer should detect this pattern (single-letter label + `)` or `.` + short text, repeated 3+ times) and convert to a list. This is a high-value transformation — it turns noise into structure.

---

## 4. Edge Cases

### 4.1 Ambiguous Structures

#### A line that looks like both a heading and a list item

```
1. Introduction
```

Is this a heading ("Introduction") or the first item of a numbered list? The normalizer should use context:

- If followed by a blank line and then non-list text: it's a heading.
- If followed by `2.`, `3.`, etc.: it's a list item.
- If uncertain: treat as a heading. In exam documents, `1. Introduction` is almost always a section heading.

#### A paragraph that's actually a question

```
What is the capital of France?
```

This looks like a paragraph, but in an exam, it's a question. The normalizer should NOT try to detect this — there's no reliable signal. The parser (especially PDF) cannot distinguish a question from a statement. Leave it as a paragraph. The user or an AI post-processor (post-MVP) can add structure later.

#### Consecutive blank lines

Multiple blank lines should be collapsed to exactly one. More than one blank line in Markdown creates no additional visual separation — it's just noise.

### 4.2 Special Characters

| Character       | Source        | Normalization                              |
|-----------------|---------------|--------------------------------------------|
| `\u2013` (–)    | Word/PDF      | Preserve as-is (en dash)                   |
| `\u2014` (—)    | Word/PDF      | Preserve as-is (em dash)                   |
| `\u2018\u2019`  | Word          | Convert to `'` (straight apostrophe)       |
| `\u201C\u201D`  | Word          | Convert to `"` (straight double quote)      |
| `\u00A0`        | Word/PDF      | Convert to regular space                   |
| `\u2026` (...)  | Word          | Preserve as-is (ellipsis)                  |
| `\u00D7` (×)    | Math PDFs     | Preserve as-is (multiplication sign)        |
| `\u00F7` (÷)    | Math PDFs     | Preserve as-is (division sign)             |
| `\u2248` (≈)    | Math PDFs     | Preserve as-is                             |
| `\u2260` (≠)    | Math PDFs     | Preserve as-is                             |
| `\u2264` (≤)    | Math PDFs     | Preserve as-is                             |
| `\u2265` (≥)    | Math PDFs     | Preserve as-is                             |

**Rule: Convert smart quotes to straight quotes.** Markdown doesn't distinguish them, and smart quotes cause search/replace problems when users edit the output.

**Rule: Preserve mathematical symbols.** Exam documents often contain math. Don't touch Unicode math symbols.

**Rule: Convert non-breaking spaces to regular spaces.** `\u00A0` is invisible but causes formatting issues in Markdown renderers.

### 4.3 Encoding Issues

#### PDF Encoding

OpenDataLoader may produce:
- Garbled characters from improperly encoded PDFs (common in older documents).
- Ligatures: `fi` as a single glyph, `fl` as a single glyph. These should be decomposed to their component letters.
- Right-to-left text (Arabic, Hebrew): Preserve as-is. Don't attempt to re-order. The Markdown renderer handles BiDi.

**Rule: Decompose typographic ligatures.** Replace `\uFB01` (fi) with `fi`, `\uFB02` (fl) with `fl`, `\uFB03` (ffi) with `ffi`, `\uFB04` (ffl) with `ffl`.

**Rule: Replace replacement character.** If the source contains `\uFFFD` (the Unicode replacement character), replace with `[?]` to make the data loss visible rather than silent.

#### Word/Pandoc Encoding

Pandoc handles encoding well. The normalizer should:
- Ensure the output is valid UTF-8.
- Strip any BOM (`\uFEFF`) that Pandoc might emit.

### 4.4 Whitespace Normalization

| Pattern                          | Rule                                                 |
|----------------------------------|------------------------------------------------------|
| Trailing whitespace on any line  | Remove                                               |
| Multiple spaces between words    | Collapse to one space                                |
| Multiple blank lines             | Collapse to one blank line                           |
| Tab characters                   | Convert to 2 spaces                                  |
| Leading whitespace on non-list lines | Remove (unless inside code block)                |
| Trailing blank lines at end of document | Remove (document ends with content, not whitespace) |

### 4.5 Line Break Handling

- **Hard line breaks** (two trailing spaces or `\` in Markdown): Preserve if intentional. In exam documents, these are rare — most line breaks are paragraph wrapping artifacts.
- **Paragraph detection:** A blank line signals a paragraph break. If the PDF parser joined lines without blank lines between them, the normalizer should detect paragraph boundaries using sentence-ending punctuation (`.`, `?`, `!`) followed by a capital letter on the next line.

**Rule: Don't join lines that the parser separated.** If the PDF parser put a newline between two lines, the normalizer should treat it as a potential paragraph break, not a wrapping artifact. Err on the side of more paragraphs rather than fewer.

### 4.6 Page Breaks

The PDF parser inserts `---` (horizontal rule) between pages. The normalizer should:

- **Remove page-break markers** (`---` that appear between sections of continuous text). They add no value in Markdown.
- **Exception:** If the `---` appears between two distinct sections (e.g., between "Part A" and "Part B"), keep it as a visual separator. Heuristic: keep `---` if it's preceded and followed by headings.

### 4.7 Pandoc Artifacts

Pandoc produces several artifacts that should be cleaned:

| Artifact                          | Cleanup                                              |
|-----------------------------------|------------------------------------------------------|
| `<!-- -->` HTML comments          | Remove (usually metadata cruft)                      |
| `{.unstyled}` / `{.some-class}`  | Remove Pandoc div/span attributes                    |
| `[]{#anchor}` anchor spans        | Remove                                               |
| `\` escapes of non-special chars  | Remove unnecessary backslash escapes                 |
| `[text](#anchor)` internal links  | Preserve (may be useful)                             |
| `![image](url)` image references  | Remove images (not in MVP scope) but leave a `<!-- image removed -->` comment |
| Pandoc's `\newpage`              | Remove                                               |
| Raw HTML blocks (`<div>`, `<span>`) | Convert to Markdown equivalents where possible, strip otherwise |

---

## 5. Readability Principles

These are not specific rules — they are the *intent* behind every rule above. When a new edge case appears that isn't covered by a specific rule, these principles should guide the decision.

### 5.1 The Output Should Read Like a Well-Written Document

A teacher should be able to open the Markdown file, read it as raw text, and understand it immediately. This means:

- **Consistent formatting.** Every heading looks the same. Every list looks the same. No surprises.
- **Visual breathing room.** Blank lines between blocks. Not too many, not too few. Exactly one.
- **No machine artifacts.** No `{.unstyled}`, no `<!-- -->`, no `\uFFFD`. The output should look human-authored.

### 5.2 Structure Should Be Scannable

When a teacher opens a 500-question exam, they need to find Question 237 quickly. This means:

- **Headings are the table of contents.** Every section and question group should have a heading. The teacher should be able to use their Markdown editor's outline view to navigate.
- **Lists are lists.** Answer options should be lists, not paragraphs. The teacher should see the structure at a glance.
- **Tables are tables.** Not code blocks, not aligned text. If it's tabular data, it should look tabular.

### 5.3 The Output Should Be Editable

The teacher will likely edit the Markdown after conversion. This means:

- **No overly long lines.** If a paragraph is 500 characters on one line, it's hard to edit. But don't hard-wrap at 80 characters either — that creates problems when re-rendering. Let the editor handle wrapping. (This means: preserve the parser's line structure, don't re-wrap.)
- **Straightforward syntax.** Use `-` not `*` for lists. Use `**bold**` not `__bold__`. Use `#` not underlines for headings. The simpler syntax is easier to type and edit.
- **No ambiguity.** If a line could be interpreted two ways, the normalizer should pick the interpretation that's more likely correct for exam documents and make it unambiguous through formatting.

### 5.4 Meaning Over Format

The original document's formatting is a *hint* about its meaning, not the meaning itself. The normalizer's job is to extract meaning and express it in Markdown:

- A 24pt bold line in Word isn't meaningful because it's 24pt and bold — it's meaningful because it's a section heading.
- A PDF line with ALL CAPS isn't meaningful because of the capitalization — it's meaningful because it denotes a structural division.
- An indented paragraph in PDF isn't meaningful because of the indentation — it might be a continuation of the previous point, or a block quote, or just bad formatting.

**When in doubt, preserve the text and simplify the structure.** It's better to have a clean paragraph than a badly-structured heading.

---

## 6. Normalization Pipeline

The rules above should be applied in this specific order. Order matters because some transformations depend on the output of earlier ones.

```
RawDocument.content (string from parser)
    │
    ▼
┌─────────────────────────────────────┐
│  PHASE 1: Encoding Cleanup          │
│  - Fix UTF-8 issues                 │
│  - Decompose ligatures              │
│  - Replace \uFFFD with [?]          │
│  - Strip BOM                        │
│  - Convert non-breaking spaces      │
│  - Convert smart quotes             │
│  - Convert tabs to spaces           │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 2: Structural Detection      │
│  (PDF only — Pandoc already did this│
│  - Detect headings (heuristics)     │
│  - Detect lists                     │
│  - Detect answer option patterns    │
│  - Detect tabular data              │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 3: Heading Normalization     │
│  (Both sources)                     │
│  - Collapse heading levels          │
│  - Ensure single H1                │
│  - Remove heading duplicates        │
│  - Trim heading punctuation         │
│  - Ensure blank lines around        │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 4: List Normalization        │
│  - Unify bullet character to `-`    │
│  - Unify ordered list to `1.`       │
│  - Normalize indentation (2-space)  │
│  - Preserve answer option labels    │
│  - Detect paragraph-style options   │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 5: Table Normalization       │
│  - Clean pipe table padding         │
│  - Convert grid tables to pipe      │
│  - Wrap unextractable PDF tables    │
│    in code blocks                   │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 6: Pandoc Artifact Cleanup   │
│  (Word source only)                 │
│  - Remove HTML comments             │
│  - Remove Pandoc class attributes   │
│  - Remove anchor spans              │
│  - Remove unnecessary escapes       │
│  - Remove image references          │
│  - Remove \newpage                  │
│  - Flatten raw HTML blocks          │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 7: Whitespace & Flow         │
│  - Collapse multiple blank lines    │
│  - Remove trailing whitespace       │
│  - Remove trailing blank lines      │
│  - Ensure blank lines around blocks │
│  - Remove page-break markers        │
│  - Detect paragraph boundaries      │
│    (PDF only)                       │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│  PHASE 8: Final Validation          │
│  - Verify valid UTF-8               │
│  - Verify no empty headings (## \n) │
│  - Verify no orphan list items      │
│  - Verify document is non-empty     │
└──────────────────┬──────────────────┘
                   │
                   ▼
Clean Markdown string → returned as normalization result
```

### 6.1 Phase Ordering Rationale

- **Encoding first.** Everything downstream assumes clean Unicode. Fix it once, up front.
- **Detection before normalization.** You must know *what* something is before you can normalize it. Heading detection must happen before heading normalization.
- **Heading normalization before list normalization.** Some lines could be headings or list items. Heading detection (which runs first) claims what it can; the rest are candidates for list detection.
- **Pandoc cleanup last (before whitespace).** Pandoc artifacts are Markdown-level noise. They should be removed after structural normalization is complete, so that the normalizer doesn't accidentally treat a Pandoc class attribute as content.
- **Whitespace last.** All other transformations may introduce or remove blank lines. Fix whitespace once, at the end, as a final pass.

---

## 7. Testing Strategy

### 7.1 Unit Tests Per Phase

Each phase should have independent unit tests:

| Phase | Test Input                                    | Assert Output                                |
|-------|-----------------------------------------------|----------------------------------------------|
| 1     | `"smart\u201Cquotes\u201D"`                   | `"smart\"quotes\""`                          |
| 2     | `"ALL CAPS TITLE\n\nSome text"`               | `"## ALL CAPS TITLE\n\nSome text"`           |
| 3     | `"# Title\n### Sub"`                          | `"# Title\n## Sub"` (gap collapsed)          |
| 4     | `"* item\n* item"`                            | `"- item\n- item"`                           |
| 5     | `"+-----+\n| a | b |\n+-----+"`               | `"| a | b |\n|--|--|"`                        |
| 6     | `"text {.unstyled} more"`                     | `"text  more"` (or `"text more"`)            |
| 7     | `"line1\n\n\n\nline2"`                        | `"line1\n\nline2"`                           |

### 7.2 Integration Tests (Full Pipeline)

| Scenario             | Input Fixture             | Expected Characteristics                    |
|----------------------|---------------------------|---------------------------------------------|
| Simple PDF exam      | `sample-exam.pdf`         | Headings detected, options listed, clean     |
| Complex Word exam    | `complex-exam.docx`       | Heading levels collapsed, artifacts removed  |
| Math-heavy PDF       | `math-exam.pdf`           | Symbols preserved, no garbled characters     |
| Table-rich Word doc  | `tables-exam.docx`        | Tables preserved, padding cleaned            |
| Minimal document     | `one-page.docx`           | No structure lost, no over-normalization     |
| Corrupt encoding     | `broken-encoding.pdf`     | `[?]` markers where data was lost            |

### 7.3 Golden File Testing

For each integration test, maintain a "golden" expected output file. On each code change, re-run the normalizer and diff against the golden file. Any difference is a regression or an intentional improvement (update the golden file).

---

## 8. Source-Specific Behavior Summary

| Behavior                          | PDF (OpenDataLoader)        | Word (Pandoc)               |
|-----------------------------------|-----------------------------|------------------------------|
| Heading detection                 | Heuristic (Phase 2)         | Already present; normalize (Phase 3) |
| List detection                    | Heuristic (Phase 2)         | Already present; normalize (Phase 4) |
| Table handling                    | Code block fallback         | Pipe table cleanup           |
| Bold / Italic                     | Not extracted (plain text)  | Preserved by Pandoc          |
| Pandoc artifact cleanup           | Not applicable              | Required (Phase 6)           |
| Page break handling               | Remove `---` separators     | Not applicable (no pages)    |
| Paragraph boundary detection      | Heuristic needed            | Pandoc already paragraphs    |
| Answer option detection           | Pattern-match needed        | Usually already lists        |

---

## 9. What This Design Does NOT Do

Being explicit about boundaries prevents scope creep and false expectations:

1. **Does not add structure that isn't there.** If the PDF has no detectable headings, the output has no headings. Better honest plain text than wrong headings.
2. **Does not correct content.** Typos, wrong answers, incomplete questions — these are the author's problem, not the normalizer's.
3. **Does not reformat for a specific LMS.** The output is standard GitHub-Flavored Markdown. If the learning platform needs a different format, that's a separate transformation.
4. **Does not handle scanned PDFs.** If the PDF is an image (no extractable text), the parser fails before the normalizer runs. This is documented as a known limitation.
5. **Does not merge or split questions.** If two questions are run together in the source, they stay run together. Question boundary detection is a content-understanding problem, not a formatting problem.
6. **Does not produce structured data.** The output is a Markdown string, not JSON or XML. Structured output is a post-MVP concern (see ARCHITECTURE.md §5.5).

---

## 10. Configuration & Tuning

The normalizer should expose a small set of tunable parameters for edge cases:

```java
public record NormalizerConfig(
    int maxHeadingLength,          // default: 80 — lines longer than this are never headings
    int minHeadingSignalCount,     // default: 2 — minimum heuristic signals for PDF heading
    int tabWidth,                  // default: 2 — spaces per tab
    boolean preservePageBreaks,    // default: false — keep --- between pages
    boolean removeImages,          // default: true — strip image references
    String bulletChar,             // default: "-" — character for unordered lists
    Set<String> headingPatterns    // default: exam patterns — regex patterns for heading detection
) {}
```

These should be set to sensible defaults and not require user configuration in MVP. They exist so that future maintainers can tune behavior without rewriting logic.

---

## Appendix A: Decision Log

| Decision                                        | Alternatives Considered                     | Rationale                                          |
|-------------------------------------------------|---------------------------------------------|----------------------------------------------------|
| Collapse heading levels to contiguous sequence  | Preserve original levels                    | Exam documents have inconsistent levels; contiguous is more readable |
| Use `-` for all bullets                         | Preserve source bullet character            | Consistency; `-` is the most common Markdown bullet |
| Remove Pandoc artifacts in a dedicated phase    | Remove inline during other phases           | Separation of concerns; easier to test and maintain |
| Wrap un-extracted PDF tables in code blocks      | Flatten to paragraphs                       | Code block preserves column alignment; paragraphs destroy it |
| Detect answer option patterns in PDF            | Leave as plain text                         | High-value transformation for exam documents; worth the heuristic risk |
| Convert smart quotes to straight                | Preserve smart quotes                       | Markdown doesn't distinguish them; straight quotes are easier to search/edit |
| Remove all images                               | Preserve image references                   | MVP scope excludes images; preserving broken references is worse than removing them |
| Single blank line between all blocks            | Vary blank lines by block type              | Simplicity; one rule is easier to implement and predict than five |
