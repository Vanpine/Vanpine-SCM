<template>
  <el-card>
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-semibold text-base">租户管理</span>
        <el-button v-if="hasPermission('tenant:create', perms)" type="primary" disabled>
          新增租户
        </el-button>
      </div>
    </template>
  </el-card>
</template>
<script setup>
import { usePermissionStore } from '@/stores/permission'
import { hasPermission } from '@/utils/permission'

const perms = usePermissionStore().permissions
</script>
