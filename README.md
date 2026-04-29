# DocConv

A document format conversion tool that converts Word/PDF exam documents to clean, structured Markdown.

## Features

- **Single file upload** - Upload .docx or .pdf files for conversion
- **Markdown output** - Convert documents to clean, structured Markdown
- **Browser preview** - Preview converted content directly in the browser
- **Download support** - Download converted Markdown files

## Tech Stack

| Layer    | Technology                      |
|----------|--------------------------------|
| Frontend | Nuxt + Nuxt UI + TypeScript    |
| Backend  | Java + Spring Boot             |
| PDF      | OpenDataLoader PDF SDK         |
| Word     | Pandoc CLI                     |

## Prerequisites

- Java 21+
- Pandoc (for Word document conversion)
- Node.js (for frontend development)

### Install Pandoc

**macOS**
```bash
brew install pandoc
```

**Ubuntu/Debian**
```bash
sudo apt install pandoc
```

**Windows**
```powershell
winget install pandoc
# or download installer from https://pandoc.org/installing.html
```

**Verify**
```bash
pandoc --version
```

## Getting Started

### Backend

```bash
cd server
./gradlew bootRun
```

The API server starts at `http://localhost:8080`.

### Frontend

```bash
cd web
pnpm install
pnpm dev
```

The frontend starts at `http://localhost:3000`.

## API Endpoints

### Convert Document

```
POST /api/convert
Content-Type: multipart/form-data

Body:
  - file: the document file (.docx or .pdf)

Response:
{
  "markdown": "..."
}
```

### Download Converted File

```
GET /api/convert/{id}/download

Returns: .md file
```

## Project Structure

```
docconv/
├── server/          # Java Spring Boot backend
│   └── src/
│       └── main/java/com/docconv/
│           └── ...
└── web/             # Nuxt.js frontend
    ├── app/
    └── ...
```
