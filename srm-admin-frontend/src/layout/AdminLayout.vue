<template>
  <div class="admin-layout">
    <el-config-provider :locale="elLocale">
      <!-- 侧边栏 -->
      <aside
        class="layout-sidebar"
        :style="{ width: sidebarWidth }"
      >
        <Sidebar />
      </aside>

      <!-- 右侧主体 -->
      <div class="layout-main">
        <!-- 顶栏 -->
        <header class="layout-header">
          <AppHeader />
        </header>

        <!-- 内容区 -->
      <main class="layout-content">
        <router-view v-slot="{ Component, route }">
          <keep-alive :include="cachedViews">
            <component
              :is="Component"
              :key="route.path"
            />
          </keep-alive>
        </router-view>
      </main>
      </div>

      <!-- AI 对话：尽调中 / 事务所不展示 -->
      <AiChatWidget v-if="canUseAiChat" />
    </el-config-provider>
  </div>
</template>


<script setup>
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import Sidebar from '@/components/Sidebar.vue'
import AppHeader from '@/components/AppHeader.vue'
import AiChatWidget from '@/components/ai/AiChatWidget.vue'
import { canUseAiAssistant } from '@/utils/permission'

const router = useRouter()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

const sidebarWidth = computed(() =>
  appStore.sidebarCollapsed ? '64px' : '220px',
)

const elLocale = computed(() => (appStore.lang === 'zh-CN' ? zhCn : en))

/** 尽调预审、事务所不挂 AI 悬浮球 */
const canUseAiChat = computed(() => canUseAiAssistant(permissionStore.roles))

/**
 * 按菜单 isCache 配置计算需要 keep-alive 缓存的组件 name 列表。
 * 列表页默认不缓存，切换菜单重新挂载重新请求；只有显式勾了 isCache 的菜单才缓存。
 */
const cachedViews = computed(() => {
  const names = []
  for (const path of permissionStore.cachedPaths) {
    const resolved = router.resolve(path)
    if (resolved.name) names.push(resolved.name)
  }
  return names
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f5f6f8;
}

.layout-sidebar {
  flex-shrink: 0;
  height: 100%;
  overflow: hidden;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.layout-header {
  flex-shrink: 0;
  height: 56px;
}

.layout-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  max-width: 97%;
  margin: 0 auto;
  width: 100%;
}
</style>
