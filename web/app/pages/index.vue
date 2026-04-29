<script setup lang="ts">
import { useConvert } from '~/composables/useConvert'

const {
  state,
  markdown,
  errorMessage,
  fileName,
  convertFile,
  downloadMarkdown,
  reset
} = useConvert()

function handleFileSelect(file: File) {
  convertFile(file)
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

      <!-- Uploading / Converting state: show progress -->
      <div v-if="state === 'uploading' || state === 'converting'" class="progress-section">
        <div class="progress-card">
          <div class="spinner" />
          <p class="progress-text">
            <template v-if="state === 'uploading'">Uploading {{ fileName }}...</template>
            <template v-else>Converting {{ fileName }}...</template>
          </p>
          <p class="progress-hint">This may take a few seconds for large documents</p>
        </div>
      </div>

      <!-- Preview state: show markdown -->
      <div v-if="state === 'preview'" class="preview-section">
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
          </div>
          <DownloadButton :file-name="fileName" @download="downloadMarkdown" />
        </div>
        <MarkdownPreview :markdown="markdown" />
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

/* Upload section */
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

/* Progress section */
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

/* Preview section */
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
</style>
