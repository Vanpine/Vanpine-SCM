<template>
  <el-dropdown trigger="click" @command="handleChange">
    <button class="lang-btn" title="Language">
      <el-icon :size="18"><Promotion /></el-icon>
      <span class="lang-label">{{ appStore.lang === 'zh-CN' ? '中文' : 'EN' }}</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh-CN" :class="{ 'is-active': appStore.lang === 'zh-CN' }">
          中文
        </el-dropdown-item>
        <el-dropdown-item command="en" :class="{ 'is-active': appStore.lang === 'en' }">
          English
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { useAppStore } from '@/stores/app'
import { Promotion } from '@element-plus/icons-vue'

const appStore = useAppStore()

function handleChange(lang) {
  appStore.setLang(lang)
}
</script>

<style scoped>
.lang-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 8px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.lang-btn:hover {
  background: #f1f5f9;
  color: #0066ff;
}

.lang-label {
  font-size: 13px;
  font-weight: 500;
}

:deep(.is-active) {
  color: #0066ff;
  font-weight: 600;
}
</style>
