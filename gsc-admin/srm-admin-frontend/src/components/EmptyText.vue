<template>
  <span v-if="blank" class="empty-text">无</span>
  <span v-else><slot>{{ value }}</slot></span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { default: undefined }
})

const blank = computed(() => props.value == null || props.value === '')
</script>
