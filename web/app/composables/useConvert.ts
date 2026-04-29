import { ref } from 'vue'
import type { ConvertResult, ConvertState } from '~/types/convert'

export function useConvert() {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase as string

  const state = ref<ConvertState>('idle')
  const markdown = ref('')
  const resultId = ref('')
  const originalFilename = ref('')
  const errorMessage = ref('')
  const fileName = ref('')

  function reset() {
    state.value = 'idle'
    markdown.value = ''
    resultId.value = ''
    originalFilename.value = ''
    errorMessage.value = ''
    fileName.value = ''
  }

  async function convertFile(file: File) {
    const allowedExtensions = ['.docx', '.pdf']

    // Validate extension
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!allowedExtensions.includes(ext)) {
      state.value = 'error'
      errorMessage.value = 'Please upload a .docx or .pdf file.'
      return
    }

    // Validate size (10MB)
    if (file.size > 10 * 1024 * 1024) {
      state.value = 'error'
      errorMessage.value = 'File exceeds the 10MB size limit.'
      return
    }

    if (file.size === 0) {
      state.value = 'error'
      errorMessage.value = 'The file is empty. Please select a valid document.'
      return
    }

    fileName.value = file.name
    state.value = 'uploading'
    errorMessage.value = ''

    const formData = new FormData()
    formData.append('file', file)

    try {
      state.value = 'converting'

      const response = await $fetch<ConvertResult>(`${apiBase}/api/v1/convert`, {
        method: 'POST',
        body: formData
      })

      markdown.value = response.markdown
      resultId.value = response.id
      originalFilename.value = response.originalFilename
      state.value = 'preview'
    } catch (err: any) {
      state.value = 'error'

      if (err?.data?.message) {
        errorMessage.value = err.data.message
      } else if (err?.message) {
        errorMessage.value = err.message
      } else {
        errorMessage.value = 'An unexpected error occurred. Please try again.'
      }
    }
  }

  function downloadMarkdown() {
    if (!markdown.value) return

    const blob = new Blob([markdown.value], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName.value.replace(/\.(docx|pdf)$/i, '.md')
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  return {
    state,
    markdown,
    resultId,
    originalFilename,
    errorMessage,
    fileName,
    convertFile,
    downloadMarkdown,
    reset
  }
}
