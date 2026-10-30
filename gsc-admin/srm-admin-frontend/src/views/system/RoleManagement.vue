<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form
        :model="searchForm"
        inline
      >
        <el-form-item label="角色名称">
          <el-input
            v-model="searchForm.roleName"
            placeholder="请输入角色名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input
            v-model="searchForm.roleCode"
            placeholder="请输入角色编码"
            clearable
            @keyup.enter="handleSearch"
          />
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
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">角色列表</span>
        <el-button
          v-if="hasPermission('role', perms)"
          type="primary"
          :icon="Plus"
          @click="handleCreate"
        >
          新增角色
        </el-button>
      </div>

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
          prop="roleName"
          label="角色名称"
          min-width="120"
        />
        <el-table-column
          prop="roleCode"
          label="角色编码"
          min-width="140"
        />
        <el-table-column
          prop="roleDesc"
          label="描述"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="角色身份"
          min-width="90"
          align="center"
        >
          <template #default="{ row }">
            <template v-if="row.roleType === 'buyer'">
              <el-tag type="primary" size="small">采购方</el-tag>
            </template>
            <template v-else-if="row.roleType === 'buyer_admin'">
              <el-tag type="primary" effect="dark" size="small">采购方管理</el-tag>
            </template>
            <template v-else-if="row.roleType === 'supplier'">
              <el-tag type="success" size="small">供应方</el-tag>
            </template>
            <template v-else-if="row.roleType === 'supplier_admin'">
              <el-tag type="success" effect="dark" size="small">供应方管理</el-tag>
            </template>
            <template v-else-if="row.roleType === 'platform'">
              <el-tag type="danger" size="small">平台</el-tag>
            </template>
            <template v-else-if="row.roleType === 'both_admin'">
              <el-tag type="warning" effect="dark" size="small">双端管理</el-tag>
            </template>
            <template v-else-if="row.roleType === 'custom'">
              <el-tag type="info" size="small">自定义</el-tag>
            </template>
            <template v-else>
              <el-tag size="small">通用</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column
          label="操作" min-width="80"
          class-name="ops-col"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <!-- 系统角色：编辑+权限（禁用/可用取决于是否超管） -->
            <template v-if="row.isSystem">
              <el-tooltip
                :content="!isSuperAdmin ? '系统内置角色不可修改，如需调整请联系超管' : ''"
                :disabled="isSuperAdmin"
                placement="top"
              >
                <span>
                  <el-button
                    v-if="hasPermission('role', perms)"
                    type="primary"
                    link
                    :icon="Edit"
                    :disabled="!isSuperAdmin"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </el-button>
                </span>
              </el-tooltip>
              <el-tooltip
                :content="!isSuperAdmin ? '系统内置角色不可修改，如需调整请联系超管' : ''"
                :disabled="isSuperAdmin"
                placement="top"
              >
                <span>
                  <el-button
                    v-if="hasPermission('role', perms)"
                    type="success"
                    link
                    :icon="Setting"
                    :disabled="!isSuperAdmin"
                    @click="handleAssignPermission(row)"
                  >
                    权限
                  </el-button>
                </span>
              </el-tooltip>
            </template>
            <!-- 自定义角色：编辑外露 + ⋮收起(权限/删除) -->
            <template v-else>
              <el-button
                v-if="hasPermission('role', perms)"
                type="primary"
                link
                :icon="Edit"
                @click="handleEdit(row)"
              >
                编辑
              </el-button>
              <el-dropdown
                v-if="hasPermission('role', perms)"
                trigger="click"
                @command="(cmd) => handleRoleCmd(cmd, row)"
              >
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="perm">权限</el-dropdown-item>
                    <el-dropdown-item command="delete" divided style="color:#f56c6c">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </template>
        </el-table-column>
              <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
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

    <RoleForm
      :key="formKey"
      v-model:visible="formDialogVisible"
      :title="formDialogTitle"
      :role-id="editingRoleId"
      @submit="handleFormSubmit"
    />

    <!-- 权限分配弹窗 -->
    <el-dialog
      v-model="permDialogVisible"
      title="分配权限和菜单"
      width="680px"
      :close-on-click-modal="false"
    >
      <p class="mb-4 text-sm text-gray-500">
        为 <span class="highlight-name">{{ permDialogRoleName }}</span> 分配，展开模块可勾选具体权限
      </p>
      <el-tree
        ref="permTreeRef"
        :data="permTreeData"
        show-checkbox
        node-key="id"
        :default-checked-keys="defaultCheckedKeys"
        default-expand-all
        :props="{ children: 'children', label: 'label' }"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="permDialogLoading"
          @click="handlePermSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, nextTick, computed, onMounted } from 'vue'
import {
  getRoleList, createRole, updateRole, deleteRole,
  assignRolePermissions, assignRoleMenus, getAllPermissions, getAllMenus,
} from '@/api/role'
import { usePermissionStore } from '@/stores/permission'
import { useAuthStore } from '@/stores/auth'
import { hasPermission } from '@/utils/permission'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Setting, MoreFilled } from '@element-plus/icons-vue'
import RoleForm from '@/views/system/RoleForm.vue'

const permissionStore = usePermissionStore()
const perms = permissionStore.permissions
const authStore = useAuthStore()
const isSuperAdmin = computed(() => String(authStore.tenantId) === '1')

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

const searchForm = reactive({ roleName: '', roleCode: '' })

const formDialogVisible = ref(false)
const formDialogTitle = ref('新增角色')
const editingRoleId = ref(null)
const formKey = ref(0)

const permDialogVisible = ref(false)
const permDialogRoleId = ref(null)
const permDialogRoleName = ref('')
const permTreeRef = ref(null)
const permTreeData = ref([])
const defaultCheckedKeys = ref([])
const permDialogLoading = ref(false)

/** 获取角色分页列表 */
async function fetchList() {
  loading.value = true
  try {
    // 调用后端角色分页接口，传入搜索条件
    const { data } = await getRoleList({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
    list.value = data.records || []  // 分页记录
    total.value = data.total || 0    // 总条数
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() { searchForm.roleName = ''; searchForm.roleCode = ''; pageNum.value = 1; fetchList() }
function handlePageChange(page) { pageNum.value = page; fetchList() }
function handleSizeChange(size) { pageSize.value = size; pageNum.value = 1; fetchList() }

function handleCreate() { formDialogTitle.value = '新增角色'; editingRoleId.value = null; formKey.value++; formDialogVisible.value = true }
function handleEdit(row) { formDialogTitle.value = '编辑角色'; editingRoleId.value = row.id; formDialogVisible.value = true }

/** 提交角色表单（新增或编辑） */
async function handleFormSubmit(formData) {
  try {
    if (editingRoleId.value) {
      // 编辑：调用更新角色接口
      await updateRole(editingRoleId.value, formData)
      ElMessage.success('更新成功')
    } else {
      // 新增：调用创建角色接口
      await createRole(formData)
      ElMessage.success('创建成功')
    }
    formDialogVisible.value = false
    fetchList()  // 刷新列表
  } catch { /* 拦截器已处理 */ }
}

/** 自定义角色下拉菜单命令分发 */
function handleRoleCmd(cmd, row) {
  if (cmd === 'perm') handleAssignPermission(row)
  else if (cmd === 'delete') handleDelete(row)
}

/** 删除角色 */
async function handleDelete(row) {
  if (row.isSystem) { ElMessage.warning('系统内置角色不可删除'); return }
  try { await ElMessageBox.confirm(`确认删除角色 <span class="highlight-name">${row.roleName}</span> 吗？`, '删除确认', { type: 'warning', dangerouslyUseHTMLString: true }) } catch { return }
  try {
    // 调用后端删除角色接口
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchList()  // 刷新列表
  } catch { /* 拦截器已处理 */ }
}

// 权限缓存：所有权限的平铺列表，用于从菜单 permCode 推导权限ID
const allPermCache = ref([])
// 扁平菜单列表：从后端获取的菜单树，用于从选中菜单推导权限编码
const flatMenuList = ref([])

/** 打开权限分配弹窗，加载权限树和菜单树 */
async function handleAssignPermission(row) {
  permDialogRoleId.value = row.id  // 当前编辑的角色ID
  permDialogRoleName.value = row.roleName  // 当前角色名称

  // 构造已选中的 key（菜单ID加前缀 menu_）
  const checkedKeys = (row.menuIds || []).map((id) => 'menu_' + id)
  defaultCheckedKeys.value = checkedKeys  // el-tree 默认勾选

  try {
    // 并行请求：获取全部权限列表 + 当前用户可见的菜单树
    const [permRes, menuRes] = await Promise.all([getAllPermissions(), getAllMenus()])
    flatMenuList.value = menuRes.data || []  // 后端返回的菜单树（已按用户权限过滤）
    // 使用 store 的 buildPermTree 将菜单转为 el-tree 所需格式
    permTreeData.value = permissionStore.buildPermTree(flatMenuList.value)
    allPermCache.value = permRes.data || []  // 全部权限平铺列表，用于后续 permCode → ID 推导

    // 树渲染后再设置勾选状态，确保异步数据加载后生效
    await nextTick()
    permTreeRef.value?.setCheckedKeys(checkedKeys, false)  // false: 不严格模式，父子独立
  } catch {
    // 加载失败时清空，防止脏数据
    permTreeData.value = []
    allPermCache.value = []
  }
  permDialogVisible.value = true
}

/** 提交权限分配：从选中的菜单推导权限ID，同时保存菜单和权限关联 */
async function handlePermSubmit() {
  // 获取 el-tree 中所有勾选的节点 key，去掉 'menu_' 前缀得到菜单ID
  const menuIds = permTreeRef.value.getCheckedKeys().map((k) => Number(k.slice(5)))

  // 1. 从选中的菜单节点收集 permCode
  const codes = new Set()
  function collect(nodes) {
    nodes.forEach((n) => {
      // 后端 Long 序列化为 String，n.id 是字符串；menuIds 已 Number 化，比较需统一类型
      if (menuIds.includes(Number(n.id)) && n.permCode) codes.add(n.permCode)
      if (n.children) collect(n.children)
    })
  }
  collect(flatMenuList.value)

  // 2. 从菜单 permCode 推导具体权限ID
  // "user"（模块前缀）→ 推导所有 user:* 权限（如 user:read, user:create...）
  // "user:read"（精确码）→ 只授予 user:read，实现模块只读
  const expandedCodes = new Set()
  for (const code of codes) {
    if (code.includes(':')) {
      // 精确权限码：只加自身
      if (allPermCache.value.some((p) => p.permCode === code)) {
        expandedCodes.add(code)
      }
    } else {
      // 模块前缀：先加模块级权限自身，再推导子权限
      if (allPermCache.value.some((p) => p.permCode === code)) {
        expandedCodes.add(code)
      }
      const prefix = code + ':'
      for (const p of allPermCache.value) {
        if (p.permCode && p.permCode.startsWith(prefix)) {
          expandedCodes.add(p.permCode)
        }
      }
    }
  }

  // 3. 根据 permCode 查找对应的权限ID
  const permIds = allPermCache.value.filter((p) => expandedCodes.has(p.permCode)).map((p) => p.id)

  permDialogLoading.value = true
  try {
    // 并行保存角色-权限关联 + 角色-菜单关联
    await Promise.all([
      assignRolePermissions(permDialogRoleId.value, permIds),  // 全量替换权限
      assignRoleMenus(permDialogRoleId.value, menuIds),        // 全量替换菜单
    ])
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
    fetchList()  // 刷新列表
  } catch {
    /* 拦截器已处理 */
  } finally {
    permDialogLoading.value = false
  }
}

onMounted(() => { fetchList() })
</script>
