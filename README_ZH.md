# DocConv

文档格式转换工具 - 将 Word/PDF 考试文档转换为干净的 Markdown 格式。

## 功能特性

- **单文件上传** - 上传 .docx 或 .pdf 文件进行转换
- **Markdown 输出** - 转换为干净、结构化的 Markdown
- **浏览器预览** - 直接在浏览器中预览转换后的内容
- **下载支持** - 下载转换后的 Markdown 文件

## 技术栈

| 层级    | 技术                          |
|---------|-------------------------------|
| 前端    | Nuxt + Nuxt UI + TypeScript  |
| 后端    | Java + Spring Boot           |
| PDF     | OpenDataLoader PDF SDK       |
| Word    | Pandoc CLI                   |

## 前置要求

- Java 21+
- Pandoc（用于 Word 文档转换）
- Node.js（用于前端开发）

### 安装 Pandoc

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
# 或从 https://pandoc.org/installing.html 下载安装包
```

**验证安装**
```bash
pandoc --version
```

## 快速开始

### 后端启动

```bash
cd server
./gradlew bootRun
```

API 服务启动于 `http://localhost:8080`。

### 前端启动

```bash
cd web
pnpm install
pnpm dev
```

前端启动于 `http://localhost:3000`。

## API 接口

### 转换文档

```
POST /api/convert
Content-Type: multipart/form-data

请求体:
  - file: 文档文件（.docx 或 .pdf）

响应:
{
  "markdown": "..."
}
```

### 下载转换文件

```
GET /api/convert/{id}/download

返回: .md 文件
```

## 项目结构

```
docconv/
├── server/          # Java Spring Boot 后端
│   └── src/
│       └── main/java/com/docconv/
│           └── ...
└── web/             # Nuxt.js 前端
    ├── app/
    └── ...
```
