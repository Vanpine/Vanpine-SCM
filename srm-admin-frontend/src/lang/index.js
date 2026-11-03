/**
 * i18n 实例
 * 从 localStorage 读取上次选择的语言，默认中文
 */
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

const savedLang = localStorage.getItem('lang') || 'zh-CN'

const i18n = createI18n({
  legacy: false,
  locale: savedLang,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    en,
  },
})

export default i18n
