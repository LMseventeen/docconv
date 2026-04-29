export type ConvertState = 'idle' | 'uploading' | 'converting' | 'preview' | 'error'

export interface ConvertResult {
  id: string
  markdown: string
  originalFilename: string
  sourceFormat: string | null
  pageCount: number | null
}

export interface ApiError {
  status: number
  error: string
  message: string
}
