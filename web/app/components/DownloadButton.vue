<script setup lang="ts">
defineProps<{
  fileName?: string
}>()

defineEmits<{
  download: []
}>()
</script>

<template>
  <button class="download-btn" @click="$emit('download')">
    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </svg>
    Download .md
  </button>
</template>

<style scoped>
.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.25rem;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease;
}

.download-btn:hover {
  background: #4f46e5;
}

.download-btn:active {
  background: #4338ca;
}
</style>
