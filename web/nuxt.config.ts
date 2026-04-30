export default defineNuxtConfig({
  devtools: { enabled: true },

  modules: ['@nuxt/ui'],

  compatibilityDate: '2024-11-01',

  app: {
    head: {
      title: 'DocConv - Document to Markdown Converter',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Convert Word and PDF exam documents to clean Markdown' }
      ]
    }
  },

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || ''
    }
  },

  nitro: {
    routeRules: {
      '/api/**': {
        proxy: {
          to: process.env.NUXT_API_PROXY_TARGET || 'http://127.0.0.1:8080/api/**',
        },
      },
    },
  },
})
