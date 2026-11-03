/**
 * SRM Admin - 应用入口
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import i18n from './lang'
import permissionDirective from './directives/permission'
import EmptyText from './components/EmptyText.vue'

// 全局样式
import './styles/theme.css'
import './styles/index.scss'

const app = createApp(App)

// Pinia
const pinia = createPinia()
app.use(pinia)

// Vue Router
app.use(router)

// vue-i18n
app.use(i18n)

// Element Plus（中文，无空数据文字）
zhCn.el.table.emptyText = ''
zhCn.el.table.loadingText = ''
app.use(ElementPlus, { locale: zhCn })

// 注册所有 Element Plus 图标
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})

// 注册自定义指令
app.directive('permission', permissionDirective)
app.component('EmptyText', EmptyText)

app.mount('#app')

// 全局：鼠标在可横向滚动的表格/容器上滚轮时，自动转为横向滚动
let scrollRAF = null
let scrollAccum = 0
document.addEventListener('wheel', (e) => {
  let el = e.target
  while (el && el !== document.body) {
    const style = window.getComputedStyle(el)
    const overflowX = style.overflowX || style.overflow
    const canScrollX = (overflowX === 'auto' || overflowX === 'scroll') && el.scrollWidth > el.clientWidth
    if (canScrollX && Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
      e.preventDefault()
      scrollAccum += e.deltaY
      if (!scrollRAF) {
        scrollRAF = requestAnimationFrame(() => {
          el.scrollBy({ left: scrollAccum, behavior: 'instant' })
          scrollAccum = 0
          scrollRAF = null
        })
      }
      return
    }
    el = el.parentElement
  }
}, { passive: false })
