# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Document format conversion tool — convert Word/PDF exam documents to clean, structured Markdown.

## Product Spec

**One-line:** Turn your exam documents into clean structured content.

**User scenario:** Teachers have Word/PDF exam banks (500-1000 questions) that need to be imported into learning platforms. Manual copy-paste is time-consuming and loses formatting.

**MVP scope:** File upload → Markdown output. No AI, no editor, no batch processing.

### In MVP
- Single file upload
- .docx and .pdf formats
- Convert to Markdown
- Browser preview + download
- Local deployment

### Not in MVP
- Batch upload
- .doc format
- AI structuring
- Editor integration
- Streaming output

## Architecture

```
Frontend (Nuxt + Nuxt UI)        → Upload file, call API, preview/download Markdown
Backend (Java + Spring Boot)     → PDF via OpenDataLoader PDF SDK, Word via Pandoc CLI
```

## Tech Stack

| Layer    | Technology                      | Notes                          |
|----------|--------------------------------|--------------------------------|
| Frontend | Nuxt + Nuxt UI + TypeScript    | Vue ecosystem, rich components |
| Backend  | Java + Spring Boot             | Mature and stable              |
| PDF      | OpenDataLoader PDF (Java SDK)  | 0.015s/page, top benchmark     |
| Word     | Pandoc                         | Good structure retention, CLI  |
| Deploy   | Local                          | Requires Java 11+ and Pandoc   |

## API Design

```
POST /api/convert
  - Param: file (multipart/form-data)
  - Returns: { markdown: "..." }

GET /api/convert/{id}/download
  - Returns: .md file
```

## Dependencies

- Java 11+
- Pandoc (`brew install pandoc`)
- Node.js (frontend dev)
