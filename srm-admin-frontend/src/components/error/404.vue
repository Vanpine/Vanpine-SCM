<template>
  <div class="flex flex-col items-center justify-center h-screen bg-gray-50">
    <h1 class="text-6xl font-bold text-gray-300">
      404
    </h1>
    <p class="mt-4 text-lg text-gray-500">
      页面不存在
    </p>
    <el-button
      type="primary"
      class="mt-6"
      @click="goHome"
    >
      返回首页
    </el-button>
  </div>
</template>


<script setup>
import { useRouter } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { homePath } from '@/utils/homePath'

const router = useRouter()
const permissionStore = usePermissionStore()

function goHome() {
  router.push(homePath(permissionStore.roles))  // 按角色回首页
}
</script>
