<template>
  <div
    ref="barRef"
    class="drag-verify"
    :style="{
      width: width ? width + 'px' : '100%',
      height: height + 'px',
      lineHeight: height + 'px',
      borderRadius: height / 2 + 'px',
    }"
    @mousemove="dragMoving"
    @mouseup="dragFinish"
    @mouseleave="dragFinish"
    @touchmove.prevent="dragMoving"
    @touchend="dragFinish"
  >
    <div
      class="dv-progress"
      :class="{ done: verified }"
      :style="{
        width: left > 0 ? left + blockW / 2 + 'px' : '0',
        height: height + 'px',
        borderRadius: height / 2 + 'px 0 0 ' + height / 2 + 'px',
      }"
    />
    <div
      class="dv-msg"
      :class="{ done: verified }"
      :style="{ height: height + 'px', width: width ? width + 'px' : '100%', fontSize: '13px' }"
    >
      {{ msg }}
    </div>
    <div
      class="dv-ball"
      :class="{ done: verified }"
      :style="{
        width: height + 'px',
        height: height + 'px',
        left: left + 'px',
        transition: isMoving ? 'none' : 'left 0.3s ease',
      }"
      @mousedown="dragStart"
      @touchstart="dragStart"
    >
      <svg
        v-if="!verified"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#999"
        stroke-width="2.5"
        class="ball-icon"
      >
        <line
          x1="5"
          y1="12"
          x2="19"
          y2="12"
        />
        <polyline points="12,5 19,12 12,19" />
      </svg>
      <svg
        v-else
        viewBox="0 0 24 24"
        fill="none"
        stroke="#22c55e"
        stroke-width="2.5"
        class="ball-icon"
      >
        <polyline points="20,6 9,17 4,12" />
      </svg>
    </div>
  </div>
</template>


<script setup>
import { ref, computed, onMounted } from 'vue'
import './drag.css'

const props = defineProps({
  modelValue: Boolean,
  width: { type: Number, default: 0 },
  height: { type: Number, default: 44 },
  text: { type: String, default: '请按住滑块，拖动到最右边' },
  successText: { type: String, default: '验证通过' },
  textColor: { type: String, default: '#94a3b8' },
})

const emit = defineEmits(['update:modelValue', 'passCallback'])

const barRef = ref(null)
const realW = ref(props.width || 400)
const blockW = computed(() => props.height)
const maxLeft = computed(() => realW.value - blockW.value)

const isMoving = ref(false)
const startX = ref(0)
const left = ref(0)
const verified = ref(false)

const msg = computed(() => verified.value ? props.successText : props.text)

onMounted(() => {
  if (barRef.value && !props.width) realW.value = barRef.value.offsetWidth
  const el = barRef.value
  if (el) {
    el.style.setProperty('--textColor', props.textColor)
    el.style.setProperty('--halfW', Math.floor(realW.value / 2) + 'px')
    el.style.setProperty('--negHalfW', -Math.floor(realW.value / 2) + 'px')
  }
})

function reset() {
  left.value = 0
  verified.value = false
  emit('update:modelValue', false)
}

function dragStart(e) {
  if (verified.value) return
  isMoving.value = true
  startX.value = (e.pageX || e.touches[0].pageX) - left.value
}

function dragMoving(e) {
  if (!isMoving.value || verified.value) return
  e.preventDefault()
  const pageX = e.pageX || (e.touches && e.touches[0].pageX) || 0
  const dx = pageX - startX.value
  left.value = Math.max(0, Math.min(dx, maxLeft.value))
}

function dragFinish() {
  if (!isMoving.value || verified.value) return
  isMoving.value = false
  if (left.value >= maxLeft.value - 2) {
    left.value = maxLeft.value
    verified.value = true
    emit('update:modelValue', true)
    emit('passCallback')
  } else {
    left.value = 0
  }
}

defineExpose({ reset })
</script>
