/**
 * SRM Admin - 应用状态管理
 * 管理侧边栏折叠、语言等全局 UI 状态
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n from '@/lang'

export const useAppStore = defineStore('app', () => {
  // ---- state ----
  const sidebarCollapsed = ref(false)
  const lang = ref(localStorage.getItem('lang') || 'zh-CN')

  // ---- actions ----
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function collapseSidebar() {
    sidebarCollapsed.value = true
  }

  function expandSidebar() {
    sidebarCollapsed.value = false
  }

  function setLang(newLang) {
    lang.value = newLang
    localStorage.setItem('lang', newLang)
    i18n.global.locale.value = newLang
  }

  return {
    sidebarCollapsed,
    lang,
    toggleSidebar,
    collapseSidebar,
    expandSidebar,
    setLang,
  }
})
