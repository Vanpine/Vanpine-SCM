/**
 * SRM Admin - 制裁筛查展示层翻译映射
 * 仅用于前端展示（给人看），名称/别名保持原文不做翻译（匹配铁律）
 */

/** 国别 ISO 3166-1 alpha-2 码 → 中文（覆盖制裁名单高频国家，未命中回退原文） */
export const COUNTRY_CN_MAP = {
  us: '美国', ru: '俄罗斯', ua: '乌克兰', cn: '中国', ir: '伊朗', kp: '朝鲜',
  sy: '叙利亚', cu: '古巴', vn: '越南', mm: '缅甸', by: '白俄罗斯', ve: '委内瑞拉',
  sd: '苏丹', zw: '津巴布韦', ye: '也门', so: '索马里', iq: '伊拉克', ly: '利比亚',
  af: '阿富汗', su: '苏丹', ss: '南苏丹', et: '埃塞俄比亚', er: '厄立特里亚',
  ml: '马里', ne: '尼日尔', bf: '布基纳法索', cd: '刚果（金）', cg: '刚果（布）',
  lr: '利比里亚', sl: '塞拉利昂', ci: '科特迪瓦', gn: '几内亚', gw: '几内亚比绍',
  cf: '中非', td: '乍得', cm: '喀麦隆', ng: '尼日利亚', gh: '加纳', ke: '肯尼亚',
  tz: '坦桑尼亚', ug: '乌干达', rw: '卢旺达', bi: '布隆迪', za: '南非', zw2: '津巴布韦',
  eg: '埃及', ma: '摩洛哥', dz: '阿尔及利亚', tn: '突尼斯', lb: '黎巴嫩', jo: '约旦',
  sa: '沙特阿拉伯', ae: '阿联酋', qa: '卡塔尔', kw: '科威特', om: '阿曼', bh: '巴林',
  il: '以色列', ps: '巴勒斯坦', tr: '土耳其', pk: '巴基斯坦', in: '印度', bd: '孟加拉国',
  lk: '斯里兰卡', np: '尼泊尔', th: '泰国', my: '马来西亚', sg: '新加坡', id: '印度尼西亚',
  ph: '菲律宾', kh: '柬埔寨', la: '老挝', mn: '蒙古', kz: '哈萨克斯坦', uz: '乌兹别克斯坦',
  kg: '吉尔吉斯斯坦', tj: '塔吉克斯坦', tm: '土库曼斯坦', az: '阿塞拜疆', ge: '格鲁吉亚',
  am: '亚美尼亚', md: '摩尔多瓦', rs: '塞尔维亚', ba: '波黑', me: '黑山', mk: '北马其顿',
  al: '阿尔巴尼亚', xk: '科索沃', gr: '希腊', bg: '保加利亚', ro: '罗马尼亚', hu: '匈牙利',
  pl: '波兰', cz: '捷克', sk: '斯洛伐克', at: '奥地利', de: '德国', fr: '法国', it: '意大利',
  es: '西班牙', pt: '葡萄牙', nl: '荷兰', be: '比利时', lu: '卢森堡', ie: '爱尔兰',
  gb: '英国', ch: '瑞士', se: '瑞典', no: '挪威', fi: '芬兰', dk: '丹麦', is: '冰岛',
  ca: '加拿大', mx: '墨西哥', br: '巴西', ar: '阿根廷', cl: '智利', co: '哥伦比亚',
  pe: '秘鲁', ec: '厄瓜多尔', bo: '玻利维亚', py: '巴拉圭', uy: '乌拉圭', au: '澳大利亚',
  nz: '新西兰', jp: '日本', kr: '韩国', tw: '台湾', hk: '香港', mo: '澳门'
}

/** 名单来源名 → 中文（分号分隔，逐段翻译，未命中回退原文） */
export const SOURCE_CN_MAP = {
  'US OFAC Specially Designated Nationals (SDN) List': '美国 OFAC 特别指定国民名单',
  'US Trade Consolidated Screening List (CSL)': '美国贸易综合筛查名单',
  'US Non-SDN Menu-Based Sanctions List': '美国非 SDN 菜单式制裁名单',
  'EU Council Official Journal Sanctioned Entities': '欧盟理事会官方公报制裁实体',
  'EU Sanctions Map': '欧盟制裁地图',
  'UN Security Council Consolidated List': '联合国安理会综合名单',
  'UK FCDO Sanctions List': '英国 FCDO 制裁名单',
  'UK Office of Financial Sanctions Implementation': '英国金融制裁执行办公室',
  'Ukraine NSDC State Register of Sanctions': '乌克兰国安与国防委员会制裁登记册',
  'Swiss SECO Sanctions List': '瑞士 SECO 制裁名单',
  'Australian Sanctions Consolidated List': '澳大利亚综合制裁名单',
  'Canadian Consolidated Autonomous Sanctions List': '加拿大综合自主制裁名单',
  'Japan METI End User List': '日本经济产业省最终用户名单',
  'OpenSanctions Penalties': 'OpenSanctions 处罚名单'
}

/** 国别码翻译（支持逗号/分号分隔多值，如 "ru,mm" → "俄罗斯、缅甸"） */
export function countryCn(jurisdiction) {
  if (!jurisdiction) return '-'
  return String(jurisdiction)
    .split(/[,;]/)
    .map((c) => COUNTRY_CN_MAP[c.trim().toLowerCase()] || c.trim())
    .filter(Boolean)
    .join('、')
}

/** 来源名翻译（分号分隔多值，如 "OFAC;CSL" → "OFAC、CSL"） */
export function sourceCn(sourceName) {
  if (!sourceName) return '-'
  return String(sourceName)
    .split(/[;]/)
    .map((s) => SOURCE_CN_MAP[s.trim()] || s.trim())
    .filter(Boolean)
    .join('、')
}
