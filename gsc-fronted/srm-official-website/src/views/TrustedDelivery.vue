<template>
  <div class="bg-white">
    <!-- Hero -->
    <section class="relative h-screen min-h-[800px] w-full flex items-center justify-center overflow-hidden">
      <div class="absolute inset-0 z-0">
        <img :src="IMAGES.hero" alt="Global Logistics Network"
          class="w-full h-full object-cover object-center hero-bg-zoom" />
        <div class="absolute inset-0 bg-gradient-to-b from-[#0a0d16]/30 via-transparent to-[#0a0d16]/50"></div>
      </div>

      <div class="relative z-10 max-w-6xl mx-auto px-6 text-center -mt-20">
        <h1 v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 100, ease: [0.16, 1, 0.3, 1] } }"
          class="text-6xl md:text-8xl font-semibold tracking-tight text-white mb-6 text-cn">
          {{ $t('trustedDelivery.heroTitle') }}
        </h1>

        <p v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 200, ease: [0.16, 1, 0.3, 1] } }"
          class="text-xl md:text-3xl text-white/90 font-light mt-12 mb-10 tracking-wide max-w-3xl mx-auto text-cn">
          {{ $t('trustedDelivery.heroSub') }}
        </p>
      </div>
    </section>

    <!-- KPI Stats -->
    <section class="relative z-20 -mt-24 w-full mx-auto px-6 lg:px-8">
      <div v-motion :initial="{ opacity: 0, y: 40 }"
        :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true"
        class="bg-white/10 backdrop-blur-2xl border border-white/20 rounded-[2rem] p-8 md:p-12 shadow-[0_30px_60px_-15px_rgba(0,0,0,0.3)] grid grid-cols-2 md:grid-cols-4 gap-8 divide-x divide-white/10">
        <div v-for="(stat, idx) in STATS" :key="idx"
          :class="['text-center px-4', idx % 2 === 0 ? 'border-l-0 md:border-l' : '', idx === 0 ? 'border-l-0' : '']">
          <div class="text-title font-light text-white mb-2">{{ stat.value }}</div>
          <div class="text-content text-slate-500 font-medium mb-1 text-cn">{{ $t(stat.labelKey) }}</div>
          <div class="text-slate-500 text-xs uppercase tracking-wider">{{ $t(stat.subKey) }}</div>
        </div>
      </div>
    </section>

    <!-- Capabilities Zig-Zag -->
    <section class="py-32 overflow-hidden bg-[#fafafa]">
      <div class="w-[85%] mx-auto px-6 lg:px-8 space-y-32">
        <div v-for="cap in CAPABILITIES" :key="cap.id" v-motion :initial="{ opacity: 0, y: 50 }"
          :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true"
          :class="['flex flex-col lg:flex-row items-center gap-12 lg:gap-24', cap.reverse ? 'lg:flex-row-reverse' : '']">
          <div class="flex-1 space-y-8 w-full">
            <div>
              <h2 class="text-title font-semibold text-slate-900 mb-4 text-cn">{{ $t(cap.titleKey) }}</h2>
            </div>

            <p class="text-content text-slate-500 font-light leading-relaxed text-cn">{{ $t(cap.descKey) }}</p>

          </div>

          <div class="flex-1 w-full">
            <div
              class="relative rounded-[2.5rem] overflow-hidden aspect-[4/3] bg-slate-200 shadow-2xl shadow-slate-900/10 transform-gpu">
              <img :src="cap.image" :alt="$t(cap.titleKey)"
                class="absolute left-0 top-0 w-full h-[130%] object-cover parallax-img" loading="lazy" />
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Black Box → White Box (Scroll Animation) -->
    <section class="relative bg-[#0a0d16] text-white bb-wb-container h-[500vh]">
      <!-- Sticky container for both title and slides to prevent offset/gaps -->
      <div class="sticky top-0 h-screen w-full overflow-hidden z-20 bb-wb-sticky">

        <!-- Full-screen background elements for blending -->
        <div class="absolute inset-0 z-0 pointer-events-none">
          <div
            class="absolute top-0 right-0 w-[800px] h-[800px] bg-blue-600/10 blur-[150px] rounded-full mix-blend-screen">
          </div>
          <div
            class="absolute bottom-0 left-0 w-[600px] h-[600px] bg-cyan-600/10 blur-[120px] rounded-full mix-blend-screen">
          </div>
        </div>

        <!-- Fixed Title Layer -->
        <div class="absolute top-0 left-0 w-full z-30 pt-16 md:pt-24 pointer-events-none">
          <div class="text-center max-w-5xl mx-auto px-6">
            <h2 class="text-title font-semibold text-white tracking-tight text-cn drop-shadow-2xl">
              {{ $t('trustedDelivery.bbTitle') }}
            </h2>
          </div>
        </div>

        <!-- Central Divider Icon (Static) -->
        <div
          class="absolute left-1/2 top-[55%] md:top-1/2 -translate-x-1/2 -translate-y-1/2 z-40 flex items-center justify-center w-12 h-12 md:w-16 md:h-16 rounded-full bg-[#111524] border-[3px] md:border-4 border-[#0a0d16] text-slate-400 shadow-[0_0_30px_rgba(0,0,0,0.8)]">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="w-5 h-5 md:w-6 md:h-6">
            <path d="M5 12h14M12 5l7 7-7 7" />
          </svg>
        </div>

        <!-- The comparison slides -->
        <div v-for="(item, idx) in bbWbData" :key="idx"
          class="absolute inset-0 w-full h-full flex flex-col md:flex-row bb-wb-slide" :style="{ zIndex: 10 + idx }">

          <!-- Left/Top: Black Box -->
          <div class="w-full md:w-1/2 h-1/2 md:h-full relative overflow-hidden bg-[#05070a] bb-side">
            <!-- Background Image -->
            <img :src="item.bbImg"
              class="absolute inset-0 w-full h-full object-cover opacity-30 grayscale scale-110 bb-bg" loading="lazy" />
            <div
              class="absolute inset-0 bg-gradient-to-b from-[#0a0d16]/80 via-[#0a0d16]/50 to-[#0a0d16] md:bg-gradient-to-r md:from-[#0a0d16]/90 md:to-[#0a0d16]/40">
            </div>

            <!-- Content -->
            <div
              class="absolute inset-0 flex flex-col justify-end md:justify-center items-start md:items-end text-left md:text-right px-6 md:px-16 pb-12 md:pb-0 pt-32 bb-content">
              <h4
                class="text-2xl md:text-4xl font-bold mb-4 md:mb-6 text-slate-300 text-cn leading-snug md:leading-tight">
                {{ $t(item.bbTitleKey) }}</h4>
              <div class="max-w-lg lg:max-w-xl">
                <p class="text-base md:text-xl text-slate-500 leading-normal text-cn text-left">{{ $t(item.bbDescKey) }}
                </p>
              </div>
            </div>
          </div>

          <!-- Right/Bottom: White Box -->
          <div
            class="w-full md:w-1/2 h-1/2 md:h-full relative overflow-hidden bg-[#0a1128] wb-side border-t md:border-t-0 border-white/5">
            <!-- Background Image -->
            <img :src="item.wbImg"
              class="absolute inset-0 w-full h-full object-cover opacity-40 mix-blend-screen scale-110 wb-bg"
              loading="lazy" />
            <div
              class="absolute inset-0 bg-gradient-to-t from-[#0a0d16] via-blue-900/20 to-[#0a0d16]/80 md:bg-gradient-to-l md:from-[#0a0d16]/90 md:to-[#0a0d16]/40">
            </div>
            <div class="absolute inset-0 bg-blue-500/5 mix-blend-overlay"></div>

            <!-- Content -->
            <div
              class="absolute inset-0 flex flex-col justify-start md:justify-center items-start text-left px-6 md:px-16 pt-12 md:pt-32 wb-content">
              <h4 class="text-2xl md:text-4xl font-bold mb-4 md:mb-6 text-white text-cn leading-snug md:leading-tight">
                {{ $t(item.wbTitleKey) }}</h4>
              <div class="max-w-lg lg:max-w-xl">
                <p class="text-base md:text-xl text-blue-200/80 leading-normal text-cn">{{ $t(item.wbDescKey) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Ecosystem -->
    <section class="py-32 bg-white relative overflow-hidden">
      <div class="w-[85%] mx-auto px-6 lg:px-8 relative z-10">
        <div class="text-center max-w-3xl mx-auto mb-20">
          <h2 class="text-title font-semibold text-slate-900 mb-6 text-cn">{{ $t('trustedDelivery.ecosystemTitle') }}
          </h2>
          <p class="text-content text-slate-500 font-light leading-relaxed text-cn">
            {{ $t('trustedDelivery.ecosystemDesc', { count1: '100+', count2: '3 天' }) }}
          </p>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div v-for="(eco, idx) in ECOSYSTEM" :key="idx"
            class="bg-[#fafafa] rounded-[2rem] p-8 border border-slate-100 hover:bg-white hover:shadow-[0_20px_40px_-15px_rgba(0,0,0,0.05)] transition-all duration-300 group cursor-pointer">
            <svg v-if="idx === 0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" stroke-linejoin="round"
              class="w-10 h-10 mb-6 text-slate-400 group-hover:scale-110 transition-all duration-300">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="6" x2="12" y2="18" />
              <path d="M12 6a4 4 0 0 0-4 4" />
            </svg>
            <svg v-else-if="idx === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" stroke-linejoin="round"
              class="w-10 h-10 mb-6 text-slate-400 group-hover:scale-110 transition-all duration-300">
              <path
                d="M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.3-.8.8L6 15l-1.5 3.5c-.4 1 .3 2 1.3 1.6L9 18.5l3.5 1.5c1 .4 2-.3 1.6-1.3L12.5 15l2.5-1.5c1-.4 2 .3 1.6 1.3L15 18.5l2.8 1.2c.5.2 1.1-.3.9-.8L17.8 19.2z" />
            </svg>
            <svg v-else-if="idx === 2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" stroke-linejoin="round"
              class="w-10 h-10 mb-6 text-slate-400 group-hover:scale-110 transition-all duration-300">
              <rect x="4" y="2" width="16" height="20" rx="2" />
              <line x1="9" y1="2" x2="9" y2="22" />
              <line x1="15" y1="2" x2="15" y2="22" />
              <line x1="4" y1="9" x2="20" y2="9" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
              stroke-linejoin="round"
              class="w-10 h-10 mb-6 text-slate-400 group-hover:scale-110 transition-all duration-300">
              <ellipse cx="12" cy="5" rx="9" ry="3" />
              <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
              <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
            </svg>
            <div class="text-card-title font-light text-slate-900 mb-2">{{ eco.count }}</div>
            <div class="text-slate-900 font-medium text-cn">{{ $t(eco.titleKey) }}</div>
            <div class="text-sm text-slate-400 mt-1 text-cn">{{ $t(eco.descKey) }}</div>
          </div>
        </div>

        <p class="text-center text-slate-400 text-sm mt-12 font-light">{{ $t('trustedDelivery.ecoMore') }}</p>
      </div>
    </section>

  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

let ctx

onMounted(() => {
  ctx = gsap.context(() => {
    const parallaxImages = document.querySelectorAll('.parallax-img')
    parallaxImages.forEach(img => {
      gsap.fromTo(img,
        { yPercent: -15, scale: 1.35 },
        {
          yPercent: 15,
          scale: 1,
          ease: "none",
          scrollTrigger: {
            trigger: img.parentElement,
            start: "top bottom",
            end: "bottom top",
            scrub: true
          }
        }
      )
    })

    // ===== Black Box -> White Box Animation =====
    const bbWbContainer = document.querySelector('.bb-wb-container')
    const slides = document.querySelectorAll('.bb-wb-slide')

    if (bbWbContainer && slides.length > 0) {
      const tlBbWb = gsap.timeline({
        scrollTrigger: {
          trigger: bbWbContainer,
          start: 'top top',
          end: 'bottom bottom',
          scrub: 1
        }
      })

      // We only care about desktop (>768px) animation for the split clip-paths
      const isDesktop = window.innerWidth > 768;

      if (isDesktop) {
        // Set initial states
        for (let i = 1; i < slides.length; i++) {
          gsap.set(slides[i].querySelector('.bb-side'), { clipPath: 'inset(100% 0 0 0)' })
          gsap.set(slides[i].querySelector('.wb-side'), { clipPath: 'inset(0 0 100% 0)' })
        }

        let time = 0;
        for (let i = 1; i < slides.length; i++) {
          tlBbWb.to(slides[i].querySelector('.bb-side'), { clipPath: 'inset(0% 0 0 0)', ease: 'none', duration: 1 }, time)
          tlBbWb.to(slides[i].querySelector('.wb-side'), { clipPath: 'inset(0 0 0% 0)', ease: 'none', duration: 1 }, time)
          tlBbWb.to(slides[i].querySelectorAll('.bb-bg, .wb-bg'), { scale: 1, ease: 'none', duration: 1 }, time)
          tlBbWb.fromTo(slides[i].querySelectorAll('.bb-content, .wb-content'),
            { y: 60, opacity: 0 },
            { y: 0, opacity: 1, ease: 'power2.out', duration: 0.6 }, time + 0.4)

          time += 1.3;
        }
        tlBbWb.to({}, { duration: 0.3 })
      } else {
        // Simple crossfade for mobile to avoid performance issues
        for (let i = 1; i < slides.length; i++) {
          gsap.set(slides[i], { opacity: 0 });
        }

        let time = 0;
        for (let i = 1; i < slides.length; i++) {
          tlBbWb.to(slides[i], { opacity: 1, ease: 'none', duration: 1 }, time)
          time += 1.3;
        }
        tlBbWb.to({}, { duration: 0.3 })
      }
    }
  })
})

onUnmounted(() => {
  if (ctx) ctx.revert()
})

const IMAGES = {
  hero: "https://images.unsplash.com/photo-1684610529682-553625a1ffed?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxnbG9iYWwlMjBnbG93aW5nJTIwZWFydGglMjBjb25uZWN0ZWQlMjBuZXR3b3JrJTIwZnV0dXJpc3RpYyUyMGRhcmt8ZW58MXx8fHwxNzgyMjg1NDQ3fDA&ixlib=rb-4.1.0&q=80&w=2000",
  tracking: "https://images.unsplash.com/photo-1579719558505-ad4a5fee0847?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxjb250cm9sJTIwdG93ZXIlMjBzY3JlZW4lMjBkYXNoYm9hcmR8ZW58MXx8fHwxNzgyMjg1NDM5fDA&ixlib=rb-4.1.0&q=80&w=1080",
  warning: "https://images.unsplash.com/photo-1500740516770-92bd004b996e?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx3ZWF0aGVyJTIwcmFkYXIlMjBzdG9ybSUyMGFic3RyYWN0fGVufDF8fHx8MTc4MjI4NTQzOXww&ixlib=rb-4.1.0&q=80&w=1080",
  customs: "https://images.unsplash.com/photo-1590935216109-8d3318de2c1c?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxkaWdpdGFsJTIwZG9jdW1lbnQlMjBjb21wbGlhbmNlJTIwYWJzdHJhY3R8ZW58MXx8fHwxNzgyMjg1NDM5fDA&ixlib=rb-4.1.0&q=80&w=1080",
  settlement: "https://images.unsplash.com/photo-1762267683517-6e9bc20675e9?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxmaW5hbmNpYWwlMjBkYXRhJTIwY2hhcnRzJTIwYWJzdHJhY3QlMjBkYXJrfGVufDF8fHx8MTc4MjI4NTQzOXww&ixlib=rb-4.1.0&q=80&w=1080"
}

const STATS = [
  { value: "95%", labelKey: "trustedDelivery.statVisibility", subKey: "trustedDelivery.statSubVisibility" },
  { value: "48h", labelKey: "trustedDelivery.statWarning", subKey: "trustedDelivery.statSubWarning" },
  { value: "99%", labelKey: "trustedDelivery.statCompliance", subKey: "trustedDelivery.statSubCompliance" },
  { value: "分钟级", labelKey: "trustedDelivery.statReconciliation", subKey: "trustedDelivery.statSubReconciliation" }
]

const CAPABILITIES = [
  {
    id: "01", titleKey: "trustedDelivery.capTracking", tagline: "全域可视",
    descKey: "trustedDelivery.capTrackingDesc",
    image: "https://images.unsplash.com/photo-1579719558505-ad4a5fee0847?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxjb250cm9sJTIwdG93ZXIlMjBzY3JlZW4lMjBkYXNoYm9hcmR8ZW58MXx8fHwxNzgyMjg1NDM5fDA&ixlib=rb-4.1.0&q=80&w=1080",
    reverse: false
  },
  {
    id: "02", titleKey: "trustedDelivery.capWarning", tagline: "风险前置",
    descKey: "trustedDelivery.capWarningDesc",
    image: "https://images.unsplash.com/photo-1500740516770-92bd004b996e?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx3ZWF0aGVyJTIwcmFkYXIlMjBzdG9ybSUyMGFic3RyYWN0fGVufDF8fHx8MTc4MjI4NTQzOXww&ixlib=rb-4.1.0&q=80&w=1080",
    reverse: true
  },
  {
    id: "03", titleKey: "trustedDelivery.capCustoms", tagline: "合规预审",
    descKey: "trustedDelivery.capCustomsDesc",
    image: "https://images.unsplash.com/photo-1590935216109-8d3318de2c1c?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxkaWdpdGFsJTIwZG9jdW1lbnQlMjBjb21wbGlhbmNlJTIwYWJzdHJhY3R8ZW58MXx8fHwxNzgyMjg1NDM5fDA&ixlib=rb-4.1.0&q=80&w=1080",
    reverse: false
  },
  {
    id: "04", titleKey: "trustedDelivery.capSettlement", tagline: "自动稽核",
    descKey: "trustedDelivery.capSettlementDesc",
    image: "https://images.unsplash.com/photo-1762267683517-6e9bc20675e9?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxmaW5hbmNpYWwlMjBkYXRhJTIwY2hhcnRzJTIwYWJzdHJhY3QlMjBkYXJrfGVufDF8fHx8MTc4MjI4NTQzOXww&ixlib=rb-4.1.0&q=80&w=1080",
    reverse: true
  }
]

const bbWbData = [
  {
    bbTitleKey: "trustedDelivery.bb1Title",
    bbDescKey: "trustedDelivery.bb1Desc",
    bbImg: "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1200&q=80",
    wbTitleKey: "trustedDelivery.wb1Title",
    wbDescKey: "trustedDelivery.wb1Desc",
    wbImg: "https://images.unsplash.com/photo-1579719558505-ad4a5fee0847?auto=format&fit=crop&w=1200&q=80",
  },
  {
    bbTitleKey: "trustedDelivery.bb2Title",
    bbDescKey: "trustedDelivery.bb2Desc",
    bbImg: "https://images.unsplash.com/photo-1500740516770-92bd004b996e?auto=format&fit=crop&w=1200&q=80",
    wbTitleKey: "trustedDelivery.wb2Title",
    wbDescKey: "trustedDelivery.wb2Desc",
    wbImg: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1200&q=80",
  },
  {
    bbTitleKey: "trustedDelivery.bb3Title",
    bbDescKey: "trustedDelivery.bb3Desc",
    bbImg: "https://images.unsplash.com/photo-1605810230434-7631ac76ec81?auto=format&fit=crop&w=1200&q=80",
    wbTitleKey: "trustedDelivery.wb3Title",
    wbDescKey: "trustedDelivery.wb3Desc",
    wbImg: "https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=1200&q=80",
  },
  {
    bbTitleKey: "trustedDelivery.bb4Title",
    bbDescKey: "trustedDelivery.bb4Desc",
    bbImg: "https://images.unsplash.com/photo-1450101499163-c8848c66ca85?auto=format&fit=crop&w=1200&q=80",
    wbTitleKey: "trustedDelivery.wb4Title",
    wbDescKey: "trustedDelivery.wb4Desc",
    wbImg: "https://images.unsplash.com/photo-1639322537228-f710d846310a?auto=format&fit=crop&w=1200&q=80",
  }
]

const ECOSYSTEM = [
  { count: "35+", titleKey: "trustedDelivery.ecoCarriers", descKey: "trustedDelivery.ecoCarriersDesc" },
  { count: "80+", titleKey: "trustedDelivery.ecoAirlines", descKey: "trustedDelivery.ecoAirlinesDesc" },
  { count: "40+", titleKey: "trustedDelivery.ecoCustoms", descKey: "trustedDelivery.ecoCustomsDesc" },
  { count: "30+", titleKey: "trustedDelivery.ecoERP", descKey: "trustedDelivery.ecoERPDesc" }
]
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
</style>
