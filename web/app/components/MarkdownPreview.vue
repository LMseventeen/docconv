<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps<{
  markdown: string
}>()

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true
})

const renderedHtml = computed(() => {
  if (!props.markdown) return ''
  return md.render(props.markdown)
})
</script>

<template>
  <div class="preview-container">
    <div class="preview-header">
      <span class="preview-label">Markdown Preview</span>
    </div>
    <div class="preview-body markdown-body" v-html="renderedHtml" />
  </div>
</template>

<style scoped>
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

/* Markdown styling */
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
