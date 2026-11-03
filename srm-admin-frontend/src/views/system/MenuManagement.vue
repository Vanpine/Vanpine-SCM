<template>
  <div>
    <el-card v-loading="loading" class="search-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">菜单管理</span>
        <el-button
          type="primary"
          :icon="Plus"
          @click="handleCreate"
        >
          新增菜单
        </el-button>
      </div>

      <el-table :data="treeData"
        row-key="id"
        border
        stripe
        default-expand-all
      >
        <el-table-column
          prop="menuName"
          label="菜单名称"
          min-width="200"
        >
          <template #default="{ row }">
            <span>{{ row.menuName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="menuPath"
          label="路由"
          min-width="160"
        >
          <template #default="{ row }">
            <span v-if="row.menuPath">{{ row.menuPath }}</span>
            <span
              v-else
              class="text-gray-300"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="permCode"
          label="权限标识"
          min-width="160"
        >
          <template #default="{ row }">
            <span v-if="row.permCode">{{ row.permCode }}</span>
            <span
              v-else
              class="text-gray-300"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          min-width="70"
          align="center"
        />
        <el-table-column
          prop="isVisible"
          label="可见"
          min-width="70"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.isVisible ? 'success' : 'info'"
              size="small"
            >
              {{ row.isVisible ? '是' : '否' }}
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
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              link
              :icon="Delete"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
              <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :key="formKey"
      v-model="formVisible"
      :title="formTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item
          label="父菜单"
          prop="parentId"
        >
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="留空则为一级菜单"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="菜单名称"
          prop="menuName"
        >
          <el-input
            v-model="form.menuName"
            placeholder="如：用户管理"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item
          label="菜单类型"
          prop="menuType"
        >
          <el-select
            v-model="form.menuType"
            style="width: 100%"
          >
            <el-option
              label="菜单"
              value="menu"
            />
            <el-option
              label="按钮"
              value="button"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="路由路径"
          prop="menuPath"
        >
          <el-input
            v-model="form.menuPath"
            placeholder="如：/admin/user"
            :maxlength="256"
          />
        </el-form-item>
        <el-form-item
          label="图标"
          prop="menuIcon"
        >
          <el-input
            v-model="form.menuIcon"
            placeholder="Element Plus 图标名"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item
          label="权限标识"
          prop="permCode"
        >
          <el-select
            v-model="form.permCode"
            placeholder="一级菜单无需权限标识"
            clearable
            filterable
            :disabled="isParentMenu"
            style="width: 100%"
          >
            <el-option
              v-for="item in permOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="排序"
          prop="sortOrder"
        >
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="是否可见">
          <el-switch v-model="form.isVisible" />
        </el-form-item>
        <el-form-item label="是否缓存">
          <el-switch v-model="form.isCache" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getMenuTree, getMenuById, createMenu, updateMenu, deleteMenu } from '@/api/menu'
import { getAllPermissions } from '@/api/role'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

const treeData = ref([])
const loading = ref(true)
const submitting = ref(false)
const originalData = ref(null)  // 编辑时的原始数据，用于脏检查
const formVisible = ref(false)
const formTitle = ref('新增菜单')
const editingId = ref(null)
const formKey = ref(0)
const formRef = ref(null)
const parentOptions = ref([])
const allPermList = ref([])

const form = reactive({
  parentId: 0,
  menuType: 'menu',
  menuName: '',
  menuPath: '',
  menuIcon: '',
  permCode: '',
  sortOrder: 0,
  isVisible: true,
  isCache: false,
})

const rules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

/** 顶级菜单 → 其模块下的权限标识前缀（前缀匹配，新增权限自动出现） */
const MODULE_PERM_PREFIX = {
  '系统管理': ['tenant', 'user', 'role', 'application', 'menu', 'audit-log', 'subscription'],
  '采购管理': ['procurement'],
  '可信交付': ['delivery'],
  '智能撮合': ['match'],
  '尽职调查': ['due-diligence'],
  '决策中枢': ['decision'],
  '供应链情报': ['intel'],
}

/** 查找节点所属的顶级菜单名称 */
function findTopMenuName(tree, targetId, topParentName = null) {
  for (const node of tree) {
    const currentTop = node.parentId === 0 ? node.menuName : topParentName
    if (String(node.id) === String(targetId)) return currentTop
    if (node.children) {
      const found = findTopMenuName(node.children, targetId, currentTop)
      if (found) return found
    }
  }
  return null
}

/** 权限标识下拉选项：根据父菜单所属的顶级模块过滤 */
const permOptions = computed(() => {
  let list = (allPermList.value || [])
    .filter((p) => p.permCode && p.permCode !== '*:*:*' && p.permCode !== 'admin')

  // 根据选中的父菜单，前缀匹配该顶级模块下的权限标识
  if (form.parentId) {
    const topName = findTopMenuName(parentOptions.value, form.parentId)
    const prefixes = MODULE_PERM_PREFIX[topName]
    if (prefixes) {
      list = list.filter((p) => prefixes.some(prefix => p.permCode === prefix || p.permCode.startsWith(prefix + ':')))
    }
  }

  return list.map((p) => ({
    label: p.permName ? `${p.permCode} — ${p.permName}` : p.permCode,
    value: p.permCode,
  }))
})

/** 是否一级菜单/父菜单（禁用权限标识） */
const isParentMenu = computed(() => {
  // 一级菜单（parentId=0）：不需要权限标识（String() 兼容 Long→String）
  if (String(form.parentId) === '0') return true
  // 编辑模式：已有子菜单的父菜单也不需要
  if (editingId.value) {
    const hasChildren = (tree, id) => tree.some((n) =>
      String(n.id) === String(id)
        ? (n.children && n.children.length > 0)
        : hasChildren(n.children || [], id),
    )
    if (hasChildren(treeData.value, editingId.value)) return true
  }
  return false
})

/** 加载全部权限列表（供权限标识下拉使用） */
async function loadPermissions() {
  try {
    const { data } = await getAllPermissions()
    allPermList.value = data || []
  } catch {
    allPermList.value = []
  }
}

/** 加载菜单树（超管返回全部，企业管理员返回自己有权限的） */
async function fetchTree() {
  loading.value = true
  try {
    // 调用后端菜单树接口
    const { data } = await getMenuTree()
    treeData.value = data || []
    // 父菜单下拉也用同一棵树（深拷贝防止树形数据被修改时相互影响）
    parentOptions.value = JSON.parse(JSON.stringify(data || []))
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

/** 重置表单 */
function resetForm() {
  Object.assign(form, {
    parentId: 0, menuType: 'menu', menuName: '', menuPath: '',
    menuIcon: '', permCode: '', sortOrder: 0,
    isVisible: true, isCache: false,
  })
  formRef.value?.resetFields()
}

/** 从树中移除指定ID的节点及其子孙 */
function filterTree(tree, excludeId) {
  return tree
    .filter((node) => String(node.id) !== String(excludeId))
    .map((node) => ({
      ...node,
      children: node.children ? filterTree(node.children, excludeId) : [],
    }))
}

/** 新增 */
function handleCreate() {
  formTitle.value = '新增菜单'
  editingId.value = null
  originalData.value = null
  formKey.value++
  resetForm()
  parentOptions.value = JSON.parse(JSON.stringify(treeData.value))
  formVisible.value = true
}

/** 编辑菜单：先加载详情，再打开弹窗（避免先显示旧数据再刷新） */
async function handleEdit(row) {
  formTitle.value = '编辑菜单'
  editingId.value = row.id
  formKey.value++
  resetForm()
  // 父菜单下拉排除自身及其子节点，防止循环引用
  parentOptions.value = filterTree(JSON.parse(JSON.stringify(treeData.value)), row.id)
  loading.value = true
  try {
    // 调用后端获取菜单详情接口
    const { data } = await getMenuById(row.id)
    Object.assign(form, {
      parentId: data.parentId || 0,
      menuType: data.menuType || 'menu',
      menuName: data.menuName || '',
      menuPath: data.menuPath || '',
      menuIcon: data.menuIcon || '',
      permCode: data.permCode || '',
      sortOrder: data.sortOrder || 0,
      isVisible: data.isVisible !== false,
      isCache: data.isCache === true,
    })
    // 保存原始数据用于脏检查
    originalData.value = {
      parentId: form.parentId, menuType: form.menuType, menuName: form.menuName,
      menuPath: form.menuPath, menuIcon: form.menuIcon, permCode: form.permCode,
      sortOrder: form.sortOrder, isVisible: form.isVisible, isCache: form.isCache,
    }
  } catch {
    /* 拦截器已处理 */
    return
  } finally {
    loading.value = false
  }
  formVisible.value = true
}

/** 删除菜单（后端校验有子菜单时拒绝） */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除菜单 <span class="highlight-name">${row.menuName}</span> 吗？有子菜单则无法删除。`, '删除确认', { type: 'warning', dangerouslyUseHTMLString: true })
  } catch {
    return
  }
  try {
    // 调用后端逻辑删除菜单接口
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchTree()  // 刷新树
  } catch { /* 拦截器已处理 */ }
}

/** 提交菜单表单（新增或编辑） */
async function handleSubmit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }

  // 编辑时检查数据是否变化，未修改则直接关闭
  if (editingId.value && originalData.value) {
    const unchanged =
      String(form.parentId) === String(originalData.value.parentId) &&
      form.menuType === originalData.value.menuType &&
      form.menuName === originalData.value.menuName &&
      form.menuPath === originalData.value.menuPath &&
      form.menuIcon === originalData.value.menuIcon &&
      form.permCode === originalData.value.permCode &&
      form.sortOrder === originalData.value.sortOrder &&
      form.isVisible === originalData.value.isVisible &&
      form.isCache === originalData.value.isCache
    if (unchanged) {
      formVisible.value = false
      return
    }
  }

  submitting.value = true
  const data = { ...form, parentId: form.parentId || 0 }
  try {
    if (editingId.value) {
      // 编辑：调用更新菜单接口
      await updateMenu(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      // 新增：调用创建菜单接口
      await createMenu(data)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    fetchTree()  // 刷新树
  } catch { /* 拦截器已处理 */ } finally {
    submitting.value = false
  }
}

onMounted(() => { fetchTree(); loadPermissions() })
</script>
