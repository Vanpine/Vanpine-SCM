<template>
  <header class="fixed top-0 inset-x-0 z-50 group">
    <div
      :class="['mx-auto transition-all duration-500 ease-[cubic-bezier(0.16,1,0.3,1)]', navContainerWidth, navPadding]">
      <div
        :class="['rounded-full transition-all duration-500 ease-[cubic-bezier(0.16,1,0.3,1)]', navBackground, 'px-6 md:px-8 py-3 flex items-center justify-between', textColor]">

        <RouterLink to="/" class="flex items-center text-lg font-semibold tracking-wide z-10 group" aria-label="Home">
          <ImageWithFallback :src="logo2" alt="Logo" class="h-8 w-auto object-contain" />
        </RouterLink>

        <nav class="hidden md:flex items-center gap-8">
          <RouterLink to="/" :class="['text-[15px] py-1', getNavItemStyle(isHomeActive)]">{{ $t('navbar.home') }}</RouterLink>

          <div class="relative group/nav">
            <button :class="['flex items-center gap-1.5 text-[15px] py-2', getNavItemStyle(isSolutionsActive)]">
              {{ $t('navbar.solutions') }}
              <ChevronDown
                :class="['w-4 h-4 transition-transform duration-300 group-hover/nav:rotate-180', isSolutionsActive ? (isScrolled ? 'text-blue-600' : 'text-white') : '']" />
            </button>

            <div
              class="absolute top-full left-1/2 -translate-x-1/2 pt-4 opacity-0 invisible group-hover/nav:opacity-100 group-hover/nav:visible transition-all duration-300 ease-out translate-y-2 group-hover/nav:translate-y-0 z-20">
              <div
                class="w-40 bg-white/95 backdrop-blur-3xl rounded-2xl p-2 shadow-[0_20px_40px_-10px_rgba(0,0,0,0.1)] border border-slate-100 text-slate-800">
                <RouterLink to="/smart-procurement"
                  :class="['flex items-center justify-between px-4 py-2.5 text-sm font-medium rounded-xl transition-colors group/item', route.path === '/smart-procurement' ? 'text-blue-600 bg-slate-50' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50']">
                  {{ $t('navbar.smartProcurement') }}
                  <ArrowRight
                    :class="['w-3 h-3 transition-all', route.path === '/smart-procurement' ? 'opacity-100 translate-x-0 text-blue-600' : 'opacity-0 -translate-x-2 group-hover/item:opacity-100 group-hover/item:translate-x-0 text-blue-600']" />
                </RouterLink>
                <RouterLink to="/trusted-delivery"
                  :class="['flex items-center justify-between px-4 py-2.5 text-sm font-medium rounded-xl transition-colors group/item', route.path === '/trusted-delivery' ? 'text-blue-600 bg-slate-50' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50']">
                  {{ $t('navbar.trustedDelivery') }}
                  <ArrowRight
                    :class="['w-3 h-3 transition-all', route.path === '/trusted-delivery' ? 'opacity-100 translate-x-0 text-blue-600' : 'opacity-0 -translate-x-2 group-hover/item:opacity-100 group-hover/item:translate-x-0 text-blue-600']" />
                </RouterLink>
                <RouterLink to="/decision-center"
                  :class="['flex items-center justify-between px-4 py-2.5 text-sm font-medium rounded-xl transition-colors group/item', route.path === '/decision-center' ? 'text-blue-600 bg-slate-50' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50']">
                  {{ $t('navbar.decisionCenter') }}
                  <ArrowRight
                    :class="['w-3 h-3 transition-all', route.path === '/decision-center' ? 'opacity-100 translate-x-0 text-blue-600' : 'opacity-0 -translate-x-2 group-hover/item:opacity-100 group-hover/item:translate-x-0 text-blue-600']" />
                </RouterLink>
              </div>
            </div>
          </div>

          <RouterLink to="/news" :class="['text-[15px] py-1', getNavItemStyle(isNewsActive)]">{{ $t('navbar.news') }}</RouterLink>
        </nav>

        <div class="hidden md:flex items-center gap-3">
          <button @click="toggleLang"
            :class="['flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[13px] font-medium border transition-all duration-300', langBtnStyle]">
            <Globe class="w-3.5 h-3.5" />
            <span class="w-4 text-center">{{ lang }}</span>
          </button>

          <RouterLink to="/join"
            :class="['px-5 py-2.5 rounded-full text-sm font-medium transition-all duration-300', isScrolled ? 'bg-slate-900 text-white hover:bg-blue-600 shadow-md hover:shadow-blue-500/25' : 'bg-white text-slate-900 hover:bg-blue-50 hover:shadow-[0_0_20px_rgba(255,255,255,0.3)]']">
            {{ $t('navbar.join') }}
          </RouterLink>
        </div>

        <button @click="mobileMenuOpen = !mobileMenuOpen"
          :class="['md:hidden p-2 relative z-50 transition-colors', mobileMenuOpen ? 'text-slate-900' : mobileToggleColor]">
          <X v-if="mobileMenuOpen" class="w-6 h-6" />
          <Menu v-else class="w-6 h-6" />
        </button>

      </div>
    </div>

    <div
      :class="['fixed inset-0 bg-white/98 backdrop-blur-xl z-40 transition-transform duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] md:hidden', mobileMenuOpen ? 'translate-y-0' : '-translate-y-full']">
      <div class="pt-32 px-6 flex flex-col gap-6 h-full overflow-y-auto pb-10">
        <RouterLink to="/" :class="['text-2xl font-semibold', route.path === '/' ? 'text-blue-600' : 'text-slate-900']">
          {{ $t('navbar.home') }}</RouterLink>

        <div class="space-y-4">
          <div
            :class="['text-sm font-semibold uppercase tracking-wider', isSolutionsActive ? 'text-blue-600' : 'text-slate-500']">
            {{ $t('navbar.solutions') }}</div>
          <div class="flex flex-col gap-4 pl-4 border-l-2 border-slate-100">
            <RouterLink to="/smart-procurement"
              :class="['text-xl font-medium', route.path === '/smart-procurement' ? 'text-blue-600' : 'text-slate-700']">
              {{ $t('navbar.smartProcurement') }}</RouterLink>
            <RouterLink to="/trusted-delivery"
              :class="['text-xl font-medium', route.path === '/trusted-delivery' ? 'text-blue-600' : 'text-slate-700']">
              {{ $t('navbar.trustedDelivery') }}</RouterLink>
            <RouterLink to="/decision-center"
              :class="['text-xl font-medium', route.path === '/decision-center' ? 'text-blue-600' : 'text-slate-700']">
              {{ $t('navbar.decisionCenter') }}</RouterLink>
          </div>
        </div>

        <RouterLink to="/news"
          :class="['text-2xl font-semibold', route.path === '/news' ? 'text-blue-600' : 'text-slate-900']">{{ $t('navbar.news') }}
        </RouterLink>

        <div class="mt-auto pt-8 border-t border-slate-100 flex flex-col gap-4">
          <button @click="toggleLang"
            class="flex items-center justify-center gap-2 py-3 rounded-xl bg-slate-50 text-slate-600 font-medium border border-slate-200">
            <Globe class="w-5 h-5" />
            {{ $t('navbar.switchLang', { lang: locale === 'zh' ? 'English' : '中文' }) }}
          </button>

          <RouterLink to="/join"
            class="flex items-center justify-center w-full py-4 rounded-full bg-blue-600 text-white text-lg font-medium shadow-lg shadow-blue-500/25">
            {{ $t('navbar.join') }}
          </RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Globe, ChevronDown, ArrowRight, Menu, X } from 'lucide-vue-next'
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import logo2 from '@/assets/logo-2.webp'

const route = useRoute()
const { locale } = useI18n()

const isScrolled = ref(false)
const mobileMenuOpen = ref(false)

const lang = computed(() => locale.value === 'en' ? '中' : 'EN')

function toggleLang() {
  locale.value = locale.value === 'zh' ? 'en' : 'zh'
  localStorage.setItem('lang', locale.value)
  document.documentElement.lang = locale.value
}

function handleScroll() {
  isScrolled.value = window.scrollY > 20
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

// Close mobile menu on route change
watch(() => route.path, () => {
  mobileMenuOpen.value = false
})

// Computed styles
const navContainerWidth = computed(() => isScrolled.value ? 'max-w-6xl' : 'max-w-7xl')
const navBackground = computed(() =>
  isScrolled.value
    ? 'bg-white/85 backdrop-blur-2xl shadow-[0_8px_30px_rgba(0,0,0,0.06)] border border-slate-200/50'
    : 'bg-transparent border border-transparent'
)
const navPadding = computed(() => isScrolled.value ? 'py-3 px-6' : 'py-6 px-8')
const textColor = computed(() => isScrolled.value ? 'text-slate-800' : 'text-white')
const mobileToggleColor = computed(() => isScrolled.value ? 'text-slate-800' : 'text-white')
const langBtnStyle = computed(() =>
  isScrolled.value
    ? 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 border-transparent'
    : 'text-white/80 hover:bg-white/10 hover:text-white border-white/20'
)

const isHomeActive = computed(() => route.path === '/')
const isSolutionsActive = computed(() => ['/smart-procurement', '/trusted-delivery', '/decision-center'].includes(route.path))
const isNewsActive = computed(() => route.path === '/news')

function getNavItemStyle(isActive) {
  if (isActive) {
    if (!isScrolled.value) {
      return 'text-white font-medium relative after:absolute after:-bottom-1.5 after:left-0 after:w-full after:h-[2px] after:bg-white after:rounded-full'
    } else {
      return 'text-blue-600 font-semibold'
    }
  } else {
    return `text-[15px] font-medium transition-colors ${isScrolled.value ? 'text-slate-700 hover:text-blue-600' : 'text-white/80 hover:text-white'}`
  }
}
</script>
