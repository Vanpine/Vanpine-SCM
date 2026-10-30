/**
 * SRM Admin - 系统常量
 */

/** localStorage 键名 */
export const TOKEN_KEY = 'srm_access_token'
export const REFRESH_TOKEN_KEY = 'srm_refresh_token'
export const USER_INFO_KEY = 'srm_user_info'

/** Token 过期时间（秒），与后端 SystemConstants 保持一致 */
export const TOKEN_EXPIRES_IN = 7200
export const REFRESH_TOKEN_EXPIRES_IN = 604800

/** 分页默认配置 */
export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

/** 请求超时（毫秒），需大于后端 RestClient readTimeout=120s */
export const REQUEST_TIMEOUT = 130_000

/** 用户状态 */
export const USER_STATUS = {
  ACTIVE: 'active',
  DISABLED: 'disabled',
  LOCKED: 'locked',
}

export const USER_STATUS_MAP = {
  active: { label: '正常', type: 'success' },
  disabled: { label: '禁用', type: 'danger' },
  locked: { label: '锁定', type: 'warning' },
}

/** 租户状态 */
export const TENANT_STATUS = {
  PENDING: 'pending',
  ACTIVE: 'active',
  SUSPENDED: 'suspended',
  REJECTED: 'rejected',
}

export const TENANT_STATUS_MAP = {
  pending: { label: '待审核', type: 'warning' },
  active: { label: '正常', type: 'success' },
  suspended: { label: '已停用', type: 'danger' },
  rejected: { label: '已拒绝', type: 'info' },
}

/** 入驻申请状态 */
export const APPLICATION_STATUS = {
  PENDING_REVIEW: 'pending_review',
  APPROVED: 'approved',
  REJECTED: 'rejected',
}

export const APPLICATION_STATUS_MAP = {
  pending_review: { label: '待审核', type: 'primary' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
}

/** 申请身份中文映射 */
export const APPLICANT_ROLE_MAP = {
  buyer: '采购商',
  supplier: '供应商',
  'buyer,supplier': '采购商+供应商',
}

/** 制裁筛查状态 */
export const SANCTION_STATUS_MAP = {
  clean: { label: '未命中', type: 'success' },
  warning: { label: '命中名单', type: 'warning' },
  pending: { label: '未筛查', type: 'info' },
}

/** 角色名 → el-tag type 映射（Dashboard / 用户列表角色标签用） */
export const ROLE_TAG_TYPE_MAP = {
  '超级管理员': 'danger',
  '采购企业管理员': 'danger',
  '供应企业管理员': 'danger',
  '企业管理员': 'danger',
  '采购员': 'primary',
  '供应商业务员': 'warning',
  '尽调企业': 'info',
  '事务所工作人员': 'info',
}

/**
 * 尽调套餐 — requiredGroups 标明该套餐需要哪些信息维度
 * prices：展示兜底，须与后端 DdPackageCatalog 一致；支付金额以后端价目表为准
 * ⚠️ 各币种价格为占位值，待运营确认后替换
 */
export const DD_PACKAGES = {
  basic: {
    label: '基础套餐',
    prices: { USD: 14900, EUR: 13900, CNY: 1 },
    desc: '企业基本信息验证',
    features: ['企业资质核验', '法人信息核实', '基础信用报告', '7个工作日内完成'],
    requiredGroups: ['license', 'coreInfo', 'contact', 'legalPerson'],
  },
  standard: {
    label: '标准套餐',
    prices: { USD: 44900, EUR: 41900, CNY: 299900 },
    desc: '深度企业背景调查',
    features: ['含基础套餐全部服务', '股权结构穿透', '企业规模与经营分析', '采购/供应业务资质审核', '5个工作日内完成'],
    requiredGroups: ['license', 'coreInfo', 'contact', 'legalPerson', 'scale', 'shareholder', 'buyerFields', 'supplierFields'],
  },
  premium: {
    label: '高级套餐',
    prices: { USD: 89900, EUR: 83900, CNY: 599900 },
    desc: '全方位尽职调查',
    features: ['含标准套餐全部服务', '财务审计报告审核', '法律合规审查', '加急3个工作日', '专属客户经理'],
    requiredGroups: ['license', 'coreInfo', 'contact', 'legalPerson', 'scale', 'shareholder', 'buyerFields', 'supplierFields', 'financial', 'legal'],
  },
}

/** 已开通支付币种兜底（接口失败时用）。默认币种按企业所在国家推断，不是列表第一项 */
export const DD_PAYMENT_CURRENCIES = Object.values(DD_PACKAGES)
  .flatMap(p => Object.keys(p.prices))
  .filter((c, i, arr) => arr.indexOf(c) === i)

/** 须与后端 DdPackageCatalog 保持一致 */
const CNY_COUNTRIES = new Set([
  'cn', 'chn', 'china', 'prc', '中国', '中华人民共和国', '中国大陆',
  'mainlandchina', 'peoplesrepublicofchina',
])
const EUR_COUNTRIES = new Set([
  'at', 'aut', 'austria', '奥地利',
  'be', 'bel', 'belgium', '比利时',
  'cy', 'cyp', 'cyprus', '塞浦路斯',
  'de', 'deu', 'germany', '德国',
  'ee', 'est', 'estonia', '爱沙尼亚',
  'es', 'esp', 'spain', '西班牙',
  'fi', 'fin', 'finland', '芬兰',
  'fr', 'fra', 'france', '法国',
  'gr', 'grc', 'greece', '希腊',
  'hr', 'hrv', 'croatia', '克罗地亚',
  'ie', 'irl', 'ireland', '爱尔兰',
  'it', 'ita', 'italy', '意大利',
  'lt', 'ltu', 'lithuania', '立陶宛',
  'lu', 'lux', 'luxembourg', '卢森堡',
  'lv', 'lva', 'latvia', '拉脱维亚',
  'mt', 'mlt', 'malta', '马耳他',
  'nl', 'nld', 'netherlands', '荷兰',
  'pt', 'prt', 'portugal', '葡萄牙',
  'si', 'svn', 'slovenia', '斯洛文尼亚',
  'sk', 'svk', 'slovakia', '斯洛伐克',
  'eurozone', '欧元区',
])

function normalizeCountry(country) {
  return String(country || '').trim().toLowerCase().replace(/[^\u4e00-\u9fffa-z0-9]/g, '')
}

/**
 * 按企业所在国家推断默认支付币种：中国大陆 CNY，欧元区 EUR，其余（含港澳台）USD
 * @param {string} country
 * @returns {'USD'|'EUR'|'CNY'}
 */
export function defaultPaymentCurrency(country) {
  const key = normalizeCountry(country)
  if (!key) return 'USD'
  // 港澳台优先：避免被「中国」子串误判成 CNY
  if (/香港|hongkong|台湾|taiwan|澳门|macau|macao/.test(key)) return 'USD'
  if (CNY_COUNTRIES.has(key) || key.includes('中国') || key.includes('中國') || key.includes('china')) return 'CNY'
  if (EUR_COUNTRIES.has(key) || key.includes('欧元区')) return 'EUR'
  return 'USD'
}

/** 表单字段 → 套餐信息维度映射（用于判断字段属于哪个套餐维度） */
export const FIELD_GROUP_MAP = {
  // 资质文件
  businessLicenseUrl: 'license',
  // 企业核心信息
  companyName: 'coreInfo',
  regNumber: 'coreInfo',
  country: 'coreInfo',
  establishedYear: 'coreInfo',
  province: 'coreInfo',
  city: 'coreInfo',
  // 联系信息
  contactName: 'contact',
  contactEmail: 'contact',
  contactPhone: 'contact',
  // 法人信息
  legalPersonName: 'legalPerson',
  legalPersonId: 'legalPerson',
  // 股权结构
  shareholderInfo: 'shareholder',
  // 企业规模
  employeeCount: 'scale',
  annualRevenue: 'scale',
  // 采购方业务
  buyCategory: 'buyerFields',
  buyVolume: 'buyerFields',
  buyMarket: 'buyerFields',
  industry: 'buyerFields',
  procurementDetail: 'buyerFields',
  supplierRequirement: 'buyerFields',
  // 供应方业务
  supCategory: 'supplierFields',
  supCapacity: 'supplierFields',
  supCerts: 'supplierFields',
  supExport: 'supplierFields',
  mainProducts: 'supplierFields',
  keyClients: 'supplierFields',
  // 审计报告
  financialReportUrl: 'financial',
  // 诉讼记录
  legalRecord: 'legal',
}

/** 字段中文提示 */
export const FIELD_MESSAGES = {
  businessLicenseUrl: '请上传营业执照',
  companyName: '请输入企业名称',
  regNumber: '请输入企业注册号',
  country: '请输入国家/地区',
  establishedYear: '请输入成立年份',
  province: '请输入所在省份',
  city: '请输入所在城市',
  contactName: '请输入联系人',
  contactEmail: '请输入邮箱',
  contactPhone: '请输入手机',
  legalPersonName: '请输入法人姓名',
  legalPersonId: '请输入法人身份证号',
  shareholderInfo: '请上传股权结构文件',
  employeeCount: '请选择员工人数',
  annualRevenue: '请选择年营业额',
  buyCategory: '请输入采购品类',
  buyVolume: '请选择年采购额',
  buyMarket: '请输入采购产品明细',
  industry: '请输入所属行业',
  procurementDetail: '请输入采购产品明细',
  supplierRequirement: '请输入供应商资质要求',
  supCategory: '请输入生产品类',
  supCapacity: '请输入年产能',
  supCerts: '请输入资质认证',
  supExport: '请选择出口经验',
  mainProducts: '请输入主要产品/服务',
  keyClients: '请输入主要客户/行业',
  financialReportUrl: '请上传审计报告',
  legalRecord: '请填写诉讼记录',
}

/** 员工人数选项 */
export const EMPLOYEE_COUNT_OPTIONS = [
  { value: '1', label: '1-50人' },
  { value: '2', label: '51-200人' },
  { value: '3', label: '201-500人' },
  { value: '4', label: '501-1000人' },
  { value: '5', label: '1000人以上' },
]

/** 年营业额选项 */
export const ANNUAL_REVENUE_OPTIONS = [
  { value: '1', label: '1000万以下' },
  { value: '2', label: '1000-5000万' },
  { value: '3', label: '5000万-1亿' },
  { value: '4', label: '1-5亿' },
  { value: '5', label: '5亿以上' },
]

/** 常用 ISO 4217 币种；选择器允许输入列表外的合法 ISO 编码 */
export const CURRENCY_OPTIONS = [
  { value: 'USD', label: 'USD - US Dollar' },
  { value: 'CNY', label: 'CNY - Chinese Yuan' },
  { value: 'EUR', label: 'EUR - Euro' },
  { value: 'GBP', label: 'GBP - British Pound' },
  { value: 'JPY', label: 'JPY - Japanese Yen' },
  { value: 'KRW', label: 'KRW - South Korean Won' },
  { value: 'HKD', label: 'HKD - Hong Kong Dollar' },
  { value: 'TWD', label: 'TWD - New Taiwan Dollar' },
  { value: 'SGD', label: 'SGD - Singapore Dollar' },
  { value: 'AUD', label: 'AUD - Australian Dollar' },
  { value: 'CAD', label: 'CAD - Canadian Dollar' },
  { value: 'CHF', label: 'CHF - Swiss Franc' },
  { value: 'INR', label: 'INR - Indian Rupee' },
  { value: 'IDR', label: 'IDR - Indonesian Rupiah' },
  { value: 'MYR', label: 'MYR - Malaysian Ringgit' },
  { value: 'THB', label: 'THB - Thai Baht' },
  { value: 'PHP', label: 'PHP - Philippine Peso' },
  { value: 'VND', label: 'VND - Vietnamese Dong' },
  { value: 'AED', label: 'AED - UAE Dirham' },
  { value: 'SAR', label: 'SAR - Saudi Riyal' },
  { value: 'BRL', label: 'BRL - Brazilian Real' },
  { value: 'MXN', label: 'MXN - Mexican Peso' },
  { value: 'ZAR', label: 'ZAR - South African Rand' },
  { value: 'TRY', label: 'TRY - Turkish Lira' },
  { value: 'SEK', label: 'SEK - Swedish Krona' },
  { value: 'NOK', label: 'NOK - Norwegian Krone' },
  { value: 'DKK', label: 'DKK - Danish Krone' },
  { value: 'PLN', label: 'PLN - Polish Zloty' },
  { value: 'NZD', label: 'NZD - New Zealand Dollar' },
  { value: 'RUB', label: 'RUB - Russian Ruble' },
]

/** 管理体系认证选项 */
export const MANAGEMENT_CERT_OPTIONS = [
  { value: 'ISO9001', label: 'ISO9001 质量管理体系' },
  { value: 'ISO14001', label: 'ISO14001 环境管理体系' },
  { value: 'ISO45001', label: 'ISO45001 职业健康安全' },
  { value: 'IATF16949', label: 'IATF16949 汽车行业' },
  { value: 'ISO13485', label: 'ISO13485 医疗器械' },
  { value: 'ISO22000', label: 'ISO22000 食品安全' },
  { value: 'SA8000', label: 'SA8000 社会责任' },
  { value: 'other', label: '其他认证' },
]

/** 产品认证选项 */
export const PRODUCT_CERT_OPTIONS = [
  { value: 'CE', label: 'CE（欧盟）' },
  { value: 'FCC', label: 'FCC（美国）' },
  { value: 'RoHS', label: 'RoHS（环保）' },
  { value: 'UL', label: 'UL（美国安全）' },
  { value: 'CCC', label: 'CCC（中国）' },
  { value: 'REACH', label: 'REACH（欧盟化学品）' },
  { value: 'FDA', label: 'FDA（美国食品药物）' },
  { value: 'other', label: '其他认证' },
]

/** 交货方式选项 */
export const DELIVERY_TERMS_OPTIONS = [
  { value: 'FOB', label: 'FOB（装运港船上交货）' },
  { value: 'CIF', label: 'CIF（成本+保险费+运费）' },
  { value: 'EXW', label: 'EXW（工厂交货）' },
  { value: 'DDP', label: 'DDP（完税后交货）' },
  { value: 'CFR', label: 'CFR（成本+运费）' },
  { value: 'other', label: '其他' },
]

/** 交货周期选项 */
export const LEAD_TIME_OPTIONS = [
  { value: '1', label: '7天内' },
  { value: '2', label: '7-15天' },
  { value: '3', label: '15-30天' },
  { value: '4', label: '30-60天' },
  { value: '5', label: '60天以上' },
]

/** 付款方式选项 */
export const PAYMENT_TERMS_OPTIONS = [
  { value: 'T/T', label: 'T/T（电汇）' },
  { value: 'L/C', label: 'L/C（信用证）' },
  { value: 'D/P', label: 'D/P（付款交单）' },
  { value: 'D/A', label: 'D/A（承兑交单）' },
  { value: 'other', label: '其他' },
]

/** 出口经验选项 */
export const SUP_EXPORT_OPTIONS = [
  { value: '0', label: '无出口经验' },
  { value: '1', label: '3年以下' },
  { value: '2', label: '3-5年' },
  { value: '3', label: '5-10年' },
  { value: '4', label: '10年以上' },
]

/** 年采购额选项（注意：与 ANNUAL_REVENUE_OPTIONS 不同，采购额是企业采购支出，营业额是企业营收） */
export const BUY_VOLUME_OPTIONS = [
  { value: '1', label: '100万以下' },
  { value: '2', label: '100-500万' },
  { value: '3', label: '500-2000万' },
  { value: '4', label: '2000万-1亿' },
  { value: '5', label: '1亿以上' },
]

/** 通用：从选项数组中查 label */
export function optionLabel(options, val) {
  if (!val && val !== 0) return '-'
  return options.find(o => o.value === String(val))?.label || val || '-'
}

/** 多币种金额展示：不改币种，不换汇，按当前语言格式化 */
export function currencyAmountLabel(amount, currency, locale) {
  if (!amount || !currency) return ''
  try {
    return new Intl.NumberFormat(locale || navigator.language, {
      style: 'currency',
      currency,
    }).format(Number(amount))
  } catch {
    return `${currency} ${amount}`
  }
}

/**
 * ISO 4217 货币小数位（USD=2、JPY=0、KWD=3），由 Intl 推导，避免硬编码
 */
export function currencyExponent(currency) {
  try {
    const opts = new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
    return opts.maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

/** 最小货币单位金额（如 14900）→ 带币种符号的展示文案（如 $149.00） */
export function minorAmountLabel(amount, currency, locale) {
  if (amount === null || amount === undefined || amount === '' || !currency) return ''
  return currencyAmountLabel(Number(amount) / Math.pow(10, currencyExponent(currency)), currency, locale)
}

/**
 * 套餐在指定币种下的定价（最小货币单位），未配置返回 null
 * @param {string} packageType
 * @param {string} currency
 * @param {Record<string, Record<string, number>>} [priceTable] 后端价目表，优先于本地兜底
 */
export function packagePrice(packageType, currency, priceTable) {
  if (priceTable?.[packageType]?.[currency] != null) {
    return priceTable[packageType][currency]
  }
  return DD_PACKAGES[packageType]?.prices?.[currency] ?? null
}

/** 套餐在指定币种下的价格文案，未配置返回 '' */
export function packagePriceLabel(packageType, currency, locale, priceTable) {
  return minorAmountLabel(packagePrice(packageType, currency, priceTable), currency, locale)
}

/** 出口经验码值 → 中文 */
export function supExportLabel(val) {
  return optionLabel(SUP_EXPORT_OPTIONS, val)
}

/** 年采购额码值 → 中文 */
export function buyVolumeLabel(val) {
  return optionLabel(BUY_VOLUME_OPTIONS, val)
}

/** 交货周期码值 → 中文 */
export function leadTimeLabel(val) {
  return optionLabel(LEAD_TIME_OPTIONS, val)
}

/** 年营业额码值 → 中文 */
export function annualRevenueLabel(val) {
  return optionLabel(ANNUAL_REVENUE_OPTIONS, val)
}

/** 员工人数码值 → 中文 */
export function employeeCountLabel(val) {
  return optionLabel(EMPLOYEE_COUNT_OPTIONS, val)
}
