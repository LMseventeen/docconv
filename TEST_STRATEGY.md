# Test Strategy: Document-to-Markdown Conversion Tool

> Author: QA / Quality Engineer
> Date: 2026-04-29
> Status: Active — this document is the quality gate for the project.
> Philosophy: If you can't measure it, you can't ship it.

---

## Guiding Principles

1. **Test behavior, not implementation.** We don't care if `PdfParser` uses OpenDataLoader or black magic — we care that a 10-page exam PDF produces Markdown with 10 page breaks and recognizable headings.
2. **The golden dataset is the spec.** Our test fixtures ARE the product requirements in executable form. If a fixture is missing, a requirement is untested.
3. **Fail fast, fail loudly.** A conversion that silently drops 30% of content is worse than a 500 error. We test for silent data loss aggressively.
4. **Reproduction over diagnosis.** Every failing test must be reproducible with a checked-in fixture file. "It works on my machine" is not a test result.

---

## 1. Test Pyramid

### Ratios

```
                    ╱╲
                   ╱ E2E ╲           ~10% of test count
                  ╱  (5-8)  ╲        Slow, expensive, high confidence
                 ╱────────────╲
                ╱ Integration   ╲    ~30% of test count
               ╱  (20-30 tests)  ╲   Component boundaries, real I/O
              ╱────────────────────╲
             ╱    Unit Tests         ╲  ~60% of test count
            ╱    (60-80 tests)        ╲ Fast, isolated, deterministic
           ╱────────────────────────────╲
```

### Layer Breakdown

#### 1.1 Unit Tests (60-80 tests) — JUnit 5 + Mockito

| Component | What to Test | Mock Strategy |
|-----------|-------------|---------------|
| `MarkdownNormalizer` | Whitespace cleanup, encoding fixes, heading normalization, list formatting | No mocks — pure function, test with strings |
| `ParserRegistry` | Extension resolution (.pdf, .docx, .PDF, .DOCX case handling), unknown extension rejection | Mock `DocumentParser` implementations |
| `ConvertService` | Parser selection, normalizer invocation, store interaction, error propagation | Mock registry, normalizer, store |
| `ConvertController` | Request validation (empty file, wrong type, oversized), response shape | Mock `ConvertService`, use `MockMvc` |
| `WordParser` | Command construction, timeout handling, stderr capture | Mock `ProcessBuilder` / subprocess |
| `PdfParser` | Temp file lifecycle, page concatenation, heuristic heading detection | Mock OpenDataLoader SDK |
| `GlobalExceptionHandler` | Each exception type maps to correct HTTP status + message | N/A — test the handler directly |

**Key unit test rules:**
- `MarkdownNormalizer` gets the most unit tests. It's the quality bottleneck — pure logic, no I/O, fully deterministic. If normalizer tests pass but output looks bad, the tests are wrong.
- Every public method on every class gets at least one happy-path and one failure-path test.
- No Spring context loading in unit tests. If you need `@Autowired`, it's an integration test.

#### 1.2 Integration Tests (20-30 tests) — @SpringBootTest + TestContainers (if needed)

| Scenario | What It Proves |
|----------|---------------|
| PDF file → `ConvertService` → Markdown string | End-to-end parser+normalizer pipeline for PDF |
| DOCX file → `ConvertService` → Markdown string | End-to-end parser+normalizer pipeline for Word |
| `POST /api/convert` with real `.docx` → 200 + valid JSON | HTTP boundary + Spring multipart handling |
| `POST /api/convert` with real `.pdf` → 200 + valid JSON | HTTP boundary + PDF SDK integration |
| `POST /api/convert` with `.exe` → 400 | Rejection of unsupported formats |
| `POST /api/convert` with empty file → 400 | Empty upload handling |
| `POST /api/convert` with 11MB file → 413 | Size limit enforcement |
| `GET /api/convert/{id}/download` after conversion → .md file | Store + download pipeline |
| `GET /api/convert/{nonexistent-id}` → 404 | Missing ID handling |
| Pandoc not on PATH → 503 | System dependency detection |
| Corrupt PDF → 422 | Graceful parser failure |
| Corrupt DOCX → 500 with descriptive message | Pandoc error propagation |
| Concurrent uploads (5 simultaneous) | No file collision, no temp file leaks |

**Key integration test rules:**
- Use real fixture files, not mocks. The point is to catch real-world failures.
- Integration tests MUST run Pandoc and OpenDataLoader for real. If Pandoc is not available, skip with `@EnabledIf` — never mock it in integration tests.
- Every integration test asserts on the Markdown output structure, not just HTTP status.

#### 1.3 E2E Tests (5-8 tests) — Playwright (frontend) or REST-assured (full stack)

| Scenario | What It Proves |
|----------|---------------|
| Upload `.docx` → see preview → download `.md` | Full happy path, user-visible |
| Upload `.pdf` → see preview → download `.md` | Full happy path, user-visible |
| Upload `.xlsx` → see error message | Frontend validation works |
| Upload 11MB file → see error message | Size limit surfaced to user |
| Upload → cancel mid-upload → clean state | Resilience |
| Downloaded `.md` file matches preview content | Preview = download (no drift) |

**E2E test rules:**
- E2E tests are the LAST line of defense, not the first. If an E2E test catches a bug that a unit test should have caught, write the missing unit test.
- Run E2E tests against the full stack (Nuxt + Spring Boot) in CI.
- Screenshot on failure. Always.

---

## 2. Golden Datasets

The golden dataset is our most valuable test asset. Every fixture file is a contract: "this input must produce this output."

### 2.1 Required Fixture Files

| Fixture ID | File | Purpose | Expected Behavior |
|-----------|------|---------|-------------------|
| `FIX-DOCX-SIMPLE` | `simple-exam.docx` | 5 questions, headings, bold, basic formatting | All headings preserved, bold intact, 5 question markers |
| `FIX-DOCX-COMPLEX` | `complex-exam.docx` | 50 questions, tables, nested lists, images, footnotes | Tables as Markdown tables, lists preserved, images stripped, footnotes handled |
| `FIX-DOCX-LARGE` | `large-exam.docx` | 500 questions, ~200 pages | Completes in <30s, all questions present in output |
| `FIX-DOCX-EMPTY-CONTENT` | `empty-content.docx` | Valid .docx with no text content | Returns empty or minimal Markdown, no crash |
| `FIX-DOCX-UNICODE` | `unicode-exam.docx` | Chinese, Arabic, math symbols, emoji | Characters preserved, no mojibake |
| `FIX-DOCX-TRACK-CHANGES` | `track-changes.docx` | Track changes enabled, comments | Accepts all changes, strips comments cleanly |
| `FIX-PDF-SIMPLE` | `simple-exam.pdf` | 5-page exam, clean text | Text extracted, page breaks present |
| `FIX-PDF-COMPLEX` | `complex-exam.pdf` | Multi-column layout, tables, headers/footers | Columns collapsed to single flow, headers/footers stripped or isolated |
| `FIX-PDF-LARGE` | `large-exam.pdf` | 100+ pages | Completes in <10s, all text present |
| `FIX-PDF-SCANNED` | `scanned-exam.pdf` | Image-only PDF (no text layer) | Graceful failure or empty output — documented as known limitation |
| `FIX-PDF-ENCRYPTED` | `encrypted-exam.pdf` | Password-protected PDF | 422 error, clear message |
| `FIX-PDF-UNICODE` | `unicode-exam.pdf` | Chinese, Arabic, math symbols | Characters preserved where possible |
| `FIX-PDF-CORRUPT` | `corrupt.pdf` | Truncated / malformed PDF file | 422 error, no crash, temp files cleaned up |
| `FIX-DOCX-CORRUPT` | `corrupt.docx` | Truncated / malformed DOCX file | 500 error with Pandoc stderr message |

### 2.2 Fixture Management Rules

1. **Fixtures live in `src/test/resources/fixtures/`** — versioned in Git, not generated at test time.
2. **Every fixture has a companion `.expected.md` file** — the expected Markdown output. This IS the spec.
3. **Fixtures are immutable.** If a fixture needs to change, create a new one with a new ID. Never modify an existing fixture — it breaks regression detection.
4. **Large fixtures (>1MB) use Git LFS.** Don't bloat the repo.
5. **Document every fixture.** Each file gets a comment block explaining what it exercises and why it exists.

### 2.3 How to Build the Golden Dataset

```
Phase 1 (Pre-implementation):
  - Create FIX-DOCX-SIMPLE and FIX-PDF-SIMPLE by hand in Word/LibreOffice
  - Manually produce the expected Markdown output
  - These become the first passing tests

Phase 2 (During implementation):
  - Generate FIX-DOCX-COMPLEX and FIX-PDF-COMPLEX from real exam banks
    (anonymized — strip student names, school names)
  - Run the converter, review the output, commit as expected

Phase 3 (Edge cases):
  - Create corrupt files by truncating valid files with `dd`
  - Create encrypted PDFs with a known password
  - Use ICU or native tools to generate Unicode-heavy documents
```

---

## 3. Snapshot Testing

Snapshot testing catches unexpected output changes. For a document converter, output IS the product — snapshots are essential.

### 3.1 Strategy

```
Approach: "Golden file comparison with semantic diffing"

For each fixture:
  1. Run conversion
  2. Compare output to .expected.md
  3. On mismatch:
     a. If structural diff (missing headings, lost content) → FAIL
     b. If cosmetic diff (extra blank line, trailing whitespace) → WARN, update if intentional
```

### 3.2 Snapshot Categories

| Category | What to Assert | Tolerance |
|----------|---------------|-----------|
| **Content completeness** | Every paragraph from source appears in output | Zero tolerance — any missing content is a FAIL |
| **Heading structure** | Heading hierarchy matches source | Zero tolerance for structural loss |
| **List formatting** | Bullet points and numbered lists preserved | Zero tolerance for list corruption |
| **Table formatting** | Tables render as valid Markdown tables | Zero tolerance for table loss |
| **Bold/Italic** | Inline formatting preserved (DOCX only) | Zero tolerance — `**bold**` must appear |
| **Whitespace** | No double-blank-lines, no trailing spaces | Cosmetic — fixable by normalizer |
| **Encoding** | UTF-8 throughout, no replacement characters | Zero tolerance |
| **Page breaks** (PDF) | `\n\n---\n\n` between pages | Zero tolerance for missing breaks |
| **Metadata** | `title`, `pageCount`, `author` populated | Warn if null, fail if wrong |

### 3.3 Diff Tooling

```
Implementation approach:
  - Use JUnit 5's assertLinesMatch() for ordered line comparison
  - Use a custom MarkdownDiffer that classifies diffs as STRUCTURAL | COSMETIC | NOISE
  - Structural diffs always fail
  - Cosmetic diffs produce warnings and are auto-fixable via an "update snapshots" Gradle task
  - On CI: structural diff = build failure, cosmetic diff = build warning

Example assertion:
  ConversionResult result = converter.convert(fixture);
  MarkdownDiff diff = MarkdownDiffer.compare(result.markdown(), expectedMd);
  assertThat(diff.structuralDiffs()).isEmpty();
  // cosmetic diffs logged but not blocking
```

### 3.4 Snapshot Update Process

```
When to update snapshots:
  1. Intentional normalizer improvement (e.g., "we now strip trailing whitespace")
  2. Pandoc version upgrade changes output format
  3. OpenDataLoader SDK upgrade changes text extraction

How to update:
  1. Run: ./gradlew updateSnapshots -Pfixture=FIX-DOCX-SIMPLE
  2. Review the diff in the PR
  3. Human approves the new expected output
  4. Commit updated .expected.md

NEVER:
  - Bulk-update all snapshots without review
  - Update snapshots to fix a failing test without understanding why
  - Auto-update on CI
```

---

## 4. Edge Case Matrix

### 4.1 Input Validation Edge Cases

| Edge Case | Input | Expected Outcome | Test Type |
|-----------|-------|------------------|-----------|
| Empty file | 0-byte `.docx` | 400 + "File is empty" | Unit + Integration |
| Zero-byte `.pdf` | 0-byte `.pdf` | 400 + "File is empty" | Unit + Integration |
| Wrong extension, valid content | Rename `.txt` to `.docx` | 400 or Pandoc error — either acceptable, must not crash | Integration |
| Double extension | `exam.docx.pdf` | Resolve by last extension (`.pdf`) | Unit |
| Uppercase extension | `exam.DOCX` | Must work — case-insensitive resolution | Unit + Integration |
| No extension | `exam` (no dot) | 400 + "Unsupported format" | Unit |
| Null filename | Multipart with no filename | 400 — don't NPE | Unit |
| Filename with path traversal | `../../etc/passwd.docx` | Sanitized — no filesystem access | Unit |
| Filename with special chars | `exám (copy) [final].docx` | Handled gracefully | Integration |
| Exactly 10MB | 10,000,000 bytes `.docx` | 400 — boundary, not 413 | Integration |
| 10MB + 1 byte | 10,000,001 bytes | 413 | Integration |
| 9.99MB | 9,999,999 bytes | 200 — just under limit | Integration |

### 4.2 PDF-Specific Edge Cases

| Edge Case | Description | Expected Outcome | Test Type |
|-----------|-------------|------------------|-----------|
| Scanned PDF | Image-only, no text layer | Empty or near-empty output. Document as known limitation. | Integration |
| Encrypted/password-protected | Requires password to open | 422 + "Cannot process encrypted PDF" | Integration |
| Corrupt PDF | Truncated file, bad xref table | 422 + descriptive error. Temp files cleaned up. | Integration |
| PDF with forms | Fillable form fields | Form field values extracted as text | Integration |
| PDF with annotations | Comments, highlights | Annotations ignored or extracted separately | Integration |
| Multi-column layout | Academic paper style | Columns collapsed to single reading order | Integration |
| PDF with embedded images | Image-heavy document | Images ignored, surrounding text preserved | Integration |
| Very large PDF (500+ pages) | Performance test | Completes in <30s, memory stable | Performance |
| PDF with CJK characters | Chinese/Japanese/Korean text | Characters preserved, no encoding errors | Integration |
| PDF with right-to-left text | Arabic, Hebrew | Text preserved, direction may be lost (acceptable) | Integration |
| PDF with math formulas | LaTeX equations | May be garbled — document as limitation | Integration |
| PDF with watermarks | Semi-transparent overlays | Watermark text may appear in output (acceptable, document) | Integration |
| Empty pages | Pages with no text content | No empty page markers in output | Unit |

### 4.3 DOCX-Specific Edge Cases

| Edge Case | Description | Expected Outcome | Test Type |
|-----------|-------------|------------------|-----------|
| Track changes | Revision history present | All changes accepted, clean output | Integration |
| Comments | Reviewer comments | Comments stripped from output | Integration |
| Embedded objects | Excel charts, OLE objects | Objects ignored, text preserved | Integration |
| Complex tables | Merged cells, nested tables | Best-effort Markdown table | Integration |
| Nested lists (5+ levels) | Deep indentation | Preserved as Markdown (Pandoc handles this) | Integration |
| Custom styles | Non-standard Word styles | Mapped to closest Markdown equivalent | Integration |
| Headers and footers | Page numbers, doc title | May appear in output — document behavior | Integration |
| Table of contents | Auto-generated TOC | TOC links may not work in Markdown (acceptable) | Integration |
| Macros | VBA macros present | Macros ignored, content extracted | Integration |
| .doc vs .docx | Old binary format | 400 — explicitly not supported in MVP | Unit |
| DOCX with no content | Valid file, empty body | Empty or minimal Markdown, no crash | Integration |
| Very large DOCX (200+ pages) | Performance | Completes in <30s | Performance |

### 4.4 System / Infrastructure Edge Cases

| Edge Case | Description | Expected Outcome | Test Type |
|-----------|-------------|------------------|-----------|
| Pandoc not installed | Missing system dependency | 503 + "Pandoc not found on system" | Integration |
| Pandoc crashes mid-conversion | Process killed, segfault | 500 + stderr message, temp files cleaned | Integration |
| Pandoc timeout (>30s) | Hung process | 500 + "Conversion timed out", process killed | Integration |
| Disk full | No space for temp files | 500 + descriptive error | Integration |
| Temp file not cleaned up | Verify cleanup after every conversion | No temp files in `/tmp/docconv` after test | Integration |
| Concurrent conversion of same file | Race condition | Each gets unique UUID, no collision | Integration |
| Memory pressure | 50 concurrent large file conversions | Graceful degradation, no OOM | Performance |
| Conversion store TTL expiry | Result stored >60 min ago | 404 on download — "Result expired" | Unit |

### 4.5 API Edge Cases

| Edge Case | Description | Expected Outcome | Test Type |
|-----------|-------------|------------------|-----------|
| POST without `file` field | Missing multipart field | 400 + "No file provided" | Unit |
| POST with multiple files | Two files in one request | 400 — single file only in MVP | Unit |
| GET with invalid UUID | Malformed ID | 400 or 404 | Unit |
| GET after store eviction | ID was valid, now expired | 404 | Unit |
| Large response body | 500-question Markdown (~500KB) | Response completes, no truncation | Integration |
| Content-Type wrong | `application/json` instead of `multipart/form-data` | 400 or 415 | Unit |
| CORS headers | Frontend on :3000, backend on :8080 | Correct `Access-Control-Allow-Origin` | Integration |

---

## 5. Validation Rules: Measuring "Good" vs "Bad" Conversion

We need quantitative metrics, not vibes. Every conversion is scored.

### 5.1 Conversion Quality Score (CQS)

```
CQS = (completeness * 0.4) + (structure * 0.3) + (fidelity * 0.2) + (cleanliness * 0.1)

Where:
  completeness = percentage of source paragraphs present in output (0.0 - 1.0)
  structure    = percentage of headings/lists/tables correctly represented (0.0 - 1.0)
  fidelity     = percentage of inline formatting preserved — bold, italic, code (0.0 - 1.0)
  cleanliness  = 1.0 minus penalty for encoding artifacts, double-blanks, trailing spaces
```

### 5.2 Scoring Thresholds

| CQS Range | Verdict | Action |
|-----------|---------|--------|
| 0.95 - 1.0 | **Excellent** | Ship it |
| 0.85 - 0.94 | **Good** | Ship with known limitations documented |
| 0.70 - 0.84 | **Acceptable** | Ship for simple documents only, file issues for edge cases |
| 0.50 - 0.69 | **Poor** | Do not ship. Fix before release. |
| < 0.50 | **Broken** | Block release. Fundamental extraction failure. |

### 5.3 Per-Element Validation Rules

#### Completeness Checks

```java
// Every paragraph in the source document must appear (fuzzy-matched) in the output
assertThat(output).containsParagraphFrom(source, threshold=0.8);

// No section of the source can be entirely missing
assertThat(output).hasAtLeast(source.paragraphCount() * 0.9, "paragraphs");

// For exam documents: every question number must appear
assertAll(
  () -> assertThat(output).contains("Question 1"),
  () -> assertThat(output).contains("Question 2"),
  // ... up to N
);
```

#### Structure Checks

```java
// Heading hierarchy must be non-decreasing (no h3 under h1 without h2)
assertThat(markdown).hasValidHeadingHierarchy();

// Lists must be well-formed (no orphan list items)
assertThat(markdown).hasWellFormedLists();

// Tables must have consistent column counts
assertThat(markdown).hasConsistentTables();

// No raw HTML leaking through (unless intentional)
assertThat(markdown).doesNotContainPattern("<div|<span|<table(?!.*\\|)");
```

#### Fidelity Checks

```java
// Bold text from DOCX must appear as **bold** in Markdown
assertThat(markdown).contains("**important term**");

// Italic text must appear as *italic*
assertThat(markdown).contains("*emphasis*");

// Inline code must appear as `code`
assertThat(markdown).contains("`code snippet`");
```

#### Cleanliness Checks

```java
// No more than 2 consecutive blank lines
assertThat(markdown).doesNotContain("\n\n\n\n");

// No trailing whitespace on any line
assertThat(markdown.lines())
  .noneMatch(line -> line.endsWith(" ") || line.endsWith("\t"));

// Valid UTF-8, no replacement character
assertThat(markdown).doesNotContain("\uFFFD");

// No raw Pandoc artifacts (e.g., ":::" for divs)
assertThat(markdown).doesNotContain(":::");

// No page number artifacts like "Page 3 of 50"
assertThat(markdown).doesNotContainPattern("Page \\d+ of \\d+");
```

### 5.4 Performance Validation

| Metric | Threshold | Measurement |
|--------|-----------|-------------|
| Simple DOCX (5 questions) | < 2s end-to-end | Integration test timer |
| Complex DOCX (50 questions) | < 5s end-to-end | Integration test timer |
| Large DOCX (500 questions) | < 30s end-to-end | Performance test |
| Simple PDF (5 pages) | < 3s end-to-end | Integration test timer |
| Large PDF (100 pages) | < 10s end-to-end | Performance test |
| API response time (p95) | < 5s | Load test with 10 concurrent users |
| Memory usage (large PDF) | < 512MB heap | JVM monitoring during test |

---

## 6. Rejection Criteria: When QA Blocks a Release

### 6.1 Absolute Blockers (P0) — Release CANNOT proceed

| # | Criterion | Rationale |
|---|-----------|-----------|
| 1 | Any fixture produces CQS < 0.50 | Fundamental extraction failure — product doesn't work |
| 2 | Data loss: source content missing from output with no error | Silent data loss is the worst failure mode for a converter |
| 3 | Conversion crashes the server (unhandled exception, OOM) | Reliability — server must stay up |
| 4 | Temp files not cleaned up after conversion | Disk exhaustion in production |
| 5 | Security: path traversal, filename injection, file overwrite | Security vulnerability |
| 6 | Encrypted/corrupt PDF causes infinite hang (no timeout) | Denial of service via file upload |
| 7 | Pandoc not installed → user gets 500 instead of 503 | Misleading error, unfixable by user |
| 8 | Download endpoint returns wrong file or another user's file | Data leak |

### 6.2 Should Block (P1) — Release should not proceed without agreement

| # | Criterion | Rationale |
|---|-----------|-----------|
| 1 | Any DOCX fixture produces CQS < 0.85 | Word conversion is our strong suit — it must be solid |
| 2 | Any PDF fixture produces CQS < 0.70 | PDF is harder, but still must be usable |
| 3 | Performance regression: >20% slower than previous release | Users notice speed |
| 4 | Unicode content garbled in any fixture | Internationalization failure |
| 5 | E2E happy path test failing | Core user flow broken |
| 6 | More than 3 known edge case failures unfixed | Accumulating tech debt |

### 6.3 Ship with Warning (P2) — Track and fix in next sprint

| # | Criterion | Rationale |
|---|-----------|-----------|
| 1 | Scanned PDF produces empty output | Known limitation — document it |
| 2 | PDF multi-column layout reading order imperfect | Heuristic, acceptable for MVP |
| 3 | PDF tables not extracted as Markdown tables | SDK limitation |
| 4 | Math formulas garbled in either format | Niche feature, low priority |
| 5 | Cosmetic Markdown differences (extra blank lines) | Aesthetics, not correctness |
| 6 | PDF watermarks appear in output | Annoying but not blocking |

### 6.4 Release Gate Checklist

Before every release, QA runs this checklist and signs off:

```markdown
## Release QA Sign-off — vX.Y.Z

### Automated Gates (CI must pass)
- [ ] All unit tests passing (0 failures)
- [ ] All integration tests passing (0 failures)
- [ ] All E2E tests passing (0 failures)
- [ ] No P0 issues open
- [ ] CQS scores for all fixtures above thresholds
- [ ] Performance benchmarks within thresholds
- [ ] Temp file cleanup test passing

### Manual Gates (QA reviews)
- [ ] Spot-checked 3 random DOCX files from real exam banks
- [ ] Spot-checked 3 random PDF files from real exam banks
- [ ] Downloaded .md file renders correctly in 2+ Markdown viewers
- [ ] Error messages are user-friendly (not stack traces)
- [ ] Known limitations documented in RELEASE_NOTES.md

### Sign-off
- QA Engineer: _______________  Date: _______________
- Verdict: [ ] SHIP  [ ] BLOCK  [ ] SHIP WITH WARNINGS
```

---

## 7. Test Infrastructure Requirements

### 7.1 CI Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Compile     │───▶│  Unit Tests  │───▶│ Integration  │───▶│     E2E      │
│              │    │   (~2 min)   │    │  (~5 min)    │    │  (~3 min)    │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
       │                  │                   │                    │
       ▼                  ▼                   ▼                    ▼
   Fail fast         Fail fast          Generate CQS          Screenshot
                                        Report                on failure
```

### 7.2 Test Environment

| Requirement | Specification |
|-------------|--------------|
| Java | 11+ (match production) |
| Pandoc | Latest stable (match production version) |
| Node.js | 18+ (frontend E2E only) |
| Disk | 1GB free for temp files during tests |
| Memory | 2GB heap for large file tests |
| Pandoc version pinned | `pandoc --version` checked in CI setup |

### 7.3 Test Data Seeding

```
For integration tests:
  1. Fixtures checked into src/test/resources/fixtures/
  2. Expected outputs checked into src/test/resources/fixtures/expected/
  3. Large fixtures use Git LFS
  4. CI caches Pandoc binary (don't brew install on every run)
```

---

## 8. Testing Anti-Patterns to Avoid

| Anti-Pattern | Why It's Bad | What to Do Instead |
|-------------|-------------|-------------------|
| Testing only HTTP status codes | A 200 with empty body is still broken | Assert on Markdown content, not just status |
| Mocking Pandoc in integration tests | Hides real Pandoc behavior differences | Use real Pandoc, skip if unavailable |
| Generating fixtures at test time | Non-deterministic, hard to reproduce | Check in fixtures as static files |
| "It works for my test file" | One file isn't coverage | Use the full fixture matrix |
| Ignoring temp file cleanup | Disk exhaustion | Assert temp dir is empty after every test |
| Snapshot auto-update | Masks regressions | Human review required for snapshot changes |
| Testing implementation details | Fragile tests, blocks refactoring | Test input → output behavior |
| Skipping Unicode tests | "We only support English" isn't a strategy for exam content with math | Test CJK, Arabic, math symbols |
| Performance tests only on fast machines | Masks real-world slowness | Run on CI (typically slower), set realistic thresholds |

---

## 9. Metrics & Reporting

### 9.1 Test Health Dashboard (CI)

```
Published after every CI run:
  - Total tests: N
  - Pass rate: X%
  - CQS by fixture (table + trend)
  - Performance by fixture (table + trend)
  - Coverage: line coverage + branch coverage
  - Flaky test count (tests that passed on retry)
```

### 9.2 Quality Trend Tracking

```
Track over time:
  - CQS per fixture (regression detection)
  - Test count growth
  - Mean conversion time per format
  - Edge case coverage (% of matrix covered)
```

---

## 10. Summary: What Gets Tested and When

| Phase | Tests Written | Fixtures Needed | Gate |
|-------|--------------|-----------------|------|
| **Parser implementation** | Unit tests for each parser, normalizer | FIX-DOCX-SIMPLE, FIX-PDF-SIMPLE | All unit tests green |
| **Service integration** | Integration tests for full pipeline | All simple + complex fixtures | CQS > 0.85 for DOCX, > 0.70 for PDF |
| **API layer** | Contract tests, error handling | Edge case fixtures (corrupt, empty, etc.) | All error paths covered |
| **Frontend** | E2E tests | 1 DOCX + 1 PDF for happy path | Full user flow works |
| **Performance** | Load tests, large file tests | FIX-DOCX-LARGE, FIX-PDF-LARGE | Within thresholds |
| **Release** | Full regression suite | All fixtures | Release gate checklist signed |

---

> **QA Authority Statement:** This strategy is not aspirational — it is a contract. If the team cannot measure conversion quality, the team cannot ship. Every fixture without an expected output is an untested requirement. Every CQS score below threshold is a bug. Block the release.
