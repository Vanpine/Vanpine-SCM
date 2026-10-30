<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form
        :model="searchForm"
        inline
      >
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="searchForm.realName"
            placeholder="请输入姓名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option
              label="正常"
              value="active"
            />
            <el-option
              label="禁用"
              value="disabled"
            />
            <el-option
              label="锁定"
              value="locked"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Search"
            @click="handleSearch"
          >
            搜索
          </el-button>
          <el-button
            :icon="Refresh"
            @click="handleReset"
          >
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 内容卡片 -->
    <el-card v-loading="loading" class="content-card">
      <!-- 操作栏 -->
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">用户列表</span>
        <el-button
          v-if="hasPermission('user', perms)"
          type="primary"
          :icon="Plus"
          @click="handleCreate"
        >
          新增用户
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column
          type="index"
          :index="(idx) => (pageNum - 1) * pageSize + idx + 1"
          label="ID"
          width="50"
          align="center"
        />
        <el-table-column
          prop="username"
          label="用户名"
          min-width="110"
        />
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="90"
        />
        <el-table-column
          prop="email"
          label="邮箱"
          min-width="160"
        />
        <el-table-column
          prop="phone"
          label="手机号"
          min-width="130"
          align="center"
        />
        <el-table-column
          label="角色"
          min-width="130"
        >
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames.length">
              <el-tag
                v-for="name in row.roleNames"
                :key="name"
                :type="roleTagType(name)"
                size="small"
                class="role-tag-item"
              >
                {{ name }}
              </el-tag>
            </template>
            <span
              v-else
              class="text-gray-400 text-sm"
            >未分配</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          min-width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="USER_STATUS_MAP[row.status]?.type || 'info'"
              size="small"
            >
              {{ USER_STATUS_MAP[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作" min-width="80"
          class-name="ops-col"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="hasPermission('user', perms)"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-dropdown
              v-if="hasPermission('user', perms) || hasPermission('role', perms)"
              trigger="click"
              @command="(cmd) => handleMoreCommand(cmd, row)"
            >
              <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="hasPermission('role', perms)"
                    command="role"
                    :icon="UserFilled"
                  >
                    分配角色
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="hasPermission('user', perms)"
                    command="resetPwd"
                    :icon="Key"
                  >
                    重置密码
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="hasPermission('user', perms)"
                    command="toggleStatus"
                    :icon="row.status === 'active' ? CircleClose : CircleCheck"
                  >
                    {{ row.status === 'active' ? '禁用' : '启用' }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="hasPermission('user', perms)"
                    command="delete"
                    divided
                    style="color:#f56c6c"
                    :icon="Delete"
                  >
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
              <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <UserForm
      :key="formKey"
      v-model:visible="formDialogVisible"
      :title="formDialogTitle"
      :user-id="editingUserId"
      @submit="handleFormSubmit"
    />

    <!-- 角色分配弹窗 -->
    <el-dialog
      v-model="roleDialogVisible"
      title="分配角色"
      width="440px"
      :close-on-click-modal="false"
    >
      <p class="mb-4 text-sm text-gray-500">
        为 <span class="highlight-name">{{ roleDialogUsername }}</span> 分配角色
      </p>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox
          v-for="role in allRoles"
          :key="role.id"
          :value="role.id"
          :label="role.id"
        >
          {{ role.roleName }} <span class="text-gray-400 text-xs">({{ role.roleCode }})</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="roleDialogLoading"
          @click="handleRoleSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser,
  assignUserRoles,
  resetUserPassword,
  updateUserStatus,
} from '@/api/user'
import { getAllRoles } from '@/api/role'
import { usePermissionStore } from '@/stores/permission'
import { hasPermission } from '@/utils/permission'
import {
  DEFAULT_PAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  USER_STATUS_MAP,
  ROLE_TAG_TYPE_MAP,
} from '@/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Key, UserFilled, MoreFilled, CircleClose, CircleCheck } from '@element-plus/icons-vue'
import UserForm from '@/views/system/UserForm.vue'

const permissionStore = usePermissionStore()
const perms = permissionStore.permissions

// ---- 数据 ----
const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

// ---- 搜索 ----
const searchForm = reactive({
  username: '',
  realName: '',
  status: '',
})

// ---- 弹窗控制 ----
const formDialogVisible = ref(false)
const formDialogTitle = ref('新增用户')
const editingUserId = ref(null)
const formKey = ref(0)

const roleDialogVisible = ref(false)
const roleDialogUserId = ref(null)
const roleDialogUsername = ref('')
const allRoles = ref([])
const selectedRoleIds = ref([])
const roleDialogLoading = ref(false)

// ---- 获取列表 ----
/** 获取用户分页列表 */
async function fetchList() {
  loading.value = true
  try {
    // 调用后端用户分页查询接口，传入分页参数和搜索条件
    const { data } = await getUserList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchForm,
    })
    list.value = data.records || []  // 分页记录
    total.value = data.total || 0    // 总条数
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  searchForm.username = ''
  searchForm.realName = ''
  searchForm.status = ''
  pageNum.value = 1
  fetchList()
}

function handlePageChange(page) {
  pageNum.value = page
  fetchList()
}

function handleSizeChange(size) {
  pageSize.value = size
  pageNum.value = 1
  fetchList()
}

/** 根据角色名返回 el-tag type */
function roleTagType(name) {
  return ROLE_TAG_TYPE_MAP[name] || 'info'
}

// ---- 新增/编辑 ----
function handleCreate() {
  formDialogTitle.value = '新增用户'
  editingUserId.value = null
  formKey.value++
  formDialogVisible.value = true
}

function handleEdit(row) {
  formDialogTitle.value = '编辑用户'
  editingUserId.value = row.id
  formDialogVisible.value = true
}

/** 提交用户表单（新增或编辑） */
async function handleFormSubmit(formData) {
  try {
    if (editingUserId.value) {
      // 编辑：调用更新用户接口
      await updateUser(editingUserId.value, formData)
      ElMessage.success('更新成功')
    } else {
      // 新增：调用创建用户接口
      await createUser(formData)
      ElMessage.success('创建成功')
    }
    formDialogVisible.value = false
    fetchList()  // 刷新列表
  } catch {
    // 错误已在拦截器中处理
  }
}

/** 下拉菜单命令分发 */
function handleMoreCommand(cmd, row) {
  switch (cmd) {
    case 'role': handleAssignRole(row); break
    case 'resetPwd': handleResetPassword(row); break
    case 'toggleStatus': handleToggleStatus(row); break
    case 'delete': handleDelete(row); break
  }
}

/** 删除用户 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户 <span class="highlight-name">${row.username}</span> 吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', dangerouslyUseHTMLString: true },
    )
  } catch {
    return
  }

  try {
    // 调用后端删除用户接口
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()  // 刷新列表
  } catch {
    // 错误已在拦截器中处理
  }
}

/** 打开角色分配弹窗 */
async function handleAssignRole(row) {
  roleDialogUserId.value = row.id
  roleDialogUsername.value = row.username

  // 调用后端获取当前租户下所有角色列表（用于下拉选择）
  try {
    const { data } = await getAllRoles()
    // 排序：管理员在前 → 身份角色 → 自定义角色
    const ADMIN_CODES = ['BUYER_ADMIN', 'SUPPLIER_ADMIN', 'TENANT_ADMIN']
    const IDENTITY_CODES = ['BUYER', 'SUPPLIER']
    const sorted = [...(data || [])].sort((a, b) => {
      const pa = ADMIN_CODES.includes(a.roleCode) ? 1 : IDENTITY_CODES.includes(a.roleCode) ? 2 : 3
      const pb = ADMIN_CODES.includes(b.roleCode) ? 1 : IDENTITY_CODES.includes(b.roleCode) ? 2 : 3
      return pa - pb
    })
    allRoles.value = sorted
  } catch {
    allRoles.value = []
  }

  // 设置已选中的角色（当前用户已有的角色）
  selectedRoleIds.value = row.roleIds || []

  roleDialogVisible.value = true
}

/** 提交角色分配 */
async function handleRoleSubmit() {
  roleDialogLoading.value = true
  try {
    // 调用后端全量替换用户角色关联
    await assignUserRoles(roleDialogUserId.value, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    fetchList()  // 刷新列表
  } catch {
    // 错误已在拦截器中处理
  } finally {
    roleDialogLoading.value = false
  }
}

/** 重置用户密码 */
async function handleResetPassword(row) {
  let password = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `为 <span class="highlight-name">${row.username}</span> 设置新密码`,
      '重置密码',
      {
        dangerouslyUseHTMLString: true,
        inputType: 'password',
        inputPlaceholder: '请输入新密码（至少6位）',
        inputValidator: (val) => {
          if (!val || val.length < 6) return '密码至少6位'
          return true
        },
      },
    )
    password = value
  } catch {
    return
  }

  try {
    // 调用后端重置密码接口
    await resetUserPassword(row.id, password)
    ElMessage.success('密码重置成功')
  } catch {
    // 错误已在拦截器中处理
  }
}

/** 切换用户状态（启用/禁用） */
async function handleToggleStatus(row) {
  const newStatus = row.status === 'active' ? 'disabled' : 'active'
  const actionText = newStatus === 'active' ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户 <span class="highlight-name">${row.username}</span> 吗？`,
      `${actionText}确认`,
      { dangerouslyUseHTMLString: true },
    )
  } catch {
    return
  }

  try {
    // 调用后端更新用户状态接口（禁用会同时删除token强制下线）
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(`${actionText}成功`)
    fetchList()  // 刷新列表
  } catch {
    // 错误已在拦截器中处理
  }
}

// ---- 初始化 ----
onMounted(() => {
  fetchList()
})
</script>

<style scoped>
/** 角色标签间距 */
.role-tag-item {
  margin-right: 6px;
  margin-bottom: 2px;
}
</style>
