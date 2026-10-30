<template>
  <div>
    <!-- 采购商视角 -->
    <template v-if="isBuyer">
      <!-- 无指定询盘 → 列出有报价的询盘 -->
      <template v-if="!inquiryId">
        <el-card class="search-card">
          <el-form :model="buyerSearchForm" inline>
            <el-form-item label="关键词">
              <el-input v-model="buyerSearchForm.keyword" placeholder="标题/品类/编号" clearable style="width:220px" @keyup.enter="fetchBuyerList" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="buyerSearchForm.status" clearable placeholder="全部" style="width:150px" @change="fetchBuyerList">
                <el-option label="报价中" value="quoting" />
                <el-option label="待授标" value="pending_award" />
                <el-option label="已授标" value="awarded" />
                <el-option label="已关闭" value="closed" />
                <el-option label="已过期" value="expired" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="fetchBuyerList">搜索</el-button>
              <el-button :icon="Refresh" @click="buyerHandleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card v-loading="buyerLoading" class="content-card">
          <div class="table-toolbar">
            <span class="text-base font-semibold text-gray-700">我的询盘</span>
          </div>

          <el-table :data="buyerList" stripe border @row-click="(row) => openCompare(row)" style="cursor:pointer">
            <IdColumn :page-num="buyerPageNum" :page-size="buyerPageSize" />
            <el-table-column prop="inquiryNo" label="询盘编号" min-width="250" show-overflow-tooltip />
            <el-table-column prop="title" label="询盘标题" min-width="350" show-overflow-tooltip />
            <el-table-column label="采购品类" min-width="250" show-overflow-tooltip>
              <template #default="{ row }"><EmptyText :value="row.category" /></template>
            </el-table-column>
            <el-table-column label="数量" min-width="100">
              <template #default="{ row }"><EmptyText :value="row.quantity" /></template>
            </el-table-column>
            <el-table-column label="单位" min-width="80">
              <template #default="{ row }"><EmptyText :value="row.unit" /></template>
            </el-table-column>
            <el-table-column label="已授标数量" min-width="120" align="center">
              <template #default="{ row }">
                <span v-if="(row.awardedQuantityTotal || 0) > 0" style="color:#409eff">{{ row.awardedQuantityTotal }}</span>
                <span v-else class="empty-text">无</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getDisplayStatusTag(row)" size="small">{{ getDisplayStatus(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="报价" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.quoteCount" type="primary" size="small" effect="plain">{{ row.quoteCount }}</el-tag>
                <span v-else style="color:#909399">0</span>
              </template>
            </el-table-column>
            <el-table-column label="报价截止" min-width="200">
              <template #default="{ row }"><EmptyText :value="row.quoteDeadline">{{ formatTime(row.quoteDeadline) }}</EmptyText></template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="180">
              <template #default="{ row }"><EmptyText :value="row.createTime">{{ formatTime(row.createTime) }}</EmptyText></template>
            </el-table-column>
            <el-table-column label="操作" min-width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="((row.status === 'quoting' || row.status === 'expired') || isQuoteExpired(row)) && row.quoteCount" link type="success" size="small" @click.stop="openCompare(row)">比价</el-button>
                <el-button v-else-if="row.status === 'awarded'" link type="primary" size="small" @click.stop="openCompare(row)">查看结果</el-button>
                <el-button v-else link :type="row.quoteCount ? 'primary' : 'info'" size="small" @click.stop="openCompare(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!buyerLoading && buyerList.length === 0" description="暂无询盘" />

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="buyerPageNum"
              v-model:page-size="buyerPageSize"
              :page-sizes="PAGE_SIZE_OPTIONS"
              :total="buyerTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchBuyerList"
              @size-change="fetchBuyerList"
            />
          </div>
        </el-card>
      </template>

      <!-- 指定询盘 → 报价横向对比 -->
      <el-card v-else v-loading="compareLoading" class="content-card compare-card">
        <div class="table-toolbar">
          <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
          <span class="text-base font-semibold text-gray-700">{{ inquiry?.status === 'awarded' ? '比价结果' : '比价管理' }}</span>
        </div>

        <el-descriptions v-if="inquiry" :column="3" border class="compare-info" style="margin-bottom:16px">
          <el-descriptions-item label="询盘编号">{{ inquiry.inquiryNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getDisplayStatusTag(inquiry)" size="small">{{ getDisplayStatus(inquiry) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报价币种"><EmptyText :value="inquiry.currency" /></el-descriptions-item>
          <el-descriptions-item label="询盘标题" :span="3">{{ inquiry.title }}</el-descriptions-item>
          <el-descriptions-item label="采购品类"><EmptyText :value="inquiry.category" /></el-descriptions-item>
          <el-descriptions-item label="数量"><EmptyText :value="inquiry.quantity" /></el-descriptions-item>
          <el-descriptions-item label="单位"><EmptyText :value="inquiry.unit" /></el-descriptions-item>
          <el-descriptions-item label="已授标数量">
            <span v-if="(inquiry.awardedQuantityTotal || 0) > 0" style="color:#409eff">{{ inquiry.awardedQuantityTotal }}</span>
            <span v-else class="empty-text">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="报价截止"><EmptyText :value="inquiry.quoteDeadline">{{ formatTime(inquiry.quoteDeadline) }}</EmptyText></el-descriptions-item>
          <el-descriptions-item label="交付日期"><EmptyText :value="inquiry.deliveryDate" /></el-descriptions-item>
          <el-descriptions-item label="交货地点"><EmptyText :value="inquiry.deliveryPlace" /></el-descriptions-item>
          <el-descriptions-item label="付款条件" :span="2"><EmptyText :value="inquiry.paymentTerms" /></el-descriptions-item>
          <el-descriptions-item label="规格条款" :span="3"><EmptyText :value="inquiry.specText" /></el-descriptions-item>
        </el-descriptions>

        <!-- 授标进度摘要（已有分量授标即显示） -->
        <el-row v-if="quotes.length > 0 && awardStats.awardedCount > 0" :gutter="12" style="margin-bottom:12px">
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-num">{{ awardStats.awardedCount }} 家</div>
              <div class="stat-label">已授标</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-num">{{ awardStats.allocated }} / {{ awardStats.total }}</div>
              <div class="stat-label">已分配 / 总量</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card">
              <div class="stat-num" :class="{ 'text-red-500': awardStats.remaining > 0 }">{{ awardStats.remaining }}</div>
              <div class="stat-label">剩余可分配</div>
            </div>
          </el-col>
        </el-row>

        <!-- AI 比价分析 -->
        <div v-if="quotes.length > 0 && inquiry?.status !== 'awarded'" class="ai-analysis">
          <div class="ai-analysis-header" @click="aiExpanded = !aiExpanded">
            <span class="ai-header-text">AI 比价分析</span>
            <span style="margin-left:8px;font-size:12px;color:#909399">
              <el-icon v-if="!aiExpanded"><ArrowRight /></el-icon>
              <el-icon v-else><ArrowDown /></el-icon>
            </span>
            <el-button v-if="quotes.length > 0 && !aiResult && !aiLoading" link type="primary" size="small" style="margin-left:12px" @click.stop="handleAiAnalyze">开始分析</el-button>
            <el-button v-if="aiResult && !aiLoading" link type="primary" size="small" style="margin-left:12px" @click.stop="handleAiAnalyze">重新分析</el-button>
            <el-icon v-if="aiLoading" class="is-loading" style="margin-left:12px;color:#409eff"><Loading /></el-icon>
          </div>

          <div v-if="aiExpanded" v-loading="aiLoading" class="ai-analysis-body">
            <el-alert v-if="aiStale" type="warning" :closable="false" show-icon style="margin-bottom:12px">
              报价已更新，建议
              <el-button link type="warning" size="small" @click="handleAiAnalyze" style="vertical-align:baseline">重新分析</el-button>
            </el-alert>
            <template v-if="aiResult">
              <!-- 总结 -->
              <el-alert v-if="aiResult.summary" :title="aiResult.summary" type="info" :closable="false" show-icon style="margin-bottom:12px" />

              <!-- 综合排名 -->
              <div v-if="aiResult.rankings && aiResult.rankings.length > 0" style="margin-bottom:12px">
                <div class="ai-section-title">综合排名</div>
                <div v-for="item in aiResult.rankings" :key="item.rank" class="ai-rank-card">
                  <div class="ai-rank-header">
                    <el-tag :type="item.rank === 1 ? 'danger' : item.rank === 2 ? 'warning' : 'info'" size="small">{{ item.rank }}</el-tag>
                    <span class="ai-rank-name">{{ item.supplierName }}</span>
                    <span class="ai-rank-score">{{ item.score }}分</span>
                  </div>
                  <div class="ai-rank-detail">
                    <div class="ai-rank-line"><span class="ai-label">优势：</span>{{ item.strength }}</div>
                    <div class="ai-rank-line"><span class="ai-label">风险：</span>{{ item.risk || '暂无显著风险' }}</div>
                    <div class="ai-rank-line"><span class="ai-label">点评：</span>{{ item.comment }}</div>
                  </div>
                </div>
              </div>

              <!-- 风险提示 -->
              <div v-if="aiResult.risks && aiResult.risks.length > 0" style="margin-bottom:12px">
                <div class="ai-section-title">风险提示</div>
                <div v-for="(risk, idx) in aiResult.risks" :key="idx" class="ai-risk-item">
                  <el-icon style="color:#e6a23c;margin-right:4px"><WarningFilled /></el-icon>
                  <span class="ai-risk-supplier">{{ risk.supplierName }}</span>：{{ risk.description }}
                </div>
              </div>

              <!-- 授标建议 -->
              <div v-if="aiResult.recommendation && aiResult.recommendation.supplierName">
                <div class="ai-section-title">授标建议</div>
                <el-alert type="success" :closable="false" show-icon>
                  <template #title>
                    推荐 <strong>{{ aiResult.recommendation.supplierName }}</strong>
                  </template>
                  {{ aiResult.recommendation.reason }}
                </el-alert>
              </div>

            </template>
            <el-empty v-else-if="!aiLoading" description="点击「开始分析」获取 AI 比价建议" :image-size="60" />
          </div>
        </div>

        <div v-if="(inquiry?.status === 'quoting' || inquiry?.status === 'expired') && quoteStats.count > 0" style="margin-bottom:8px">
          <el-button type="success" :icon="Check" :loading="awardLoading" @click="handleAward">选定中标</el-button>
          <el-button
            v-if="awardStats.awardedCount > 0 && awardStats.remaining > 0"
            type="warning"
            plain
            :loading="completeAwardLoading"
            @click="handleCompleteAward"
          >完成授标</el-button>
          <span style="margin-left:10px;font-size:12px;color:#909399">未分配满需求量前保持报价中，可继续授标；未满量结束请点「完成授标」</span>
        </div>
        <el-table v-if="quotes.length > 0" :data="quotes" stripe border>
          <IdColumn />
          <el-table-column prop="supplierCompanyName" label="供应商" min-width="300" show-overflow-tooltip />
          <el-table-column prop="unitPrice" label="单价" min-width="110" sortable />
          <el-table-column label="总价" min-width="180" align="center" sortable>
            <template #default="{ row }">
              <EmptyText :value="row.totalPrice" />
              <el-tag v-if="row.status !== 'withdrawn' && String(row.id) === String(quoteStats?.minPrice?.id)" size="small" type="danger" effect="dark" style="margin-left:4px">最低</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="币种" width="80">
            <template #default="{ row }"><EmptyText :value="row.currency" /></template>
          </el-table-column>
          <el-table-column prop="supplyQuantity" label="供应数量" min-width="130" sortable>
            <template #default="{ row }"><EmptyText :value="row.supplyQuantity" /></template>
          </el-table-column>
          <el-table-column label="授标数量" min-width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.awardedQuantity != null" style="color:#409eff">{{ row.awardedQuantity }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="成交金额" min-width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.isWinning && row.awardedQuantity != null" style="color:#67c23a">{{ formatAmount(row.unitPrice, row.awardedQuantity) }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isWinning ? 'success' : (row.status === 'withdrawn' ? 'info' : 'primary')">{{ row.isWinning ? '已中标' : (QUOTE_STATUS_MAP[row.status] || row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deliveryDays" label="交货天数" min-width="150" sortable>
            <template #default="{ row }">
              <template v-if="row.deliveryDays != null">
                {{ row.deliveryDays }}
                <el-tag v-if="row.status !== 'withdrawn' && row.deliveryDays && String(row.id) === String(quoteStats?.minDelivery?.id)" size="small" type="warning" effect="dark" style="margin-left:4px">最快</el-tag>
              </template>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column prop="deliveryTerms" label="交货条款" min-width="250" show-overflow-tooltip>
            <template #default="{ row }"><EmptyText :value="row.deliveryTerms" /></template>
          </el-table-column>
          <el-table-column label="报价有效期" min-width="200">
            <template #default="{ row }"><EmptyText :value="row.validUntil">{{ formatTime(row.validUntil) }}</EmptyText></template>
          </el-table-column>
          <el-table-column prop="remark" label="报价说明" min-width="160" show-overflow-tooltip>
            <template #default="{ row }"><EmptyText :value="row.remark" /></template>
          </el-table-column>
          <el-table-column label="附件" width="80">
            <template #default="{ row }">
              <a v-if="row.attachmentUrl" :href="getFileUrl(row.attachmentUrl)" target="_blank" style="color:#409eff">查看</a>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column v-if="inquiry?.status === 'awarded'" label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <span v-if="row.contractId" class="text-gray-400" style="font-size:12px">已创建</span>
              <el-button v-else-if="row.isWinning || row.awardedQuantity != null" link type="primary" size="small" @click="handleCreateContract(row)">创建合同</el-button>
              <span v-else class="text-gray-400">-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无供应商报价" />
      </el-card>
    </template>

    <!-- 供应商视角：只看中标结果 -->
    <template v-else>
      <el-card class="search-card">
        <el-form :model="searchForm" inline>
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="标题/品类/编号" clearable style="width:220px" @keyup.enter="handleSupplierSearch" />
          </el-form-item>
          <el-form-item label="状态">
              <el-select v-model="searchForm.status" clearable placeholder="全部" style="width:150px" @change="handleSupplierSearch">
              <el-option label="报价中" value="quoting" />
              <el-option label="待授标" value="pending_award" />
              <el-option label="已授标" value="awarded" />
              <el-option label="已关闭" value="closed" />
              <el-option label="已过期" value="expired" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="searchForm.result" clearable placeholder="全部" style="width:120px" @change="handleSupplierSearch">
              <el-option label="已中标" value="won" />
              <el-option label="未中标" value="lost" />
              <el-option label="待定" value="pending" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSupplierSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-loading="loading" class="content-card">
        <div class="table-toolbar">
          <span class="text-base font-semibold text-gray-700">我的报价结果</span>
        </div>

        <el-table :data="supplierList" stripe border>
            <IdColumn :page-num="pageNum" :page-size="pageSize" />
          <el-table-column prop="inquiryNo" label="询盘编号" min-width="250" show-overflow-tooltip />
          <el-table-column prop="title" label="询盘标题" min-width="350" show-overflow-tooltip />
          <el-table-column prop="buyerCompanyName" label="采购企业" min-width="280" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="getDisplayStatusTag(row)" size="small">{{ getDisplayStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="我的单价" min-width="110">
            <template #default="{ row }"><EmptyText :value="row.myQuoteUnitPrice" /></template>
          </el-table-column>
          <el-table-column label="我的报价" min-width="150">
            <template #default="{ row }"><EmptyText :value="row.myQuotePrice" /></template>
          </el-table-column>
          <el-table-column label="币种" min-width="80">
            <template #default="{ row }"><EmptyText :value="row.myQuotePrice != null ? row.myQuoteCurrency : null" /></template>
          </el-table-column>
          <el-table-column label="授标数量" min-width="110">
            <template #default="{ row }">
              <span v-if="row.myAwardedQuantity != null" style="color:#409eff">{{ row.myAwardedQuantity }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="中标金额" min-width="120">
            <template #default="{ row }">
              <span v-if="row.myAwardedAmount != null" style="color:#67c23a">{{ row.myAwardedAmount }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isWinning" type="success" size="small">中标</el-tag>
              <el-tag v-else-if="row.status === 'awarded'" type="info" size="small">未中标</el-tag>
              <el-tag v-else type="info" size="small">待定</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="80" fixed="right">
            <template #default="{ row }">
              <el-button link :type="(row.quoteCount || row.myQuotePrice) ? 'primary' : 'info'" size="small" @click="handleSupplierDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && supplierList.length === 0" description="暂无数据" />

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="fetchSupplierList"
            @size-change="fetchSupplierList"
          />
        </div>
      </el-card>
    </template>

    <!-- 供应商查看中标详情 Dialog -->
    <el-dialog v-model="detailVisible" title="询盘结果" :close-on-click-modal="false" class="inquiry-dialog">
      <div v-loading="supplierDetailLoading" style="min-height:150px">
      <template v-if="supplierDetail">
        <el-descriptions :column="2" border style="margin-bottom:16px">
          <el-descriptions-item label="询盘编号">{{ supplierDetail.inquiryNo }}</el-descriptions-item>
          <el-descriptions-item label="采购企业"><EmptyText :value="supplierDetail.buyerCompanyName" /></el-descriptions-item>
          <el-descriptions-item label="询盘标题" :span="2">{{ supplierDetail.title }}</el-descriptions-item>
          <el-descriptions-item label="采购品类"><EmptyText :value="supplierDetail.category" /></el-descriptions-item>
          <el-descriptions-item label="数量"><EmptyText :value="supplierDetail.quantity" /></el-descriptions-item>
          <el-descriptions-item label="单位"><EmptyText :value="supplierDetail.unit" /></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getDisplayStatusTag(supplierDetail)" size="small">{{ getDisplayStatus(supplierDetail) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider>我的报价</el-divider>
        <el-empty v-if="!(supplierDetail.quotes && supplierDetail.quotes.length)" description="暂无报价" />
        <el-table v-else :data="supplierDetail.quotes" size="small" border>
          <IdColumn />
          <el-table-column prop="quoteNo" label="报价编号" min-width="190" show-overflow-tooltip />
          <el-table-column label="单价" min-width="110">
            <template #default="{ row }"><EmptyText :value="row.unitPrice" /></template>
          </el-table-column>
          <el-table-column prop="totalPrice" label="总价" min-width="120">
            <template #default="{ row }"><EmptyText :value="row.totalPrice" /></template>
          </el-table-column>
          <el-table-column label="币种" min-width="80">
            <template #default="{ row }"><EmptyText :value="row.currency" /></template>
          </el-table-column>
          <el-table-column prop="deliveryDays" label="交货天数" width="100" />
          <el-table-column label="授标数量" min-width="100">
            <template #default="{ row }">
              <span v-if="row.awardedQuantity" style="color:#409eff">{{ row.awardedQuantity }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="中标金额" min-width="120">
            <template #default="{ row }">
              <span v-if="row.awardedAmount" style="color:#67c23a">{{ row.awardedAmount }}</span>
              <span v-else class="empty-text">无</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isWinning" type="success">中标</el-tag>
              <el-tag v-else-if="supplierDetail.status === 'awarded'" type="info">未中标</el-tag>
              <el-tag v-else type="info">待定</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="合同" min-width="150">
            <template #default="{ row }">
              <template v-if="row.contractId">
                <el-tag size="small" :type="CONTRACT_STATUS_TAG[row.contractStatus] || 'info'">{{ getContractStatusLabel(row.contractStatus, true, row.supplierConfirmed === 1) }}</el-tag>
                <el-button link type="primary" size="small" style="margin-left:6px" @click="handleViewContract(row.contractId)">查看合同</el-button>
              </template>
              <span v-else-if="row.isWinning">未生成</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="supplierDetail.status === 'awarded' && supplierDetail.awardReason"
          type="info"
          :title="'授标说明：' + supplierDetail.awardReason"
          :closable="false"
          show-icon
          style="margin-top:12px"
        />
      </template>
      </div>
    </el-dialog>

    <!-- 选定中标 Dialog（支持分量授标） -->
    <el-dialog v-model="awardDialogVisible" title="选定中标" :close-on-click-modal="false" class="award-dialog">
      <div v-if="inquiry?.quantity" style="margin-bottom:8px;color:#606266;font-size:13px">
        询盘数量：<strong>{{ inquiry.quantity }} {{ inquiry.unit || '' }}</strong>
        <span :class="awardTotalQty > inquiry.quantity ? 'text-red-500' : 'text-green-600'" style="margin-left:12px">
          已分配：{{ awardTotalQty }} / {{ inquiry.quantity }}
        </span>
      </div>
      <el-alert v-if="awardShortageWarning" type="warning" :title="awardShortageWarning" :closable="false" show-icon style="margin-bottom:12px" />
      <el-table :data="awardCandidates" size="small" max-height="280" stripe>
        <el-table-column width="40">
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" :disabled="!!row.isWinning || (!row.selected && awardTotalQty >= (inquiry?.quantity || Infinity))" @change="onCandidateCheck(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="supplierCompanyName" label="供应商" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.supplierCompanyName }}
            <el-tag v-if="row.isWinning" size="small" type="success" style="margin-left:4px">已中标</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="100">
          <template #default="{ row }"><EmptyText :value="row.unitPrice" /></template>
        </el-table-column>
        <el-table-column label="总价" width="120">
          <template #default="{ row }">
            <span v-if="row.selected && row.awardQty > 0" style="color:#67c23a;font-weight:600">{{ formatAmount(row.unitPrice, row.awardQty) }}</span>
            <span v-else><EmptyText :value="row.totalPrice" /></span>
          </template>
        </el-table-column>
        <el-table-column label="币种" width="80">
          <template #default="{ row }"><EmptyText :value="row.currency" /></template>
        </el-table-column>
        <el-table-column label="供应数量" width="90">
          <template #default="{ row }"><EmptyText :value="row.supplyQuantity" /></template>
        </el-table-column>
        <el-table-column label="交货天数" width="90">
          <template #default="{ row }"><EmptyText :value="row.deliveryDays" /></template>
        </el-table-column>
        <el-table-column label="授标数量" min-width="150" fixed="right">
          <template #default="{ row }">
            <el-input-number v-model="row.awardQty" :min="0" :max="row.supplyQuantity || inquiry?.quantity || 999999" size="small" :disabled="!!row.isWinning || !row.selected" style="width:130px" />
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="85px" style="margin-top:12px">
        <el-form-item label="授标理由">
          <div style="flex:1">
            <el-input
              v-model="awardReasonText"
              type="textarea"
              :rows="4"
              placeholder="选填"
            />
            <div style="margin-top:6px;text-align:right">
              <el-button link type="primary" :loading="aiReasonLoading" @click="handleAiGenerateReason">✨ AI 生成</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="awardDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="awardLoading" :disabled="awardTotalQty === 0 || awardTotalQty > (inquiry?.quantity || 0)" @click="handleConfirmAward">确认中标</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, ArrowLeft, ArrowRight, ArrowDown, Loading, WarningFilled, Check } from '@element-plus/icons-vue'
import { getInquiryDetail, listQuotes, listInquiries, awardInquiry, completeAward, getAiAnalysis, analyzeQuotes, generateAwardReason } from '@/api/procurement'
import { getRequirementDetail } from '@/api/match'
import { CONTRACT_STATUS_TAG, getContractStatusLabel } from '@/constants/contract'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import { getFileUrl } from '@/utils/file'
import { formatTime } from '@/utils/format'
import IdColumn from '@/components/IdColumn.vue'

/** 成交金额 = 单价 × 授标数量 */
function formatAmount(unitPrice, awardedQuantity) {
  const p = Number(unitPrice), q = Number(awardedQuantity)
  if (isNaN(p) || isNaN(q) || q <= 0) return '-'
  return (p * q).toFixed(2)
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const STATUS_MAP = { open: '询价中', quoting: '报价中', awarded: '已授标', closed: '已关闭', expired: '已过期', cancelled: '已取消' }
const STATUS_TAG = { open: 'primary', quoting: 'warning', awarded: 'success', closed: 'info', expired: 'danger', cancelled: 'info' }
const QUOTE_STATUS_MAP = { pending: '已报价', withdrawn: '已撤回' }

function isQuoteExpired(row) {
  return row.quoteDeadline && new Date(row.quoteDeadline) < new Date()
}
function isDeadlinePassed(row) {
  return row.status === 'expired' || ((row.status === 'open' || row.status === 'quoting') && isQuoteExpired(row))
}
function isPendingAward(row) {
  return isDeadlinePassed(row) && Number(row.quoteCount) > 0
}
function getDisplayStatus(row) {
  if (isPendingAward(row)) return '待授标'
  if (isDeadlinePassed(row)) return '已过期'
  return STATUS_MAP[row.status] || row.status
}
function getDisplayStatusTag(row) {
  if (isPendingAward(row)) return 'warning'
  if (isDeadlinePassed(row)) return 'danger'
  return STATUS_TAG[row.status] || 'info'
}

const isBuyer = computed(() => authStore.profileType !== 'supplier')

// ========== AI 比价分析 ==========
const aiLoading = ref(false)
const aiResult = ref(null)
const aiStale = ref(false)
const aiExpanded = ref(false)

/** 按 quoteId 或"供应商N"序号从 quotes 里找真实公司名（前端兜底，防 AI 编造） */
function realName(quoteId, supplierName) {
  // 优先用 quoteId 匹配
  if (quoteId != null) {
    // String() 兼容后端 Long→String 序列化后的 id 比较
    const q = quotes.value.find(x => String(x.id) === String(quoteId))
    if (q?.supplierCompanyName) return q.supplierCompanyName
  }
  // Fallback：匹配 "供应商N" 模式
  if (supplierName && /^供应商\d+$/.test(supplierName)) {
    const idx = parseInt(supplierName.slice(3)) - 1
    if (idx >= 0 && idx < quotes.value.length) {
      return quotes.value[idx]?.supplierCompanyName || null
    }
  }
  return null
}

/** 强制用真实公司名替换 AI 结果里的 supplierName（不改原始数据） */
function applyRealNames(result) {
  if (!result) return result
  if (Array.isArray(result.rankings)) {
    result.rankings.forEach(item => {
      const name = realName(item.quoteId, item.supplierName)
      if (name) item.supplierName = name
    })
  }
  if (Array.isArray(result.risks)) {
    result.risks.forEach(item => {
      const name = realName(item.quoteId, item.supplierName)
      if (name) item.supplierName = name
    })
  }
  if (result.recommendation) {
    const name = realName(result.recommendation.quoteId, result.recommendation.supplierName)
    if (name) result.recommendation.supplierName = name
  }
  return result
}

async function handleAiAnalyze() {
  if (!inquiryId.value) return
  aiLoading.value = true
  aiExpanded.value = true
  try {
    const res = await analyzeQuotes(inquiryId.value)
    aiResult.value = applyRealNames(res?.data || res)
    aiStale.value = false
  } catch {
    ElMessage.warning('AI 分析暂不可用，请稍后重试')
  } finally {
    aiLoading.value = false
  }
}

// ========== 采购商：询盘列表（无指定询盘时） ==========
const buyerLoading = ref(true)
const buyerList = ref([])
const buyerTotal = ref(0)
const buyerPageNum = ref(1)
const buyerPageSize = ref(10)
const buyerSearchForm = reactive({ keyword: '', status: '', role: 'buyer' })

async function fetchBuyerList() {
  buyerLoading.value = true
  try {
    const res = await listInquiries({ pageNum: buyerPageNum.value, pageSize: buyerPageSize.value, ...buyerSearchForm })
    buyerList.value = res?.data?.records || res?.records || []
    buyerTotal.value = res?.data?.total || res?.total || 0
  } finally { buyerLoading.value = false }
}

function buyerHandleReset() {
  buyerSearchForm.keyword = ''
  buyerSearchForm.status = ''
  buyerPageNum.value = 1
  fetchBuyerList()
}

function openCompare(row) {
  router.push({ name: 'PriceComparison', query: { inquiryId: row.id } })
}

// ========== 采购商：比价板（指定询盘） ==========
const inquiryId = ref(route.query.inquiryId)
const inquiry = ref(null)
const quotes = ref([])

const quoteStats = computed(() => {
  const list = quotes.value || []
  const sorted = [...list].filter(q => q.status !== 'withdrawn')
  const withdrawnCount = list.length - sorted.length
  if (sorted.length === 0) {
    return { count: 0, minPrice: null, minDelivery: null, avgPrice: 0, withdrawnCount }
  }
  const num = (v) => Number(v) || 0
  const minPrice = sorted.reduce((a, b) => num(a.totalPrice) < num(b.totalPrice) ? a : b)
  const minDelivery = sorted.reduce((a, b) => (Number(a.deliveryDays) || 999) < (Number(b.deliveryDays) || 999) ? a : b)
  const avgPrice = sorted.reduce((s, q) => s + num(q.totalPrice), 0) / sorted.length
  return { count: sorted.length, minPrice, minDelivery, avgPrice, withdrawnCount }
})

/** 授标进度统计（多中标模型）：已授标家数 / 已分配数量 / 剩余可分配 */
const awardStats = computed(() => {
  const list = quotes.value || []
  const awarded = list.filter(q => q.isWinning === 1 || q.isWinning === true)
  const inquiryQty = Number(inquiry.value?.quantity) || 0
  const allocated = awarded.reduce((s, q) => s + (Number(q.awardedQuantity) || 0), 0)
  return {
    awardedCount: awarded.length,
    allocated,
    total: inquiryQty,
    remaining: inquiryQty - allocated
  }
})

onMounted(async () => {
  if (isBuyer.value) {
    if (inquiryId.value) {
      await loadCompare()
    } else {
      fetchBuyerList()
    }
  } else {
    fetchSupplierList()
  }
})

// 同路由跳转时（列表→比价）监听 query 变化
watch(() => route.query.inquiryId, async (newId) => {
  inquiryId.value = newId
  if (newId) {
    await loadCompare()
  } else {
    fetchBuyerList()
  }
})

const compareLoading = ref(true)

async function loadCompare() {
  compareLoading.value = true
  try {
    const [iqRes, qRes, aiRes] = await Promise.all([
      getInquiryDetail(inquiryId.value),
      listQuotes(inquiryId.value),
      getAiAnalysis(inquiryId.value),
    ])
    inquiry.value = iqRes?.data || iqRes
    quotes.value = qRes?.data || qRes || []
    // 加载已缓存的 AI 分析
    const cached = aiRes?.data || aiRes
    if (cached && cached.analysis) {
      aiResult.value = applyRealNames(cached.analysis)
      aiStale.value = cached.stale === true
    } else {
      aiResult.value = null
      aiStale.value = false
    }
  } catch {
    ElMessage.error('加载询盘详情失败')
    router.push({ name: 'PriceComparison' })
  } finally {
    compareLoading.value = false
  }
}

function handleBack() {
  router.push({ name: 'PriceComparison' })
}

const awardLoading = ref(false)
const completeAwardLoading = ref(false)

/** AI 授标理由生成按钮 loading */
const aiReasonLoading = ref(false)

// 授标 Dialog（分量授标模式）
const awardDialogVisible = ref(false)
const awardCandidates = ref([])
const awardReasonText = ref('')
/** 关联采购需求的目标供应商数（用于"量分满但目标家数未补齐"警告） */
const requirementTargetCount = ref(null)

/** 汇总已选中的授标数量 */
const awardTotalQty = computed(() => {
  return awardCandidates.value
    .filter(c => c.selected)
    .reduce((sum, c) => sum + (c.awardQty || 0), 0)
})

/** 授标警告：目标家数 > 当前报价家数，且分配量已达询盘采购量 → 后续补供将无量可分 */
const awardShortageWarning = computed(() => {
  const target = requirementTargetCount.value
  const current = awardCandidates.value.length
  const total = Number(inquiry.value?.quantity) || 0
  if (!target || current >= target) return ''
  if (total <= 0 || awardTotalQty.value < total) return ''
  return `需求目标 ${target} 家供应商，当前仅 ${current} 家参与报价；采购量(${total} ${inquiry.value?.unit || ''})已分配完毕，后续补齐供应商将无可分配数量`
})

/** 打开授标弹窗：列出所有未撤回报价作为候选，并拉取需求目标家数 */
async function handleAward() {
  awardReasonText.value = ''
  awardCandidates.value = quotes.value
    .filter(q => q.status !== 'withdrawn')
    .map(q => ({
      ...q,
      selected: q.isWinning ? true : false,
      awardQty: q.isWinning ? (q.awardedQuantity || 0) : 0
    }))
  // 拉取关联需求的目标供应商数（询盘可能不关联需求，无则跳过警告）
  requirementTargetCount.value = null
  const reqId = inquiry.value?.requirementId
  if (reqId) {
    try {
      const reqRes = await getRequirementDetail(reqId)
      requirementTargetCount.value = reqRes?.data?.targetSupplierCount || null
    } catch { /* 拿不到就跳过警告 */ }
  }
  awardDialogVisible.value = true
}

/** AI 授标理由生成：对勾选的每家中标供应商逐个生成理由并拼接填入 textarea */
async function handleAiGenerateReason() {
  const selected = awardCandidates.value.filter(c => c.selected && c.awardQty > 0)
  if (selected.length === 0) { ElMessage.warning('请先勾选中标供应商'); return }
  aiReasonLoading.value = true
  try {
    const parts = []
    for (const c of selected) {
      try {
        const res = await generateAwardReason(inquiryId.value, c.id)
        const reason = res?.data
        if (reason) parts.push(`【${c.supplierCompanyName || `报价${c.id}`}】${reason}`)
      } catch { /* 单家失败跳过，拦截器已弹错，继续下一家 */ }
    }
    if (parts.length === 0) {
      ElMessage.warning('AI 生成失败，请手动填写')
      return
    }
    awardReasonText.value = parts.join('\n')
  } finally { aiReasonLoading.value = false }
}

/** 选中供应商时自动填满供应数量；已中标行只读，不可改勾选和数量 */
function onCandidateCheck(row) {
  if (row.isWinning) {
    row.selected = true
    row.awardQty = row.awardedQuantity || row.awardQty || 0
    return
  }
  if (row.selected) {
    const remaining = (inquiry.value?.quantity || 0) - awardTotalQty.value
    const maxQty = row.supplyQuantity ? Math.min(row.supplyQuantity, remaining) : remaining
    row.awardQty = Math.max(1, maxQty)
  } else {
    row.awardQty = 0
  }
}

async function handleConfirmAward() {
  const locked = awardCandidates.value.filter(c => c.isWinning)
  const unlockedSelected = awardCandidates.value.filter(c => !c.isWinning && c.selected && (c.awardQty || 0) > 0)
  if (locked.some(c => c.awardedQuantity == null || c.awardedQuantity <= 0)) {
    ElMessage.warning('已中标数量异常，请刷新后重试')
    return
  }
  const selected = [
    ...locked.map(c => ({ ...c, selected: true, awardQty: c.awardedQuantity })),
    ...unlockedSelected
  ]
  if (selected.length === 0) { ElMessage.warning('请至少选择一个供应商并填写数量'); return }
  const totalQty = selected.reduce((s, c) => s + c.awardQty, 0)
  if (inquiry.value?.quantity && totalQty > inquiry.value.quantity) {
    ElMessage.warning(`授标总量(${totalQty})不能超过询盘数量(${inquiry.value.quantity})`)
    return
  }
  awardLoading.value = true
  compareLoading.value = true
  awardDialogVisible.value = false
  try {
    const awardsPayload = selected.map(c => ({ quoteId: c.id, awardedQuantity: c.awardQty }))
    const reason = awardReasonText.value || ''
    const res = await awardInquiry(inquiryId.value, awardsPayload, reason)
    const completed = res?.data?.completed === true
    const remaining = res?.data?.remaining
    ElMessage.success(completed ? '中标已选定' : `已保存授标，需求量还差 ${remaining}，询盘仍为报价中`)
    const iqRes2 = await getInquiryDetail(inquiryId.value)
    inquiry.value = iqRes2?.data || iqRes2
    const qRes2 = await listQuotes(inquiryId.value)
    quotes.value = qRes2?.data || qRes2 || []
    if (!completed) return
    try {
      await ElMessageBox.confirm(
        `<div style="white-space:nowrap;margin-bottom:4px">需求量已分配完毕，是否立即创建合同？</div><div>将从授标信息自动填充中标报价和供应方。</div>`,
        '创建合同',
        { confirmButtonText: '去创建', cancelButtonText: '稍后', type: 'success', dangerouslyUseHTMLString: true }
      )
      router.push({ name: 'ContractManagement', query: { inquiryId: inquiryId.value } })
    } catch { /* 用户选择稍后 */ }
  } catch { /* ignore */ }
  finally { awardLoading.value = false; compareLoading.value = false }
}

async function handleCompleteAward() {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">需求量尚未分配满（剩余 ${awardStats.value.remaining}），确认结束询盘吗？</div><div>结束后状态变为已授标，未分配部分将不再授标，随后可创建合同。</div>`,
      '完成授标',
      { confirmButtonText: '确认结束', cancelButtonText: '取消', type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  completeAwardLoading.value = true
  try {
    await completeAward(inquiryId.value)
    ElMessage.success('授标已完成')
    await loadCompare()
    try {
      await ElMessageBox.confirm(
        `<div style="white-space:nowrap;margin-bottom:4px">授标已结束，是否立即创建合同？</div><div>将从授标信息自动填充中标报价和供应方。</div>`,
        '创建合同',
        { confirmButtonText: '去创建', cancelButtonText: '稍后', type: 'success', dangerouslyUseHTMLString: true }
      )
      router.push({ name: 'ContractManagement', query: { inquiryId: inquiryId.value } })
    } catch { /* 用户选择稍后 */ }
  } catch { /* ignore */ }
  finally { completeAwardLoading.value = false }
}

function handleCreateContract(row) {
  router.push({ name: 'ContractManagement', query: { inquiryId: inquiryId.value, quoteId: row?.id || '' } })
}

// ========== 供应商：中标结果列表 ==========
const loading = ref(true)
const supplierList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ keyword: '', status: '', result: '', role: 'supplier' })

const detailVisible = ref(false)
const supplierDetail = ref(null)

async function fetchSupplierList() {
  loading.value = true
  try {
    const { result, ...rest } = searchForm
    const params = { pageNum: pageNum.value, pageSize: pageSize.value, ...rest }
    const res = await listInquiries(params)
    // 后端已批量填充 myQuotePrice / myQuoteCurrency / myQuoteIsWinning，无需逐条查报价
    const records = res?.data?.records || res?.records || []
    const enriched = records.map(inq => ({
      ...inq,
      myQuotePrice: inq.myQuotePrice || null,
      myQuoteUnitPrice: inq.myQuoteUnitPrice || null,
      myQuoteCurrency: inq.myQuoteCurrency || null,
      myAwardedQuantity: inq.myAwardedQuantity ?? null,
      myAwardedAmount: inq.myAwardedAmount ?? null,
      myAwardedCurrency: inq.myAwardedCurrency || null,
      isWinning: inq.myQuoteIsWinning || false
    }))
    const filtered = result
      ? enriched.filter(r => {
          if (result === 'won') return r.isWinning
          if (result === 'lost') return !r.isWinning && r.status === 'awarded'
          return !r.isWinning && r.status !== 'awarded'
        })
      : enriched
    supplierList.value = filtered
    total.value = result ? filtered.length : (res?.data?.total || res?.total || 0)
  } finally { loading.value = false }
}

function handleSupplierSearch() {
  pageNum.value = 1
  fetchSupplierList()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.result = ''
  pageNum.value = 1
  fetchSupplierList()
}

const supplierDetailLoading = ref(false)

async function handleSupplierDetail(row) {
  supplierDetailLoading.value = true
  try {
    const dRes = await getInquiryDetail(row.id, { role: 'supplier' })
    supplierDetail.value = dRes?.data || dRes
    detailVisible.value = true
  } catch { /* ignore */ }
  finally { supplierDetailLoading.value = false }
}

/** 供应商从询盘结果跳转查看合同 */
function handleViewContract(contractId) {
  router.push({ name: 'ContractManagement', query: { contractId } })
}
</script>

<style>
.inquiry-dialog { width: 1000px !important; max-width: 100vw; }
.inquiry-dialog .el-descriptions__label {
  width: 88px !important;
  text-align: center !important;
}
.compare-card {
  height: 100% !important;
  display: flex !important;
  flex-direction: column !important;
}
.compare-card .el-card__body {
  flex: 1 !important;
  overflow-y: auto;
}
.compare-info .el-descriptions__label {
  width: 110px !important;
  text-align: center !important;
}
.compare-info .el-descriptions__content {
  font-size: 15px !important;
}
.compare-info .el-descriptions__cell {
  padding-top: 10px !important;
  padding-bottom: 10px !important;
}
.stat-card {
  text-align: center;
  padding: 12px 8px;
  background: #f5f7fa;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  min-height: 100px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.stat-num {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.stat-sub {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

/* AI 比价分析 */
.ai-analysis {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 12px;
  overflow: hidden;
}
.ai-analysis-header {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
}
.ai-header-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.ai-analysis-body {
  padding: 16px;
  background: #fff;
}
.ai-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}
.ai-rank-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 14px;
  margin-bottom: 8px;
  background: #fafafa;
}
.ai-rank-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.ai-rank-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.ai-rank-score {
  font-size: 14px;
  font-weight: 700;
  color: #409eff;
}
.ai-rank-detail {
  padding-left: 4px;
}
.ai-rank-line {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}
.ai-label {
  font-size: 12px;
  color: #909399;
  margin-right: 4px;
}
.ai-risk-item {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
  display: flex;
  align-items: center;
}
.ai-risk-supplier {
  font-weight: 600;
  color: #e6a23c;
}
.award-dialog {
  width: auto !important;
  max-width: 820px;
}
</style>
