<template>
  <div class="min-h-screen flex flex-col font-sans text-slate-900 selection:bg-blue-100 selection:text-blue-900 overflow-x-clip">
    <AppNavbar />
    <main class="flex-1 bg-white">
      <RouterView />
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import AppNavbar from '@/components/AppNavbar.vue'
import AppFooter from '@/components/AppFooter.vue'

const router = useRouter()
watch(() => router.currentRoute.value.path, () => {
  const lenis = window.__lenis
  if (lenis) {
    lenis.scrollTo(0, { immediate: true })
  } else {
    window.scrollTo({ top: 0, behavior: 'instant' })
  }
})
</script>
