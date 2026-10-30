<template>
  <div class="bg-[#F5F5F7] min-h-screen text-slate-900 selection:bg-blue-200">

    <!-- ===== Hero ===== -->
    <section class="relative h-screen min-h-[800px] w-full flex items-center justify-center overflow-hidden">
      <div class="absolute inset-0 z-0">
        <img src="https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=2500&q=80"
          alt="Global Network Background" class="w-full h-full object-cover object-center hero-bg-zoom" />
        <div class="absolute inset-0 bg-gradient-to-b from-[#0a0d16]/60 via-[#0a0d16]/20 to-[#0a0d16]/70"></div>
      </div>

      <div class="relative z-10 max-w-6xl mx-auto px-6 text-center -mt-20">
        <h1 v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 100, ease: [0.16, 1, 0.3, 1] } }"
          class="text-6xl md:text-8xl font-semibold tracking-tight text-white mb-6 text-cn">
          {{ $t('intel.heroTitle') }}
        </h1>
        <p v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 200, ease: [0.16, 1, 0.3, 1] } }"
          class="text-xl md:text-3xl text-white/90 font-light mt-12 mb-10 tracking-wide max-w-3xl mx-auto text-cn">
          {{ $t('intel.heroSub') }}
        </p>
      </div>
    </section>

    <!-- ===== News Groups ===== -->
    <section
      class="relative z-20 bg-[#F5F5F7] rounded-t-[3rem] -mt-16 pt-24 pb-24 px-6 lg:px-8 w-full max-w-[85%] mx-auto">
      <div v-for="group in NEWS_GROUPS" :key="group.categoryKey" v-motion :initial="{ opacity: 0, y: 40 }"
        :visible="{ opacity: 1, y: 0, transition: { duration: 600, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true"
        class="mb-20 last:mb-0">
        <h3
          class="flex items-center gap-3 text-card-title font-semibold text-slate-900 mb-8 pb-4 border-b border-slate-200 text-cn">
          <component :is="group.icon" class="w-6 h-6 text-blue-600" />
          {{ $t(group.categoryKey) }}
        </h3>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <article v-for="(item, idx) in group.items" :key="idx"
            class="group bg-white rounded-[2rem] overflow-hidden shadow-[0_8px_30px_rgba(0,0,0,0.04)] hover:shadow-xl transition-all duration-300 cursor-pointer flex flex-col"
            @click="item.href ? $router.push(item.href) : null">
            <div class="h-52 overflow-hidden relative">
              <ImageWithFallback :src="item.img" :alt="$t(item.titleKey)"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
                loading="lazy" />
              <span
                :class="['absolute top-4 left-4 px-3 py-1.5 rounded-full text-xs font-semibold backdrop-blur-md', item.catStyle]">
                {{ $t(group.categoryKey) }}
              </span>
            </div>

            <div class="p-6 flex-1 flex flex-col">
              <h4
                class="text-card-title font-semibold text-slate-900 mb-3 group-hover:text-blue-600 transition-colors leading-snug text-cn">
                {{ $t(item.titleKey) }}
              </h4>
              <p class="text-content text-slate-500 leading-relaxed mb-5 flex-1 text-cn">
                {{ $t(item.summaryKey) }}
              </p>
              <div class="flex items-center justify-between text-xs text-slate-400 pt-4 border-t border-slate-100">
                <span>{{ item.meta }}</span>
                <span class="text-blue-600 font-medium group-hover:translate-x-1 transition-transform duration-300">{{
                  $t('intel.readMore') }}
                  &rarr;</span>
              </div>
            </div>
          </article>
        </div>
      </div>

      <p class="text-center text-sm text-slate-400 mt-8">
        {{ $t('intel.newsDisclaimer') }}
      </p>
    </section>

    <!-- ===== Leaflet Map ===== -->
    <section class="py-16 px-6 lg:px-8 w-[85%] mx-auto">
      <div class="text-center mb-12">
        <div class="text-sm font-bold tracking-widest text-slate-400 mb-4 uppercase">{{ $t('intel.mapLabel') }}</div>
        <h2 class="text-title font-semibold text-slate-900 mb-4 text-cn">{{ $t('intel.mapTitle') }}</h2>
        <p class="text-content text-slate-500 font-light max-w-2xl mx-auto text-cn">
          {{ $t('intel.mapDesc') }}
        </p>
      </div>

      <div
        class="leaflet-wrap relative rounded-[2.5rem] overflow-hidden shadow-[0_20px_60px_rgba(0,0,0,0.1)] border border-slate-200">
        <div id="riskLeaflet" class="w-full h-[500px] md:h-[600px]" />
        <button class="map-theme-btn" @click="toggleMapTheme"
          :title="darkMode ? $t('intel.themeLight') : $t('intel.themeDark')">{{ darkMode ? '☀️'
            : '🌙' }}</button>
        <div
          class="absolute bottom-6 left-6 flex gap-4 text-xs font-medium bg-white/90 backdrop-blur-md px-4 py-2 rounded-full border border-slate-200 shadow-sm z-[1000]">
          <span class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-red-500"></span> {{
            $t('intel.riskHigh') }}</span>
          <span class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-amber-500"></span> {{
            $t('intel.riskWatch') }}</span>
          <span class="flex items-center gap-2"><span class="w-2 h-2 rounded-full bg-emerald-500"></span> {{
            $t('intel.riskNormal') }}</span>
        </div>
      </div>
      <p class="text-center text-sm text-slate-400 mt-6">
        {{ $t('intel.mapDisclaimer') }}
      </p>
    </section>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, markRaw } from 'vue'
import { useI18n } from 'vue-i18n'
import { Globe, FileText, Truck, Zap, Ship } from 'lucide-vue-next'
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const { t } = useI18n()

// ── News Data ──────────────────────────────────────────

const NEWS_GROUPS = [
  {
    categoryKey: 'intel.categoryGeo',
    icon: markRaw(Globe),
    items: [
      {
        titleKey: 'intel.news1Title',
        summaryKey: 'intel.news1Summary',
        meta: 'Modern Diplomacy · 5 小时前',
        img: 'https://images.unsplash.com/photo-1494412651409-8963ce7935a7?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-red-500/90 text-white',
      },
      {
        titleKey: 'intel.news2Title',
        summaryKey: 'intel.news2Summary',
        meta: 'Kyiv Independent · 8 小时前',
        img: 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-red-500/90 text-white',
      },
      {
        titleKey: 'intel.news3Title',
        summaryKey: 'intel.news3Summary',
        meta: 'Insurance Business · 今日',
        img: 'https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-red-500/90 text-white',
      },
    ],
  },
  {
    categoryKey: 'intel.categoryLogistics',
    icon: markRaw(Truck),
    items: [
      {
        titleKey: 'intel.news4Title',
        summaryKey: 'intel.news4Summary',
        meta: 'Reuters · 海运 · 今日',
        img: 'https://images.unsplash.com/photo-1605745341112-85968b19335b?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-blue-500/90 text-white',
      },
      {
        titleKey: 'intel.news5Title',
        summaryKey: 'intel.news5Summary',
        meta: 'GMK Center · 1 天前',
        img: 'https://images.unsplash.com/photo-1566576721346-d4a3b4eaeb55?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-blue-500/90 text-white',
      },
      {
        titleKey: 'intel.news6Title',
        summaryKey: 'intel.news6Summary',
        meta: 'Tico Times · 1 天前',
        img: 'https://images.unsplash.com/photo-1605745341112-85968b19335b?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-blue-500/90 text-white',
      },
    ],
  },
  {
    categoryKey: 'intel.categoryEnergy',
    icon: markRaw(Zap),
    items: [
      {
        titleKey: 'intel.news7Title',
        summaryKey: 'intel.news7Summary',
        meta: 'Bloomberg · 能源 · 2 小时前',
        img: 'https://images.unsplash.com/photo-1578575437130-527eed3abbec?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-amber-500/90 text-white',
      },
      {
        titleKey: 'intel.news8Title',
        summaryKey: 'intel.news8Summary',
        meta: 'European Commission · 2 天前',
        img: 'https://images.unsplash.com/photo-1494412651409-8963ce7935a7?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-amber-500/90 text-white',
      },
    ],
  },
  {
    categoryKey: 'intel.categoryTrade',
    icon: markRaw(FileText),
    items: [
      {
        titleKey: 'intel.news9Title',
        summaryKey: 'intel.news9Summary',
        meta: 'Baker Tilly · 今日',
        img: 'https://images.unsplash.com/photo-1450101499163-c8848c66ca85?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
      {
        titleKey: 'intel.news10Title',
        summaryKey: 'intel.news10Summary',
        meta: 'Flexport · 今日',
        img: 'https://images.unsplash.com/photo-1517048676732-d65bc937f952?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
      {
        titleKey: 'intel.news11Title',
        summaryKey: 'intel.news11Summary',
        meta: 'TechTimes · IEA · 3 小时前',
        img: 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
      {
        titleKey: 'intel.news12Title',
        summaryKey: 'intel.news12Summary',
        meta: 'Al Jazeera · BIS · 1 天前',
        img: 'https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
      {
        titleKey: 'intel.news13Title',
        summaryKey: 'intel.news13Summary',
        meta: 'Baker Tilly · 1 天前',
        img: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
      {
        titleKey: 'intel.news14Title',
        summaryKey: 'intel.news14Summary',
        meta: 'Deluair · 2 天前',
        img: 'https://images.unsplash.com/photo-1605902711622-cfb43c4437b5?auto=format&fit=crop&w=900&q=75',
        catStyle: 'bg-purple-500/90 text-white',
      },
    ],
  },
]

// ═══════════════════════════════════════════════════════
// 2D Leaflet 地图 — GDACS 灾害 + USGS 地震（实时）
// ═══════════════════════════════════════════════════════

const USGS_URL = 'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson'
const TILE_DARK = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
const TILE_LIGHT = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png'
const LEVEL_COLOR = { high: '#ff3b30', watch: '#ff9500', normal: '#34c759' }
const LEVEL_RADIUS = { high: 9, watch: 7, normal: 5 }

const mapContainer = ref(null)
const darkMode = ref(false)

let mapInstance = null
let disasterLayer = null
let quakeLayer = null
let tileLayer = null
let mapTimer = null

function gdacsTypeZh(eventType) {
  return t('common.gdacsTypes.' + eventType) || eventType
}

function gdacsLevel(alertLevel) {
  if (alertLevel === 'Red') return 'high'
  if (alertLevel === 'Orange') return 'watch'
  return 'normal'
}

function magLevel(m) {
  return m >= 6 ? 'high' : (m >= 4.5 ? 'watch' : 'normal')
}

function makeCircleMarker(lat, lng, level, popupHtml) {
  const color = LEVEL_COLOR[level] || LEVEL_COLOR.normal
  const classes = level !== 'normal' ? 'pulse-marker pulse-' + level : ''
  return L.circleMarker([lat, lng], {
    radius: LEVEL_RADIUS[level] || 5,
    fillColor: color,
    color: '#fff',
    weight: 1.5,
    fillOpacity: 0.85,
    className: classes,
  }).bindTooltip(popupHtml, { direction: 'top', offset: [0, -8], opacity: 0.92, sticky: true })
}

async function fetchGDACS() {
  try {
    const res = await fetch('/gdacs/xml/rss.xml')
    const xmlText = await res.text()
    const parser = new DOMParser()
    const doc = parser.parseFromString(xmlText, 'text/xml')
    const geoNS = 'http://www.w3.org/2003/01/geo/wgs84_pos#'
    const gdacsNS = 'http://www.gdacs.org'

    const markers = []
    doc.querySelectorAll('item').forEach(item => {
      const eventType = item.getElementsByTagNameNS(gdacsNS, 'eventtype')[0]?.textContent
      if (!eventType || eventType === 'EQ') return

      const pointEl = item.getElementsByTagNameNS(geoNS, 'Point')[0]
      const latEl = pointEl?.getElementsByTagNameNS(geoNS, 'lat')[0]
      const lngEl = pointEl?.getElementsByTagNameNS(geoNS, 'long')[0]
      const lat = parseFloat(latEl?.textContent)
      const lng = parseFloat(lngEl?.textContent)
      if (isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) return

      const alertLevel = item.getElementsByTagNameNS(gdacsNS, 'alertlevel')[0]?.textContent || 'Green'
      const title = item.querySelector('title')?.textContent || ''
      const country = item.getElementsByTagNameNS(gdacsNS, 'country')[0]?.textContent || ''
      const typeZh = gdacsTypeZh(eventType)
      const level = gdacsLevel(alertLevel)
      const popup = `<b>${typeZh}</b><br>${title}<br><small>${country} · ${alertLevel}</small>`

      markers.push(makeCircleMarker(lat, lng, level, popup))
    })
    return markers
  } catch (e) {
    console.warn('GDACS 灾害数据获取失败', e)
    return []
  }
}

async function fetchUSGS() {
  try {
    const res = await fetch(USGS_URL)
    const geo = await res.json()
    return (geo.features || []).slice(0, 60).map(f => {
      const [lng, lat] = f.geometry.coordinates
      const mag = f.properties.mag || 0
      const place = f.properties.place || ''
      const level = magLevel(mag)
      const popup = `<b>M${mag.toFixed(1)} ${t('common.gdacsTypes.EQ')}</b><br>${place}`
      return makeCircleMarker(lat, lng, level, popup)
    })
  } catch (e) {
    console.warn('USGS 地震数据获取失败', e)
    return []
  }
}

async function refreshMap() {
  if (!mapInstance) return
  const [disasters, quakes] = await Promise.all([fetchGDACS(), fetchUSGS()])
  if (disasterLayer) mapInstance.removeLayer(disasterLayer)
  if (quakeLayer) mapInstance.removeLayer(quakeLayer)
  disasterLayer = L.layerGroup(disasters).addTo(mapInstance)
  quakeLayer = L.layerGroup(quakes).addTo(mapInstance)
}

function toggleMapTheme() {
  if (!tileLayer || !mapInstance) return
  darkMode.value = !darkMode.value
  mapInstance.removeLayer(tileLayer)
  tileLayer = L.tileLayer(darkMode.value ? TILE_DARK : TILE_LIGHT, { maxZoom: 18, noWrap: false }).addTo(mapInstance)
}

function initMap() {
  const el = document.getElementById('riskLeaflet')
  if (!el) return
  mapInstance = L.map(el, {
    center: [20, 0], zoom: 2, minZoom: 2, maxZoom: 12,
    maxBounds: [[-85, -180], [85, 180]],
    maxBoundsViscosity: 0.8,
    zoomControl: true, attributionControl: false,
    scrollWheelZoom: true, worldCopyJump: true,
  })
  tileLayer = L.tileLayer(darkMode.value ? TILE_DARK : TILE_LIGHT, { maxZoom: 18, noWrap: false }).addTo(mapInstance)
  refreshMap()
  mapTimer = setInterval(refreshMap, 60000)
}

function destroyMap() {
  clearInterval(mapTimer)
  if (mapInstance) { mapInstance.remove(); mapInstance = null }
}

onMounted(() => {
  initMap()
})

onUnmounted(() => {
  destroyMap()
})
</script>

<style scoped>
.hero-bg-zoom {
  animation: heroZoom 20s ease-in-out infinite alternate;
  transform-origin: center center;
}

@keyframes heroZoom {
  0% {
    transform: scale(1);
  }

  100% {
    transform: scale(1.12);
  }
}

.map-theme-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 1000;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  font-size: 18px;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.2s;
}

.map-theme-btn:hover {
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* Pulse markers for high/watch risk on 2D map */
:deep(.pulse-marker) {
  animation: pulse-ring 2s ease-out infinite;
}

:deep(.pulse-high) {
  animation-duration: 1.4s;
}

:deep(.pulse-watch) {
  animation-duration: 2.2s;
}

@keyframes pulse-ring {
  0% {
    r: 5;
    opacity: 0.9;
  }

  100% {
    r: 18;
    opacity: 0;
  }
}
</style>
