<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
}>()

const isDragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function onDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function onDragLeave() {
  isDragging.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false

  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    emit('select', files[0])
  }
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (files && files.length > 0) {
    emit('select', files[0])
    // Reset input so same file can be selected again
    target.value = ''
  }
}

function openFilePicker() {
  fileInput.value?.click()
}
</script>

<template>
  <div
    class="upload-zone"
    :class="{ 'dragging': isDragging, 'disabled': disabled }"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
    @click="openFilePicker"
  >
    <input
      ref="fileInput"
      type="file"
      accept=".docx,.pdf"
      class="hidden"
      @change="onFileChange"
    />

    <div class="upload-content">
      <div class="upload-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="17 8 12 3 7 8" />
          <line x1="12" y1="3" x2="12" y2="15" />
        </svg>
      </div>
      <p class="upload-title">
        Drop your document here or click to browse
      </p>
      <p class="upload-hint">
        Supports .docx and .pdf files (max 10MB)
      </p>
    </div>
  </div>
</template>

<style scoped>
.upload-zone {
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  padding: 3rem 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fafafa;
}

.upload-zone:hover {
  border-color: #6366f1;
  background: #f5f3ff;
}

.upload-zone.dragging {
  border-color: #6366f1;
  background: #ede9fe;
  transform: scale(1.01);
}

.upload-zone.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.upload-icon {
  color: #9ca3af;
}

.dragging .upload-icon {
  color: #6366f1;
}

.upload-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
}

.upload-hint {
  font-size: 0.875rem;
  color: #9ca3af;
  margin: 0;
}

.hidden {
  display: none;
}
</style>
