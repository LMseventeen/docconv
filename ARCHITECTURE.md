# System Architecture: Document-to-Markdown Conversion Tool

> Author: System Architect
> Date: 2026-04-29
> Status: Design (Pre-implementation)

---

## 1. System Diagram

```
                          ┌──────────────────────────────────────────┐
                          │           FRONTEND (Nuxt 3)              │
                          │                                          │
                          │  ┌─────────┐    ┌──────┐    ┌────────┐  │
                          │  │ Upload   │───▶│ API  │───▶│Preview │  │
                          │  │ Component│    │Client│    │+Export │  │
                          │  └─────────┘    └──────┘    └────────┘  │
                          └──────────────┬───────────────────────────┘
                                         │
                            HTTP multipart/form-data
                            POST /api/convert
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     BACKEND (Spring Boot)                            │
│                                                                      │
│  ┌─────────────┐   ┌──────────────┐   ┌───────────────────────────┐ │
│  │ Controller   │──▶│ Convert      │──▶│ Parser Registry           │ │
│  │ Layer        │   │ Service      │   │                           │ │
│  │              │   │ (Orchestr.)  │   │  ┌─────────────────────┐  │ │
│  └─────────────┘   └──────────────┘   │  │ DocumentParser      │  │ │
│                                        │  │ <<interface>>       │  │ │
│                                        │  └────────┬────────────┘  │ │
│                                        │           │               │ │
│                                        │     ┌─────┴──────┐       │ │
│                                        │     │             │       │ │
│                                        │  ┌──▼───┐  ┌─────▼────┐  │ │
│                                        │  │PdfP  │  │WordP     │  │ │
│                                        │  │arser │  │arser     │  │ │
│                                        │  └──┬───┘  └─────┬────┘  │ │
│                                        │     │             │       │ │
│  ┌─────────────┐   ┌──────────────┐   │     │             │       │ │
│  │ /api/convert│   │ Cache /      │   └─────┼─────────────┼───────┘ │
│  │ /{id}/down  │   │ TempStorage  │         │             │         │
│  └─────────────┘   └──────────────┘         ▼             ▼         │
│                                   ┌──────────────┐ ┌────────────┐  │
│                                   │OpenDataLoader│ │ Pandoc CLI │  │
│                                   │  PDF SDK     │ │ (subprocess│  │
│                                   └──────────────┘ └────────────┘  │
│                                          │                │         │
│                                          ▼                ▼         │
│                                   ┌─────────────────────────────┐  │
│                                   │   MarkdownNormalizer        │  │
│                                   │   (shared post-processing)  │  │
│                                   └──────────────┬──────────────┘  │
│                                                  │                  │
└──────────────────────────────────────────────────┼──────────────────┘
                                                   │
                                                   ▼
                                    ┌─────────────────────────────┐
                                    │       Markdown Result       │
                                    │  { id, markdown, metadata } │
                                    └─────────────────────────────┘
```

---

## 2. Data Flow

### 2.1 Happy Path — PDF Upload

```
User selects .pdf
    │
    ▼
Nuxt frontend validates extension + size (< 10MB for MVP)
    │
    ▼
POST /api/convert  (multipart, field: "file")
    │
    ▼
ConvertController receives MultipartFile
    │
    ▼
ConvertService.resolveParser("file.pdf")
    → matches PdfParser (by extension)
    │
    ▼
PdfParser.parse(inputStream)
    │
    ├─ Write temp file to disk (PDF SDK needs file path)
    ├─ Call OpenDataLoader SDK: LoaderFactory.createPdfLoader(path).load()
    ├─ Receive List<Document>  (one per page)
    ├─ Concatenate page text with structural hints
    └─ Return RawDocument { text, metadata: { pageCount, title } }
    │
    ▼
MarkdownNormalizer.normalize(rawDocument)
    ├─ Fix encoding issues
    ├─ Normalize whitespace / line breaks
    └─ Apply Markdown conventions (headings, lists, bold/italic)
    │
    ▼
Store result in ConversionCache (keyed by UUID)
    │
    ▼
Return { id: "abc-123", markdown: "...", metadata: { ... } }
    │
    ▼
Nuxt renders Markdown preview (markdown-it or similar)
User can click Download → GET /api/convert/abc-123/download → .md file
```

### 2.2 Happy Path — Word Upload

```
User selects .docx
    │
    ▼
POST /api/convert  (multipart, field: "file")
    │
    ▼
ConvertService.resolveParser("file.docx")
    → matches WordParser (by extension)
    │
    ▼
WordParser.parse(inputStream)
    │
    ├─ Write temp file to disk (Pandoc needs file path)
    ├─ Spawn Pandoc process: pandoc input.docx -t markdown --wrap=none
    ├─ Capture stdout (the Markdown output)
    ├─ On failure: capture stderr, throw ConversionException
    └─ Return RawDocument { text, metadata: { pageCount?, title? } }
    │
    ▼
MarkdownNormalizer.normalize(rawDocument)
    │
    ▼
Same cache → return → preview flow as PDF path.
```

### 2.3 Error Flow

```
POST /api/convert
    │
    ├─ Invalid extension (.exe, .jpg)     → 400 Bad Request
    ├─ Empty / oversized file              → 400 Bad Request
    ├─ Pandoc not installed on system      → 500 + descriptive error
    ├─ PDF SDK exception (corrupt file)    → 422 Unprocessable Entity
    └─ Unknown error                       → 500 Internal Server Error
```

---

## 3. Key Abstractions

### 3.1 Core Interfaces

```java
/**
 * Every document format implements this interface.
 * Adding a new format = one new class + register in the factory.
 */
public interface DocumentParser {

    /** File extensions this parser handles, e.g. {"pdf"} */
    Set<String> supportedExtensions();

    /** Parse an uploaded file into our intermediate representation. */
    RawDocument parse(InputStream input, String originalFilename)
        throws ConversionException;
}
```

```java
/**
 * Intermediate representation between parsing and Markdown output.
 * Parsers produce this; the normalizer consumes it.
 */
public record RawDocument(
    String content,            // extracted text / preliminary Markdown
    String sourceFormat,       // "pdf" or "docx"
    DocumentMetadata metadata  // title, pageCount, author, etc.
) {}
```

```java
public record DocumentMetadata(
    String title,
    Integer pageCount,
    String author,
    Instant parsedAt
) {}
```

```java
/**
 * Post-processing pass that all parser output goes through.
 * Ensures consistent Markdown quality regardless of source format.
 */
public interface MarkdownNormalizer {
    String normalize(RawDocument raw);
}
```

```java
/**
 * Holds conversion results for download-by-ID.
 * In-memory for MVP; replaceable with file-based or DB storage.
 */
public interface ConversionStore {
    void store(String id, ConversionResult result);
    Optional<ConversionResult> retrieve(String id);
}
```

### 3.2 Service Layer

```java
/**
 * The orchestrator. Thin layer that:
 * 1. Selects the right parser via registry
 * 2. Calls parse()
 * 3. Runs normalizer
 * 4. Stores result
 * 5. Returns response DTO
 */
@Service
public class ConvertService {
    private final ParserRegistry registry;
    private final MarkdownNormalizer normalizer;
    private final ConversionStore store;

    public ConvertResult convert(MultipartFile file);
}
```

```java
/**
 * Extension → Parser lookup. Populated at startup via Spring DI.
 */
@Component
public class ParserRegistry {
    private final Map<String, DocumentParser> parsers;

    public ParserRegistry(List<DocumentParser> allParsers) {
        // index by supportedExtensions()
    }

    public DocumentParser resolve(String filename);
}
```

---

## 4. Module Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│  com.docconv                                                │
│                                                             │
│  ├── api/                  ← HTTP boundary only             │
│  │   ├── ConvertController       (receive file, return JSON)│
│  │   └── ConvertResponse         (DTO for API response)     │
│  │                                                           │
│  ├── service/              ← Business logic                 │
│  │   ├── ConvertService          (orchestration)            │
│  │   ├── MarkdownNormalizer      (text cleanup / formatting)│
│  │   └── ConversionStore         (result caching interface) │
│  │                                                           │
│  ├── parser/               ← Format-specific extraction     │
│  │   ├── DocumentParser          (interface)                │
│  │   ├── ParserRegistry          (extension → parser map)   │
│  │   ├── PdfParser               (OpenDataLoader SDK)       │
│  │   ├── WordParser              (Pandoc CLI)               │
│  │   └── model/                                              │
│  │       ├── RawDocument                                      │
│  │       └── DocumentMetadata                                 │
│  │                                                           │
│  ├── config/               ← Spring configuration           │
│  │   ├── PandocConfig            (path to pandoc binary)    │
│  │   ├── StorageConfig           (temp dir, cache limits)   │
│  │   └── WebConfig               (CORS, multipart limits)  │
│  │                                                           │
│  └── exception/            ← Error types                    │
│      ├── ConversionException     (base)                     │
│      ├── UnsupportedFormatException                          │
│      └── ParserExecutionException                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Responsibility Matrix

| Module             | Knows About                    | Does NOT Know About       |
|--------------------|--------------------------------|---------------------------|
| `api/`             | `service/`, response DTOs      | parser internals, Pandoc  |
| `service/`         | `parser/` interfaces, `config` | specific parser impls     |
| `parser/PdfParser` | OpenDataLoader SDK, `model/`   | HTTP, Spring MVC          |
| `parser/WordParser`| Pandoc CLI, `model/`           | HTTP, OpenDataLoader      |
| `config/`          | Environment, Spring beans      | Business logic            |

---

## 5. Intermediate Representation (IR)

### 5.1 Design Goals

- **Format-agnostic**: Both PDF and Word parsers produce the same structure.
- **Lossy but sufficient**: We lose some original structure (e.g., exact PDF layout), but retain what matters for Markdown (headings, paragraphs, bold, lists, tables).
- **Simple**: One level of nesting — no deep AST needed for MVP.

### 5.2 IR Structure

```
RawDocument
├── content: String           ← the main payload
├── sourceFormat: "pdf" | "docx"
└── metadata: DocumentMetadata
    ├── title: String?
    ├── pageCount: Integer?
    ├── author: String?
    └── parsedAt: Instant
```

For MVP, `content` is **raw text with Markdown-compatible formatting already partially applied** by the parser. This is deliberate — it keeps the IR simple and avoids an AST that neither parser can reliably produce.

### 5.3 What Each Parser Produces in `content`

| Element         | PDF (OpenDataLoader)                    | Word (Pandoc)              |
|-----------------|-----------------------------------------|----------------------------|
| Headings        | Detected by font size / weight heuristics (or not — falls back to plain text) | Preserved as `#`, `##`, etc. by Pandoc |
| Bold / Italic   | Not reliably extracted — treated as plain text | Preserved as `**bold**`, `*italic*` |
| Lists           | Detected as lines starting with bullets/numbers | Preserved as `- item` or `1. item` |
| Tables          | Not extracted in MVP                    | Preserved as Markdown tables |
| Images          | Ignored                                 | Ignored (stripped)         |
| Math / LaTeX    | Ignored                                 | May pass through if present |

### 5.4 Why Not a Structured AST?

```
AST approach (rejected for MVP):
  Document
    ├── Heading(level=2, children=[Text("Question 1")])
    ├── Paragraph(children=[Text("The "), Bold("capital"), Text(" of France...")])
    └── List(ordered=false, children=[ListItem("A"), ListItem("B")])

Text-with-hints approach (chosen for MVP):
  "## Question 1\nThe **capital** of France...\n- A\n- B\n"
```

Rationale:
1. **OpenDataLoader SDK does not produce an AST.** It returns plain text per page. Building an AST would require re-parsing that text — fragile and unnecessary.
2. **Pandoc already produces Markdown.** Its output IS the Markdown we want. Wrapping it in an AST just to unwrap it again is wasted work.
3. **MVP scope is narrow.** A structured AST becomes valuable when we need to re-render to multiple formats, run AI on structured blocks, or do fine-grained editing. None of that is in scope.
4. **Extensibility path is clear.** If an AST is needed later, the `RawDocument` content string can be parsed into one in a `v2` normalizer without changing the parser contract.

### 5.5 Extensibility for Post-MVP

When the time comes for structured output:

```
RawDocument (v2)
├── content: String              ← backward-compatible
├── elements: List<DocumentElement>  ← optional structured blocks
│   ├── HeadingElement
│   ├── ParagraphElement
│   ├── ListElement
│   └── TableElement
├── sourceFormat: String
└── metadata: DocumentMetadata
```

The `DocumentParser` interface does not change — only the `RawDocument` record gains a new optional field. Parsers that can produce structured data populate `elements`; others leave it null and the normalizer handles it.

---

## 6. PDF Parser — OpenDataLoader Integration

### 6.1 Processing Pipeline

```
InputStream (from upload)
    │
    ▼
Write to temp file  (PDF SDK requires file path, not stream)
    │
    ▼
PdfDocument pdf = PDFDocumentFactory.createDocument(tempFile)
    │
    ▼
LoaderFactory.createPdfLoader(tempFile).load()
    │
    ▼
List<Document> pages = result.getDocuments()
    │
    ▼
For each page:
    page.getContent()  →  String (page text)
    │
    ▼
Concatenate with page separators: "\n\n---\n\n"
    │
    ▼
Run heuristic heading detection:
    - Lines that are significantly shorter than average
    - Lines that are ALL CAPS or Title Case
    - Lines followed by a blank line
    → Prefix with "## " if heuristic matches
    │
    ▼
Return RawDocument(content, "pdf", metadata)
```

### 6.2 Key Considerations

- **Performance**: ~0.015s/page per OpenDataLoader benchmarks. A 100-page exam = ~1.5s. Acceptable.
- **Temp file cleanup**: Always delete in a `finally` block or use `Files.createTempFile` with auto-cleanup.
- **Scanned PDFs**: OpenDataLoader may not handle scanned (image-only) PDFs well. For MVP, this is out of scope — document it as a known limitation.
- **Memory**: Load one page at a time if documents are large. The SDK supports page-by-page access.

---

## 7. Word Parser — Pandoc Integration

### 7.1 Processing Pipeline

```
InputStream (from upload)
    │
    ▼
Write to temp .docx file
    │
    ▼
Build Pandoc command:
    pandoc <input.docx> --to=markdown --wrap=none --no-highlight
    │
    ▼
ProcessBuilder.start()
    │
    ├─ stdin:  (unused — file path passed as arg)
    ├─ stdout: → String (Markdown content)
    └─ stderr: → String (error messages, if any)
    │
    ▼
if exitCode != 0:
    throw ParserExecutionException(stderr)
    │
    ▼
Return RawDocument(stdout, "docx", metadata)
```

### 7.2 Pandoc Command Flags

| Flag              | Purpose                                             |
|-------------------|-----------------------------------------------------|
| `--to=markdown`   | Output GitHub-flavored Markdown                     |
| `--wrap=none`     | Don't wrap lines at 80 chars (preserve paragraphs)  |
| `--no-highlight`  | Don't syntax-highlight code blocks                  |
| `--standalone`    | NOT used (adds YAML header we don't want)           |

### 7.3 Key Considerations

- **Pandoc must be on PATH.** Spring Boot config provides the full path as a fallback (`/usr/local/bin/pandoc`).
- **Timeout**: 30-second kill timeout per invocation. Prevents hangs on corrupt files.
- **Large files**: Pandoc spawns a child process. Memory usage is external to JVM — no heap pressure.
- **Encoding**: Force UTF-8 output via `--to=markdown+smart` if needed.

---

## 8. Frontend Architecture

### 8.1 Component Tree

```
app.vue
└── pages/
    └── index.vue
        ├── FileUpload.vue         ← drag-and-drop + click-to-browse
        ├── ConversionProgress.vue ← loading spinner during conversion
        ├── MarkdownPreview.vue    ← rendered Markdown (v-html or markdown-it)
        └── DownloadButton.vue     ← triggers .md file download
```

### 8.2 State Flow

```
[state: idle]
    │ user selects file
    ▼
[state: uploading]
    │ POST /api/convert
    ▼
[state: converting]     (server-side processing)
    │ response received
    ▼
[state: preview]         ← shows Markdown + Download button
    │ user clicks Download
    ▼
GET /api/convert/{id}/download → browser downloads .md
```

---

## 9. Error Handling Strategy

### 9.1 Error Classification

| Error Type               | HTTP Code | Example                         | User Message                         |
|--------------------------|-----------|---------------------------------|--------------------------------------|
| `ValidationException`    | 400       | Wrong file type, empty file     | "Please upload a .docx or .pdf file" |
| `UnsupportedFormat`      | 400       | .xlsx, .pptx                    | "Format not supported"               |
| `FileTooLarge`           | 413       | File exceeds limit              | "File exceeds 10MB limit"            |
| `ParserExecution`        | 500       | Pandoc crash, SDK error         | "Conversion failed. Try again."      |
| `SystemDependency`       | 503       | Pandoc not installed            | "Service temporarily unavailable"    |

### 9.2 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ErrorResponse> handle(ConversionException e) {
        // map to appropriate HTTP status
        // log with correlation ID
        // return user-friendly message (not stack traces)
    }
}
```

---

## 10. Configuration

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

docconv:
  pandoc:
    path: pandoc              # resolved from PATH
    timeout-seconds: 30
  storage:
    temp-dir: /tmp/docconv     # temp files during conversion
    cache-expiry-minutes: 60   # in-memory result TTL
```

---

## 11. Deployment & Runtime Requirements

```
┌────────────────────────────────────────┐
│  Local Machine                         │
│                                        │
│  ┌──────────────┐  ┌───────────────┐  │
│  │  Nuxt Dev    │  │ Spring Boot   │  │
│  │  Server      │  │ Application   │  │
│  │  :3000       │  │ :8080         │  │
│  └──────┬───────┘  └───────┬───────┘  │
│         │                  │           │
│         └────── proxy ─────┘           │
│                                        │
│  Requires:                             │
│  - Java 11+                            │
│  - Pandoc installed (brew install)     │
│  - Node.js 18+ (frontend dev)          │
└────────────────────────────────────────┘
```

---

## 12. Key Design Decisions & Rationale

| Decision                                      | Rationale                                                                 |
|-----------------------------------------------|---------------------------------------------------------------------------|
| Text-based IR instead of AST                  | Neither parser natively produces an AST. Building one adds complexity with no MVP value. |
| Pandoc via CLI instead of library             | Pandoc's Java bindings are unmaintained. CLI is reliable, versioned, and easy to upgrade. |
| In-memory ConversionStore                     | MVP is single-user, local. No persistence needed. Replaceable interface for future DB. |
| ParserRegistry over if/else in service        | Open/Closed principle. New format = new class, no service modification.   |
| Temporary files over streams for parsing      | Both Pandoc and OpenDataLoader require file paths. Respecting tool constraints. |
| MarkdownNormalizer as separate pass           | Keeps parsers focused on extraction. Normalization logic is shared and testable independently. |
| No streaming output in MVP                    | Single-file upload. Response fits in memory. Streaming adds complexity for no user benefit. |

---

## 13. Future Extension Points

```
1. New format (e.g., .rtf, .html)
   → Add RtfParser implements DocumentParser
   → Register via @Component (Spring auto-discovers)

2. Structured AST for AI processing
   → Add elements: List<DocumentElement> to RawDocument
   → Parsers opt-in; normalizer handles fallback

3. Batch processing
   → Add /api/batch endpoint
   → Parallelize parser calls with CompletableFuture

4. Streaming output (SSE)
   → Replace POST response with SSE stream
   → Frontend renders incrementally

5. Cloud storage
   → Swap InMemoryConversionStore for S3/FileStore
   → No service/parser changes needed
```

---

## 14. Testing Strategy

| Layer          | Approach                                    | Tool               |
|----------------|---------------------------------------------|--------------------|
| Unit tests     | Parser logic, normalizer, registry          | JUnit 5 + Mockito  |
| Integration    | Controller → Service → Parser → Markdown    | @SpringBootTest    |
| Contract       | API response shape                          | Spring MockMvc     |
| E2E            | Full upload → preview → download            | Playwright (frontend) |
| Pandoc parser  | Test against sample .docx fixtures          | JUnit + test files |
| PDF parser     | Test against sample .pdf fixtures           | JUnit + test files |
