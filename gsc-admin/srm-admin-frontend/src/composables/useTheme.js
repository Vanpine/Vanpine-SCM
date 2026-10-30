import { ref, watchEffect } from 'vue'

const THEME_KEY = 'gsc-theme'
const isDark = ref(localStorage.getItem(THEME_KEY) === 'dark')

watchEffect(() => {
  const html = document.documentElement
  if (isDark.value) {
    html.classList.add('dark')
  } else {
    html.classList.remove('dark')
  }
  localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
})

export function useTheme() {
  function toggle() {
    isDark.value = !isDark.value
  }
  return { isDark, toggle }
}
