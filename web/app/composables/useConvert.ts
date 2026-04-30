import { ref } from 'vue'
import type { ConvertState } from '~/types/convert'

export function useConvert() {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase as string

  const state = ref<ConvertState>('idle')
  const markdown = ref('')
  const resultId = ref('')
  const originalFilename = ref('')
  const errorMessage = ref('')
  const fileName = ref('')
  const isStreaming = ref(false)

  function reset() {
    state.value = 'idle'
    markdown.value = ''
    resultId.value = ''
    originalFilename.value = ''
    errorMessage.value = ''
    fileName.value = ''
    isStreaming.value = false
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

      const response = await $fetch<{ markdown: string, id: string, originalFilename: string }>(`${apiBase}/api/v1/convert`, {
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

  async function convertFileStream(file: File) {
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
    markdown.value = ''
    isStreaming.value = true

    const formData = new FormData()
    formData.append('file', file)

    try {
      state.value = 'converting'

      // Use fetch with ReadableStream for SSE
      const response = await fetch(`${apiBase}/api/v1/convert/stream`, {
        method: 'POST',
        body: formData
      })

      if (!response.ok) {
        throw new Error(`Server error: ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('Unable to read response stream')
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let chunkCount = 0

      state.value = 'preview'

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // Format: data:{...}\ndata:\n\n - each event followed by empty event
        let events = buffer.split('data:\n\n')
        buffer = events.pop() || ''

        for (const event of events) {
          if (event.startsWith('data:')) {
            let json = event.substring(5)
            try {
              const parsed = JSON.parse(json)
              chunkCount++
              if (parsed.type === 'chunk') {
                markdown.value += parsed.content
              } else if (parsed.type === 'complete') {
                markdown.value = parsed.content
              }
            } catch (e) {
              // Skip
            }
          }
        }
      }

      isStreaming.value = false

      isStreaming.value = false
      console.log('[Stream] Finished streaming, final markdown length:', markdown.value.length)
    } catch (err: any) {
      state.value = 'error'
      isStreaming.value = false
      console.error('[Stream] Error during streaming:', err)

      if (err?.message) {
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
    isStreaming,
    convertFile,
    convertFileStream,
    downloadMarkdown,
    reset
  }
}
