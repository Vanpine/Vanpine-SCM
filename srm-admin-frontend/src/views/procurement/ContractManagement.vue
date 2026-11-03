<template>
  <div>
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="合同标题" clearable style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:160px" @change="handleSearch">
            <el-option v-for="opt in CONTRACT_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isDual" label="视角">
          <el-select v-model="searchForm.role" style="width:120px" @change="fetchList">
            <el-option label="采购方" value="buyer" />
            <el-option label="供应方" value="supplier" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 内容卡片 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">合同列表</span>
        <el-button v-if="searchForm.role === 'buyer'" type="primary" :icon="Plus" @click="handleCreate">创建合同</el-button>
      </div>

      <el-table :data="list" stripe border>
        <IdColumn :page-num="pageNum" :page-size="pageSize" />
        <el-table-column prop="contractNo" label="合同编号" min-width="250" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.contractNo" /></template>
        </el-table-column>
        <el-table-column prop="title" label="合同标题" min-width="350" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.title" /></template>
        </el-table-column>
        <el-table-column prop="inquiryNo" label="询盘编号" min-width="250" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.inquiryNo" /></template>
        </el-table-column>
        <el-table-column prop="buyerCompanyName" label="采购企业" min-width="280" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.buyerCompanyName" /></template>
        </el-table-column>
        <el-table-column prop="supplierCompanyName" label="供应企业" min-width="280" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.supplierCompanyName" /></template>
        </el-table-column>
        <el-table-column label="数量" min-width="100">
          <template #default="{ row }"><EmptyText :value="row.quantity" /></template>
        </el-table-column>
        <el-table-column label="合同金额" min-width="120">
          <template #default="{ row }"><EmptyText :value="row.amount" /></template>
        </el-table-column>
        <el-table-column label="币种" min-width="80">
          <template #default="{ row }"><EmptyText :value="row.currency" /></template>
        </el-table-column>
        <el-table-column prop="deliveryPlace" label="交货地点" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><EmptyText :value="row.deliveryPlace" /></template>
        </el-table-column>
        <el-table-column label="交货日期" min-width="120">
          <template #default="{ row }"><EmptyText :value="row.deliveryDate" /></template>
        </el-table-column>
        <el-table-column label="截止日期" min-width="120">
          <template #default="{ row }"><EmptyText :value="row.validUntil" /></template>
        </el-table-column>
        <el-table-column label="状态" min-width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status] || 'info'" size="small">{{ getContractStatusLabel(row.status, searchForm.role === 'supplier', row.supplierConfirmed === 1, !!mySignUrl(row)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="200">
          <template #default="{ row }"><EmptyText :value="row.createTime">{{ formatDateTime(row.createTime) }}</EmptyText></template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <!-- 采购方视角 -->
            <template v-if="searchForm.role === 'buyer'">
              <!-- 无主操作的状态（草稿/待确认/签署详情收进三点）：详情平铺 -->
              <el-button v-if="row.status !== 'draft' && row.status !== 'pending_supplier' && row.status !== 'signed'" link type="primary" size="small" @click="handleDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'draft'" link type="primary" size="small" @click="handleSubmitContract(row)">发起</el-button>
              <template v-if="row.status === 'pending_supplier'">
                <!-- 供应商已确认条款后才可上传盖章件 -->
                <el-button v-if="row.supplierConfirmed === 1" link type="warning" size="small" @click="openUploadSign(row)">上传</el-button>
                <!-- 三点：详情 / 撤回 -->
                <el-dropdown trigger="click" @command="(cmd) => handleWithdrawCmd(cmd, row)" style="vertical-align:middle">
                  <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:8px;vertical-align:middle">
                    <MoreFilled />
                  </el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="detail">详情</el-dropdown-item>
                      <el-dropdown-item command="withdraw" divided style="color:#f56c6c">撤回</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              <template v-if="row.status === 'pending_confirm'">
                <!-- 本方未盖章（对方已上传）：上传双章合同即签署 -->
                <el-button v-if="!mySignUrl(row)" link type="warning" size="small" @click="openUploadSign(row)">上传</el-button>
              </template>
              <el-button v-if="row.status === 'signed'" link type="success" size="small" @click="handleComplete(row)">完成</el-button>
              <el-dropdown v-if="row.status === 'draft' || row.status === 'signed'" trigger="click" @command="(cmd) => handleCmd(cmd, row)" style="vertical-align:middle">
                <el-icon :size="16" style="cursor:pointer;color:#409eff;margin-left:8px;vertical-align:middle">
                  <MoreFilled />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.status === 'draft'" command="detail">详情</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'draft'" command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'signed'" command="detail">详情</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'signed'" command="terminate" divided style="color:#f56c6c">终止合同</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <!-- 供应方视角 -->
            <template v-else>
              <el-button link type="primary" size="small" @click="handleDetail(row)">详情</el-button>
              <template v-if="row.status === 'pending_supplier'">
                <!-- 未确认：审核下拉（确认 / 拒绝） -->
                <el-dropdown v-if="row.supplierConfirmed !== 1" trigger="click" @command="(cmd) => handleAuditCmd(cmd, row)" style="vertical-align:middle;margin-left:6px">
                  <el-button link type="success" size="small">审核</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="confirm">确认</el-dropdown-item>
                      <el-dropdown-item command="decline" divided style="color:#f56c6c">拒绝</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <!-- 已确认：可上传盖章件 -->
                <el-button v-else link type="warning" size="small" @click="openUploadSign(row)">上传</el-button>
              </template>
              <template v-if="row.status === 'pending_confirm'">
                <!-- 本方未盖章（对方已上传）：上传双章合同即签署 -->
                <el-button v-if="!mySignUrl(row)" link type="warning" size="small" @click="openUploadSign(row)">上传</el-button>
              </template>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" :description="searchForm.keyword ? '未找到匹配的合同' : '暂无合同'" />

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 创建/编辑 Dialog -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑合同' : '创建合同'" :close-on-click-modal="false" class="contract-dialog" @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="right">
        <el-form-item label="询盘" prop="inquiryId">
          <el-select v-model="form.inquiryId" :disabled="!!editingId" filterable placeholder="请选择询盘" style="width:100%" @focus="loadInquiryOptions" @change="handleInquiryChange">
            <el-option v-for="iq in inquiryOptions" :key="iq.id" :label="`${iq.inquiryNo} - ${iq.title || '无标题'}`" :value="iq.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="中标供应商" prop="winningQuoteId">
          <el-select v-model="form.winningQuoteId" :disabled="!!editingId" filterable placeholder="请选择中标供应商" style="width:100%" @change="handleSupplierChange">
            <el-option v-for="q in supplierOptions" :key="q.id" :disabled="activeQuoteIds.includes(q.id)" :label="`${q.supplierCompanyName}（供 ${q.awardedQuantity ?? q.supplyQuantity ?? '-'}）${activeQuoteIds.includes(q.id) ? ' · 已签约' : ''}`" :value="q.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板类型">
          <el-select v-model="form.templateType" style="width:100%">
            <el-option label="标准采购合同" value="standard" />
            <el-option label="国际贸易合同" value="international" disabled />
            <el-option label="框架协议" value="framework" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="合同标题" prop="title" required>
          <el-input v-model="form.title" :maxlength="200" placeholder="如：2025年度钢材采购合同" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="合同金额">
              <el-input v-model="form.amount" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种">
              <el-input v-model="form.currency" :maxlength="8" placeholder="CNY" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="合同总量">
          <el-input :model-value="contractQty != null ? String(contractQty) : '未定'" disabled />
        </el-form-item>
        <el-form-item label="截止日期" prop="validUntil" required>
          <el-date-picker v-model="form.validUntil" type="date" style="width:100%" value-format="YYYY-MM-DD" placeholder="请选择截止日期" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交货地点">
              <el-input v-model="form.deliveryPlace" placeholder="如：深圳，选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货日期">
              <el-date-picker v-model="form.deliveryDate" type="date" style="width:100%" value-format="YYYY-MM-DD" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最晚发运日期">
              <el-date-picker v-model="form.plannedShipDate" type="date" style="width:100%" value-format="YYYY-MM-DD" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许延期天数">
              <el-input-number v-model="form.allowedDelayDays" :min="0" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 断供影响 -->
        <el-divider content-position="left">断供影响</el-divider>
        <el-row :gutter="32" class="rule-row">
          <el-col :span="12">
            <el-form-item label="生产需求日期">
              <el-date-picker v-model="form.productionNeedDate" type="date" style="width:160px" value-format="YYYY-MM-DD" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料关键等级">
              <el-select v-model="form.materialCriticalLevel" clearable placeholder="选填" style="width:140px">
                <el-option label="低" value="low" />
                <el-option label="中" value="medium" />
                <el-option label="高" value="high" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存覆盖天数">
              <el-input-number v-model="form.inventoryCoverDays" :min="0" :controls="false" style="width:120px" placeholder="如：7" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 交付规则 -->
        <el-divider content-position="left">交付规则</el-divider>
        <el-row :gutter="32" class="rule-row">
          <el-col :span="12">
            <el-form-item label="发运反馈天数">
              <el-input-number v-model="form.deliveryNoticeDays" :min="0" :controls="false" style="width:120px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="异常通知小时数">
              <el-input-number v-model="form.exceptionNoticeHours" :min="0" :controls="false" style="width:120px" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 违约金条款（选填：留空则不计算违约金） -->
        <el-row :gutter="32" class="rule-row">
          <el-col :span="12">
            <el-form-item label="每日违约金率">
              <el-input-number v-model="form.penaltyDailyRate" :min="0" :max="1" :precision="6" :step="0.0001" :controls="false" style="width:120px" />
              <span style="color:#909399;font-size:12px;margin-left:6px">万分之五=0.0005</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="违约金上限比例">
              <el-input-number v-model="form.penaltyMaxRate" :min="0" :max="1" :precision="4" :step="0.01" :controls="false" style="width:120px" />
              <span style="color:#909399;font-size:12px;margin-left:6px">10%=0.10</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="32" class="rule-row">
          <el-col :span="12">
            <el-form-item label="允许部分交付">
              <el-switch v-model="form.partialDeliveryAllowed" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许替代物料">
              <el-switch v-model="form.substitutionAllowed" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许加急运输">
              <el-switch v-model="form.expediteAllowed" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="分批计划">
          <div class="batch-plan">
            <div v-for="(row, idx) in batchPlanRows" :key="idx" class="batch-row">
              <span class="batch-no">第 {{ idx + 1 }} 批</span>
              <el-input-number v-model="row.quantity" :min="1" :controls="false" placeholder="数量" class="batch-qty" />
              <el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="交货日期" class="batch-date" />
              <el-input v-model="row.remark" placeholder="备注" class="batch-remark" />
              <el-button link type="danger" @click="removeBatchRow(idx)">删除</el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addBatchRow">+ 添加批次</el-button>
            <div class="batch-hint" :class="{ 'batch-error': batchMismatch }">
              已分配 {{ batchSum }} / 总量 {{ contractQty ?? '—' }}，剩余 {{ batchRemaining }}<template v-if="batchMismatch">（各批之和必须等于合同总量）</template>
            </div>
          </div>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="付款条件">
              <el-input v-model="form.paymentTerms" placeholder="如：货到付款，选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货条款">
              <el-input v-model="form.deliveryTerms" placeholder="如：FOB，选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="合同附件">
          <el-upload
            :show-file-list="false"
            :http-request="handleAttachmentUpload"
            :before-upload="beforeAttachmentUpload"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png"
          >
            <el-button type="primary" plain :loading="attachmentUploading">
              <el-icon><Plus /></el-icon> {{ form.attachmentUrl ? '重新上传' : '选择文件' }}
            </el-button>
          </el-upload>
          <div v-if="form.attachmentUrl" style="margin-top:8px;color:#67c23a;font-size:13px">已选择：{{ getFileName(form.attachmentUrl) }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- 详情 Dialog -->
    <el-dialog v-model="detailVisible" title="合同详情" :close-on-click-modal="false" class="contract-dialog contract-detail-dialog">
      <div v-loading="detailLoading" style="min-height:150px">
      <template v-if="detail">
        <!-- 采购方撤回提示（draft 且撤回时间非空） -->
        <el-alert v-if="detail.status === 'draft' && detail.withdrawnAt" type="warning" :closable="false" show-icon
          title="采购方已撤回修改" description="合同已由采购方撤回，条款可能已调整，请等待重新发起。" style="margin-bottom:12px" />
        <el-descriptions title="合同信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="合同编号"><EmptyText :value="detail.contractNo" /></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="STATUS_TAG[detail.status] || 'info'">{{ getContractStatusLabel(detail.status, searchForm.role === 'supplier', detail.supplierConfirmed === 1) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合同标题" :span="2"><EmptyText :value="detail.title" /></el-descriptions-item>
          <el-descriptions-item label="采购企业"><EmptyText :value="detail.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="供应企业"><EmptyText :value="detail.supplierCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="数量"><EmptyText :value="detail.quantity" /></el-descriptions-item>
          <el-descriptions-item label="合同金额"><EmptyText :value="detail.amount" /></el-descriptions-item>
          <el-descriptions-item label="币种"><EmptyText :value="detail.currency" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="关联信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="询盘"><EmptyText :value="detail.inquiryTitle" /></el-descriptions-item>
          <el-descriptions-item label="询盘编号"><EmptyText :value="detail.inquiryNo" /></el-descriptions-item>
         <el-descriptions-item label="中标金额"><EmptyText :value="detail.quoteAmount" /></el-descriptions-item>
          <el-descriptions-item label="中标供应商"><EmptyText :value="detail.supplierCompanyName" /></el-descriptions-item>
       </el-descriptions>

        <!-- 分批计划 -->
        <div v-if="detail.batchPlans && detail.batchPlans.length" class="batch-detail-section">
          <div class="batch-detail-title">分批计划</div>
          <el-table :data="detail.batchPlans" border size="small" style="width:100%">
            <el-table-column label="批次" type="index" width="60" align="center" />
            <el-table-column label="数量" prop="quantity" width="100" align="center" />
            <el-table-column label="交货日期" prop="deliveryDate" width="140" align="center">
              <template #default="{ row }"><EmptyText :value="row.deliveryDate" /></template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="120">
              <template #default="{ row }"><EmptyText :value="row.remark" /></template>
            </el-table-column>
          </el-table>
          <div class="batch-detail-summary">
            合计：{{ detail.batchPlans.reduce((s, b) => s + (Number(b.quantity) || 0), 0) }} / 合同总量 {{ detail.quantity ?? '—' }}
          </div>
        </div>

        <el-descriptions title="执行信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="签署日期"><EmptyText :value="detail.signDate" /></el-descriptions-item>
          <el-descriptions-item label="截止日期"><EmptyText :value="detail.validUntil" /></el-descriptions-item>
          <el-descriptions-item label="交货地点"><EmptyText :value="detail.deliveryPlace" /></el-descriptions-item>
          <el-descriptions-item label="交货日期"><EmptyText :value="detail.deliveryDate" /></el-descriptions-item>
          <el-descriptions-item label="付款条件"><EmptyText :value="detail.paymentTerms" /></el-descriptions-item>
          <el-descriptions-item label="交货条款"><EmptyText :value="detail.deliveryTerms" /></el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="其他信息" :column="2" border class="detail-descriptions">
          <el-descriptions-item label="供应商确认">
            <span v-if="detail.supplierConfirmed === 1 && detail.supplierConfirmedAt">{{ formatDateTime(detail.supplierConfirmedAt) }}</span>
            <span v-else-if="detail.supplierConfirmed === 1">已确认</span>
            <span v-else>待确认</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'draft' && detail.declineReason" label="拒绝理由">
            <span style="color:#f56c6c">{{ detail.declineReason }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="采购方盖章件">
            <a v-if="detail.buyerSignAttachmentUrl" :href="getFileUrl(detail.buyerSignAttachmentUrl)" target="_blank" style="color:#409eff">{{ getFileName(detail.buyerSignAttachmentUrl) }}</a>
            <span v-else>未盖章</span>
          </el-descriptions-item>
          <el-descriptions-item label="供应方盖章件">
            <a v-if="detail.supplierSignAttachmentUrl" :href="getFileUrl(detail.supplierSignAttachmentUrl)" target="_blank" style="color:#409eff">{{ getFileName(detail.supplierSignAttachmentUrl) }}</a>
            <span v-else>未盖章</span>
          </el-descriptions-item>
          <el-descriptions-item label="最终合同">
            <a v-if="detail.buyerSignAttachmentUrl && detail.supplierSignAttachmentUrl && detail.signAttachmentUrl" :href="getFileUrl(detail.signAttachmentUrl)" target="_blank" style="color:#67c23a;font-weight:600">{{ getFileName(detail.signAttachmentUrl) }}</a>
            <span v-else class="empty-text">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="合同模板">
            <el-button link type="primary" size="small" @click="handlePdf(detail)">生成/预览合同模板</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="合同附件">
            <a v-if="detail.attachmentUrl" :href="getFileUrl(detail.attachmentUrl)" target="_blank" style="color:#409eff">查看附件</a>
            <span v-else class="empty-text">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间"><EmptyText :value="detail.createTime">{{ formatDateTime(detail.createTime) }}</EmptyText></el-descriptions-item>
          <el-descriptions-item label="更新时间"><EmptyText :value="detail.updateTime">{{ formatDateTime(detail.updateTime) }}</EmptyText></el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'terminated'" label="终止类型"><EmptyText :value="TERMINATE_TYPE_MAP[detail.terminateType] || detail.terminateType" /></el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'terminated'" label="终止原因" :span="2"><EmptyText :value="detail.terminateReason" /></el-descriptions-item>
        </el-descriptions>
      </template>
      </div>
      <template #footer>
        <!-- 采购方视角操作 -->
        <template v-if="detail && searchForm.role === 'buyer'">
          <el-button v-if="detail.status === 'draft'" type="primary" @click="handleDetailSubmit">发起</el-button>
          <el-button v-if="detail.status === 'draft'" type="primary" @click="handleEditFromDetail">编辑</el-button>
          <!-- 供应商已确认条款后才可上传盖章件 -->
          <el-button v-if="detail.status === 'pending_supplier' && detail.supplierConfirmed === 1" type="warning" @click="handleDetailUploadSign">上传盖章件</el-button>
          <!-- 采购方撤回：pending_supplier 状态可撤回待修改 -->
          <el-button v-if="detail.status === 'pending_supplier'" type="warning" @click="handleDetailWithdraw">撤回</el-button>
          <template v-if="detail.status === 'pending_confirm'">
            <!-- 本方未盖章（对方已上传）：上传双章合同即签署 -->
            <el-button v-if="!mySignUrl(detail)" type="warning" @click="handleDetailUploadSign">上传盖章件</el-button>
          </template>
          <el-button v-if="detail.status === 'signed'" type="success" @click="handleDetailComplete">完成</el-button>
          <el-button v-if="detail.status === 'signed'" type="danger" @click="handleDetailTerminate">终止合同</el-button>
        </template>
        <!-- 供应方视角操作 -->
        <template v-if="detail && searchForm.role === 'supplier'">
          <template v-if="detail.status === 'pending_supplier'">
            <!-- 未确认：确认 / 拒绝 -->
            <template v-if="detail.supplierConfirmed !== 1">
              <el-button type="success" @click="handleDetailConfirm">确认条款</el-button>
              <el-button type="danger" @click="handleDetailDecline">拒绝并退回</el-button>
            </template>
            <!-- 已确认：可上传盖章件 -->
            <el-button v-else type="warning" @click="handleDetailUploadSign">上传盖章件</el-button>
          </template>
          <template v-if="detail.status === 'pending_confirm'">
            <!-- 本方未盖章（对方已上传）：上传双章合同即签署 -->
            <el-button v-if="!mySignUrl(detail)" type="warning" @click="handleDetailUploadSign">上传盖章件</el-button>
          </template>
        </template>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 终止合同 Dialog -->
    <el-dialog v-model="terminateVisible" title="终止合同" :close-on-click-modal="false" width="460px">
      <el-form label-width="80px" label-position="right">
        <el-form-item label="终止类型">
          <el-select v-model="terminateForm.type" style="width:100%">
            <el-option label="协商终止" value="mutual" />
            <el-option label="供应商违约" value="supplier_breach" />
            <el-option label="采购方违约" value="buyer_breach" />
            <el-option label="不可抗力" value="force_majeure" />
          </el-select>
        </el-form-item>
        <el-form-item label="终止原因">
          <el-input v-model="terminateForm.reason" type="textarea" :rows="3" placeholder="请详细说明终止原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="terminateVisible = false">取消</el-button>
        <el-button type="danger" :loading="terminating" @click="handleConfirmTerminate">确认终止</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝合同 Dialog -->
    <el-dialog v-model="declineVisible" title="拒绝合同" :close-on-click-modal="false" width="460px">
      <el-form label-width="80px" label-position="right">
        <el-form-item label="拒绝理由" required>
          <el-input v-model="declineReason" type="textarea" :rows="3" placeholder="请说明拒绝原因，将退回采购方修改后重新发起" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="declineVisible = false">取消</el-button>
        <el-button type="danger" :loading="declining" @click="handleConfirmDecline">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 上传盖章件 Dialog -->
    <el-dialog v-model="uploadSignVisible" :title="uploadSignTitle" :close-on-click-modal="false" width="460px">
      <div style="padding:8px 0">
        <div style="margin-bottom:10px;color:#606266;font-size:13px">{{ uploadSignTitle === '上传双章合同' ? '上传双方盖章后的最终合同扫描件（PDF/JPG/PNG，≤20MB）' : '上传本方盖章后的合同扫描件（PDF/JPG/PNG，≤20MB）' }}</div>
        <el-upload
          :show-file-list="false"
          :http-request="handleSignFileUpload"
          :before-upload="beforeSignFileUpload"
          accept=".pdf,.jpg,.jpeg,.png"
        >
          <el-button type="primary" plain :loading="signUploading">
            <el-icon><Plus /></el-icon> {{ signAttachmentKey ? '重新上传' : '选择文件' }}
          </el-button>
        </el-upload>
        <div v-if="signAttachmentKey" style="margin-top:8px;color:#67c23a;font-size:13px">已选择：{{ signAttachmentName || '文件' }}</div>
      </div>
      <template #footer>
        <el-button @click="uploadSignVisible = false">取消</el-button>
        <el-button type="primary" :loading="signSubmitting" :disabled="!signAttachmentKey" @click="handleConfirmUploadSign">确认上传</el-button>
      </template>
   </el-dialog>
 </div>
    <!-- 合同 PDF 预览 -->
    <el-dialog v-model="pdfPreviewVisible" title="合同预览" width="80%" top="5vh" destroy-on-close>
      <iframe :src="pdfPreviewUrl" style="width:100%;height:78vh;border:none;"></iframe>
    </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MoreFilled } from '@element-plus/icons-vue'
import {
  listContracts, createContract, updateContract, getContractDetail,
  completeContract, terminateContract,
  submitContract, confirmContract, declineContract, withdrawContract, uploadSignContract, generateContractPdf,
  listInquiries, listQuotes, getActiveContractQuoteIds
} from '@/api/procurement'
import { CONTRACT_STATUS_TAG as STATUS_TAG, CONTRACT_STATUS_OPTIONS, TERMINATE_TYPE_MAP, getContractStatusLabel } from '@/constants/contract'
import { useAuthStore } from '@/stores/auth'
import { uploadFile } from '@/api/upload'
import { getFileUrl, getFileName } from '@/utils/file'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { formatDateTime } from '@/utils/datetime'
import IdColumn from '@/components/IdColumn.vue'

const route = useRoute()
const authStore = useAuthStore()
const isDual = computed(() => authStore.profileType === 'both')
/** 当前登录人租户ID（判断盖章件是否本人上传，本人不可自确认） */
const myTenantId = computed(() => authStore.tenantId)

const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
// 默认视角跟随身份：供应商默认看自己作为乙方的合同，采购方/双身份默认看采购视角
const searchForm = reactive({ keyword: '', status: '', role: authStore.profileType === 'supplier' ? 'supplier' : 'buyer' })

const formVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ inquiryId: null, winningQuoteId: null, title: '', amount: '', currency: 'CNY', signDate: '', validUntil: '', deliveryPlace: '', deliveryDate: '', paymentTerms: '', deliveryTerms: '', attachmentUrl: '', templateType: 'standard', productionNeedDate: '', materialCriticalLevel: '', inventoryCoverDays: null, plannedShipDate: '', allowedDelayDays: 0, partialDeliveryAllowed: 1, substitutionAllowed: 0, expediteAllowed: 1, deliveryNoticeDays: 3, exceptionNoticeHours: 24, penaltyDailyRate: null, penaltyMaxRate: null })
const rules = {
  inquiryId: [{ required: true, message: '请选择询盘', trigger: 'change' }],
  winningQuoteId: [{ required: true, message: '请选择中标供应商', trigger: 'change' }],
  title: [{ required: true, message: '请输入合同标题', trigger: 'blur' }],
  validUntil: [{ required: true, message: '请选择截止日期', trigger: 'change' }]
}
const supplierOptions = ref([])
const activeQuoteIds = ref([])   // 已有活跃合同的报价ID（下拉禁用用）

// ========== 分批计划 ==========
const contractQty = ref(null)   // 合同总量：创建取授标数量，编辑取合同自身数量
const batchPlanRows = ref([])   // 分批行 [{ quantity, deliveryDate, remark }]
const batchSum = computed(() => batchPlanRows.value.reduce((s, r) => s + (Number(r.quantity) || 0), 0))
const batchRemaining = computed(() => contractQty.value != null ? contractQty.value - batchSum.value : 0)
const batchMismatch = computed(() => contractQty.value != null && batchSum.value !== contractQty.value)

function addBatchRow() { batchPlanRows.value.push({ quantity: null, deliveryDate: '', remark: '' }) }
function removeBatchRow(idx) { batchPlanRows.value.splice(idx, 1) }
function resetBatchRows(fullQty, deliveryDate) {
  batchPlanRows.value = [{ quantity: fullQty ?? null, deliveryDate: deliveryDate || '', remark: '' }]
}
/** 回填分批行：有分批计划按计划回填，无则默认一批全量 */
function fillBatchPlanRows(batchPlans) {
  if (batchPlans && batchPlans.length) {
    batchPlanRows.value = batchPlans.map(b => ({ quantity: b.quantity ?? null, deliveryDate: b.deliveryDate || '', remark: b.remark || '' }))
  } else {
    resetBatchRows(contractQty.value, form.deliveryDate || '')
  }
}

const detailVisible = ref(false)
const detail = ref(null)

const inquiryOptions = ref([])
async function loadInquiryOptions() {
  if (inquiryOptions.value.length > 0) return
  try {
    const res = await listInquiries({ role: 'buyer', status: 'awarded', pageSize: 100 })
    inquiryOptions.value = res?.data?.records || res?.records || []
  } catch { /* ignore */ }
}

/** 选择询盘后加载中标供应商列表 */
async function handleInquiryChange(inquiryId) {
  form.winningQuoteId = null
  form.amount = ''
  supplierOptions.value = []
  activeQuoteIds.value = []
  contractQty.value = null
  batchPlanRows.value = []
  if (!inquiryId) return
  // 标题 + 交付/付款信息自动带出（询盘信息；String() 兼容 Long 序列化为字符串与旧 number 缓存）
  const iq = inquiryOptions.value.find(x => String(x.id) === String(inquiryId))
  if (iq) {
    if (!form.title) form.title = iq.title || ''
    if (!form.deliveryPlace) form.deliveryPlace = iq.deliveryPlace || ''
    if (!form.deliveryDate) form.deliveryDate = iq.deliveryDate || ''
    if (!form.paymentTerms) form.paymentTerms = iq.paymentTerms || ''
  }
  await loadSupplierOptions(inquiryId)
  // 加载已有活跃合同的报价ID，用于下拉禁用
  try {
    const res = await getActiveContractQuoteIds(inquiryId)
    activeQuoteIds.value = res?.data || res || []
  } catch { /* ignore */ }
}

/** 加载某询盘下的中标供应商列表（isWinning 或有授标数量） */
async function loadSupplierOptions(inquiryId) {
  try {
    const res = await listQuotes(inquiryId)
    const quotes = res?.data || res || []
    supplierOptions.value = quotes.filter(q => q.isWinning || q.awardedQuantity != null)
  } catch { /* ignore */ }
}

/** 选中供应商后带出金额 = 单价×授标数量，币种/交付条款自动带出（String() 兼容 Long→String） */
function handleSupplierChange(quoteId) {
  const q = supplierOptions.value.find(x => String(x.id) === String(quoteId))
  if (!q) return
  const qty = q.awardedQuantity ?? q.supplyQuantity
  contractQty.value = qty != null ? Number(qty) : null
  resetBatchRows(contractQty.value, form.deliveryDate || '')
  if (q.unitPrice && qty) {
    form.amount = (Number(q.unitPrice) * Number(qty)).toFixed(2)
  } else {
    form.amount = q.totalPrice ?? ''
  }
  if (!form.currency) form.currency = q.currency || 'CNY'
  if (!form.deliveryTerms) form.deliveryTerms = q.deliveryTerms || ''
}

onMounted(() => {
  // 供应商"查看合同"跳转 → 自动打开合同详情弹窗（供应商视角只读）
  const qContractId = route.query.contractId
  if (qContractId) {
    searchForm.role = 'supplier'
    fetchList()
    getContractDetail(Number(qContractId)).then(res => {
      detail.value = res?.data || res
      detailVisible.value = true
    }).catch(() => {})
    return
  }
  // 从比价页面跳转过来 → 自动打开创建弹窗并预填询盘 + 中标供应商
  const qInquiryId = route.query.inquiryId
  const qQuoteId = route.query.quoteId
  if (qInquiryId) {
    fetchList()
    loadInquiryOptions().then(async () => {
      editingId.value = null
      form.inquiryId = Number(qInquiryId)
      formVisible.value = true
      await handleInquiryChange(form.inquiryId)
      // 比价页带 quoteId → 直接选中对应中标供应商并带出金额
      if (qQuoteId) {
        form.winningQuoteId = Number(qQuoteId)
        handleSupplierChange(form.winningQuoteId)
      }
    })
    return
  }
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await listContracts({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
    list.value = res?.data?.records || res?.records || []
    total.value = res?.data?.total || res?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchList() }
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  pageNum.value = 1
  fetchList()
}

function handleCreate() {
  editingId.value = null
  Object.assign(form, { inquiryId: null, winningQuoteId: null, title: '', amount: '', currency: 'CNY', signDate: '', validUntil: '', deliveryPlace: '', deliveryDate: '', paymentTerms: '', deliveryTerms: '', attachmentUrl: '', templateType: 'standard', productionNeedDate: '', materialCriticalLevel: '', inventoryCoverDays: null, plannedShipDate: '', allowedDelayDays: 0, partialDeliveryAllowed: 1, substitutionAllowed: 0, expediteAllowed: 1, deliveryNoticeDays: 3, exceptionNoticeHours: 24, penaltyDailyRate: null, penaltyMaxRate: null })
  supplierOptions.value = []
  activeQuoteIds.value = []
  contractQty.value = null
  batchPlanRows.value = []
  formVisible.value = true
}

function onDialogClosed() { formRef.value?.resetFields() }

async function handleSubmit() {
  try { await formRef.value.validate() } catch { return }
  // 组装分批计划并校验各批之和 = 合同总量
  const batchPlans = batchPlanRows.value
    .filter(r => r.quantity != null && Number(r.quantity) > 0)
    .map(r => ({ quantity: Number(r.quantity), deliveryDate: r.deliveryDate || null, remark: r.remark || null }))
  if (contractQty.value != null && batchPlans.length > 0) {
    const sum = batchPlans.reduce((s, b) => s + b.quantity, 0)
    if (sum !== contractQty.value) {
      ElMessage.warning('各批数量之和必须等于合同总量')
      return
    }
  }
  submitting.value = true
  // 空字符串转 null，避免后端 BigDecimal 解析空串报错
  const payload = {
    ...form,
    amount: form.amount === '' || form.amount == null ? null : form.amount,
    validUntil: form.validUntil || null,
    deliveryPlace: form.deliveryPlace || null,
    deliveryDate: form.deliveryDate || null,
    paymentTerms: form.paymentTerms || null,
    deliveryTerms: form.deliveryTerms || null,
    // 后端 CreateContractDTO 字段名为 attachmentKey（存 OSS 对象路径，非签名 URL）
    attachmentKey: form.attachmentUrl || null,
    productionNeedDate: form.productionNeedDate || null,
    materialCriticalLevel: form.materialCriticalLevel || null,
    inventoryCoverDays: form.inventoryCoverDays,
    plannedShipDate: form.plannedShipDate || null,
    allowedDelayDays: form.allowedDelayDays ?? 0,
    partialDeliveryAllowed: form.partialDeliveryAllowed ?? 1,
    substitutionAllowed: form.substitutionAllowed ?? 0,
    expediteAllowed: form.expediteAllowed ?? 1,
    deliveryNoticeDays: form.deliveryNoticeDays ?? 3,
    exceptionNoticeHours: form.exceptionNoticeHours ?? 24,
    penaltyDailyRate: form.penaltyDailyRate ?? null,
    penaltyMaxRate: form.penaltyMaxRate ?? null,
    batchPlans: batchPlans.length ? batchPlans : null
  }
  delete payload.templateType // 模板类型一期不落库，仅 UI 预留
  try {
    if (editingId.value) {
      await updateContract(editingId.value, payload)
      ElMessage.success('合同已更新')
    } else {
      await createContract(payload)
      ElMessage.success('合同已创建')
    }
    formVisible.value = false
    detailVisible.value = false
    fetchList()
  } finally { submitting.value = false }
}

const detailLoading = ref(false)

async function handleDetail(row) {
  detailLoading.value = true
  try {
    const dRes = await getContractDetail(row.id)
    detail.value = dRes?.data || dRes
    detailVisible.value = true
  } catch { /* ignore */ }
  finally { detailLoading.value = false }
}

function handleEditFromDetail() {
  if (!detail.value) return
  editingId.value = detail.value.id
  Object.assign(form, {
    inquiryId: detail.value.inquiryId,
    winningQuoteId: detail.value.winningQuoteId || null,
    title: detail.value.title || '',
    amount: detail.value.amount || '',
    currency: detail.value.currency || 'CNY',
    signDate: detail.value.signDate || '',
    validUntil: detail.value.validUntil || '',
    deliveryPlace: detail.value.deliveryPlace || '',
    deliveryDate: detail.value.deliveryDate || '',
    paymentTerms: detail.value.paymentTerms || '',
    deliveryTerms: detail.value.deliveryTerms || '',
    // 附件回填：用 key（VO 同时返回 attachmentKey），URL 会过期不能回传
    attachmentUrl: detail.value.attachmentKey || '',
    templateType: detail.value.templateType || 'standard',
    productionNeedDate: detail.value.productionNeedDate || '',
    materialCriticalLevel: detail.value.materialCriticalLevel || '',
    inventoryCoverDays: detail.value.inventoryCoverDays ?? null,
    plannedShipDate: detail.value.plannedShipDate || '',
    allowedDelayDays: detail.value.allowedDelayDays ?? 0,
    partialDeliveryAllowed: detail.value.partialDeliveryAllowed ?? 1,
    substitutionAllowed: detail.value.substitutionAllowed ?? 0,
    expediteAllowed: detail.value.expediteAllowed ?? 1,
    deliveryNoticeDays: detail.value.deliveryNoticeDays ?? 3,
    exceptionNoticeHours: detail.value.exceptionNoticeHours ?? 24,
    penaltyDailyRate: detail.value.penaltyDailyRate ?? null,
    penaltyMaxRate: detail.value.penaltyMaxRate ?? null
  })
  contractQty.value = detail.value.quantity != null ? Number(detail.value.quantity) : null
  fillBatchPlanRows(detail.value.batchPlans)
  loadSupplierOptions(detail.value.inquiryId)
  detailVisible.value = false
  formVisible.value = true
}

// ========== 操作按钮 ==========

function handleCmd(cmd, row) {
  if (cmd === 'detail') handleDetail(row)
  else if (cmd === 'edit') handleEditRow(row)
  else if (cmd === 'terminate') handleTerminate(row)
}

/** 供应商"审核"下拉命令：确认 / 拒绝 */
function handleAuditCmd(cmd, row) {
  if (cmd === 'confirm') handleConfirm(row)
  else if (cmd === 'decline') openDecline(row)
}

// ========== 详情弹窗操作（复用列表操作，操作完成后刷新详情） ==========

async function handleDetailSubmit() { await handleSubmitContract(detail.value); await refreshDetail() }
async function handleDetailUploadSign() { openUploadSign(detail.value) }
async function handleDetailConfirm() { await handleConfirm(detail.value); await refreshDetail() }
async function handleDetailDecline() { openDecline(detail.value) }
async function handleDetailComplete() { await handleComplete(detail.value); await refreshDetail() }
async function handleDetailTerminate() { await handleTerminate(detail.value); await refreshDetail() }

async function refreshDetail() {
  try {
    const dRes = await getContractDetail(detail.value.id)
    detail.value = dRes?.data || dRes
  } catch { /* ignore */ }
}

function handleEditRow(row) {
  editingId.value = row.id
  Object.assign(form, {
    inquiryId: row.inquiryId,
    winningQuoteId: row.winningQuoteId || null,
    title: row.title || '',
    amount: row.amount || '',
    currency: row.currency || 'CNY',
    signDate: row.signDate || '',
    validUntil: row.validUntil || '',
    deliveryPlace: row.deliveryPlace || '',
    deliveryDate: row.deliveryDate || '',
    paymentTerms: row.paymentTerms || '',
    deliveryTerms: row.deliveryTerms || '',
    // 附件回填：用 key（VO 同时返回 attachmentKey），URL 会过期不能回传
    attachmentUrl: row.attachmentKey || '',
    templateType: row.templateType || 'standard',
    productionNeedDate: row.productionNeedDate || '',
    materialCriticalLevel: row.materialCriticalLevel || '',
    inventoryCoverDays: row.inventoryCoverDays ?? null,
    plannedShipDate: row.plannedShipDate || '',
    allowedDelayDays: row.allowedDelayDays ?? 0,
    partialDeliveryAllowed: row.partialDeliveryAllowed ?? 1,
    substitutionAllowed: row.substitutionAllowed ?? 0,
    expediteAllowed: row.expediteAllowed ?? 1,
    deliveryNoticeDays: row.deliveryNoticeDays ?? 3,
    exceptionNoticeHours: row.exceptionNoticeHours ?? 24,
    penaltyDailyRate: row.penaltyDailyRate ?? null,
    penaltyMaxRate: row.penaltyMaxRate ?? null
  })
  contractQty.value = row.quantity != null ? Number(row.quantity) : null
  fillBatchPlanRows(row.batchPlans)
  loadSupplierOptions(row.inquiryId)
  formVisible.value = true
}

// ========== 发起 / 供应商确认 / 拒绝 ==========

async function handleSubmitContract(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认发起合同 <span class="highlight-name">${contractDisplayName(row)}</span> 给供应商吗？</div><div>发起后供应商可确认或拒绝条款。</div>`,
      '发起合同',
      { confirmButtonText: '确认发起', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await submitContract(row.id)
    ElMessage.success('已发起，等待供应商确认')
    fetchList()
    refreshDetailIfOpen(row.id)
  } catch { /* ignore */ }
}

/** 采购方撤回/修订（pending_supplier → draft，无需理由） */
async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认撤回合同 <span class="highlight-name">${contractDisplayName(row)}</span> 吗？</div><div>撤回后返回草稿，可修改后重新发起；供应商确认标记将清除。</div>`,
      '撤回合同',
      { confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await withdrawContract(row.id)
    ElMessage.success('合同已撤回，返回草稿')
    fetchList()
    refreshDetailIfOpen(row.id)
  } catch { /* ignore */ }
}

async function handleDetailWithdraw() { await handleWithdraw(detail.value); await refreshDetail() }

/** pending_supplier 三点菜单命令（详情 / 撤回） */
function handleWithdrawCmd(cmd, row) {
  if (cmd === 'detail') handleDetail(row)
  else if (cmd === 'withdraw') handleWithdraw(row)
}

async function handleConfirm(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认同意合同 <span class="highlight-name">${contractDisplayName(row)}</span> 的条款吗？</div><div>确认后双方线下盖章，任一方上传盖章件。</div>`,
      '确认条款',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await confirmContract(row.id)
    ElMessage.success('已确认条款')
    fetchList()
    refreshDetailIfOpen(row.id)
  } catch { /* ignore */ }
}

function openDecline(row) {
  declineTarget.value = row
  declineReason.value = ''
  declineVisible.value = true
}

async function handleConfirmDecline() {
  if (!declineReason.value.trim()) { ElMessage.warning('拒绝理由不能为空'); return }
  declining.value = true
  try {
    await declineContract(declineTarget.value.id, declineReason.value.trim())
    ElMessage.success('已拒绝并退回采购方')
    declineVisible.value = false
    fetchList()
    refreshDetailIfOpen(declineTarget.value.id)
  } catch { /* ignore */ }
  finally { declining.value = false }
}

// ========== 上传盖章件 / 确认盖章件签署 ==========

function openUploadSign(row) {
  uploadSignTarget.value = row
  signAttachmentKey.value = ''
  signAttachmentName.value = ''
  // pending_confirm 且本方未盖章 → 上传双章合同；否则上传本方盖章件
  uploadSignTitle.value = row.status === 'pending_confirm' && !mySignUrl(row) ? '上传双章合同' : '上传盖章件'
  uploadSignVisible.value = true
}

function beforeSignFileUpload(file) {
  const maxSize = 20 * 1024 * 1024
  if (file.size > maxSize) { ElMessage.error('文件不能超过 20MB'); return false }
  return true
}

const attachmentUploading = ref(false)
function beforeAttachmentUpload(file) {
  const maxSize = 20 * 1024 * 1024
  if (file.size > maxSize) { ElMessage.error('文件不能超过 20MB'); return false }
  return true
}

async function handleAttachmentUpload({ file }) {
  attachmentUploading.value = true
  try {
    const { data } = await uploadFile(file, 'contract')
    // 只存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    form.attachmentUrl = data?.key || ''
    ElMessage.success('附件已上传')
  } catch { /* ignore */ }
  finally { attachmentUploading.value = false }
}

async function handleSignFileUpload({ file }) {
  signUploading.value = true
  try {
    const { data } = await uploadFile(file, 'contract')
    // 只保存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    signAttachmentKey.value = data?.key || ''
    signAttachmentName.value = data?.originalName || ''
  } catch { /* ignore */ }
  finally { signUploading.value = false }
}

async function handleConfirmUploadSign() {
  if (!uploadSignTarget.value || !signAttachmentKey.value) return
  signSubmitting.value = true
  try {
    await uploadSignContract(uploadSignTarget.value.id, signAttachmentKey.value)
    // 对方已盖章 → 本次上传双章完成签署；否则等对方盖章上传
    const t = uploadSignTarget.value
    const otherUploaded = myTenantId.value === t.buyerTenantId ? !!t.supplierSignAttachmentUrl : !!t.buyerSignAttachmentUrl
    ElMessage.success(otherUploaded ? '合同已正式签署' : '盖章件已上传，等待对方盖章')
    uploadSignVisible.value = false
    fetchList()
    refreshDetailIfOpen(uploadSignTarget.value.id)
  } catch { /* ignore */ }
  finally { signSubmitting.value = false }
}

/** 合同展示名：优先标题，无标题用合同编号 */
function contractDisplayName(row) {
  return (row?.title || row?.contractNo) || ''
}

/** 生成/预览合同 PDF */
async function handlePdf(row) {
  try {
    const res = await generateContractPdf(row.id)
    const url = res?.data?.url || ''
    if (url) {
      // 归一化为自定义域名，避免 OSS 默认域名强制下载导致 iframe 无法内联预览
      pdfPreviewUrl.value = getFileUrl(url)
      pdfPreviewVisible.value = true
    }
  } catch { /* ignore */ }
}

/** 本方盖章件 URL（空串=本方未传） */
function mySignUrl(row) {
  if (myTenantId.value === row.buyerTenantId) return row.buyerSignAttachmentUrl || ''
  if (myTenantId.value === row.supplierTenantId) return row.supplierSignAttachmentUrl || ''
  return ''
}

/** 详情弹窗开着时刷新目标合同详情 */
async function refreshDetailIfOpen(targetId) {
  if (detailVisible.value && detail.value && String(detail.value.id) === String(targetId)) {
    await refreshDetail()
  }
}

async function handleComplete(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">确认完成合同 <span class="highlight-name">${contractDisplayName(row)}</span> 吗？</div><div>合同将标记为已完成。</div>`,
      '完成合同',
      { confirmButtonText: '确认完成', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch { return }
  try {
    await completeContract(row.id)
    ElMessage.success('合同已完成')
    fetchList()
  } catch { /* ignore */ }
}

// ========== 终止合同 ==========

const terminateVisible = ref(false)
const pdfPreviewVisible = ref(false)
const pdfPreviewUrl = ref('')
const terminating = ref(false)
const terminateTarget = ref(null)
const terminateForm = reactive({ type: 'mutual', reason: '' })

// ========== 拒绝合同 ==========
const declineVisible = ref(false)
const declining = ref(false)
const declineTarget = ref(null)
const declineReason = ref('')

// ========== 上传盖章件 ==========
const uploadSignVisible = ref(false)
const uploadSignTitle = ref('上传盖章件')
const signUploading = ref(false)
const signSubmitting = ref(false)
/** 待上传的盖章件 OSS key（不存 URL——签名会过期） */
const signAttachmentKey = ref('')
/** 待上传盖章件的原始文件名（仅用于界面提示） */
const signAttachmentName = ref('')
const uploadSignTarget = ref(null)

function handleTerminate(row) {
  terminateTarget.value = row
  terminateForm.type = 'mutual'
  terminateForm.reason = ''
  terminateVisible.value = true
}

async function handleConfirmTerminate() {
  if (!terminateForm.reason.trim()) { ElMessage.warning('终止原因不能为空'); return }
  terminating.value = true
  try {
    await terminateContract(terminateTarget.value.id, terminateForm.reason.trim(), terminateForm.type)
    ElMessage.success('合同已终止')
    terminateVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { terminating.value = false }
}
</script>

<style>
.contract-dialog { width: auto !important; max-width: 860px; }
/* 合同详情弹窗：用更高权重覆盖全局展示弹窗 45%/760px 限制，扩大显示 */
.el-dialog.contract-detail-dialog:has(.el-descriptions) {
  width: 55% !important;
  max-width: 1100px;
  min-width: 760px;
}
.contract-dialog .detail-descriptions:last-child { margin-bottom: 0; }
.contract-dialog .el-form-item__label { white-space: nowrap; }
.contract-dialog .el-form-item.is-required .el-form-item__label::before { margin-right: 2px; }

/* 分批计划 */
.contract-dialog .batch-plan { width: 100%; }
.contract-dialog .batch-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.contract-dialog .batch-no { flex: 0 0 46px; color: #606266; font-size: 13px; white-space: nowrap; }
.contract-dialog .batch-qty { flex: 0 0 92px; }
.contract-dialog .batch-date { flex: 0 0 150px; }
.contract-dialog .batch-remark { flex: 1 1 auto; min-width: 90px; }
.contract-dialog .batch-hint { margin-top: 4px; color: #909399; font-size: 12px; line-height: 18px; }
.contract-dialog .batch-hint.batch-error { color: #f56c6c; }

/* 断供影响/交付规则：整体右移 */
.contract-dialog .rule-row { padding-left: 24px; }

/* 详情弹窗 - 分批计划展示 */
.batch-detail-section { margin-bottom: 16px; }
.batch-detail-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.batch-detail-summary { margin-top: 6px; font-size: 12px; color: #909399; text-align: right; }
</style>
