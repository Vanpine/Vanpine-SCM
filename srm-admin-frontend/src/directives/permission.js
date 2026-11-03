/**
 * SRM Admin - 权限指令 v-permission
 * 用法: <el-button v-permission="'user:delete'">删除</el-button>
 *       <el-button v-permission="['user:delete', 'user:update']">操作</el-button>
 */
import { usePermissionStore } from '@/stores/permission'

export default {
  mounted(el, binding) {
    const { value } = binding
    if (!value) return

    const permissionStore = usePermissionStore()
    const permissions = permissionStore.permissions

    const requiredPerms = Array.isArray(value) ? value : [value]

    const hasPerm = requiredPerms.some((perm) => permissions.includes(perm))

    if (!hasPerm) {
      el.parentNode?.removeChild(el)
    }
  },
}
