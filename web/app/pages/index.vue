<script setup lang="ts">
import { ref, computed } from 'vue'
import { useConvert } from '~/composables/useConvert'
import MarkdownIt from 'markdown-it'

const {
  state,
  markdown,
  errorMessage,
  fileName,
  isStreaming,
  convertFileStream,
  downloadMarkdown,
  reset
} = useConvert()

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true
})

const renderedHtml = computed(() => {
  if (!markdown.value) return ''
  return md.render(markdown.value)
})

function handleFileSelect(file: File) {
  convertFileStream(file)
}
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <h1 class="app-title">DocConv</h1>
      <p class="app-subtitle">Convert Word and PDF documents to clean Markdown</p>
    </header>

    <main class="app-main">
      <!-- Idle / Error state: show upload zone -->
      <div v-if="state === 'idle' || state === 'error'" class="upload-section">
        <FileUpload
          :disabled="false"
          @select="handleFileSelect"
        />
        <div v-if="state === 'error' && errorMessage" class="error-banner">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
          <span>{{ errorMessage }}</span>
          <button class="retry-btn" @click="reset">Try again</button>
        </div>
      </div>

      <!-- Uploading state: show progress -->
      <div v-if="state === 'uploading'" class="progress-section">
        <div class="progress-card">
          <div class="spinner" />
          <p class="progress-text">Uploading {{ fileName }}...</p>
          <p class="progress-hint">This may take a few seconds for large documents</p>
        </div>
      </div>

      <!-- Converting/Preview state: show streaming content -->
      <div v-if="state === 'converting' || state === 'preview'" class="preview-section">
        <div class="preview-toolbar">
          <div class="toolbar-left">
            <button class="back-btn" @click="reset">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="19" y1="12" x2="5" y2="12" />
                <polyline points="12 19 5 12 12 5" />
              </svg>
              Convert another
            </button>
            <span class="file-name">{{ fileName }}</span>
            <span v-if="isStreaming" class="streaming-badge">
              <span class="streaming-dot"></span>
              AI Processing...
            </span>
          </div>
          <DownloadButton :file-name="fileName" @download="downloadMarkdown" />
        </div>

        <!-- Live streaming preview -->
        <div class="preview-container">
          <div class="preview-header">
            <span class="preview-label">Markdown Preview</span>
          </div>
          <div v-if="state === 'converting' && !markdown" class="converting-indicator">
            <div class="spinner-small" />
            <span>AI is processing the document...</span>
          </div>
          <div v-else class="preview-body markdown-body" v-html="renderedHtml" />
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  background: #f9fafb;
}

.app-header {
  text-align: center;
  padding: 2.5rem 1rem 1.5rem;
}

.app-title {
  font-size: 2rem;
  font-weight: 800;
  color: #111827;
  margin: 0;
  letter-spacing: -0.025em;
}

.app-subtitle {
  font-size: 1rem;
  color: #6b7280;
  margin: 0.5rem 0 0;
}

.app-main {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 1.5rem 3rem;
}

.upload-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1.25rem;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: #dc2626;
  font-size: 0.9375rem;
}

.retry-btn {
  margin-left: auto;
  padding: 0.375rem 0.875rem;
  background: #dc2626;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn:hover {
  background: #b91c1c;
}

.progress-section {
  display: flex;
  justify-content: center;
  padding: 4rem 0;
}

.progress-card {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #e5e7eb;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-text {
  font-size: 1.125rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
}

.progress-hint {
  font-size: 0.875rem;
  color: #9ca3af;
  margin: 0;
}

.preview-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 1rem;
  background: white;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.file-name {
  font-size: 0.875rem;
  color: #6b7280;
  font-weight: 500;
}

.streaming-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.625rem;
  background: #ede9fe;
  color: #6366f1;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 500;
}

.streaming-dot {
  width: 6px;
  height: 6px;
  background: #6366f1;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.converting-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  background: #f5f3ff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  color: #6366f1;
  font-size: 0.875rem;
}

.spinner-small {
  width: 16px;
  height: 16px;
  border: 2px solid #e0e7ff;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.preview-container {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: white;
}

.preview-header {
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  padding: 0.75rem 1rem;
}

.preview-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.preview-body {
  padding: 1.5rem;
  max-height: 70vh;
  overflow-y: auto;
  font-size: 0.9375rem;
  line-height: 1.7;
}

.markdown-body :deep(h1) {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 1.5rem 0 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.markdown-body :deep(h2) {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 1.25rem 0 0.5rem;
}

.markdown-body :deep(h3) {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 1rem 0 0.5rem;
}

.markdown-body :deep(h4) {
  font-size: 1rem;
  font-weight: 600;
  margin: 0.75rem 0 0.5rem;
}

.markdown-body :deep(p) {
  margin: 0.5rem 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.5rem;
  margin: 0.5rem 0;
}

.markdown-body :deep(li) {
  margin: 0.25rem 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1rem 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 0.5rem 0.75rem;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}

.markdown-body :deep(code) {
  background: #f3f4f6;
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.875em;
}

.markdown-body :deep(pre) {
  background: #1f2937;
  color: #e5e7eb;
  padding: 1rem;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1rem 0;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #6366f1;
  padding-left: 1rem;
  margin: 0.75rem 0;
  color: #6b7280;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 1.5rem 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
}
</style>
