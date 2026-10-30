<script setup>
/**
 * RiskFeed.vue — 地震快讯面板（右侧列表卡片）
 *
 * 从父组件 RiskDashboard 注入 USGS 地震数据
 * 取最新 7 条，展示为带震级徽章、地名、时间和跳转链接的列表
 */

import { computed, inject, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const quakePoints = inject('quakePoints', ref([]))
const liveOK      = inject('liveOK', ref(false))
const lastUpdated = inject('lastUpdated', ref(null))

const { t } = useI18n()
const top7 = computed(() => quakePoints.value.slice(0, 7))

function minutesAgo(ms) {
  const d = Math.max(0, Date.now() - ms)
  const m = Math.round(d / 60000)
  if (m < 1) return t('riskFeed.justNow')
  if (m < 60) return m + t('riskFeed.minutesAgo')
  return Math.round(m / 60) + t('riskFeed.hoursAgo')
}

function magColor(m) {
  if (m >= 6) return '#ff3b30'
  if (m >= 4.5) return '#ff9500'
  return '#34c759'
}

function magBg(m) {
  if (m >= 6) return 'rgba(255,59,48,0.15)'
  if (m >= 4.5) return 'rgba(255,149,0,0.12)'
  return 'rgba(52,199,89,0.10)'
}
</script>

<template>
  <aside class="feed-panel">
    <div class="feed-header">
      <div class="feed-header-row">
        <span class="feed-pulse" :class="{ live: liveOK }"></span>
        <h4>{{ $t('riskFeed.usgsTitle') }}</h4>
      </div>
      <span class="feed-updated" v-if="lastUpdated">{{ $t('riskFeed.updatedAt') }}{{ minutesAgo(lastUpdated) }}</span>
    </div>

    <div class="feed-status" v-if="!liveOK">⚠ {{ $t('riskFeed.errorStatus') }}</div>

    <ul class="feed-list">
      <li v-for="(q, i) in top7" :key="'q' + i" class="feed-item">
        <a :href="q.url" target="_blank" rel="noopener noreferrer">
          <span class="mag-badge" :style="{ color: magColor(q.mag), background: magBg(q.mag) }">
            M{{ q.mag.toFixed(1) }}
          </span>
          <span class="feed-info">
            <span class="feed-place">{{ q.place || 'Unknown' }}</span>
            <span class="feed-meta">{{ minutesAgo(q.time) }}</span>
          </span>
          <span class="feed-arrow">&rarr;</span>
        </a>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.feed-panel {
  background: rgba(255,255,255,0.035);
  border: 1px solid rgba(108,178,255,0.13);
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.feed-header {
  padding: 20px 20px 14px;
  border-bottom: 1px solid rgba(108,178,255,0.10);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.feed-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.feed-header h4 {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.01em;
  margin: 0;
}

.feed-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff9500;
  flex-shrink: 0;
}
.feed-pulse.live {
  background: #34c759;
  box-shadow: 0 0 6px rgba(52,199,89,0.7);
  animation: pulse-dot 2s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 4px rgba(52,199,89,0.5); }
  50%      { box-shadow: 0 0 12px rgba(52,199,89,0.9); }
}

.feed-updated {
  font-size: 11px;
  color: #6b7280;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.feed-status {
  margin: 12px 16px 0;
  padding: 9px 12px;
  border-radius: 10px;
  background: rgba(255,149,0,0.08);
  border: 1px solid rgba(255,149,0,0.18);
  font-size: 12px;
  color: #ffae42;
  text-align: center;
}

.feed-list {
  list-style: none;
  padding: 0;
  margin: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-evenly;
}

.feed-item a {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  text-decoration: none;
  color: inherit;
  transition: background 0.2s;
  border-radius: 12px;
  margin: 0 8px;
}
.feed-item a:hover {
  background: rgba(108,178,255,0.06);
}

.mag-badge {
  flex-shrink: 0;
  width: 54px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
  transition: transform 0.2s;
}
.feed-item a:hover .mag-badge {
  transform: scale(1.06);
}

.feed-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.feed-place {
  font-size: 13px;
  font-weight: 500;
  color: #e5e7eb;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.feed-meta {
  font-size: 11px;
  color: #6b7280;
}

.feed-arrow {
  flex-shrink: 0;
  font-size: 14px;
  color: #4b5563;
  transition: all 0.2s;
}
.feed-item a:hover .feed-arrow {
  color: #6cb2ff;
  transform: translateX(2px);
}
</style>
