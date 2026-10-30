<template>
  <div>
    <!-- 搜索 -->
    <el-card class="search-card">
      <el-form
        :model="searchForm"
        inline
      >
        <el-form-item label="租户编码">
          <el-input
            v-model="searchForm.tenantCode"
            placeholder="请输入租户编码"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="企业名称">
          <el-input
            v-model="searchForm.companyName"
            placeholder="请输入企业名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option
              label="正常"
              value="active"
            />
            <el-option
              label="待审核"
              value="pending"
            />
            <el-option
              label="已停用"
              value="suspended"
            />
            <el-option
              label="已拒绝"
              value="rejected"
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

    <!-- 列表 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">租户列表</span>
        <el-button
          v-if="hasPermission('tenant', perms)"
          type="primary"
          :icon="Plus"
          @click="handleCreate"
        >
          新增租户
        </el-button>
      </div>

      <el-table :data="list"
        border
        stripe
      >
        <el-table-column
          type="index"
          :index="(idx) => (pageNum - 1) * pageSize + idx + 1"
          label="ID"
          width="50"
          align="center"
        />
        <el-table-column
          prop="tenantCode"
          label="租户编码"
          min-width="110"
        />
        <el-table-column
          prop="tenantName"
          label="企业名称"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="shortName"
          label="简称"
          min-width="100"
        />
        <el-table-column
          prop="contactName"
          label="联系人"
          min-width="90"
        />
        <el-table-column
          prop="contactEmail"
          label="联系邮箱"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="status"
          label="状态"
          min-width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              size="small"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="申请身份"
          min-width="110"
          align="center"
        >
          <template #default="{ row }">
            <template v-if="row.applicantRole === 'buyer'">
              <el-tag type="primary" size="small">采购商</el-tag>
            </template>
            <template v-else-if="row.applicantRole === 'supplier'">
              <el-tag type="success" size="small">供应商</el-tag>
            </template>
            <template v-else-if="row.applicantRole === 'buyer,supplier'">
              <el-tag type="warning" size="small">采购商+供应商</el-tag>
            </template>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="创建时间"
          min-width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ row.createTime?.slice(0, 10) }}
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
              v-if="hasPermission('tenant', perms)"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <!-- pending: 编辑 + ⋮(通过/删除) -->
            <template v-if="row.status === 'pending' && hasPermission('tenant', perms)">
              <el-dropdown trigger="click" @command="(cmd) => handleTenantCmd(cmd, row)">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="review" :icon="Select">通过</el-dropdown-item>
                    <el-dropdown-item command="delete" divided style="color:#f56c6c" :icon="Delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <!-- active: 编辑 + ⋮(停用/删除) -->
            <template v-if="row.status === 'active' && hasPermission('tenant', perms)">
              <el-dropdown trigger="click" @command="(cmd) => handleTenantCmd(cmd, row)">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="review" :icon="Remove">停用</el-dropdown-item>
                    <el-dropdown-item command="delete" divided style="color:#f56c6c" :icon="Delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <!-- suspended: 编辑 + ⋮(启用/删除) -->
            <template v-if="row.status === 'suspended' && hasPermission('tenant', perms)">
              <el-dropdown trigger="click" @command="(cmd) => handleTenantCmd(cmd, row)">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="review" :icon="Select">启用</el-dropdown-item>
                    <el-dropdown-item command="delete" divided style="color:#f56c6c" :icon="Delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <!-- rejected: 2个操作，平铺 -->
            <el-button
              v-if="row.status === 'rejected' && hasPermission('tenant', perms)"
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
    <el-dialog
      v-model="formVisible"
      :title="formTitle"
      width="640px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        autocomplete="off"
      >
        <el-form-item
          label="租户编码"
          prop="tenantCode"
        >
          <el-input
            v-model="form.tenantCode"
            placeholder="如 huawei"
            :maxlength="32"
          />
        </el-form-item>
        <el-form-item
          label="企业名称"
          prop="tenantName"
        >
          <el-input
            v-model="form.tenantName"
            placeholder="如 华为技术有限公司"
            :maxlength="128"
          />
        </el-form-item>
        <el-form-item
          label="企业简称"
          prop="shortName"
        >
          <el-input
            v-model="form.shortName"
            placeholder="如 华为"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item
          label="所在国家"
          prop="country"
        >
          <el-input
            v-model="form.country"
            placeholder="如 中国"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item
          label="注册号"
          prop="regNumber"
        >
          <el-input
            v-model="form.regNumber"
            placeholder="企业注册号"
            :maxlength="128"
          />
        </el-form-item>
        <el-form-item
          label="联系人"
          prop="contactName"
        >
          <el-input
            v-model="form.contactName"
            placeholder="联系人姓名"
            :maxlength="64"
          />
        </el-form-item>
        <el-form-item
          label="联系邮箱"
          prop="contactEmail"
        >
          <el-input
            v-model="form.contactEmail"
            placeholder="联系人邮箱"
            :maxlength="255"
          />
        </el-form-item>
        <el-form-item
          label="联系电话"
          prop="contactPhone"
        >
          <el-input
            v-model="form.contactPhone"
            placeholder="联系人电话"
            :maxlength="32"
          />
        </el-form-item>

        <!-- 管理员账号（仅新增时必填） -->
        <template v-if="!editingId">
          <el-divider content-position="left">
            管理员账号
          </el-divider>
          <el-form-item
            label="用户名"
            prop="adminUsername"
            :rules="adminRules.username"
          >
            <el-input
              v-model="form.adminUsername"
              placeholder="登录用户名"
              :maxlength="64"
              autocomplete="off"
            />
          </el-form-item>
          <el-form-item
            label="密码"
            prop="adminPassword"
            :rules="adminRules.password"
          >
            <el-input
              v-model="form.adminPassword"
              type="password"
              show-password
              placeholder="至少6位"
              :maxlength="64"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item
            label="姓名"
            prop="adminRealName"
          >
            <el-input
              v-model="form.adminRealName"
              placeholder="真实姓名"
              :maxlength="64"
            />
          </el-form-item>
        </template>
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
import { ref, reactive, onMounted } from 'vue'
import { getTenantList, getTenantById, createTenant, updateTenant, reviewTenant, deleteTenant } from '@/api/tenant'
import { usePermissionStore } from '@/stores/permission'
import { hasPermission } from '@/utils/permission'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS, TENANT_STATUS_MAP } from '@/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Select, Remove, MoreFilled } from '@element-plus/icons-vue'

const perms = usePermissionStore().permissions

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const searchForm = reactive({ tenantCode: '', companyName: '', status: '' })

const formVisible = ref(false)
const formTitle = ref('新增租户')
const editingId = ref(null)
const formRef = ref(null)
const submitting = ref(false)
const originalData = ref(null)  // 编辑时的原始数据，用于脏检查
const form = reactive({
  tenantCode: '', tenantName: '', shortName: '', country: '',
  regNumber: '', contactName: '', contactEmail: '', contactPhone: '',
  adminUsername: '', adminPassword: '', adminRealName: '',
})

const rules = {
  tenantCode: [
    { required: true, message: '请输入租户编码', trigger: 'blur' },
    { min: 2, max: 32, message: '编码长度2-32位', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9]*$/, message: '小写字母开头，仅含字母数字', trigger: 'blur' },
  ],
  tenantName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入企业简称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactEmail: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}
const adminRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 2, max: 64, message: '长度2-64位', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '至少6位', trigger: 'blur' }],
}

/** 根据租户状态返回中文标签 */
function statusLabel(s) { return TENANT_STATUS_MAP[s]?.label || s }
/** 根据租户状态返回 el-tag type */
function statusType(s) { return TENANT_STATUS_MAP[s]?.type || 'info' }

/** 获取租户分页列表 */
async function fetchList() {
  loading.value = true
  try {
    // 调用后端租户分页查询接口，传入分页参数和搜索条件
    const { data } = await getTenantList({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
    list.value = data.records || []  // 分页记录
    total.value = data.total || 0    // 总条数
  } catch { list.value = []; total.value = 0 } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() { searchForm.tenantCode = ''; searchForm.companyName = ''; searchForm.status = ''; pageNum.value = 1; fetchList() }
function handlePageChange(p) { pageNum.value = p; fetchList() }
function handleSizeChange(s) { pageSize.value = s; pageNum.value = 1; fetchList() }

function resetForm() {
  Object.assign(form, { tenantCode: '', tenantName: '', shortName: '', country: '', regNumber: '', contactName: '', contactEmail: '', contactPhone: '', adminUsername: '', adminPassword: '', adminRealName: '' })
}

function handleCreate() { formTitle.value = '新增租户'; editingId.value = null; originalData.value = null; resetForm(); formVisible.value = true }

// 弹窗关闭动画结束后再重置表单，避免用户看到数据闪烁
function onDialogClosed() {
  resetForm()
  formRef.value?.resetFields()
}

/** 编辑租户：先加载详情，再打开弹窗（避免先显示旧数据再刷新） */
async function handleEdit(row) {
  formTitle.value = '编辑租户'; editingId.value = row.id
  resetForm()  // 先清空，防止加载期间显示旧数据
  formRef.value?.resetFields()
  loading.value = true  // 复用列表的 loading 或单独设
  try {
    // 调用后端获取租户详情接口
    const { data } = await getTenantById(row.id)
    Object.assign(form, {
      tenantCode: data.tenantCode || '', tenantName: data.tenantName || '', shortName: data.shortName || '',
      country: data.country || '', regNumber: data.regNumber || '', contactName: data.contactName || '',
      contactEmail: data.contactEmail || '', contactPhone: data.contactPhone || '',
    })
    // 保存原始数据用于脏检查
    originalData.value = {
      tenantCode: form.tenantCode, tenantName: form.tenantName, shortName: form.shortName,
      country: form.country, regNumber: form.regNumber, contactName: form.contactName,
      contactEmail: form.contactEmail, contactPhone: form.contactPhone,
    }
    formVisible.value = true  // 数据就绪后再打开弹窗
  } catch { /* 拦截器已处理 */ } finally { loading.value = false }
}

/** 提交租户表单（新增或编辑） */
async function handleSubmit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }

  // 编辑时检查数据是否变化，未修改则直接关闭
  if (editingId.value && originalData.value) {
    const unchanged =
      form.tenantCode === originalData.value.tenantCode &&
      form.tenantName === originalData.value.tenantName &&
      form.shortName === originalData.value.shortName &&
      form.country === originalData.value.country &&
      form.regNumber === originalData.value.regNumber &&
      form.contactName === originalData.value.contactName &&
      form.contactEmail === originalData.value.contactEmail &&
      form.contactPhone === originalData.value.contactPhone
    if (unchanged) {
      formVisible.value = false
      return
    }
  }

  submitting.value = true
  try {
    if (editingId.value) {
      // 编辑：调用更新租户接口
      await updateTenant(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      // 新增：调用创建租户接口（含管理员账号）
      await createTenant(form)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    fetchList()  // 刷新列表
  } catch { /* 拦截器已处理 */ } finally { submitting.value = false }
}

/** 审核租户（通过/停用/拒绝） */
/** 下拉菜单命令分发 */
function handleTenantCmd(cmd, row) {
  if (cmd === 'delete') handleDelete(row)
  else if (cmd === 'review') {
    const targetStatus = row.status === 'active' ? 'suspended' : 'active'
    handleReview(row, targetStatus)
  }
}

async function handleReview(row, status) {
  const label = status === 'active' ? '通过' : status === 'suspended' ? '停用' : '操作'
  try { await ElMessageBox.confirm(`确认${label}租户 <span class="highlight-name">${row.tenantName}</span> 吗？`, `${label}确认`, { type: 'warning', dangerouslyUseHTMLString: true }) } catch { return }
  try {
    // 调用后端审核租户接口（停用会同时踢出该租户所有在线用户）
    await reviewTenant(row.id, status, '')
    ElMessage.success(`${label}成功`)
    fetchList()  // 刷新列表
  } catch { /* 拦截器已处理 */ }
}

/** 删除租户 */
async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确认删除租户 <span class="highlight-name">${row.tenantName}</span> 吗？此操作不可恢复。`, '删除确认', { type: 'warning', dangerouslyUseHTMLString: true }) } catch { return }
  try {
    // 调用后端删除租户接口
    await deleteTenant(row.id)
    ElMessage.success('删除成功')
    fetchList()  // 刷新列表
  } catch { /* 拦截器已处理 */ }
}

onMounted(() => { fetchList() })
</script>
