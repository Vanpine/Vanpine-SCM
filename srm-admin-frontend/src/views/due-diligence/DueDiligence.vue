<template>
  <div>
    <!-- 加载中 -->
    <div v-if="loading" class="flex justify-center py-20">
      <div class="flex flex-col items-center gap-3">
        <div class="w-8 h-8 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
        <span class="text-sm text-gray-400">加载中...</span>
      </div>
    </div>

    <!-- 未找到申请 -->
    <div v-else-if="!appData" class="text-center py-20 text-gray-400">
      暂无入驻申请数据
    </div>

    <!-- 审核未通过 -->
    <div v-else-if="appData.status !== 'approved'" class="text-center py-20 text-gray-400">
      入驻申请暂未通过审核
    </div>

    <!-- ==================== 主内容区 ==================== -->
    <div v-else>
      <!-- 头部状态 -->
      <el-card class="mb-4">
        <div class="flex items-center justify-between">
          <span class="text-base font-semibold text-gray-700">尽职调查</span>
          <div class="flex items-center gap-3">
            <!-- 尽调状态：按 ddFirmStatus > ddReportStatus > paymentStatus 优先级展示 -->
            <!-- 优先级：退回/待确认 > 已完成 > 核查中 > 已提交 > 待支付 -->
            <el-tag v-if="appData.paymentStatus !== 'paid'" type="warning" size="small">待支付</el-tag>
            <el-tag v-else-if="appData.ddFirmStatus === 'returned'" type="danger" size="small">需补充材料</el-tag>
            <el-tag v-else-if="appData.ddFirmStatus === 'submitted'" type="info" size="small">待管理员确认</el-tag>
            <el-tag v-else-if="appData.ddReportStatus === 'completed'" type="success" size="small">已完成</el-tag>
            <el-tag v-else-if="appData.ddFirmId" type="warning" size="small">核查中</el-tag>
            <el-tag v-else type="success" size="small">已提交</el-tag>
          </div>
        </div>
      </el-card>

      <!-- ==================== 可编辑表单：未支付 或 退回补充场景 ==================== -->
      <template v-if="appData.paymentStatus !== 'paid' || appData.ddFirmStatus === 'returned'">
        <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange" :before-leave="beforeTabLeave">
          <!-- Tab 1: 企业信息填写 -->
          <el-tab-pane label="企业信息填写" name="form">
            <!-- 退回补充提示（已支付后被退回时在此显示，引导用户修改材料后重新提交） -->
            <el-alert v-if="appData?.ddFirmStatus === 'returned'" title="事务所退回，需补充材料" type="warning" :closable="false"
              show-icon class="mb-4">
              <template #default>
                <p class="mb-2">{{ appData.ddFirmReturnReason || '请根据事务所要求补充或修正相关材料，保存后点击提交即可通知事务所重新核查。' }}</p>
              </template>
            </el-alert>
            <!-- 申请身份横幅：入驻时选定，只读展示，决定下方买/供方信息是否出现 -->
            <div class="role-banner">
              <span class="role-label">申请身份：</span>
              <span class="role-value">{{ applicantRoleLabel }}</span>
              <span class="role-tip">入驻时选定不可修改；决定下方需填写的采购方 / 供应方信息</span>
            </div>
            <el-form ref="formRef" :model="form" label-width="140px" class="dd-form">
              <!-- 资质文件上传（优先展示） -->
              <h4 class="section-title">资质文件</h4>
              <el-row :gutter="32">
                <el-col :span="12">
                  <el-form-item prop="businessLicenseUrl" :rules="[{ required: true, message: '请上传营业执照' }]"
                    :show-message="false" class="biz-upload-item">
                    <div class="upload-area">
                      <el-upload ref="uploadBizLicense" :http-request="(opts) => handleUpload(opts, 'businessLicense')"
                        :show-file-list="false" accept=".pdf,.jpg,.jpeg,.png" drag>
                        <template v-if="!form.businessLicenseUrl">
                          <el-icon class="upload-icon">
                            <UploadFilled />
                          </el-icon>
                          <div class="upload-label"><span class="required">*</span>营业执照</div>
                          <div class="upload-hint">点击或拖拽文件至此处上传</div>
                          <div class="upload-format">PDF / JPG / PNG</div>
                        </template>
                        <template v-else>
                          <el-icon class="upload-icon text-green-500">
                            <CircleCheckFilled />
                          </el-icon>
                          <div class="upload-label"><span class="required">*</span>营业执照</div>
                          <div class="upload-label" style="font-size:14px;color:#10b981;font-weight:400">已上传：{{
                            getFileName(form.businessLicenseUrl, 999) }}</div>
                          <div class="upload-actions">
                            <el-button text type="primary"
                              @click.stop="triggerUpload('uploadBizLicense')">重新上传</el-button>
                            <el-button text type="danger" @click.stop="form.businessLicenseUrl = ''">删除</el-button>
                          </div>
                        </template>
                      </el-upload>
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <div class="upload-area">
                    <el-upload ref="uploadAttachment" :http-request="(opts) => handleUpload(opts, 'ddAttachment')"
                      :show-file-list="false" accept=".pdf,.jpg,.jpeg,.png,.doc,.docx" drag>
                      <template v-if="!form.attachments.length">
                        <el-icon class="upload-icon">
                          <UploadFilled />
                        </el-icon>
                        <div class="upload-label">附件材料</div>
                        <div class="upload-hint">点击或拖拽文件至此处上传</div>
                        <div class="upload-format">PDF / 图片 / Word</div>
                      </template>
                      <template v-else>
                        <el-icon class="upload-icon text-green-500">
                          <CircleCheckFilled />
                        </el-icon>
                        <div class="upload-label">附件材料</div>
                        <div class="upload-label" style="font-size:14px;color:#10b981;font-weight:400">已上传：{{
                          form.attachments.map(a => a.fileName || '未命名').join('、') }}</div>
                        <div class="upload-actions">
                          <el-button text type="primary"
                            @click.stop="triggerUpload('uploadAttachment')">重新上传</el-button>
                          <el-button text type="danger" @click.stop="form.attachments = []">删除</el-button>
                        </div>
                      </template>
                    </el-upload>
                  </div>
                </el-col>
              </el-row>

              <!-- ====== 尽调核心材料（通用，所有身份必填） ====== -->
              <h4 class="section-title">尽调核心材料</h4>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="法人姓名" prop="legalPersonName" :rules="[{ required: true, message: '请输入法人姓名' }]">
                    <el-input v-model="form.legalPersonName" placeholder="与营业执照一致" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="法人身份证号" prop="legalPersonId" :rules="[{ required: true, message: '请输入法人身份证号' }]">
                    <el-input v-model="form.legalPersonId" placeholder="18 位公民身份证号" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="8">
                  <el-form-item label="股权结构" prop="shareholderInfo"
                    :rules="shareholderRequired ? [{ required: true, message: '请上传股权结构文件' }] : []">
                    <div v-if="!form.shareholderInfo" class="upload-btn-wrap">
                      <el-button type="primary" plain :icon="UploadFilled"
                        @click="triggerUpload('uploadShareholder')">点击上传</el-button>
                      <span class="upload-tip">公司章程或股权结构图扫描件<span v-if="shareholderRequired"
                          class="tier-tip">（标准/高级套餐必填）</span></span>
                    </div>
                    <div v-else class="uploaded-file">
                      <svg class="upload-check" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" fill="#16a34a" />
                        <path d="M8 12l3 3 5-5" stroke="#fff" stroke-width="2" stroke-linecap="round"
                          stroke-linejoin="round" />
                      </svg>
                      <span class="file-name">{{ getFileName(form.shareholderInfo) }}</span>
                      <el-button text type="primary" @click="triggerUpload('uploadShareholder')">重新上传</el-button>
                      <el-button text type="danger" @click="form.shareholderInfo = ''">删除</el-button>
                    </div>
                    <el-upload ref="uploadShareholder" :http-request="(opts) => handleUpload(opts, 'shareholderDoc')"
                      :show-file-list="false" accept=".pdf" style="display:none" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="审计报告" prop="financialReportUrl"
                    :rules="financialRequired ? [{ required: true, message: '请上传审计报告' }] : []">
                    <div v-if="!form.financialReportUrl" class="upload-btn-wrap">
                      <el-button type="primary" plain :icon="UploadFilled"
                        @click="triggerUpload('uploadFinancial')">点击上传</el-button>
                      <span class="upload-tip">近一年审计报告或财务报表<span v-if="financialRequired"
                          class="tier-tip">（高级套餐必填）</span></span>
                    </div>
                    <div v-else class="uploaded-file">
                      <svg class="upload-check" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" fill="#16a34a" />
                        <path d="M8 12l3 3 5-5" stroke="#fff" stroke-width="2" stroke-linecap="round"
                          stroke-linejoin="round" />
                      </svg>
                      <span class="file-name">{{ getFileName(form.financialReportUrl) }}</span>
                      <el-button text type="primary" @click="triggerUpload('uploadFinancial')">重新上传</el-button>
                      <el-button text type="danger" @click="form.financialReportUrl = ''">删除</el-button>
                    </div>
                    <el-upload ref="uploadFinancial" :http-request="(opts) => handleUpload(opts, 'financialReport')"
                      :show-file-list="false" accept=".pdf" style="display:none" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="诉讼记录" prop="legalRecord">
                    <div class="legal-record-group">
                      <el-radio-group v-model="legalRecordHas" class="mb-3">
                        <el-radio :value="false">无</el-radio>
                        <el-radio :value="true">有</el-radio>
                      </el-radio-group>
                      <template v-if="legalRecordHas">
                        <el-input v-model="form.legalRecord" type="textarea" :rows="3" placeholder="请说明诉讼或行政处罚的具体情况" />
                        <div v-if="!legalDocUrl" class="upload-btn-wrap mt-2">
                          <el-button type="primary" plain :icon="UploadFilled"
                            @click="triggerUpload('uploadLegalDoc')">上传相关文书</el-button>
                        </div>
                        <div v-else class="uploaded-file mt-2">
                          <svg class="upload-check" viewBox="0 0 24 24" fill="none">
                            <circle cx="12" cy="12" r="10" fill="#16a34a" />
                            <path d="M8 12l3 3 5-5" stroke="#fff" stroke-width="2" stroke-linecap="round"
                              stroke-linejoin="round" />
                          </svg>
                          <span class="file-name">{{ getFileName(legalDocUrl) }}</span>
                          <el-button text type="primary" @click="triggerUpload('uploadLegalDoc')">重新上传</el-button>
                          <el-button text type="danger" @click="legalDocUrl = ''">删除</el-button>
                        </div>
                        <el-upload ref="uploadLegalDoc" :http-request="(opts) => handleUpload(opts, 'legalDoc')"
                          :show-file-list="false" accept=".pdf,.jpg,.jpeg,.png" style="display:none" />
                      </template>
                      <div v-else class="text-gray-400 text-sm">无相关记录</div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 基本信息 -->
              <h4 class="section-title">基本信息</h4>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="企业名称" prop="companyName" :rules="[{ required: true, message: '请输入企业名称' }]"
                    class="reg-field">
                    <el-input v-model="form.companyName" placeholder="企业全称" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="企业注册号" prop="regNumber" :rules="[{ required: true, message: '请输入企业注册号' }]"
                    class="reg-field">
                    <el-input v-model="form.regNumber" placeholder="统一社会信用代码" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="国家/地区" prop="country" :rules="[{ required: true, message: '请输入国家/地区' }]"
                    class="reg-field">
                    <el-input v-model="form.country" placeholder="如 中国" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="所在省份" prop="province" :rules="[{ required: true, message: '请输入省份' }]">
                    <el-input v-model="form.province" placeholder="如 广东省" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="所在城市" prop="city" :rules="[{ required: true, message: '请输入城市' }]">
                    <el-input v-model="form.city" placeholder="如 深圳市" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="成立年份" prop="establishedYear" :rules="[{ required: true, message: '请输入成立年份' }]"
                    class="reg-field">
                    <el-input v-model.number="form.establishedYear" placeholder="如 2010" type="number" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="所属行业" prop="industry" :rules="[{ required: true, message: '请输入所属行业' }]">
                    <el-input v-model="form.industry" placeholder="如 电子制造、汽车" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="注册地址" class="reg-field">
                    <el-input v-model="form.registeredAddress" placeholder="与营业执照一致的注册地址" />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="公司简介">
                    <el-input v-model="form.description" type="textarea" :rows="5" placeholder="主营业务描述" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="企业官网">
                    <el-input v-model="form.website" placeholder="https://www.example.com" />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 企业规模 -->
              <h4 class="section-title">企业规模</h4>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="员工人数" prop="employeeCount" :rules="[{ required: true, message: '请选择员工人数' }]">
                    <el-select v-model="form.employeeCount" placeholder="请选择" clearable style="width:100%">
                      <el-option v-for="opt in EMPLOYEE_COUNT_OPTIONS" :key="opt.value" :label="opt.label"
                        :value="opt.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="年营收金额" prop="annualRevenueAmount"
                    :rules="[{ required: true, validator: positiveAmountRule, trigger: 'blur' }]">
                    <el-input v-model="form.annualRevenueAmount" placeholder="125000000.00" inputmode="decimal" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="币种" prop="annualRevenueCurrency"
                    :rules="[{ required: true, message: '请选择或输入币种' }]">
                    <el-select v-model="form.annualRevenueCurrency" placeholder="ISO 4217" filterable allow-create
                      default-first-option style="width:100%" @change="handleCurrencyChange('annualRevenueCurrency')">
                      <el-option v-for="opt in CURRENCY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="期间开始" prop="fiscalPeriodStart"
                    :rules="[{ required: true, message: '请选择开始日期' }]">
                    <el-date-picker v-model="form.fiscalPeriodStart" type="date" value-format="YYYY-MM-DD"
                      placeholder="YYYY-MM-DD" style="width:100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="期间结束" prop="fiscalPeriodEnd"
                    :rules="[{ required: true, message: '请选择结束日期' }]">
                    <el-date-picker v-model="form.fiscalPeriodEnd" type="date" value-format="YYYY-MM-DD"
                      placeholder="YYYY-MM-DD" style="width:100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 联系信息 -->
              <h4 class="section-title">联系信息</h4>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="联系人" prop="contactName" :rules="[{ required: true, message: '请输入联系人' }]"
                    class="reg-field">
                    <el-input v-model="form.contactName" placeholder="姓名" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="职位" class="reg-field">
                    <el-input v-model="form.contactTitle" placeholder="职位" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="邮箱" prop="contactEmail" :rules="[{ required: true, message: '请输入邮箱' }]"
                    class="reg-field">
                    <el-input v-model="form.contactEmail" placeholder="email@company.com" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="手机" prop="contactPhone" :rules="[{ required: true, message: '请输入手机' }]"
                    class="reg-field">
                    <el-input v-model="form.contactPhone" placeholder="+86 138-xxxx-xxxx" />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- ====== 采购方信息（角色含buyer时显示） ====== -->
              <template v-if="isBuyer">
                <h4 class="section-title">采购方信息</h4>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="采购品类" :rules='[{ "required": true, "message": "请输入采购品类" }]' prop="buyCategory"
                      class="reg-field">
                      <el-input v-model="form.buyCategory" placeholder="如 电子元器件" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="6">
                    <el-form-item label="年采购金额" prop="buyVolumeAmount"
                      :rules='[{ "required": true, "validator": positiveAmountRule, "trigger": "blur" }]' class="reg-field">
                      <el-input v-model="form.buyVolumeAmount" placeholder="30000000.00" inputmode="decimal" clearable />
                    </el-form-item>
                  </el-col>
                  <el-col :span="4">
                    <el-form-item label="币种" prop="buyVolumeCurrency"
                      :rules='[{ "required": true, "message": "请选择或输入币种" }]'>
                      <el-select v-model="form.buyVolumeCurrency" placeholder="ISO 4217" filterable allow-create
                        default-first-option style="width:100%" @change="handleCurrencyChange('buyVolumeCurrency')">
                        <el-option v-for="opt in CURRENCY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="采购产品明细" :rules='[{ "required": true, "message": "请输入采购产品明细" }]'
                      prop="procurementDetail">
                      <el-input v-model="form.procurementDetail" type="textarea" :rows="5" placeholder="具体采购什么产品/服务" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="目标市场" :rules='[{ "required": true, "message": "请输入目标市场" }]' prop="buyMarket">
                      <el-input v-model="form.buyMarket" placeholder="如 东南亚、北美" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="供应商资质要求" :rules='[{ "required": true, "message": "请输入供应商资质要求" }]'
                      prop="supplierRequirement">
                      <el-input v-model="form.supplierRequirement" type="textarea" :rows="5"
                        placeholder="要求供应商具备的资质、认证、经验等" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="付款方式偏好">
                      <el-select v-model="form.paymentTerms" placeholder="可多选" multiple clearable style="width:100%">
                        <el-option v-for="opt in PAYMENT_TERMS_OPTIONS" :key="opt.value" :label="opt.label"
                          :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="目标供应地区">
                      <el-input v-model="form.targetRegion" placeholder="如 华东、华南、东南亚" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </template>

              <!-- ====== 供应方信息（角色含supplier时显示） ====== -->
              <template v-if="isSupplier">
                <h4 class="section-title">供应方信息</h4>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="生产品类" :rules='[{ "required": true, "message": "请输入生产品类" }]' prop="supCategory"
                      class="reg-field">
                      <el-input v-model="form.supCategory" placeholder="如 电子产品" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="年产能" :rules='[{ "required": true, "message": "请输入年产能" }]' prop="supCapacity"
                      class="reg-field">
                      <el-input v-model="form.supCapacity" placeholder="如 100万件/年" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="资质认证" :rules='[{ "required": true, "message": "请输入资质认证" }]' prop="supCerts">
                      <el-input v-model="form.supCerts" placeholder="如 ISO9001、ISO14001、行业许可证等" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="管理体系认证">
                      <el-select v-model="form.managementCert" placeholder="可多选" multiple clearable style="width:100%">
                        <el-option v-for="opt in MANAGEMENT_CERT_OPTIONS" :key="opt.value" :label="opt.label"
                          :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="产品认证">
                      <el-select v-model="form.productCert" placeholder="可多选" multiple clearable style="width:100%">
                        <el-option v-for="opt in PRODUCT_CERT_OPTIONS" :key="opt.value" :label="opt.label"
                          :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="主要产品/服务" :rules='[{ "required": true, "message": "请输入主要产品/服务" }]'
                      prop="mainProducts">
                      <el-input v-model="form.mainProducts" type="textarea" :rows="5" placeholder="核心产品、服务内容、品牌等" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="主要客户/行业" :rules='[{ "required": true, "message": "请输入主要客户/行业" }]'
                      prop="keyClients">
                      <el-input v-model="form.keyClients" placeholder="如 华为、比亚迪、医疗行业" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="主要出口市场">
                      <el-input v-model="form.exportMarkets" placeholder="如 北美、欧洲、东南亚" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="出口经验" :rules='[{ "required": true, "message": "请选择出口经验" }]' prop="supExport"
                      class="reg-field">
                      <el-select v-model="form.supExport" placeholder="请选择" clearable style="width:100%">
                        <el-option label="无" value="0" />
                        <el-option label="3年以下" value="1" />
                        <el-option label="3-5年" value="2" />
                        <el-option label="5-10年" value="3" />
                        <el-option label="10年以上" value="4" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="交货方式">
                      <el-select v-model="form.deliveryTerms" placeholder="可多选" multiple clearable style="width:100%">
                        <el-option v-for="opt in DELIVERY_TERMS_OPTIONS" :key="opt.value" :label="opt.label"
                          :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="交货周期">
                      <el-select v-model="form.leadTime" placeholder="请选择" clearable style="width:100%">
                        <el-option v-for="opt in LEAD_TIME_OPTIONS" :key="opt.value" :label="opt.label"
                          :value="opt.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
              </template>

              <!-- 保存按钮 -->
              <div class="form-footer">
                <div class="footer-left">
                  <el-button :icon="Refresh" plain @click="handleReset">重置</el-button>
                </div>
                <div class="footer-right">
                  <el-button :loading="saving" @click="handleSave()">保存</el-button>
                  <el-button type="primary" @click="handleSubmitToPay">提交</el-button>
                </div>
              </div>
            </el-form>
          </el-tab-pane>

          <!-- Tab 2: 套餐与支付 -->
          <el-tab-pane label="套餐与支付" name="package">
            <div class="pay-currency-bar">
              <span class="label">支付币种</span>
              <el-select v-model="payCurrency" style="width: 200px">
                <el-option v-for="c in paymentCurrencies" :key="c" :label="optionLabel(CURRENCY_OPTIONS, c)" :value="c" />
              </el-select>
              <span class="tip">平台开通币种独立定价，不做汇率折算</span>
            </div>

            <div class="package-grid">
              <div v-for="(pkg, key) in DD_PACKAGES" :key="key"
                :class="['package-card', { 'is-selected': selectedPackage === key }]" @click="selectedPackage = key">
                <div class="package-header">
                  <h3>{{ pkg.label }}</h3>
                  <div class="package-price">
                    <span class="price">{{ currentPriceLabel(key, payCurrency) }}</span>
                  </div>
                  <p class="package-desc">{{ pkg.desc }}</p>
                </div>
                <ul class="package-features">
                  <li v-for="(f, i) in pkg.features" :key="i">
                    <el-icon>
                      <Check />
                    </el-icon> {{ f }}
                  </li>
                </ul>
                <div class="package-footer">
                  <el-button :type="selectedPackage === key ? 'primary' : 'default'"
                    @click.stop="selectedPackage = key">
                    {{ selectedPackage === key ? '已选择' : '选择套餐' }}
                  </el-button>
                </div>
              </div>
            </div>

            <div class="pay-footer">
              <el-alert v-if="payCurrency !== 'CNY'" title="当前工行通道仅支持人民币，请先将支付币种改为 CNY"
                type="warning" :closable="false" show-icon class="mb-3" />
              <el-button type="primary" size="large" :disabled="!selectedPackage || payCurrency !== 'CNY'"
                :loading="paying" @click="handlePay">
                去支付 {{ selectedPackage ? currentPriceLabel(selectedPackage, payCurrency) : '' }}
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>

      <!-- ==================== 已支付：尽调进度报告 ==================== -->
      <template v-else>
        <el-card>
          <div class="report-header">
            <div class="report-icon-wrap">
              <el-icon class="report-icon">
                <Notebook />
              </el-icon>
            </div>
            <div>
              <h2 class="report-title">尽调进度报告</h2>
              <p class="report-subtitle">{{ progressSubtitle }}</p>
            </div>
            <el-tag v-if="appData.ddReportStatus !== 'completed'" :type="progressTagType" size="large" effect="dark"
              class="report-status-tag">{{ progressTagText
              }}</el-tag>
            <!-- 尽调完成：右侧状态标签替换为下载按钮，企业可下载事务所上传的尽调报告 PDF -->
            <el-button v-else type="success" class="report-status-tag" @click="handleDownloadReport">下载尽调报告</el-button>
          </div>

          <!-- 退回补充提示（进度报告区，仅作为状态提醒，编辑在表单 tab 进行） -->
          <el-alert v-if="appData?.ddFirmStatus === 'returned'" title="事务所退回，需补充材料"
            :description="appData.ddFirmReturnReason || '请向上滚动到企业信息填写区域修改材料，保存后点击提交即可。'" type="warning"
            :closable="false" show-icon class="mb-4 mt-[-8px]" />

          <el-divider />

          <!-- 企业信息摘要 -->
          <h4 class="section-title">企业信息</h4>
          <div class="report-grid">
            <div class="report-item">
              <span class="report-label">企业名称</span>
              <span class="report-value">{{ appData.companyName || '-' }}</span>
            </div>
            <div class="report-item">
              <span class="report-label">注册号</span>
              <span class="report-value">{{ appData.regNumber || '-' }}</span>
            </div>
            <div class="report-item">
              <span class="report-label">国家/地区</span>
              <span class="report-value">{{ appData.country || '-' }}</span>
            </div>
            <div class="report-item">
              <span class="report-label">法人代表</span>
              <span class="report-value">{{ appData.legalPersonName || '-' }}</span>
            </div>
            <div class="report-item">
              <span class="report-label">注册地址</span>
              <span class="report-value">{{ appData.registeredAddress || '-' }}</span>
            </div>
          </div>

          <el-divider />

          <!-- 套餐与服务范围 -->
          <h4 class="section-title">套餐与服务范围</h4>
          <div class="pkg-info-card">
            <div class="pkg-info-header">
              <div>
                <span class="pkg-info-name">{{ DD_PACKAGES[appData.packageType]?.label || appData.packageType }}</span>
                <span class="pkg-info-price ml-2">{{ minorAmountLabel(appData.paymentAmount, appData.paymentCurrency) || '-' }}</span>
              </div>
              <el-tag type="success" effect="plain" size="small">已支付</el-tag>
            </div>
            <ul class="pkg-info-features">
              <li v-for="(f, i) in DD_PACKAGES[appData.packageType]?.features || []" :key="i">
                <el-icon>
                  <Check />
                </el-icon> {{ f }}
              </li>
            </ul>
          </div>

          <el-divider />

          <!-- 进度时间线 -->
          <h4 class="section-title">进度时间线</h4>
          <div class="report-timeline">
            <!-- 1. 企业信息填写 -->
            <div class="tl-step completed">
              <div class="tl-dot"><el-icon>
                  <Check />
                </el-icon></div>
              <div class="tl-content">
                <div class="tl-title">企业信息填写</div>
                <div class="tl-desc">已完善企业信息与资质文件</div>
              </div>
            </div>
            <!-- 2. 套餐支付 -->
            <div class="tl-step completed">
              <div class="tl-dot"><el-icon>
                  <Check />
                </el-icon></div>
              <div class="tl-content">
                <div class="tl-title">套餐支付</div>
                <div class="tl-desc">{{ DD_PACKAGES[appData.packageType]?.label || appData.packageType }} · {{ minorAmountLabel(appData.paymentAmount, appData.paymentCurrency) || '-' }}</div>
              </div>
            </div>
            <!-- 3. 资料提交（首次提交时间，支付完成时写入，固定不变） -->
            <div class="tl-step completed">
              <div class="tl-dot"><el-icon>
                  <Check />
                </el-icon></div>
              <div class="tl-content">
                <div class="tl-title">资料提交</div>
                <div class="tl-desc">资料已成功提交 · {{ formatTime(appData.ddSubmittedAt) }}</div>
              </div>
            </div>
            <!-- 3.1 资料重新提交（被事务所退回后补充再提交，仅有重提时间时显示） -->
            <div v-if="appData.ddResubmittedAt" class="tl-step completed">
              <div class="tl-dot"><el-icon>
                  <Refresh />
                </el-icon></div>
              <div class="tl-content">
                <div class="tl-title">资料重新提交</div>
                <div class="tl-desc">退回补充后已重新提交 · {{ formatTime(appData.ddResubmittedAt) }}</div>
              </div>
            </div>
            <!-- 4. 事务所尽调核查 -->
            <div :class="['tl-step', firmStepClass]">
              <div class="tl-dot">
                <el-icon v-if="firmStepClass === 'completed'">
                  <Check />
                </el-icon>
                <el-icon v-else-if="firmStepClass === 'returned'">
                  <WarningFilled />
                </el-icon>
                <el-icon v-else-if="firmStepClass === 'active'">
                  <Loading />
                </el-icon>
                <span v-else>4</span>
              </div>
              <div class="tl-content">
                <div class="tl-title">事务所尽调核查</div>
                <div class="tl-desc">{{ firmStepDesc }}</div>
              </div>
            </div>
            <!-- 5. 审核完成 -->
            <div :class="['tl-step', reportStepClass]">
              <div class="tl-dot">
                <el-icon v-if="reportStepClass === 'completed'">
                  <Check />
                </el-icon>
                <span v-else>5</span>
              </div>
              <div class="tl-content">
                <div class="tl-title">审核完成</div>
                <div class="tl-desc">{{ reportStepDesc }}</div>
              </div>
            </div>
          </div>

          <!-- 事务所信息（已分配事务所时展示，复用 searchFirms 按 ddFirmId 精确匹配） -->
          <div v-if="appData.ddFirmId" class="firm-info-section" v-loading="firmInfoLoading">
            <h4 class="section-title">事务所信息</h4>
            <template v-if="firmInfo">
              <el-descriptions :column="2" border class="firm-info-desc">
                <el-descriptions-item label="事务所名称">{{ firmInfo.firmName || appData.ddFirmName || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="联系人">{{ firmInfo.contactName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ firmInfo.contactPhone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系邮箱">{{ firmInfo.contactEmail || '-' }}</el-descriptions-item>
                <el-descriptions-item label="地址" :span="2">{{ firmAddress || '-' }}</el-descriptions-item>
                <el-descriptions-item label="服务范围" :span="2">{{ firmInfo.serviceScope || '-' }}</el-descriptions-item>
                <el-descriptions-item label="服务领域" :span="2">{{ firmInfo.serviceSector || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <!-- 外部 API 不可用时降级：只显示事务所名称 -->
            <div v-else class="firm-info-fallback">
              <span class="report-label">事务所名称</span>
              <span class="report-value">{{ appData.ddFirmName || '-' }}</span>
              <span class="firm-info-fallback-tip">事务所详细信息暂不可获取</span>
            </div>
          </div>

          <el-divider />

          <div class="report-footer">
            <el-alert v-if="appData.ddReportStatus === 'completed'" title="尽调已完成，感谢您的配合" type="success"
              :closable="false" show-icon />
            <el-alert v-else title="尽调完成后将通过绑定邮箱发送通知，请保持联系方式畅通" type="info" :closable="false" show-icon />
          </div>
        </el-card>
      </template>
    </div>

    <!-- 支付确认弹窗 -->
    <el-dialog v-model="payVisible" :title="payResult ? '请扫码支付' : '确认支付'" width="420px" @closed="resetPayDialog">
      <div v-if="selectedPackage && DD_PACKAGES[selectedPackage]" class="pay-dialog-body">
        <div class="pay-info">
          <div class="pay-info-row">
            <span class="label">套餐</span>
            <span class="value">{{ DD_PACKAGES[selectedPackage].label }}</span>
          </div>
          <div class="pay-info-row">
            <span class="label">说明</span>
            <span class="value">{{ DD_PACKAGES[selectedPackage].desc }}</span>
          </div>
          <div class="pay-info-row">
            <span class="label">币种</span>
            <span class="value">{{ payCurrency }}</span>
          </div>
          <div class="pay-info-row pay-amount">
            <span class="label">金额</span>
            <span class="value text-red-500 text-xl font-bold">{{ currentPriceLabel(selectedPackage, payCurrency) }}</span>
          </div>
        </div>
        <div v-if="payResult" class="pay-qr">
          <img :src="payQrSrc" alt="收款码" width="220" height="220" />
          <p class="pay-qr-tip">请使用银行 / 云闪付扫描上方收款码</p>
          <a v-if="payResult.qrcode" class="pay-qr-link" :href="payResult.qrcode" target="_blank" rel="noopener">打开支付链接</a>
        </div>
        <el-alert v-else title="确认后生成工行收款码，付款完成后页面会自动更新" type="info" :closable="false" show-icon class="mt-4" />
      </div>
      <template #footer>
        <el-button @click="payVisible = false">{{ payResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!payResult" type="primary" :loading="paying" @click="confirmPay">生成收款码</el-button>
        <el-button v-else type="primary" :loading="checkingPay" @click="refreshPayStatus">我已付款</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getApplicationDetail, getDdPackages, payApplication, getPayStatus, updateDdInfo, searchFirms } from '@/api/application'
import { uploadFile } from '@/api/upload'
import {
  DD_PACKAGES, DD_PAYMENT_CURRENCIES, defaultPaymentCurrency,
  EMPLOYEE_COUNT_OPTIONS, ANNUAL_REVENUE_OPTIONS,
  CURRENCY_OPTIONS, currencyAmountLabel, minorAmountLabel,
  packagePriceLabel, optionLabel,
  MANAGEMENT_CERT_OPTIONS, PRODUCT_CERT_OPTIONS,
  DELIVERY_TERMS_OPTIONS, LEAD_TIME_OPTIONS, PAYMENT_TERMS_OPTIONS,
} from '@/constants'
import { ElMessage } from 'element-plus'
import { Check, UploadFilled, CircleCheckFilled, Refresh, Notebook, Loading, WarningFilled } from '@element-plus/icons-vue'
import { usePermissionStore } from '@/stores/permission'
import { getFileUrl, getFileName } from '@/utils/file'
import { formatDateTime as formatTime } from '@/utils/datetime'

// ---- 状态 ----
const permissionStore = usePermissionStore()
const loading = ref(true)
const saving = ref(false)
const formSubmitted = ref(false)
const paying = ref(false)
const checkingPay = ref(false)
const appData = ref(null)
/** 已分配事务所的详细信息（searchFirms 按 ddFirmName 查询后用 ddFirmId 精确匹配） */
const firmInfo = ref(null)
const firmInfoLoading = ref(false)
const activeTab = ref('form')
const selectedPackage = ref('')
/** 后端价目表；接口失败时回退本地常量 */
const catalogPrices = ref(null)
const paymentCurrencies = computed(() => {
  const first = catalogPrices.value && Object.values(catalogPrices.value)[0]
  return first ? Object.keys(first) : DD_PAYMENT_CURRENCIES
})
const payCurrencyOverride = ref('')
const currencyTouched = ref(false)
const payVisible = ref(false)
const payResult = ref(null)
let payStatusTimer = null

const payQrSrc = computed(() => {
  const raw = payResult.value?.qrcode
  if (!raw) return ''
  if (raw.startsWith('data:image') || /\.(png|jpe?g|gif|webp)(\?|$)/i.test(raw)) return raw
  return `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(raw)}`
})

function currentPriceLabel(packageType, currency) {
  return packagePriceLabel(packageType, currency, undefined, catalogPrices.value)
}
const formRef = ref(null)
const legalRecordHas = ref(false)
const legalDocUrl = ref("")
/** 文件上传 refs — 用于 "重新上传" 直接弹文件选择器 */
const uploadBizLicense = ref(null)
const uploadAttachment = ref(null)
const uploadShareholder = ref(null)
const uploadFinancial = ref(null)
const uploadLegalDoc = ref(null)
const uploadRefMap = { uploadBizLicense, uploadAttachment, uploadShareholder, uploadFinancial, uploadLegalDoc }

/** 触发指定 ref 的 el-upload 文件选择器 */
function triggerUpload(refName) {
  const comp = uploadRefMap[refName]?.value
  if (comp?.$el) comp.$el.querySelector('.el-upload__input')?.click()
}

// ---- 角色判断（申请表身份 + 已分配角色双源） ----
const isBuyer = computed(() => {
  return appData.value?.applicantRole?.includes('buyer') || permissionStore.roles.includes('BUYER')
})
const isSupplier = computed(() => {
  return appData.value?.applicantRole?.includes('supplier') || permissionStore.roles.includes('SUPPLIER')
})

// ---- 申请身份（只读展示） ----
const ROLE_LABEL_MAP = { buyer: '采购商', supplier: '供应商', 'buyer,supplier': '采购商+供应商' }
const applicantRoleLabel = computed(() => {
  const r = appData.value?.applicantRole
  if (!r) return '-'
  return appData.value?.applicantRoleLabel || ROLE_LABEL_MAP[r] || r
})

// ---- 套餐维度的必填材料（股权结构/审计报告按套餐档位） ----
/** 当前生效套餐：已支付用已选套餐，未支付用用户正在选的套餐，都没则空 */
const currentPackage = computed(() => appData.value?.packageType || selectedPackage.value || '')
/** 股权结构是否必填（标准/高级套餐） */
const shareholderRequired = computed(() =>
  (DD_PACKAGES[currentPackage.value]?.requiredGroups || []).includes('shareholder'))
/** 审计报告是否必填（高级套餐） */
const financialRequired = computed(() =>
  (DD_PACKAGES[currentPackage.value]?.requiredGroups || []).includes('financial'))

// ---- 事务所信息展示（已分配时由 loadFirmInfo 拉取） ----
/** 事务所地址：省/市/区 + 详细地址拼接，空值跳过 */
const firmAddress = computed(() => {
  const f = firmInfo.value
  if (!f) return ''
  return [f.province, f.city, f.district, f.address].filter(Boolean).join(' ')
})

// ---- 标签映射 ----
const employeeCountLabel = computed(() => {
  const opt = EMPLOYEE_COUNT_OPTIONS.find(o => o.value === appData.value?.employeeCount)
  return opt?.label || appData.value?.employeeCount || '-'
})
const annualRevenueLabel = computed(() => {
  const data = appData.value
  const amountLabel = currencyAmountLabel(data?.annualRevenueAmount, data?.annualRevenueCurrency)
  if (amountLabel) return amountLabel
  const opt = ANNUAL_REVENUE_OPTIONS.find(o => o.value === data?.annualRevenue)
  return opt?.label || data?.annualRevenue || '-'
})

function positiveAmountRule(rule, value, callback) {
  if (value === '' || value === null || value === undefined) {
    callback(new Error('请输入金额'))
    return
  }
  const amount = Number(value)
  if (!Number.isFinite(amount) || amount <= 0) {
    callback(new Error('金额必须大于 0'))
    return
  }
  callback()
}

function handleCurrencyChange(field) {
  if (typeof form[field] === 'string') {
    form[field] = form[field].toUpperCase()
  }
}

// ---- 进度状态（根据事务所和报告状态动态计算） ----
const firmStepClass = computed(() => {
  const d = appData.value
  if (!d || !d.ddFirmId) return 'pending'      // 未分配事务所
  if (d.ddFirmStatus === 'completed') return 'completed'
  if (d.ddFirmStatus === 'returned') return 'returned'  // 退回补充：琥珀色警告
  if (d.ddFirmStatus === 'submitted') return 'active'   // 事务所已提交，等待管理员确认
  return 'active'
})
const firmStepDesc = computed(() => {
  const d = appData.value
  if (!d || !d.ddFirmId) return '等待管理员分配事务所'
  if (d.ddFirmStatus === 'completed') return '事务所已完成核查 · ' + formatTime(d.ddReportTime)
  if (d.ddFirmStatus === 'returned') {
    const reason = d.ddFirmReturnReason ? '原因：' + d.ddFirmReturnReason : ''
    return '事务所已退回，请补充材料。' + reason
  }
  if (d.ddFirmStatus === 'submitted') return '事务所已提交核查报告，等待管理员最终确认 · ' + formatTime(d.ddReportTime)
  if (d.ddFirmStatus === 'in_progress') return '事务所正在核查中'
  return '事务所已分配，等待开始核查'
})
const reportStepClass = computed(() => {
  return appData.value?.ddReportStatus === 'completed' ? 'completed' : 'pending'
})
const reportStepDesc = computed(() => {
  const d = appData.value
  if (d?.ddFirmStatus === 'returned') return '企业补充材料后将重新提交核查'
  if (d?.ddFirmStatus === 'submitted') return '事务所已提交报告，等待管理员确认'
  if (d?.ddReportStatus === 'completed') return '审核已通过 · ' + formatTime(d.ddConfirmedAt)
  return '等待事务所提交核查报告'
})
// 优先级：退回/待确认 > 已完成 > 核查中 > 等待分配
const progressTagType = computed(() => {
  if (appData.value?.ddFirmStatus === 'returned') return 'danger'  // 退回：红色警告
  if (appData.value?.ddFirmStatus === 'submitted') return 'info'   // 待管理员确认：蓝色
  if (appData.value?.ddReportStatus === 'completed') return 'success'
  if (appData.value?.ddFirmId) return 'warning'
  return 'info'
})
const progressTagText = computed(() => {
  if (appData.value?.ddFirmStatus === 'returned') return '需补充材料'
  if (appData.value?.ddFirmStatus === 'submitted') return '待确认'
  if (appData.value?.ddReportStatus === 'completed') return '已完成'
  if (appData.value?.ddFirmId) return '核查中'
  return '等待分配'
})
const progressSubtitle = computed(() => {
  if (appData.value?.ddFirmStatus === 'returned') {
    const reason = appData.value?.ddFirmReturnReason
    return reason ? '事务所退回原因：' + reason : '事务所已退回您的资料，请补充或修正后重新保存提交'
  }
  if (appData.value?.ddFirmStatus === 'submitted') return '事务所已提交核查报告，等待管理员最终确认'
  if (appData.value?.ddReportStatus === 'completed') return '尽调已完成，感谢您的配合'
  if (appData.value?.ddFirmId) return '事务所正在核查中，请耐心等待'
  return '您的资料已成功提交，等待管理员分配事务所'
})

// ---- 表单 ----
const form = reactive({
  companyName: '', country: '', regNumber: '', establishedYear: '',
  registeredAddress: '',
  province: '', city: '', website: '', description: '',
  employeeCount: '',
  annualRevenue: '',
  annualRevenueAmount: '', annualRevenueCurrency: '',
  fiscalPeriodStart: '', fiscalPeriodEnd: '',
  contactName: '', contactTitle: '', contactEmail: '', contactPhone: '',
  buyCategory: '', buyVolume: '',
  buyVolumeAmount: '', buyVolumeCurrency: '',
  buyMarket: '',
  industry: '', procurementDetail: '', supplierRequirement: '', targetRegion: '', paymentTerms: [],
  supCategory: '', supCapacity: '', supCerts: '', supExport: '',
  mainProducts: '', managementCert: [], productCert: [], keyClients: '',
  deliveryTerms: [], leadTime: '', exportMarkets: '',
  businessLicenseUrl: '',
  /** 附件材料（多值）：[{ ossKey, fileName }]，后端拆子表存储 */
  attachments: [],
  legalPersonName: '', legalPersonId: '',
  shareholderInfo: '', financialReportUrl: '', legalRecord: '',
})

/** 支付币种：跟着国家走；只有用户亲手改过下拉才锁定 */
const payCurrency = computed({
  get() {
    if (currencyTouched.value && paymentCurrencies.value.includes(payCurrencyOverride.value)) {
      return payCurrencyOverride.value
    }
    const next = defaultPaymentCurrency(form.country)
    return paymentCurrencies.value.includes(next) ? next : (paymentCurrencies.value[0] || 'USD')
  },
  set(val) {
    payCurrencyOverride.value = val
    currencyTouched.value = true
  },
})

/** 上传文件 */
async function handleUpload(options, type) {
  try {
    const { data } = await uploadFile(options.file, 'dd')
    // 只存 oss_key：签名 URL 会过期，存库必然变死链；后端返回 VO 时按 key 现签
    if (type === 'businessLicense') {
      form.businessLicenseUrl = data.key
    } else if (type === 'financialReport') {
      form.financialReportUrl = data.key
    } else if (type === 'legalDoc') {
      legalDocUrl.value = data.key
    } else if (type === 'shareholderDoc') {
      form.shareholderInfo = data.key
    } else {
      // 多值附件：追加一项（ossKey + 原始文件名）
      form.attachments.push({ ossKey: data.key, fileName: data.originalName })
    }
    ElMessage.success('上传成功')
  } catch {
    // 拦截器已处理
  }
}

/**
 * 加载已分配事务所的详细信息
 * 复用 searchFirms 列表接口：用事务所名称当 keyword 查询，再用 ddFirmId 精确匹配到那一条
 * 外部 API 不可用时降级为 null，卡片只显示 ddFirmName，不阻塞页面
 * @param {number} firmId 事务所 ID（appData.ddFirmId）
 * @param {string} firmName 事务所名称（appData.ddFirmName，用作搜索关键字）
 */
async function loadFirmInfo(firmId, firmName) {
  firmInfoLoading.value = true
  try {
    const { data } = await searchFirms(firmName)
    const list = Array.isArray(data) ? data : []
    // 用 ddFirmId 精确匹配（名称可能重复，ID 唯一；String() 兼容 Long→String）
    firmInfo.value = list.find(f => String(f.firmId) === String(firmId)) || null
  } catch {
    // 拦截器已弹错，这里只做状态恢复
    firmInfo.value = null
  } finally {
    firmInfoLoading.value = false
  }
}

/** 获取当前用户的入驻申请 */
async function fetchMyApplication(silent = false) {
  if (!silent) loading.value = true
  try {
    const { data } = await getApplicationDetail('my-application')
    // 合并尽调字段到顶层（向后兼容拆分后的表结构）
    // dd.country 为 null 时不能覆盖申请表上的国家，否则默认币种会掉回 USD
    const appCountry = data?.country
    if (data?.dueDiligence) {
      const { id, applicationId, ...dd } = data.dueDiligence
      Object.assign(data, dd)
      if (!data.country) data.country = appCountry
    }
    appData.value = data
    // 已分配事务所时拉一次详情；同一事务所不重复搜
    if (data?.ddFirmId && String(firmInfo.value?.firmId) !== String(data.ddFirmId)) {
      loadFirmInfo(data.ddFirmId, data.ddFirmName)
    }
    if (data) {
      form.companyName = data.companyName || ''
      form.country = data.country || ''
      form.regNumber = data.regNumber || ''
      form.establishedYear = data.establishedYear || ''
      form.registeredAddress = data.registeredAddress || ''
      form.province = data.province || ''
      form.city = data.city || ''
      form.website = data.website || ''
      form.description = data.description || ''
      form.employeeCount = data.employeeCount || ''
      form.annualRevenue = data.annualRevenue || ''
      form.annualRevenueAmount = data.annualRevenueAmount ?? ''
      form.annualRevenueCurrency = data.annualRevenueCurrency || ''
      form.contactName = data.contactName || ''
      form.contactTitle = data.contactTitle || ''
      form.contactEmail = data.contactEmail || ''
      form.contactPhone = data.contactPhone || ''
      form.buyCategory = data.buyCategory || ''
      form.buyVolume = data.buyVolume || ''
      form.buyVolumeAmount = data.buyVolumeAmount ?? ''
      form.buyVolumeCurrency = data.buyVolumeCurrency || ''
      form.fiscalPeriodStart = data.fiscalPeriodStart || ''
      form.fiscalPeriodEnd = data.fiscalPeriodEnd || ''
      form.buyMarket = data.buyMarket || ''
      form.industry = data.industry || ''
      form.procurementDetail = data.procurementDetail || ''
      form.supplierRequirement = data.supplierRequirement || ''
      form.targetRegion = data.targetRegion || ''
      form.paymentTerms = data.paymentTerms ? data.paymentTerms.split(',') : []
      form.supCategory = data.supCategory || ''
      form.supCapacity = data.supCapacity || ''
      form.supCerts = data.supCerts || ''
      form.supExport = data.supExport || ''
      form.mainProducts = data.mainProducts || ''
      form.managementCert = data.managementCert ? data.managementCert.split(',') : []
      form.productCert = data.productCert ? data.productCert.split(',') : []
      form.keyClients = data.keyClients || ''
      form.deliveryTerms = data.deliveryTerms ? data.deliveryTerms.split(',') : []
      form.leadTime = data.leadTime || ''
      form.exportMarkets = data.exportMarkets || ''
      form.businessLicenseUrl = data.businessLicenseKey || ''
      // 附件子表：用 ossKey 回填（VO 同时返回 ossKey 供编辑回填，url 仅用于展示）
      form.attachments = (data.attachments || []).map(a => ({ ossKey: a.ossKey, fileName: a.fileName }))
      form.shareholderInfo = data.shareholderInfoKey || ''
      form.financialReportUrl = data.financialReportKey || ''
      // 诉讼文本与法律文书已分列存储，各自独立回填（法律文书用 key）
      form.legalRecord = data.legalRecordText || ''
      legalDocUrl.value = data.legalRecordKey || ''
      legalRecordHas.value = !!form.legalRecord || !!legalDocUrl.value
      form.legalPersonName = data.legalPersonName || ''
      form.legalPersonId = data.legalPersonId || ''
      if (data.packageType) selectedPackage.value = data.packageType
      // 已支付/已推送的页面允许直接访问套餐 Tab
      if (data.paymentStatus === 'paid') formSubmitted.value = true
    }
  } catch {
    if (!silent) appData.value = null
  } finally {
    if (!silent) loading.value = false
  }
}

/** 保存尽调扩展信息（不强制上传资质文件，用户可随时保存） */
async function handleSave(silent = false) {
  saving.value = true
  try {
    await updateDdInfo(appData.value.id, {
      // 基础信息（官网注册数据，允许用户修改）
      companyName: form.companyName,
      regNumber: form.regNumber,
      country: form.country,
      establishedYear: form.establishedYear,
      registeredAddress: form.registeredAddress,
      contactName: form.contactName,
      contactTitle: form.contactTitle,
      contactEmail: form.contactEmail,
      contactPhone: form.contactPhone,
      buyCategory: form.buyCategory,
      buyVolume: form.buyVolume,
      buyVolumeAmount: form.buyVolumeAmount,
      buyVolumeCurrency: form.buyVolumeCurrency,
      buyMarket: form.buyMarket,
      supCategory: form.supCategory,
      supCapacity: form.supCapacity,
      supExport: form.supExport,
      // 尽调补充字段
      province: form.province,
      city: form.city,
      businessLicenseKey: form.businessLicenseUrl,
      attachments: form.attachments,
      website: form.website,
      description: form.description,
      employeeCount: form.employeeCount,
      annualRevenue: form.annualRevenue,
      annualRevenueAmount: form.annualRevenueAmount,
      annualRevenueCurrency: form.annualRevenueCurrency,
      fiscalPeriodStart: form.fiscalPeriodStart,
      fiscalPeriodEnd: form.fiscalPeriodEnd,
      supCerts: form.supCerts,
      mainProducts: form.mainProducts,
      managementCert: Array.isArray(form.managementCert) ? form.managementCert.join(',') : form.managementCert,
      productCert: Array.isArray(form.productCert) ? form.productCert.join(',') : form.productCert,
      keyClients: form.keyClients,
      deliveryTerms: Array.isArray(form.deliveryTerms) ? form.deliveryTerms.join(',') : form.deliveryTerms,
      leadTime: form.leadTime,
      exportMarkets: form.exportMarkets,
      industry: form.industry,
      procurementDetail: form.procurementDetail,
      supplierRequirement: form.supplierRequirement,
      targetRegion: form.targetRegion,
      paymentTerms: Array.isArray(form.paymentTerms) ? form.paymentTerms.join(',') : form.paymentTerms,
      legalPersonName: form.legalPersonName,
      legalPersonId: form.legalPersonId,
      shareholderInfoKey: form.shareholderInfo,
      financialReportKey: form.financialReportUrl,
      legalRecordText: legalRecordHas.value ? form.legalRecord : '',
      legalRecordKey: legalRecordHas.value ? legalDocUrl.value : '',
    })
    if (!silent) ElMessage({ message: '保存成功', type: 'success' })
  } catch {
    // 拦截器已处理
  } finally {
    saving.value = false
  }
}

/** 重置：官网注册信息保留，尽调新增字段全部清空 */
async function handleReset() {
  form.businessLicenseUrl = ''
  form.attachments = []
  form.shareholderInfo = ''
  form.financialReportUrl = ''
  form.legalRecord = ''
  form.legalPersonName = ''
  form.legalPersonId = ''
  form.registeredAddress = ''
  form.province = ''
  form.city = ''
  form.website = ''
  form.description = ''
  form.employeeCount = ''
  form.annualRevenue = ''
  form.annualRevenueAmount = ''
  form.annualRevenueCurrency = ''
  form.fiscalPeriodStart = ''
  form.fiscalPeriodEnd = ''
  form.buyVolumeAmount = ''
  form.buyVolumeCurrency = ''
  form.mainProducts = ''
  form.managementCert = []
  form.productCert = []
  form.keyClients = ''
  form.deliveryTerms = []
  form.leadTime = ''
  form.exportMarkets = ''
  form.industry = ''
  form.procurementDetail = ''
  form.supplierRequirement = ''
  form.targetRegion = ''
  form.paymentTerms = []
  legalRecordHas.value = false
  legalDocUrl.value = ''
  formSubmitted.value = false
  ElMessage.success('已重置')
}

/** 提交：校验全部必填 → 保存 → 去套餐支付 */
async function handleSubmitToPay() {
  // 1. 前端校验全部必填字段
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    // 滚动到第一个错误位置
    const firstErr = document.querySelector('.el-form-item.is-error')
    if (firstErr) firstErr.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  // 2. 保存表单（静默不弹窗）
  await handleSave(true)
  // 3. 如果已支付（退回补充场景），直接完成，无需跳转支付页
  if (appData.value?.paymentStatus === 'paid') {
    ElMessage.success('资料已重新提交，等待事务所核查')
    await fetchMyApplication()
    return
  }
  // 4. 未支付：标记已提交，跳转到套餐支付页
  formSubmitted.value = true
  activeTab.value = 'package'
}

/** 拦截Tab切换：已支付时不允许进入套餐页；未支付时必须通过"提交"按钮进入 */
function beforeTabLeave(activeName, oldActiveName) {
  if (activeName === 'package') {
    // 已支付：无需再次支付，拦截并提示
    if (appData.value?.paymentStatus === 'paid') {
      ElMessage.warning('您已完成支付，无需重复操作')
      return false
    }
    // 未支付：必须通过"提交"按钮校验后才能进入
    if (!formSubmitted.value) {
      ElMessage.warning('请先填写企业信息并点击「提交」')
      return false
    }
  }
  return true
}

/** 切换Tab不做自动保存 */
function handleTabChange() { }

/** 打开支付弹窗 */
function handlePay() {
  if (!selectedPackage.value) { ElMessage.warning('请先选择套餐'); return }
  payVisible.value = true
}

function stopPayStatusPoll() {
  if (payStatusTimer) {
    clearInterval(payStatusTimer)
    payStatusTimer = null
  }
}

function resetPayDialog() {
  stopPayStatusPoll()
  payResult.value = null
}

async function applyPaidIfNeeded() {
  stopPayStatusPoll()
  payVisible.value = false
  ElMessage.success('支付成功')
  await fetchMyApplication()
}

async function refreshPayStatus(showUnpaidTip = true) {
  if (!appData.value?.id) return
  if (showUnpaidTip) checkingPay.value = true
  try {
    const { data } = await getPayStatus(appData.value.id)
    if (data?.paymentStatus === 'paid') {
      await applyPaidIfNeeded()
      return
    }
    if (showUnpaidTip) {
      ElMessage.warning('还没收到付款通知，请稍后再点「我已付款」')
    }
  } finally {
    if (showUnpaidTip) checkingPay.value = false
  }
}

function startPayStatusPoll() {
  stopPayStatusPoll()
  payStatusTimer = setInterval(() => {
    refreshPayStatus(false)
  }, 2000)
}

/** 确认下单 → 展示收款码，等回调改已支付 */
async function confirmPay() {
  if (!selectedPackage.value) return
  if (payCurrency.value !== 'CNY') {
    ElMessage.warning('当前收款通道仅支持人民币（CNY）')
    return
  }
  // 套餐维度二次校验：标准/高级套餐需股权结构，高级套餐需审计报告
  // （首次支付时表单提交在前、选套餐在后，提交时这些字段尚未必填，故在支付前补校验）
  if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      ElMessage.warning('请先补全所选套餐要求的材料（股权结构/审计报告）')
      payVisible.value = false
      activeTab.value = 'form'
      return
    }
  }
  // 支付前再保存一次，确保选套餐后又新上传的材料落库
  await handleSave(true)
  paying.value = true
  try {
    const { data } = await payApplication(appData.value.id, {
      packageType: selectedPackage.value,
      paymentCurrency: payCurrency.value,
    })
    payResult.value = data
    startPayStatusPoll()
  } catch {
    // 拦截器已处理
  } finally {
    paying.value = false
  }
}

/** 下载尽调报告 PDF */
async function handleDownloadReport() {
  const url = getFileUrl(appData.value?.ddReportUrl)
  if (!url) {
    ElMessage.warning('报告地址不存在')
    return
  }
  const fileName = appData.value?.ddReportName || '尽调报告.pdf'
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error('下载失败')
    const blob = await res.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = fileName
    a.click()
    URL.revokeObjectURL(blobUrl)
  } catch {
    // fetch 失败（跨域等）降级为下载链接
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.target = '_blank'
    a.click()
  }
}

async function fetchDdCatalog() {
  try {
    const { data } = await getDdPackages()
    if (data?.prices) catalogPrices.value = data.prices
  } catch {
    // 拦截器已处理；展示回退本地价目表
  }
}

onMounted(() => {
  fetchDdCatalog()
  fetchMyApplication()
})

onUnmounted(() => {
  stopPayStatusPoll()
})
</script>

<style scoped>
.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #374151;
  margin: 28px 0 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #e5e7eb;
}

.sub-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
  margin: 20px 0 12px;
}

/* 申请身份横幅（只读） */
.role-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 20px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 10px;
}

.role-banner .role-label {
  font-size: 15px;
  font-weight: 600;
  color: #374151;
}

.role-banner .role-value {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.role-banner .role-tip {
  font-size: 13px;
  color: #6b7280;
}

.dd-form {
  font-size: 15px;
}

.dd-form :deep(.el-form-item__label) {
  font-size: 15px;
  font-weight: 500;
  color: #374151;
}

.dd-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.dd-form :deep(.el-input__inner),
.dd-form :deep(.el-textarea__inner),
.dd-form :deep(.el-select .el-input__inner) {
  font-size: 15px;
}

.dd-form :deep(.el-textarea__inner) {
  min-height: 120px !important;
}

.dd-form :deep(.el-checkbox) {
  font-size: 14px;
}

.form-footer {
  padding-top: 20px;
  border-top: 2px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.footer-left,
.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-footer :deep(.el-button) {
  font-size: 14px;
  padding: 9px 22px;
  height: 38px;
}

.save-tip {
  font-size: 14px;
  color: #059669;
  font-weight: 500;
}

/* 必填红色星号 */
.required {
  color: #ef4444;
  margin-right: 2px;
  font-size: 18px;
  vertical-align: middle;
}

/* 上传区域 - 大框视觉 */
.upload-area {
  width: 100%;
  margin-bottom: 4px;
}

.upload-area :deep(.el-upload) {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  border: 2px dashed #d1d5db;
  border-radius: 14px;
  background: #fafbfc;
  transition: all 0.3s ease;
  cursor: pointer;
}

.upload-area :deep(.el-upload-dragger:hover) {
  border-color: #3b82f6;
  background: #eff6ff;
}

.upload-area :deep(.el-upload-dragger.is-dragover) {
  border-color: #3b82f6;
  background: #dbeafe;
}

.upload-area :deep(.el-upload-dragger) .upload-icon {
  font-size: 52px;
  color: #9ca3af;
  margin-bottom: 6px;
}

.upload-area :deep(.el-upload-dragger) .upload-label {
  font-size: 17px;
  font-weight: 600;
  color: #374151;
}

.upload-area :deep(.el-upload-dragger) .upload-hint {
  font-size: 14px;
  color: #9ca3af;
}

.upload-area :deep(.el-upload-dragger) .upload-format {
  font-size: 13px;
  color: #9ca3af;
  margin-top: 6px;
  padding: 4px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  background: #fff;
}

.upload-area :deep(.el-upload-dragger) .text-green-500 {
  color: #10b981;
}

.upload-area :deep(.el-upload-dragger) .text-green-600 {
  color: #059669;
}


/* 套餐卡片 */
.package-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* 支付币种选择 */
.pay-currency-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.pay-currency-bar .label {
  font-size: 14px;
  color: #374151;
}

.pay-currency-bar .tip {
  font-size: 12px;
  color: #9ca3af;
}

.package-card {
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
}

.package-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
}

.package-card.is-selected {
  border-color: #3b82f6;
  background: #eff6ff;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.package-header {
  text-align: center;
  margin-bottom: 20px;
}

.package-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}

.package-price .price {
  font-size: 28px;
  font-weight: 800;
  color: #3b82f6;
}

.package-desc {
  font-size: 13px;
  color: #6b7280;
  margin-top: 8px;
}

.package-features {
  list-style: none;
  padding: 0;
  margin: 0 0 20px;
  flex: 1;
}

.package-features li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 14px;
  color: #4b5563;
}

.package-features li .el-icon {
  color: #10b981;
  font-size: 16px;
}

.package-footer {
  text-align: center;
}

.pay-footer {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

/* 支付弹窗 */
.pay-dialog-body .pay-info {
  background: #f9fafb;
  border-radius: 8px;
  padding: 16px;
}

.pay-info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 15px;
}

.pay-info-row .label {
  color: #6b7280;
}

.pay-info-row .value {
  color: #1f2937;
  font-weight: 500;
}

.pay-amount {
  border-top: 1px solid #e5e7eb;
  margin-top: 8px;
  padding-top: 12px;
}

.pay-qr {
  margin-top: 16px;
  text-align: center;
}

.pay-qr img {
  display: inline-block;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.pay-qr-tip {
  margin-top: 8px;
  color: #6b7280;
  font-size: 13px;
}

.pay-qr-link {
  display: inline-block;
  margin-top: 4px;
  font-size: 13px;
  color: #2563eb;
}

/* 提交区 */
.submit-area {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

/* ---- 尽调进度报告 ---- */
.report-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.report-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: #eff6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.report-icon {
  font-size: 28px;
  color: #3b82f6;
}

.report-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}

.report-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 4px 0 0;
}

.report-status-tag {
  margin-left: auto;
  flex-shrink: 0;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.report-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.report-label {
  font-size: 13px;
  color: #9ca3af;
}

.report-value {
  font-size: 15px;
  color: #1f2937;
  font-weight: 500;
}

.pkg-info-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 20px;
  background: #fafbfc;
}

.pkg-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.pkg-info-name {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.pkg-info-price {
  font-size: 14px;
  color: #3b82f6;
  font-weight: 600;
}

.pkg-info-features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pkg-info-features li {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4b5563;
  padding: 4px 12px;
  background: #f3f4f6;
  border-radius: 20px;
}

.pkg-info-features li .el-icon {
  color: #10b981;
  font-size: 14px;
}

/* 时间线 */
.report-timeline {
  padding-left: 8px;
}

.tl-step {
  display: flex;
  gap: 14px;
  padding-bottom: 28px;
  position: relative;
}

.tl-step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 34px;
  bottom: 0;
  width: 2px;
  background: #e5e7eb;
}

.tl-step.completed:not(:last-child)::before {
  background: #10b981;
}

.tl-step.active:not(:last-child)::before {
  background: #3b82f6;
}

.tl-step.returned:not(:last-child)::before {
  background: #f59e0b;
}

/* 退回：琥珀色连线 */
.tl-dot {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.tl-step.completed .tl-dot {
  background: #10b981;
}

.tl-step.active .tl-dot {
  background: #3b82f6;
}

.tl-step.returned .tl-dot {
  background: #f59e0b;
}

/* 退回：琥珀色圆点 */
.tl-step.pending .tl-dot {
  background: #d1d5db;
  color: #6b7280;
}

.tl-dot .el-icon {
  font-size: 16px;
}

.tl-content {
  padding-top: 4px;
}

.tl-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 2px;
}

.tl-step.pending .tl-title {
  color: #9ca3af;
}

.tl-step.returned .tl-title {
  color: #d97706;
}

/* 退回：琥珀色标题 */
.tl-desc {
  font-size: 13px;
  color: #6b7280;
}

.report-footer {
  margin-top: 8px;
}

.ml-2 {
  margin-left: 8px;
}

@media (max-width: 768px) {
  .package-grid {
    grid-template-columns: 1fr;
  }
}

/* ---- 按钮式上传 ---- */
.upload-btn-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.upload-tip {
  font-size: 12px;
  color: #9ca3af;
}

/* 套餐必填提示 */
.tier-tip {
  color: #ef4444;
  margin-left: 4px;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.upload-check {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.uploaded-file {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #f0fdf4;
  border-radius: 6px;
  border: 1px solid #bbf7d0;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  color: #16a34a;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* 忽略 Element Plus 默认的相邻按钮左边距 */
.uploaded-file :deep(.el-button + .el-button),
.upload-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.uploaded-file :deep(.el-button),
.upload-actions :deep(.el-button) {
  padding: 4px 8px;
}

/* 官网注册字段灰底区分 */
.reg-field :deep(.el-input__wrapper),
.reg-field :deep(.el-select__wrapper) {
  background: #f3f4f6 !important;
}

/* 营业执照上传区验证失败时边框变红 */
.is-error .upload-area :deep(.el-upload-dragger) {
  border-color: #ef4444;
  background: #fef2f2;
}

/* 营业执照 el-form-item 去掉多余的标签空间和间距 */
.biz-upload-item {
  margin-bottom: 0 !important;
}

.biz-upload-item :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

/* 事务所信息卡片（已分配事务所时展示） */
.firm-info-section {
  margin-top: 8px;
}

.firm-info-fallback {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
}

.firm-info-fallback-tip {
  color: #9ca3af;
  font-size: 13px;
}
</style>

<!-- 非 scoped：覆盖 el-descriptions 内部样式（scoped 穿透优先级不够） -->
<style>
.firm-info-desc .el-descriptions__label {
  width: 110px;
  font-weight: 600;
}

.firm-info-desc .el-descriptions__content {
  font-size: 14px;
}
</style>
