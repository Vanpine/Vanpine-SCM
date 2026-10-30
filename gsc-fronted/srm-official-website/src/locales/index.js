import { createI18n } from 'vue-i18n'
import zh from './zh.json'
import en from './en.json'

const saved = localStorage.getItem('lang')
const defaultLocale = (saved === 'en') ? 'en' : 'zh'

const i18n = createI18n({
  legacy: false,
  locale: defaultLocale,
  fallbackLocale: 'zh',
  messages: { zh, en },
  globalInjection: true,
})

export default i18n
