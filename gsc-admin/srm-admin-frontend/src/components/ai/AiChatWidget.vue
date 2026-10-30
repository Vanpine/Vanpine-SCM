<template>
  <div class="ai-widget">
    <!-- 悬浮球按钮 -->
    <button class="ai-float-btn" title="AI 助手" @click="drawerVisible = true">
      <el-icon :size="26" color="#fff">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="2.6" r="1.35" fill="currentColor" />
          <rect x="11.25" y="3.6" width="1.5" height="3" rx="0.75" fill="currentColor" />
          <path fill="currentColor" fill-rule="evenodd" d="M8.2 6.6c-1.6 0-2.9 1.3-2.9 2.9v7c0 1.6 1.3 2.9 2.9 2.9h7.6c1.6 0 2.9-1.3 2.9-2.9v-7c0-1.6-1.3-2.9-2.9-2.9H8.2zm1.4 4.6a1.45 1.45 0 1 1-2.9 0 1.45 1.45 0 0 1 2.9 0zm7.7 0a1.45 1.45 0 1 1-2.9 0 1.45 1.45 0 0 1 2.9 0zM9 15.35h6a.7.7 0 0 1 0 1.4H9a.7.7 0 0 1 0-1.4z" />
        </svg>
      </el-icon>
    </button>
    <!-- 抽屉 -->
    <AiChatDrawer v-model:visible="drawerVisible" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AiChatDrawer from './AiChatDrawer.vue'

const drawerVisible = ref(false)
</script>

<style scoped>
.ai-float-btn {
  position: fixed;
  right: 48px;
  bottom: 24px;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(180deg, #2a4a66 0%, #234058 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(42, 74, 102, 0.4);
  z-index: 2000;
  transition: transform 0.2s, box-shadow 0.2s;
}
.ai-float-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(42, 74, 102, 0.5);
}
</style>
