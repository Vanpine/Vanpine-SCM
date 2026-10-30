<script setup>
/**
 * Globe3D.vue — 3D 地球可视化组件
 *
 * 基于 Globe.gl 库，渲染可交互的 3D 地球。
 * 地球上的视觉元素：
 *   · 点状柱（points）  → GDACS 灾害 + USGS 地震（动态呼吸起伏）
 *   · 脉冲圈（rings）   → 高危/关注区域向外扩散的涟漪
 *   · 航线弧线（arcs）  → 全球主要贸易航线
 *   · 港口标注（labels）→ 枢纽港口
 *
 * 数据来源：通过 Vue 的 inject 从父组件 RiskDashboard 注入
 *   - riskPoints  : GDACS 实时灾害数据
 *   - riskArcs    : 航线弧线
 *   - hubPorts    : 枢纽港口
 *   - quakePoints : USGS 实时地震数据
 */

import { ref, onMounted, onUnmounted, inject, watch } from 'vue'

const riskPoints = inject('riskPoints', ref([]))
const riskArcs = inject('riskArcs', ref([]))
const hubPorts = inject('hubPorts', [])
const quakePoints = inject('quakePoints', ref([]))

const COLORS = { high: '#ff3b30', watch: '#ff9500', normal: '#34c759' }
const ALT = { high: 0.18, watch: 0.11, normal: 0.045 }

let globeWorld = null
let resizeObserver = null

function hexToRgba(hex, a) {
  const n = parseInt(hex.slice(1), 16)
  return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')'
}

function onMouseEnter() {
  if (globeWorld && globeWorld.controls()) {
    globeWorld.controls().autoRotate = false
  }
}
function onMouseLeave() {
  if (globeWorld && globeWorld.controls()) {
    globeWorld.controls().autoRotate = true
  }
}

function initGlobe() {
  const el = document.getElementById('globeViz')
  if (!el || typeof Globe === 'undefined') return

  const startTime = Date.now()
  globeWorld = Globe()
    .globeImageUrl('https://unpkg.com/three-globe/example/img/earth-blue-marble.jpg')
    .bumpImageUrl('https://unpkg.com/three-globe/example/img/earth-topology.png')
    .backgroundImageUrl('https://unpkg.com/three-globe/example/img/night-sky.png')
    .showGraticules(false)
    .showAtmosphere(true).atmosphereColor('#4da6ff').atmosphereAltitude(0.22)
    .pointsData(allPoints())
    .pointLat('lat').pointLng('lng')
    .pointColor(d => COLORS[d.level])
    .pointAltitude(d => {
      const base = ALT[d.level] || 0.045
      const t = (Date.now() - startTime) * 0.001
      const amp = d.level === 'high' ? 0.035 : (d.level === 'watch' ? 0.022 : 0.012)
      return base + Math.sin(t * 2.0) * amp
    })
    .pointRadius(d => d.level === 'high' ? 0.55 : (d.level === 'watch' ? 0.4 : 0.3))
    .pointResolution(48)
    .pointLabel(d =>
      '<div style="font:500 12.5px sans-serif;color:#fff;' +
      'background:rgba(10,14,26,0.92);padding:8px 12px;border-radius:10px;' +
      'border:1px solid ' + COLORS[d.level] + ';' +
      'box-shadow:0 0 18px ' + COLORS[d.level] + '55, inset 0 0 8px ' + COLORS[d.level] + '15;">' +
      d.zh + '</div>'
    )
    .arcsData(riskArcs.value)
    .arcColor('color')
    .arcStroke(0.7)
    .arcDashLength(0.6).arcDashGap(0.25)
    .arcDashAnimateTime(1800)
    .arcAltitudeAutoScale(0.45)
    .labelsData(hubPorts)
    .labelLat('lat').labelLng('lng')
    .labelText(d => d.en)
    .labelSize(1.1).labelDotRadius(0.35)
    .labelColor(() => 'rgba(180,210,255,0.85)')
    .labelResolution(3)
    .ringsData(riskPoints.value.filter(d => d.level !== 'normal'))
    .ringColor(d => { const c = COLORS[d.level]; return t => hexToRgba(c, 1 - t) })
    .ringMaxRadius(5.5)
    .ringPropagationSpeed(2.8)
    .ringRepeatPeriod(900)
    (el)

  globeWorld.controls().autoRotate = true
  globeWorld.controls().autoRotateSpeed = 0.5
  globeWorld.controls().enableZoom = true
  globeWorld.controls().minDistance = 170
  globeWorld.controls().maxDistance = 520
  globeWorld.pointOfView({ lat: 22, lng: 55, altitude: 1.65 })

  el.addEventListener('mouseenter', onMouseEnter)
  el.addEventListener('mouseleave', onMouseLeave)

  resize()
  window.addEventListener('resize', resize)
}

function allPoints() {
  return [...riskPoints.value, ...quakePoints.value]
}

function resize() {
  const el = document.getElementById('globeViz')
  if (!el || !globeWorld) return
  const w = el.clientWidth || el.parentElement?.clientWidth || 600
  globeWorld.width(w)
  globeWorld.height(el.clientHeight || 600)
}

function refreshGlobe() {
  if (globeWorld) {
    const all = allPoints()
    globeWorld.pointsData(all)
    globeWorld.ringsData(all.filter(d => d.level !== 'normal'))
    globeWorld.arcsData(riskArcs.value)
  }
}

watch([quakePoints, riskPoints, riskArcs], () => {
  refreshGlobe()
}, { deep: true })

onMounted(() => {
  setTimeout(() => {
    initGlobe()
    const el = document.getElementById('globeViz')
    if (el) {
      resizeObserver = new ResizeObserver(() => {
        resize()
      })
      resizeObserver.observe(el)
    }
  }, 200)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  const el = document.getElementById('globeViz')
  if (el) {
    el.removeEventListener('mouseenter', onMouseEnter)
    el.removeEventListener('mouseleave', onMouseLeave)
  }
  if (globeWorld && globeWorld.renderer()) {
    const domEl = globeWorld.renderer().domElement
    if (domEl && domEl.parentNode) {
      domEl.parentNode.removeChild(domEl)
    }
  }
  globeWorld = null
})
</script>

<template>
  <div style="display:none"></div>
</template>
