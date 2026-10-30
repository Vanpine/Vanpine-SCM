<template>
  <div class="inquiry-management-page">
    <!-- 搜索卡片 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item :label="t('common.keyword')">
          <el-input v-model="searchForm.keyword" :placeholder="t('inquiry.searchPlaceholder')" clearable
            style="width:220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="searchForm.status" clearable :placeholder="t('common.all')" style="width:150px"
            @change="handleSearch">
            <el-option :label="t('status.open')" value="open" />
            <el-option :label="t('status.quoting')" value="quoting" />
            <el-option :label="t('status.pending_award')" value="pending_award" />
            <el-option :label="t('status.awarded')" value="awarded" />
            <el-option :label="t('status.closed')" value="closed" />
            <el-option :label="t('status.expired')" value="expired" />
            <el-option :label="t('status.cancelled')" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 内容卡片 -->
    <el-card v-loading="loading" class="content-card">
      <div class="table-toolbar">
        <span class="text-base font-semibold text-gray-700">{{ t('inquiry.list') }}</span>
        <el-button v-if="isBuyer" type="primary" :icon="Plus" @click="handleCreate">{{ t('inquiry.createInquiry')
        }}</el-button>
        <el-radio-group v-if="isDual" v-model="searchForm.role" size="small" @change="fetchList"
          style="margin-left:auto">
          <el-radio-button value="buyer">{{ t('inquiry.myInitiated') }}</el-radio-button>
          <el-radio-button value="supplier">{{ t('inquiry.myInvited') }}</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 采购方视图 -->
      <template v-if="searchForm.role === 'buyer'">
        <el-table :data="list" stripe border>
          <IdColumn :page-num="pageNum" :page-size="pageSize" />
          <el-table-column prop="inquiryNo" :label="t('inquiry.inquiryNo')" min-width="250" show-overflow-tooltip />
          <el-table-column prop="title" :label="t('inquiry.inquiryTitle')" min-width="350" show-overflow-tooltip />
          <el-table-column prop="category" :label="t('inquiry.category')" min-width="250" show-overflow-tooltip />
          <el-table-column :label="t('inquiry.quantity')" min-width="100">
            <template #default="{ row }">
              <EmptyText :value="row.quantity" />
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.unit')" min-width="80">
            <template #default="{ row }">
              <EmptyText :value="row.unit" />
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.quoteCurrency')" min-width="100">
            <template #default="{ row }">
              <EmptyText :value="row.currency" />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getDisplayStatusTag(row)" size="small">{{ getDisplayStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.invited')" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.supplierCount" size="small" effect="plain">{{ row.supplierCount }}</el-tag>
              <span v-else style="color:#909399">0</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.acceptedCount')" min-width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.acceptedSupplierCount" type="success" size="small" effect="plain">{{
                row.acceptedSupplierCount }}</el-tag>
              <span v-else style="color:#909399">0</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.declinedCount')" min-width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.declinedSupplierCount" type="danger" size="small" effect="plain">{{
                row.declinedSupplierCount }}</el-tag>
              <span v-else style="color:#909399">0</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.quoteCount')" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.quoteCount" type="primary" size="small" effect="plain">{{ row.quoteCount
              }}</el-tag>
              <span v-else style="color:#909399">0</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.quoteDeadline')" min-width="170">
            <template #default="{ row }">
              <EmptyText :value="row.quoteDeadline">{{ formatTime(row.quoteDeadline) }}</EmptyText>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.createTime')" min-width="170">
            <template #default="{ row }">
              <EmptyText :value="row.createTime">{{ formatTime(row.createTime) }}</EmptyText>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.action')" min-width="170" fixed="right">
            <template #default="{ row }">
              <template v-for="action in buyerTiledActions(row)" :key="action">
                <el-button v-if="action === 'extend'" link type="warning" size="small"
                  @click="handleExtendDeadline(row)">{{ t('inquiry.extend') }}</el-button>
                <el-button v-else-if="action === 'compare'" link type="success" size="small"
                  @click="handleCompare(row)">{{ t('inquiry.compare') }}</el-button>
                <el-button v-else-if="action === 'edit'" link type="primary" size="small" @click="handleEdit(row)">{{
                  t('common.edit') }}</el-button>
                <el-button v-else-if="action === 'detail'" link type="primary" size="small"
                  @click="handleDetail(row)">{{ t('common.detail') }}</el-button>
                <el-button v-else-if="action === 'cancel'" link type="danger" size="small" @click="handleCancel(row)">{{
                  t('inquiry.cancelInquiry') }}</el-button>
                <el-button v-else-if="action === 'close'" link type="danger" size="small" @click="handleClose(row)">{{
                  t('inquiry.closeInquiry') }}</el-button>
              </template>
              <el-dropdown v-if="buyerMenuActions(row).length" trigger="click"
                @command="(cmd) => handleBuyerCmd(cmd, row)">
                <el-icon :size="16"
                  style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)">
                  <MoreFilled />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="action in buyerMenuActions(row)" :key="action" :command="action"
                      :divided="action === 'cancel' || action === 'close'"
                      :style="(action === 'cancel' || action === 'close') ? 'color:#f56c6c' : undefined">{{
                        buyerActionLabel(action) }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 供应商视图 -->
      <template v-else>
        <el-table :data="list" stripe border>
          <IdColumn :page-num="pageNum" :page-size="pageSize" />
          <el-table-column prop="inquiryNo" :label="t('inquiry.inquiryNo')" min-width="250" show-overflow-tooltip />
          <el-table-column prop="title" :label="t('inquiry.inquiryTitle')" min-width="350" show-overflow-tooltip />
          <el-table-column prop="buyerCompanyName" :label="t('inquiry.buyerCompany')" min-width="280"
            show-overflow-tooltip />
          <el-table-column prop="category" :label="t('inquiry.category')" min-width="250" show-overflow-tooltip />
          <el-table-column :label="t('inquiry.quantity')" min-width="100">
            <template #default="{ row }">
              <EmptyText :value="row.quantity" />
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.unit')" min-width="80">
            <template #default="{ row }">
              <EmptyText :value="row.unit" />
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getDisplayStatusTag(row)" size="small">{{ getDisplayStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.myInvite')" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.myInviteStatus === 'pending' && isQuoteExpired(row)" type="info" size="small">{{
                t('status.expired') }}</el-tag>
              <el-tag v-else-if="row.myInviteStatus === 'pending'" type="warning" size="small">{{
                INVITE_STATUS_MAP[row.myInviteStatus] }}</el-tag>
              <el-tag v-else-if="row.myInviteStatus === 'accepted'" type="success" size="small">{{
                INVITE_STATUS_MAP[row.myInviteStatus] }}</el-tag>
              <el-tag v-else-if="row.myInviteStatus" type="info" size="small">{{ INVITE_STATUS_MAP[row.myInviteStatus]
                || row.myInviteStatus }}</el-tag>
              <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.myQuote')" min-width="150">
            <template #default="{ row }">
              <EmptyText :value="row.myQuotePrice" />
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.currency')" min-width="80">
            <template #default="{ row }">
              <EmptyText :value="supplierQuoteCurrency(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.result')" min-width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="isAwardedQuote(row)" type="success" size="small">{{ t('inquiry.awardedTag') }}</el-tag>
              <el-tag v-else-if="row.myQuoteStatus === 'withdrawn'" type="info" size="small">{{ t('status.withdrawn')
              }}</el-tag>
              <el-tag v-else-if="row.status === 'awarded' && row.myQuotePrice" type="info" size="small">{{
                t('inquiry.notAwarded') }}</el-tag>
              <el-tag v-else-if="row.myQuotePrice" type="primary" size="small">{{ t('inquiry.quoted') }}</el-tag>
              <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.quoteDeadline')" min-width="200">
            <template #default="{ row }">
              <EmptyText :value="row.quoteDeadline">{{ formatTime(row.quoteDeadline) }}</EmptyText>
            </template>
          </el-table-column>
          <el-table-column :label="t('inquiry.createTime')" min-width="180">
            <template #default="{ row }">
              <EmptyText :value="row.createTime">{{ formatTime(row.createTime) }}</EmptyText>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.action')" min-width="170" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'open' || row.status === 'quoting'">
                <!-- 报价已截止：不可接受/拒绝/报价，仅详情 -->
                <template v-if="isQuoteExpired(row)">
                  <el-button link type="primary" size="small" @click="handleDetail(row)">{{ t('common.detail')
                  }}</el-button>
                </template>
                <template v-else>
                  <!-- pending 邀请 → [接受] 外露 -->
                  <el-button v-if="row.myInviteStatus === 'pending'" link type="success" size="small"
                    @click="handleAcceptInvite(row)">{{ t('inquiry.accept') }}</el-button>
                  <!-- pending 且未中标 → [修改报价] 外露；已中标不可改价 -->
                  <el-button v-if="row.myQuoteStatus === 'pending' && !isAwardedQuote(row)" link type="warning"
                    size="small" @click="handleOpenQuote(row)">{{ t('inquiry.editQuote') }}</el-button>
                  <!-- 无报价 + 已接受 + 未截止 → [报价] 外露 -->
                  <el-button v-else-if="row.myInviteStatus === 'accepted' && !row.myQuoteId" link type="success"
                    size="small" @click="handleOpenQuote(row)">{{ t('inquiry.quote') }}</el-button>
                  <!-- ≤2 操作：详情平铺（已中标即使报价仍 pending 也只给详情） -->
                  <el-button
                    v-if="row.myInviteStatus !== 'pending' && (row.myQuoteStatus !== 'pending' || isAwardedQuote(row))"
                    link type="primary" size="small" @click="handleDetail(row)">{{ t('common.detail') }}</el-button>
                  <!-- ≥3 操作：⋮ 收详情+危险操作 -->
                  <el-dropdown
                    v-if="row.myInviteStatus === 'pending' || (row.myQuoteStatus === 'pending' && !isAwardedQuote(row))"
                    trigger="click" @command="(cmd) => handleSupplierCmd(cmd, row)" style="vertical-align:middle">
                    <el-icon :size="16"
                      style="cursor:pointer;color:#409eff;margin-left:4px;margin-top:5px;transform:rotate(90deg)">
                      <MoreFilled />
                    </el-icon>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="detail">{{ t('common.detail') }}</el-dropdown-item>
                        <el-dropdown-item v-if="row.myInviteStatus === 'pending'" command="decline" divided
                          style="color:#f56c6c">{{ t('inquiry.declineInvite') }}</el-dropdown-item>
                        <el-dropdown-item v-if="row.myQuoteStatus === 'pending' && !isAwardedQuote(row)"
                          command="withdraw" divided style="color:#f56c6c">{{ t('inquiry.withdrawQuote')
                          }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </template>
              <!-- 非 open/quoting 状态：仅详情 -->
              <el-button v-if="row.status !== 'open' && row.status !== 'quoting'" link type="primary" size="small"
                @click="handleDetail(row)">{{ t('common.detail') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-if="!loading && list.length === 0"
        :description="searchForm.keyword ? t('inquiry.notFoundInquiries') : (searchForm.role === 'supplier' ? t('inquiry.noInvitedInquiries') : t('inquiry.noInquiries'))" />

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="PAGE_SIZE_OPTIONS"
          :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="fetchList"
          @size-change="fetchList" />
      </div>
    </el-card>

    <!-- 创建/编辑 Dialog（提取到 InquiryForm 组件） -->
    <InquiryForm v-model:visible="formVisible" :inquiry-id="editingId" :initial-data="initialFormData"
      @submit="onFormSubmitted" />

    <!-- 详情 Dialog -->
    <el-dialog v-model="detailVisible" :title="t('inquiry.detailTitle')" :close-on-click-modal="false"
      class="inquiry-detail-dialog">
      <div v-loading="detailLoading" style="min-height:200px">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('inquiry.inquiryNo')">{{ detail.inquiryNo }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">
              <el-tag :type="getDisplayStatusTag(detail)">{{ getDisplayStatus(detail) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.buyerCompany')">
              <EmptyText :value="detail.buyerCompanyName" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.quoteCurrency')">
              <EmptyText :value="detail.currency" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.inquiryTitle')" :span="2">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.category')" :span="2">
              <EmptyText :value="detail.category" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.quantity')">
              <EmptyText :value="detail.quantity" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.unit')">
              <EmptyText :value="detail.unit" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.deliveryPlace')">
              <EmptyText :value="detail.deliveryPlace" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.deliveryDate')">
              <EmptyText :value="detail.deliveryDate" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.quoteDeadline')" :span="2">
              <EmptyText :value="detail.quoteDeadline">{{ formatTime(detail.quoteDeadline) }}</EmptyText>
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.paymentTerms')" :span="2">
              <EmptyText :value="detail.paymentTerms" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.specTerms')" :span="2">
              <EmptyText :value="detail.specText" />
            </el-descriptions-item>
            <el-descriptions-item :label="t('inquiry.attachment')" :span="2">
              <a v-if="detail.attachmentUrl" :href="getFileUrl(detail.attachmentUrl)" target="_blank"
                style="color:#409eff;text-decoration:none">
                {{ getFileName(detail.attachmentUrl) }}
              </a>
              <EmptyText v-else :value="null" />
            </el-descriptions-item>
          </el-descriptions>

          <!-- 采购方看全部受邀名单 -->
          <template v-if="searchForm.role === 'buyer'">
            <div class="detail-section-title" style="position:relative">
              {{ t('inquiry.invitedSuppliers') }}
              <el-button v-if="canInviteMore(detail)" link type="primary" size="small" :icon="Plus"
                style="position:absolute;right:8px;top:50%;transform:translateY(-50%)" @click="openAddSupplierDialog">{{
                  t('inquiry.addSupplier') }}</el-button>
            </div>
            <el-table v-if="detail.invites && detail.invites.length" :data="detail.invites" size="small" border>
              <IdColumn />
              <el-table-column prop="supplierCompanyName" :label="t('inquiry.supplierCompany')" min-width="220"
                show-overflow-tooltip />
              <el-table-column :label="t('common.status')" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.inviteStatus === 'pending' && isQuoteExpired(detail)" size="small" type="info">{{
                    t('status.expired') }}</el-tag>
                  <el-tag v-else size="small" :type="INVITE_STATUS_TAG[row.inviteStatus] || 'info'">{{
                    INVITE_STATUS_MAP[row.inviteStatus] || row.inviteStatus }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('inquiry.quoteCount')" min-width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="inviteHasQuote(row)" size="small" type="primary">{{ t('inquiry.quoted') }}</el-tag>
                  <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
                </template>
              </el-table-column>
              <el-table-column :label="t('inquiry.declineReason')" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <EmptyText :value="row.inviteStatus === 'declined' ? row.declineReason : null" />
                </template>
              </el-table-column>
              <el-table-column :label="t('inquiry.inviteTime')" min-width="200">
                <template #default="{ row }">{{ formatTime(row.invitedAt) }}</template>
              </el-table-column>
              <el-table-column :label="t('inquiry.respondTime')" min-width="200">
                <template #default="{ row }">
                  <EmptyText :value="row.respondedAt">{{ formatTime(row.respondedAt) }}</EmptyText>
                </template>
              </el-table-column>
              <el-table-column :label="t('common.action')" min-width="170" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.inviteStatus === 'declined' && canInviteMore(detail)" link type="primary"
                    size="small" @click="handleReinvite(row)">{{ t('inquiry.reinvite') }}</el-button>
                  <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('inquiry.noInvitedSuppliers')" :image-size="60" />
          </template>

          <div class="detail-section-title">{{ t('inquiry.quoteList') }}</div>
          <el-table v-if="detail.quotes && detail.quotes.length" :data="detail.quotes" size="small" border>
            <IdColumn />
            <el-table-column prop="quoteNo" :label="t('inquiry.quoteNo')" min-width="190" show-overflow-tooltip />
            <el-table-column prop="supplierCompanyName" :label="t('inquiry.supplierCompany')" min-width="220"
              show-overflow-tooltip />
            <el-table-column :label="t('inquiry.unitPrice')" min-width="110">
              <template #default="{ row }">
                <EmptyText :value="row.unitPrice" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.totalPrice')" min-width="120">
              <template #default="{ row }">
                <EmptyText :value="row.totalPrice" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.currency')" min-width="80">
              <template #default="{ row }">
                <EmptyText :value="row.currency" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.supplyQuantity')" min-width="110">
              <template #default="{ row }">
                <EmptyText :value="row.supplyQuantity" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.awardedQuantity')" min-width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.awardedQuantity != null" style="color:#409eff">{{ row.awardedQuantity }}</span>
                <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.deliveryDays')" min-width="100">
              <template #default="{ row }">
                <EmptyText :value="row.deliveryDays" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.validUntil')" min-width="200">
              <template #default="{ row }">
                <EmptyText :value="row.validUntil">{{ formatTime(row.validUntil) }}</EmptyText>
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.quoteRemark')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <EmptyText :value="row.remark" />
              </template>
            </el-table-column>
            <el-table-column :label="t('inquiry.attachment')" min-width="80" align="center">
              <template #default="{ row }">
                <a v-if="row.attachmentUrl" :href="getFileUrl(row.attachmentUrl)" target="_blank"
                  style="color:#409eff">{{ t('common.view') }}</a>
                <span v-else class="empty-text">{{ t('inquiry.none') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.status')" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small"
                  :type="row.isWinning ? 'success' : (row.status === 'withdrawn' ? 'info' : 'primary')">
                  {{ row.isWinning ? t('inquiry.awardedTag') : (row.status === 'withdrawn' ? t('status.withdrawn') :
                    t('inquiry.quoted')) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="t('inquiry.noQuotes')" :image-size="60" />
        </template>
      </div>
    </el-dialog>

    <!-- 延长截止 Dialog -->
    <el-dialog v-model="extendDeadlineVisible" :title="t('inquiry.extendDeadlineTitle')" :close-on-click-modal="false"
      class="award-dialog" width="460px">
      <template v-if="extendDeadlineTarget">
        <div style="margin-bottom:12px">
          <div style="font-size:14px;font-weight:600;color:#303133">{{ t('inquiry.inquiryTitle') }}：{{
            extendDeadlineTarget.title }}</div>
          <div style="font-size:12px;color:#909399;margin-top:2px">{{ t('inquiry.inquiryNo') }}：{{
            extendDeadlineTarget.inquiryNo }}</div>
          <div style="font-size:12px;color:#909399;text-align:right;margin-top:4px">
            {{ t('inquiry.currentDeadline') }}：<span style="color:#e6a23c;font-weight:600">{{
              formatTime(extendDeadlineTarget.quoteDeadline) }}</span>
          </div>
        </div>
        <el-form :label-width="labelWidth">
          <el-form-item :label="t('inquiry.quoteDeadline')">
            <el-date-picker v-model="extendDeadlineTime" type="datetime" style="width:100%"
              value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('inquiry.selectNewDeadline')" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="extendDeadlineVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="extendDeadlineSubmitting" @click="handleConfirmExtendDeadline">{{
          t('inquiry.confirmExtend') }}</el-button>
      </template>
    </el-dialog>

    <!-- 追加供应商 Dialog -->
    <el-dialog v-model="addSupplierVisible" :title="t('inquiry.addSupplierTitle')" :close-on-click-modal="false"
      class="inquiry-dialog">
      <el-tabs v-model="addSupplierTab" type="card">
        <el-tab-pane :label="t('inquiry.matchResult')" name="match">
          <div v-if="addMatchedSuppliers.length === 0"
            style="color:#909399;font-size:13px;padding:16px 0;text-align:center">{{ t('inquiry.noMatchedConnections')
            }}
          </div>
          <div v-else class="supplier-check-list">
            <el-checkbox v-for="s in addMatchedSuppliers" :key="s.tenantId" v-model="addSelectedIds" :label="s.tenantId"
              :value="s.tenantId" class="supplier-check-item">
              <span class="supplier-name">{{ s.companyName }}</span>
            </el-checkbox>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="t('inquiry.manualAdd')" name="manual">
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <el-input v-model="addKeyword" :placeholder="t('inquiry.searchSupplierPlaceholder')"
              @keyup.enter="doAddSearch">
              <template #prefix><el-icon>
                  <Search />
                </el-icon></template>
            </el-input>
            <el-button type="primary" @click="doAddSearch">{{ t('common.search') }}</el-button>
          </div>
          <div v-if="addSearchedSuppliers.length > 0" class="supplier-check-list">
            <el-checkbox v-for="s in addSearchedSuppliers" :key="s.tenantId" v-model="addSelectedIds"
              :label="s.tenantId" :value="s.tenantId" class="supplier-check-item">
              <span class="supplier-name">{{ s.companyName }}</span>
            </el-checkbox>
          </div>
          <div v-else-if="addKeyword && !addSearching"
            style="color:#909399;font-size:13px;padding:16px 0;text-align:center">{{ t('inquiry.noSupplierFound') }}
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="addSupplierVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addSubmitting" :disabled="addSelectedIds.length === 0"
          @click="handleAddSuppliers">{{ t('inquiry.confirmAdd') }}（{{ addSelectedIds.length }}）</el-button>
      </template>
    </el-dialog>

    <!-- 提交报价 Dialog -->
    <el-dialog v-model="quoteVisible"
      :title="quoteInquiry?.myQuoteStatus === 'pending' ? t('inquiry.editQuoteTitle') : t('inquiry.submitQuoteTitle')"
      :close-on-click-modal="false" class="inquiry-dialog">
      <el-form ref="quoteFormRef" :model="quoteForm" :rules="quoteRules" :label-width="labelWidth"
        label-position="right">
        <div v-if="quoteInquiry?.quantity" style="color:#909399;font-size:13px;margin-bottom:12px">
          {{ t('inquiry.inquiryQuantity') }}：{{ quoteInquiry.quantity }} {{ quoteInquiry.unit || '' }}，{{
            t('inquiry.supplyQuantityHint') }}
        </div>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('inquiry.unitPrice')">
              <el-input v-model="quoteForm.unitPrice" :placeholder="t('inquiry.optional')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('inquiry.totalPrice')" prop="totalPrice">
              <el-input v-model="quoteForm.totalPrice" :placeholder="t('inquiry.totalPricePlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('inquiry.supplyQuantity')" prop="supplyQuantity">
              <el-input v-model="quoteForm.supplyQuantity" :placeholder="t('inquiry.supplyQuantityPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('inquiry.quoteCurrency')">
              <el-select v-model="quoteForm.currency" :disabled="!!quoteInquiry?.currency" style="width:100%">
                <el-option v-if="quoteInquiry?.currency && !['CNY', 'USD', 'EUR'].includes(quoteInquiry.currency)"
                  :label="quoteInquiry.currency" :value="quoteInquiry.currency" />
                <el-option label="CNY" value="CNY" />
                <el-option label="USD" value="USD" />
                <el-option label="EUR" value="EUR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('inquiry.deliveryDays')">
              <el-input v-model="quoteForm.deliveryDays" :placeholder="t('inquiry.deliveryDaysPlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('inquiry.deliveryTerms')">
          <el-autocomplete v-model="quoteForm.deliveryTerms" :fetch-suggestions="filterDeliveryTerms"
            :placeholder="t('inquiry.deliveryTermsPlaceholder')" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('inquiry.validUntil')">
          <el-date-picker v-model="quoteForm.validUntil" type="datetime" style="width:100%"
            value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('inquiry.optional')" />
        </el-form-item>
        <el-form-item :label="t('inquiry.quoteRemark')">
          <el-input v-model="quoteForm.remark" type="textarea" :rows="2" :placeholder="t('inquiry.optional')" />
        </el-form-item>
        <el-form-item :label="t('inquiry.attachment')">
          <div>
            <el-upload :show-file-list="false" :http-request="handleQuoteAttachmentUpload"
              :before-upload="beforeAttachmentUpload" accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.png">
              <el-button type="primary" plain :loading="attachmentUploading">
                <el-icon>
                  <Plus />
                </el-icon> {{ quoteForm.attachmentUrl ? t('inquiry.reupload') : t('inquiry.uploadFile') }}
              </el-button>
            </el-upload>
            <div v-if="quoteForm.attachmentUrl"
              style="margin-top:6px;display:flex;align-items:center;gap:4px;color:#67c23a;font-size:13px">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
              </svg>
              {{ getFileName(quoteForm.attachmentUrl) }}
            </div>
            <div v-if="!quoteForm.attachmentUrl" class="el-upload__tip" style="margin-top:4px">{{ t('inquiry.uploadTip')
            }}</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quoteVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="quoteSubmitting" @click="handleSubmitQuote">{{ t('inquiry.submitQuoteBtn')
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, MoreFilled } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/upload'
import { getFileName, getFileUrl } from '@/utils/file'
import { listInquiries, closeInquiry, cancelInquiry, getInquiryDetail, acceptInvite, declineInvite, addSuppliers, reinviteSupplier, extendDeadline, submitQuote, withdrawQuote, listQuotes } from '@/api/procurement'
import { listConnections } from '@/api/match'
import { searchSuppliers } from '@/api/procurement'
import InquiryForm from './InquiryForm.vue'
import IdColumn from '@/components/IdColumn.vue'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/utils/format'
import { PAGE_SIZE_OPTIONS } from '@/constants'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { t, locale } = useI18n()
const labelWidth = computed(() => locale.value === 'en' ? '160px' : '110px')

const STATUS_MAP = computed(() => ({ open: t('status.open'), quoting: t('status.quoting'), awarded: t('status.awarded'), closed: t('status.closed'), expired: t('status.expired'), cancelled: t('status.cancelled') }))
const STATUS_TAG = { open: 'primary', quoting: 'warning', awarded: 'success', closed: 'info', expired: 'danger', cancelled: 'info' }
const INVITE_STATUS_MAP = computed(() => ({ pending: t('status.pending'), accepted: t('status.accepted'), declined: t('status.declined'), expired: t('status.expired') }))
const INVITE_STATUS_TAG = { pending: 'primary', accepted: 'success', declined: 'danger', expired: 'info' }

// 身份
const isBuyer = computed(() => authStore.profileType !== 'supplier')
const isDual = computed(() => authStore.profileType === 'both')

// 列表
const list = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchForm = reactive({ keyword: '', status: '', role: isBuyer.value ? 'buyer' : 'supplier' })

// 表单
const formVisible = ref(false)
const editingId = ref(null)
const initialFormData = ref({})

// 详情
const detailVisible = ref(false)
const detail = ref(null)

// 报价表单
const quoteVisible = ref(false)
const quoteSubmitting = ref(false)
const quoteInquiry = ref(null)
const quoteFormRef = ref(null)
const quoteForm = reactive({
  unitPrice: '', totalPrice: '', supplyQuantity: '', currency: 'CNY',
  deliveryDays: '', deliveryTerms: '', validUntil: '', remark: '', attachmentUrl: ''
})
const quoteRules = {
  totalPrice: [{ required: true, message: () => t('message.quoteTotalPriceRequired'), trigger: 'blur' }],
  supplyQuantity: [{
    validator: (_rule, value, callback) => {
      const num = Number(value)
      if (value && isNaN(num)) { callback(new Error(t('message.validNumberRequired'))); return }
      if (num <= 0) { callback(new Error(t('message.supplyQuantityPositive'))); return }
      const max = quoteInquiry.value?.quantity
      if (max && num > Number(max)) { callback(new Error(t('message.supplyQuantityExceed', { max }))); return }
      callback()
    },
    trigger: 'blur'
  }]
}

// 自动计算总价：单价 × 供应数量
watch(
  () => [quoteForm.unitPrice, quoteForm.supplyQuantity],
  ([price, qty]) => {
    const p = Number(price), q = Number(qty)
    if (p > 0 && q > 0) {
      quoteForm.totalPrice = (p * q).toFixed(2)
    }
  }
)

onMounted(() => {
  // 对接桥：accepted 对接 → 一键发起询盘，自动预填
  if (route.query.connectionId) {
    initialFormData.value = {
      connectionId: Number(route.query.connectionId),
      requirementId: route.query.requirementId ? Number(route.query.requirementId) : undefined,
      supplierTenantId: route.query.supplierTenantId ? Number(route.query.supplierTenantId) : undefined,
      supplierName: route.query.supplierName,
      title: route.query.requirementTitle
    }
    formVisible.value = true
  }
  // 通知跳转：自动打开对应询盘详情
  if (route.query.inquiryId) {
    getInquiryDetail(Number(route.query.inquiryId), { role: searchForm.role }).then(res => {
      detail.value = res?.data || res
      detailVisible.value = true
    }).catch(() => { })
  }
  fetchList()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await listInquiries({ pageNum: pageNum.value, pageSize: pageSize.value, ...searchForm })
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
  initialFormData.value = {}
  formVisible.value = true
}

function handleEdit(row) {
  editingId.value = row.id
  initialFormData.value = {}
  formVisible.value = true
}

function onFormSubmitted() {
  pageNum.value = 1
  fetchList()
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">${t('message.cancelInquiryPrefix')} <span class="highlight-name">${row.title}</span> ${t('message.cancelInquirySuffix')}</div><div>${t('message.cancelInquiryDesc')}</div>`,
      t('message.cancelInquiryTitle'),
      { confirmButtonText: t('message.confirmCancelBtn'), cancelButtonText: t('message.keepBtn'), type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await cancelInquiry(row.id)
    ElMessage.success(t('message.inquiryCancelled'))
    fetchList()
  } catch { /* API error handled by interceptor */ }
}

async function handleClose(row) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">${t('message.closeInquiryPrefix')} <span class="highlight-name">${row.title}</span> ${t('message.closeInquirySuffix')}</div><div>${t('message.closeInquiryDesc')}</div>`,
      t('message.closeInquiryTitle'),
      { confirmButtonText: t('message.confirmCloseBtn'), cancelButtonText: t('common.cancel'), type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await closeInquiry(row.id)
    ElMessage.success(t('message.inquiryClosed'))
    fetchList()
  } catch { /* API error handled by interceptor */ }
}

function handleExtendDeadline(row) {
  extendDeadlineTarget.value = row
  extendDeadlineTime.value = ''
  extendDeadlineVisible.value = true
}

async function handleConfirmExtendDeadline() {
  if (!extendDeadlineTime.value) {
    ElMessage.warning(t('message.selectNewDeadlineWarn'))
    return
  }
  extendDeadlineSubmitting.value = true
  try {
    await extendDeadline(extendDeadlineTarget.value.id, extendDeadlineTime.value)
    ElMessage.success(t('message.deadlineExtended'))
    extendDeadlineVisible.value = false
    fetchList()
  } catch { /* API error handled by interceptor */ }
  finally { extendDeadlineSubmitting.value = false }
}

const detailLoading = ref(false)

async function handleDetail(row) {
  detailLoading.value = true
  try {
    const res = await getInquiryDetail(row.id, { role: searchForm.role })
    detail.value = res?.data || res
    detailVisible.value = true
  } catch { /* ignore */ }
  finally { detailLoading.value = false }
}

function handleCompare(row) {
  router.push({ name: 'PriceComparison', query: { inquiryId: row.id } })
}

function handleBuyerCmd(cmd, row) {
  if (cmd === 'detail') handleDetail(row)
  else if (cmd === 'compare') handleCompare(row)
  else if (cmd === 'edit') handleEdit(row)
  else if (cmd === 'cancel') handleCancel(row)
  else if (cmd === 'close') handleClose(row)
  else if (cmd === 'extend') handleExtendDeadline(row)
}

function handleSupplierCmd(cmd, row) {
  if (cmd === 'detail') handleDetail(row)
  else if (cmd === 'decline') handleDeclineInvite(row)
  else if (cmd === 'withdraw') handleWithdrawQuote(row.myQuoteId)
}

// ---- 供应商操作 ----

function supplierQuoteCurrency(row) {
  return row.myQuotePrice == null || row.myQuotePrice === '' ? null : (row.myQuoteCurrency || row.currency || null)
}

function isQuoteExpired(row) {
  return row.quoteDeadline && new Date(row.quoteDeadline) < new Date()
}

function canInviteMore(row) {
  return (row.status === 'open' || row.status === 'quoting') && !isQuoteExpired(row)
}

function isAwardedQuote(row) {
  return !!(row?.myQuoteIsWinning || (row?.myAwardedQuantity != null && row.myAwardedQuantity > 0))
}

function canExtendDeadline(row) {
  if (row.status === 'expired') return true
  return (row.status === 'open' || row.status === 'quoting') && isQuoteExpired(row)
}

function isAllDeclined(row) {
  const total = row.supplierCount || 0
  const declined = row.declinedSupplierCount || 0
  return total > 0 && declined === total && !row.acceptedSupplierCount
}

const BUYER_ACTION_LABEL = computed(() => ({
  extend: t('inquiry.extend'),
  compare: t('inquiry.compare'),
  edit: t('common.edit'),
  detail: t('common.detail'),
  cancel: t('inquiry.cancelInquiry'),
  close: t('inquiry.closeInquiry')
}))

function buyerActionLabel(action) {
  return BUYER_ACTION_LABEL.value[action] || action
}

/** 采购方操作，按优先级排列。满 3 个才进三点，1～2 个全部平铺 */
function buyerActionList(row) {
  const actions = []
  const canEdit = row.status === 'open' && !row.acceptedSupplierCount
  const allDeclined = isAllDeclined(row)
  const pastDue = canExtendDeadline(row)
  const canCompare = !!row.quoteCount && (row.status === 'quoting' || row.status === 'expired' || isQuoteExpired(row))
  if (pastDue && !canCompare) actions.push('extend')
  if (canCompare) actions.push('compare')
  if (canEdit && !allDeclined) actions.push('edit')
  actions.push('detail')
  if (canEdit && allDeclined) actions.push('edit')
  if (pastDue && canCompare) actions.push('extend')
  if (row.status === 'open') actions.push('cancel')
  if ((row.status === 'quoting' || row.status === 'expired') && !(row.awardedQuantityTotal > 0)) actions.push('close')
  return actions
}

function buyerTiledActions(row) {
  const list = buyerActionList(row)
  return list.length >= 3 ? list.slice(0, 1) : list
}

function buyerMenuActions(row) {
  const list = buyerActionList(row)
  return list.length >= 3 ? list.slice(1) : []
}

function isDeadlinePassed(row) {
  return row.status === 'expired' || ((row.status === 'open' || row.status === 'quoting') && isQuoteExpired(row))
}

function isPendingAward(row) {
  return isDeadlinePassed(row) && Number(row.quoteCount) > 0
}

function getDisplayStatus(row) {
  if (isPendingAward(row)) return t('status.pending_award')
  if (isDeadlinePassed(row)) return t('status.expired')
  return STATUS_MAP.value[row.status] || row.status
}

function getDisplayStatusTag(row) {
  if (isPendingAward(row)) return 'warning'
  if (isDeadlinePassed(row)) return 'danger'
  return STATUS_TAG[row.status] || 'info'
}

async function handleAcceptInvite(row) {
  if (!row.myInviteId) return
  if (isQuoteExpired(row)) { ElMessage.warning(t('message.quoteExpiredAcceptWarn')); return }
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">${t('message.acceptInvitePrefix')} <span class="highlight-name">${row.buyerCompanyName || ''}</span> ${t('message.acceptInviteSuffix')}</div><div style="color:#909399;font-size:13px">${row.title}</div>`,
      t('message.acceptInviteTitle'),
      { confirmButtonText: t('message.confirmAcceptBtn'), cancelButtonText: t('common.cancel'), type: 'info', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await acceptInvite(row.id, row.myInviteId)
    ElMessage.success(t('message.inviteAccepted'))
    fetchList()
  } catch { /* API error handled by interceptor */ }
}

async function handleDeclineInvite(row) {
  if (!row.myInviteId) return
  if (isQuoteExpired(row)) { ElMessage.warning(t('message.quoteExpiredDeclineWarn')); return }
  let reason
  try {
    const result = await ElMessageBox.prompt(
      `<div style="white-space:nowrap;margin-bottom:4px">${t('message.declineInvitePrefix')} <span class="highlight-name">${row.title}</span> ${t('message.declineInviteSuffix')}</div><div>${t('message.declineInviteDesc')}</div>`,
      t('message.declineInviteTitle'),
      {
        confirmButtonText: t('message.confirmDeclineBtn'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
        dangerouslyUseHTMLString: true,
        customClass: 'confirmDdMsgBox',
        inputType: 'textarea',
        inputPlaceholder: t('message.declineReasonPlaceholder'),
        inputValidator: (v) => !v || v.length <= 500 ? true : t('message.declineReasonLimit')
      }
    )
    reason = result?.value
  } catch { return }
  try {
    await declineInvite(row.id, row.myInviteId, reason || null)
    ElMessage.success(t('message.inviteDeclined'))
    fetchList()
  } catch { /* API error handled by interceptor */ }
}

// ---- 追加供应商 ----
const addSupplierVisible = ref(false)
const addSupplierTab = ref('match')
const addKeyword = ref('')
const addSearching = ref(false)
const addSubmitting = ref(false)
const addMatchedSuppliers = ref([])
const addSearchedSuppliers = ref([])
const addSelectedIds = ref([])

// 延长截止
const extendDeadlineVisible = ref(false)
const extendDeadlineTarget = ref(null)
const extendDeadlineTime = ref('')
const extendDeadlineSubmitting = ref(false)

function openAddSupplierDialog() {
  addSupplierTab.value = 'match'
  addKeyword.value = ''
  addSearchedSuppliers.value = []
  addSelectedIds.value = []
  addSupplierVisible.value = true
  loadAddMatchedSuppliers()
}

async function loadAddMatchedSuppliers() {
  try {
    const res = await listConnections({ status: 'accepted', role: 'buyer', pageSize: 200 })
    const records = res?.data?.records || res?.records || []
    const supplierMap = new Map()
    for (const conn of records) {
      if (!supplierMap.has(conn.supplierTenantId)) {
        supplierMap.set(conn.supplierTenantId, {
          tenantId: conn.supplierTenantId,
          companyName: conn.supplierCompanyName || `${t('inquiry.supplierFallback')}#${conn.supplierTenantId}`
        })
      }
    }
    // 排除已在受邀名单中的
    const existingIds = (detail.value?.invites || []).map(inv => inv.supplierTenantId)
    addMatchedSuppliers.value = [...supplierMap.values()].filter(s => !existingIds.includes(s.tenantId))
  } catch { /* ignore */ }
}

async function doAddSearch() {
  if (!addKeyword.value.trim()) return
  addSearching.value = true
  try {
    const res = await searchSuppliers({ keyword: addKeyword.value.trim(), pageSize: 50 })
    const records = res?.data?.records || res?.records || []
    const existingIds = (detail.value?.invites || []).map(inv => inv.supplierTenantId)
    const myTenantId = authStore.user?.tenantId
    addSearchedSuppliers.value = records
      .filter(s => !existingIds.includes(s.tenantId))
      .filter(s => String(s.tenantId) !== String(myTenantId))
      .map(s => ({ tenantId: s.tenantId, companyName: s.companyName }))
  } catch { /* ignore */ }
  finally { addSearching.value = false }
}

async function handleAddSuppliers() {
  if (addSelectedIds.value.length === 0) return
  addSubmitting.value = true
  try {
    await addSuppliers(detail.value.id, addSelectedIds.value)
    ElMessage.success(t('message.suppliersAdded', { count: addSelectedIds.value.length }))
    addSupplierVisible.value = false
    // 刷新详情
    const res = await getInquiryDetail(detail.value.id, { role: searchForm.role })
    detail.value = res?.data || res
  } finally { addSubmitting.value = false }
}

function inviteHasQuote(invite) {
  return (detail.value?.quotes || []).some(q => String(q.supplierTenantId) === String(invite.supplierTenantId))
}

async function handleReinvite(invite) {
  try {
    await ElMessageBox.confirm(
      `<div style="margin-bottom:4px">${t('message.reinvitePrefix')} <span class="highlight-name">${invite.supplierCompanyName}</span> ${t('message.reinviteSuffix')}</div>`,
      t('message.reinviteTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'info', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await reinviteSupplier(detail.value.id, invite.id)
    ElMessage.success(t('message.reinviteSuccess'))
    const res = await getInquiryDetail(detail.value.id, { role: searchForm.role })
    detail.value = res?.data || res
  } catch { /* API error handled by interceptor */ }
}

const DELIVERY_TERMS = [
  { value: 'FOB（船上交货）' },
  { value: 'CIF（到岸价，含运费+保险）' },
  { value: 'EXW（工厂交货）' },
  { value: 'DDP（完税后交货）' },
  { value: 'CFR（成本加运费）' },
  { value: 'FCA（货交承运人）' },
]

function filterDeliveryTerms(keyword, cb) {
  if (!keyword) { cb(DELIVERY_TERMS); return }
  const kw = keyword.toLowerCase()
  cb(DELIVERY_TERMS.filter(v => v.value.toLowerCase().includes(kw)))
}

const attachmentUploading = ref(false)

function beforeAttachmentUpload(file) {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) { ElMessage.error(t('message.fileTooLarge')); return false }
  return true
}

async function handleQuoteAttachmentUpload({ file }) {
  attachmentUploading.value = true
  try {
    const { data } = await uploadFile(file, 'quote')
    // 只存 oss_key（签名 URL 会过期）
    quoteForm.attachmentUrl = data?.key || ''
  } catch { /* ignore */ }
  finally { attachmentUploading.value = false }
}

async function handleOpenQuote(inquiry) {
  if (isAwardedQuote(inquiry)) { ElMessage.warning(t('message.awardedQuoteNoEdit')); return }
  if (isQuoteExpired(inquiry)) { ElMessage.warning(t('message.quoteExpired')); return }
  quoteInquiry.value = inquiry
  // 已有 pending 报价 → 预填表单
  if (inquiry.myQuoteId && inquiry.myQuoteStatus === 'pending') {
    try {
      const qRes = await listQuotes(inquiry.id)
      const quotes = qRes?.data || qRes || []
      const myQuote = quotes.find(q => String(q.id) === String(inquiry.myQuoteId))
      if (myQuote) {
        Object.assign(quoteForm, {
          unitPrice: myQuote.unitPrice || '',
          totalPrice: myQuote.totalPrice || '',
          supplyQuantity: myQuote.supplyQuantity || '',
          currency: myQuote.currency || 'CNY',
          deliveryDays: myQuote.deliveryDays || '',
          deliveryTerms: myQuote.deliveryTerms || '',
          validUntil: myQuote.validUntil || '',
          remark: myQuote.remark || '',
          // 附件回填：用 key（VO 同时返回 attachmentKey），URL 会过期不能回传
          attachmentUrl: myQuote.attachmentKey || ''
        })
        quoteVisible.value = true
        return
      }
    } catch { /* 失败时走空表单 */ }
  }
  Object.assign(quoteForm, {
    unitPrice: '', totalPrice: '', supplyQuantity: '',
    currency: inquiry.currency || 'CNY',
    deliveryDays: '', deliveryTerms: '', validUntil: '', remark: '', attachmentUrl: ''
  })
  quoteVisible.value = true
}

async function handleSubmitQuote() {
  try {
    await quoteFormRef.value?.validate()
  } catch { return }
  if (!quoteInquiry.value) return
  quoteSubmitting.value = true
  try {
    await submitQuote({
      inquiryId: quoteInquiry.value.id,
      unitPrice: quoteForm.unitPrice || null,
      totalPrice: quoteForm.totalPrice || null,
      supplyQuantity: quoteForm.supplyQuantity || null,
      currency: quoteForm.currency || 'CNY',
      deliveryDays: quoteForm.deliveryDays || null,
      deliveryTerms: quoteForm.deliveryTerms || null,
      validUntil: quoteForm.validUntil || null,
      remark: quoteForm.remark || null,
      // 后端 CreateQuoteDTO 字段名为 attachmentKey（存 OSS 对象路径，非签名 URL）
      attachmentKey: quoteForm.attachmentUrl || null
    })
    ElMessage.success(t('message.quoteSubmitted'))
    quoteVisible.value = false
    fetchList()
  } finally { quoteSubmitting.value = false }
}

async function handleWithdrawQuote(quoteId) {
  try {
    await ElMessageBox.confirm(
      `<div style="white-space:nowrap;margin-bottom:4px">${t('message.withdrawQuoteMsg')}</div><div>${t('message.withdrawQuoteDesc')}</div>`,
      t('message.withdrawQuoteTitle'),
      { confirmButtonText: t('message.confirmWithdrawBtn'), cancelButtonText: t('common.cancel'), type: 'warning', dangerouslyUseHTMLString: true, customClass: 'confirmDdMsgBox' }
    )
  } catch { return }
  try {
    await withdrawQuote(quoteId)
    ElMessage.success(t('message.quoteWithdrawn'))
    fetchList()
  } catch { /* API error handled by interceptor */ }
}
</script>

<style>
/* 询盘列表卡片：去掉全局 .content-card 的固定高度 + 内部滚动，让分页条自然显示在表格下方（仅限本页） */
.inquiry-management-page .content-card {
  height: auto !important;
  overflow-y: visible !important;
}

/* 询盘详情弹窗：用更高权重覆盖全局展示弹窗 45%/760px 限制，扩大显示 */
.el-dialog.inquiry-detail-dialog:has(.el-descriptions) {
  width: 55% !important;
  max-width: 1100px;
  min-width: 760px;
}

.inquiry-detail-dialog .el-descriptions__label {
  width: 100px !important;
  text-align: center !important;
}

.inquiry-detail-dialog .el-descriptions__content {
  font-size: 16px !important;
}

.inquiry-detail-dialog .el-descriptions__cell {
  padding-top: 12px !important;
  padding-bottom: 12px !important;
}

.detail-section-title {
  margin-top: 20px;
  margin-bottom: 8px;
  padding: 8px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  text-align: center;
}
</style>
