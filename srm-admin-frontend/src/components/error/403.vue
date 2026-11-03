<template>
  <div class="flex flex-col items-center justify-center h-screen bg-gray-50">
    <h1 class="text-6xl font-bold text-gray-300">
      403
    </h1>
    <p class="mt-4 text-lg text-gray-500">
      抱歉，您没有权限访问该页面
    </p>
    <el-button
      type="primary"
      class="mt-6"
      @click="goBack"
    >
      返回上一页
    </el-button>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  router.back()
}
</script>
