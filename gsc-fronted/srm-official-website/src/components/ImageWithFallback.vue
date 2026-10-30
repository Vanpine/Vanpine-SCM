<template>
  <div
    v-if="didError"
    v-bind="wrapperAttrs"
    class="inline-block bg-gray-100 text-center align-middle"
  >
    <div class="flex items-center justify-center w-full h-full">
      <img
        :src="ERROR_IMG_SRC"
        alt="Error loading image"
        :data-original-url="src"
      />
    </div>
  </div>
  <img
    v-else
    :src="src"
    :alt="alt"
    v-bind="$attrs"
    @error="handleError"
  />
</template>

<script setup>
import { ref, computed, useAttrs } from 'vue'

const ERROR_IMG_SRC = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgc3Ryb2tlPSIjMDAwIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBvcGFjaXR5PSIuMyIgZmlsbD0ibm9uZSIgc3Ryb2tlLXdpZHRoPSIzLjciPjxyZWN0IHg9IjE2IiB5PSIxNiIgd2lkdGg9IjU2IiBoZWlnaHQ9IjU2IiByeD0iNiIvPjxwYXRoIGQ9Im0xNiA1OCAxNi0xOCAzMiAzMiIvPjxjaXJjbGUgY3g9IjUzIiBjeT0iMzUiIHI9IjciLz48L3N2Zz4KCg=='

const props = defineProps({
  src: { type: String, required: true },
  alt: { type: String, default: '' },
})

const didError = ref(false)

function handleError() {
  didError.value = true
}

const attrs = useAttrs()
const wrapperAttrs = computed(() => {
  const { class: _, style: __, ...rest } = attrs
  return rest
})
</script>
