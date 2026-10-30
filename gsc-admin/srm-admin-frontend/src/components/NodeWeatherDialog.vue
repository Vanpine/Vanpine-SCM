<template>
  <el-dialog
    v-model="visible"
    title="当地天气"
    :close-on-click-modal="false"
    class="weather-dialog"
    width="560px"
  >
    <div v-loading="loading" class="weather-body">
      <template v-if="now || days.length">
        <div class="weather-place">
          <div class="weather-place-name">{{ city }}</div>
          <el-tag v-if="placeHint" size="small" type="info" effect="plain">{{ placeHint }}</el-tag>
        </div>

        <div v-if="now" class="weather-hero" :class="heroTone">
          <div class="weather-hero-main">
            <div class="weather-temp">{{ formatNum(now.temperature) }}<span class="weather-temp-unit">℃</span></div>
            <div class="weather-cond">{{ now.weatherText || '—' }}</div>
            <div class="weather-obs">观测 {{ formatObserve(now.time) }}</div>
          </div>
          <div class="weather-metrics">
            <div class="weather-metric">
              <span class="weather-metric-label">体感</span>
              <span class="weather-metric-value">{{ formatNum(now.apparentTemperature) }}℃</span>
            </div>
            <div class="weather-metric">
              <span class="weather-metric-label">湿度</span>
              <span class="weather-metric-value">{{ now.humidity ?? '—' }}%</span>
            </div>
            <div class="weather-metric" :class="{ 'is-alert': windAlert }">
              <span class="weather-metric-label">风力</span>
              <span class="weather-metric-value">{{ windText }}</span>
            </div>
            <div class="weather-metric" :class="{ 'is-alert': rainAlert }">
              <span class="weather-metric-label">降水</span>
              <span class="weather-metric-value">{{ formatNum(now.precipitation) }} mm</span>
            </div>
          </div>
        </div>

        <div v-if="days.length" class="weather-forecast">
          <div class="weather-forecast-title">近 3 日预报</div>
          <div class="weather-days">
            <div
              v-for="row in days"
              :key="row.date"
              class="weather-day"
              :class="{ 'is-alert': dayAlert(row) }"
            >
              <div class="weather-day-date">{{ dayLabel(row.date) }}</div>
              <div class="weather-day-cond">{{ row.weatherText || '—' }}</div>
              <div class="weather-day-temp">{{ formatNum(row.tempMin) }} ~ {{ formatNum(row.tempMax) }}℃</div>
              <div class="weather-day-meta">降水 {{ formatNum(row.precipitationSum) }}mm</div>
              <div class="weather-day-meta">风力 {{ row.windGradeMax != null ? row.windGradeMax + '级' : '—' }}</div>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" :description="emptyText" :image-size="60" />
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getWeatherNow, getWeatherForecast } from '@/api/delivery'
import { formatDateTime } from '@/utils/datetime'

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  city: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const now = ref(null)
const days = ref([])
const emptyText = ref('暂无天气数据')

const placeHint = computed(() => {
  const resolved = now.value?.resolvedPlace
  if (!resolved || !props.city || props.city === resolved) return ''
  return `按${resolved}查询`
})

const windText = computed(() => {
  const n = now.value
  if (!n) return '—'
  const dir = n.windDirectionText || ''
  const grade = n.windGrade != null ? `${n.windGrade}级` : ''
  return `${dir}${grade}` || '—'
})

const windAlert = computed(() => now.value?.windGrade != null && now.value.windGrade >= 8)
const rainAlert = computed(() => now.value?.precipitation != null && now.value.precipitation >= 8)

const heroTone = computed(() => {
  if (windAlert.value || rainAlert.value) return 'is-alert'
  const text = now.value?.weatherText || ''
  if (/晴/.test(text)) return 'is-clear'
  if (/雨|雪|雷/.test(text)) return 'is-wet'
  return 'is-cloud'
})

watch(
  () => [visible.value, props.city],
  ([open]) => {
    if (open) load()
  }
)

async function load() {
  now.value = null
  days.value = []
  if (!props.city) {
    emptyText.value = '该节点没有地点，无法查询天气'
    return
  }
  loading.value = true
  emptyText.value = '暂无天气数据'
  try {
    const [nowRes, forecastRes] = await Promise.allSettled([
      getWeatherNow(props.city),
      getWeatherForecast(props.city, 3)
    ])
    now.value = nowRes.status === 'fulfilled' ? nowRes.value?.data : null
    days.value = forecastRes.status === 'fulfilled' ? (forecastRes.value?.data?.daily || []) : []
    if (!now.value && !days.value.length) {
      emptyText.value = '天气数据源暂时不可用，或未解析到该地名'
    }
  } catch {
    now.value = null
    emptyText.value = '未解析到该地名，请使用规范城市名'
  } finally {
    loading.value = false
  }
}

function formatNum(v) {
  return v == null || v === '' ? '—' : v
}

function formatObserve(t) {
  const formatted = formatDateTime(t)
  return formatted === '无' ? '—' : formatted.slice(0, 16)
}

function dayLabel(date) {
  if (!date) return '—'
  const ymd = String(date).slice(0, 10)
  const today = new Date()
  const p = (n) => String(n).padStart(2, '0')
  const todayStr = `${today.getFullYear()}-${p(today.getMonth() + 1)}-${p(today.getDate())}`
  const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1)
  const tomorrowStr = `${tomorrow.getFullYear()}-${p(tomorrow.getMonth() + 1)}-${p(tomorrow.getDate())}`
  if (ymd === todayStr) return '今天'
  if (ymd === tomorrowStr) return '明天'
  const [y, m, d] = ymd.split('-').map(Number)
  if (!y || !m || !d) return ymd
  const weekday = new Date(y, m - 1, d).getDay()
  return `周${WEEKDAYS[weekday]} ${m}/${d}`
}

function dayAlert(row) {
  const wind = row.windGradeMax != null && row.windGradeMax >= 8
  const rain = row.precipitationSum != null && row.precipitationSum >= 20
  return wind || rain
}
</script>

<style>
.weather-dialog { width: auto !important; max-width: 580px; }
.weather-dialog .el-dialog__body { padding: 12px 20px 8px; }
.weather-body { min-height: 120px; }
.weather-place {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.weather-place-name { font-size: 16px; font-weight: 600; color: #303133; }
.weather-hero {
  display: flex;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 8px;
  background: #f5f7fa;
}
.weather-hero.is-clear { background: #ecf5ff; }
.weather-hero.is-wet { background: #f4f4f5; }
.weather-hero.is-alert { background: #fef0f0; }
.weather-hero-main { min-width: 140px; }
.weather-temp { font-size: 40px; font-weight: 700; line-height: 1; color: #303133; letter-spacing: -1px; }
.weather-hero.is-alert .weather-temp { color: #f56c6c; }
.weather-temp-unit { font-size: 16px; font-weight: 600; margin-left: 2px; }
.weather-cond { margin-top: 8px; font-size: 15px; font-weight: 600; color: #303133; }
.weather-obs { margin-top: 6px; font-size: 12px; color: #909399; }
.weather-metrics {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
}
.weather-metric {
  padding: 8px 10px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.weather-metric.is-alert { border-color: #fbc4c4; background: #fff6f6; }
.weather-metric-label { display: block; font-size: 12px; color: #909399; margin-bottom: 2px; }
.weather-metric-value { font-size: 14px; font-weight: 600; color: #303133; }
.weather-metric.is-alert .weather-metric-value { color: #f56c6c; }
.weather-forecast { margin-top: 16px; }
.weather-forecast-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.weather-days { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.weather-day {
  padding: 10px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  text-align: center;
}
.weather-day.is-alert { border-color: #fbc4c4; background: #fef0f0; }
.weather-day-date { font-size: 12px; color: #909399; margin-bottom: 4px; }
.weather-day-cond { font-size: 14px; font-weight: 600; color: #303133; }
.weather-day-temp { margin-top: 6px; font-size: 13px; color: #606266; }
.weather-day-meta { margin-top: 2px; font-size: 12px; color: #909399; }
</style>
