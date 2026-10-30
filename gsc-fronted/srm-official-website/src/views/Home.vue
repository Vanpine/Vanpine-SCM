<template>
  <!-- ===== Fixed Background ===== -->
  <div class="fixed inset-0 z-0">
    <img :src="bgIsland" class="w-full h-full object-cover" alt="Background" />
    <div class="absolute inset-0 bg-black/25" />
  </div>

  <div class="relative z-10 bg-transparent min-h-screen text-slate-900 selection:bg-blue-200">

    <!-- ===== Hero ===== -->
    <section class="relative h-screen min-h-[700px] flex items-center justify-center overflow-hidden bg-black">
      <video :src="heroVideo" autoplay muted loop playsinline
        class="absolute inset-0 w-full h-full object-cover z-0 opacity-90" />
      <div class="absolute inset-0 bg-gradient-to-b from-black/30 via-black/10 to-black/60 z-10" />

      <div class="relative z-20 text-center px-6 max-w-none mx-auto">
        <h1 v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }"
          class="text-5xl md:text-7xl font-semibold tracking-wider leading-tight text-white mb-6 whitespace-nowrap">
          {{ $t('home.heroTitlePart1') }}<span>{{ $t('home.heroTitlePart2') }}</span>
        </h1>
        <p v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 100, ease: [0.16, 1, 0.3, 1] } }"
          class="text-xl md:text-3xl text-white/90 font-light mb-10 tracking-wide">
          {{ $t('home.heroTagline') }}
        </p>

        <div v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 200, ease: [0.16, 1, 0.3, 1] } }"
          class="flex flex-wrap items-center justify-center gap-6 text-white/80 font-medium mb-12">
          <span class="flex items-center gap-2">
            <TrendingDown class="w-5 h-5" /> {{ $t('home.heroPillCost') }}
          </span>
          <span class="hidden md:inline-block w-1.5 h-1.5 rounded-full bg-white/30" />
          <span class="flex items-center gap-2">
            <ShieldCheck class="w-5 h-5" /> {{ $t('home.heroPillRisk') }}
          </span>
          <span class="hidden md:inline-block w-1.5 h-1.5 rounded-full bg-white/30" />
          <span class="flex items-center gap-2">
            <Cpu class="w-5 h-5" /> {{ $t('home.heroPillAI') }}
          </span>
        </div>

      </div>
    </section>

    <!-- ===== Polycrisis (Gallery Animation) ===== -->
    <div class="home-gallery-wrap bg-[#020617]">
      <section class="home-gallery">
        <!-- Shared Sticky Background -->
        <img :src="bgIsland"
          class="absolute inset-0 w-full h-full object-cover z-0 gallery-bg-img will-change-transform"
          style="object-position: 50% 0%;" alt="Island Background" loading="lazy" />
        <!-- Blur Overlay (Hardware Accelerated Opacity Animation) -->
        <div class="absolute inset-0 z-0 gallery-bg-blur opacity-0 pointer-events-none"
          style="backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);"></div>
        <!-- Darken Overlay (Hardware Accelerated Opacity Animation) -->
        <div class="absolute inset-0 bg-slate-900 z-0 gallery-bg-overlay opacity-0 pointer-events-none"></div>

        <!-- Intro Text (Group 0) -->
        <div class="gallery-text-0 absolute top-[50%] z-20 text-center w-full px-6 opacity-0 pointer-events-none">
          <h3 class="text-3xl md:text-5xl font-light text-white leading-relaxed text-cn"
            v-html="$t('home.galleryText0')">
          </h3>
        </div>

        <!-- Text Group 1 -->
        <div class="gallery-text-1 absolute top-[50%] z-20 text-center w-full px-6 opacity-0 pointer-events-none">
          <h3 class="text-3xl md:text-5xl font-light leading-relaxed text-cn" v-html="$t('home.galleryText1')">
          </h3>
        </div>

        <!-- Image Group (5 images) -->
        <div class="gallery-img-group absolute inset-0 z-10 pointer-events-none">
          <!-- Card 1 -->
          <div class="g-img-wrap g-img-wrap-1 absolute will-change-transform"
            style="width: 21.6vw; height: 16vw; max-width: 432px; max-height: 324px;">
            <div class="g-img w-full h-full rounded-2xl overflow-hidden shadow-[0_20px_40px_rgba(0,0,0,0.3)]">
              <div class="g-img-inner w-full h-full relative">
                <img :src="pexels1" class="absolute w-full h-[130%] top-[-15%] object-cover will-change-transform"
                  loading="lazy" />
              </div>
            </div>
          </div>
          <!-- Card 2 -->
          <div class="g-img-wrap g-img-wrap-2 absolute will-change-transform"
            style="width: 30.4vw; height: 22.4vw; max-width: 616px; max-height: 460px;">
            <div class="g-img w-full h-full rounded-2xl overflow-hidden shadow-[0_20px_40px_rgba(0,0,0,0.3)]">
              <div class="g-img-inner w-full h-full relative">
                <img :src="pexels6" class="absolute w-full h-[130%] top-[-15%] object-cover will-change-transform"
                  loading="lazy" />
              </div>
            </div>
          </div>
          <!-- Card 3 -->
          <div class="g-img-wrap g-img-wrap-3 absolute will-change-transform"
            style="width: 16.8vw; height: 20.8vw; max-width: 345px; max-height: 418px;">
            <div class="g-img w-full h-full rounded-2xl overflow-hidden shadow-[0_20px_40px_rgba(0,0,0,0.3)]">
              <div class="g-img-inner w-full h-full relative">
                <img :src="pexels3" class="absolute w-full h-[130%] top-[-15%] object-cover will-change-transform"
                  loading="lazy" />
              </div>
            </div>
          </div>
          <!-- Card 4 -->
          <div class="g-img-wrap g-img-wrap-4 absolute will-change-transform"
            style="width: 23.2vw; height: 16.8vw; max-width: 469px; max-height: 336px;">
            <div class="g-img w-full h-full rounded-2xl overflow-hidden shadow-[0_20px_40px_rgba(0,0,0,0.3)]">
              <div class="g-img-inner w-full h-full relative">
                <img :src="pexels4" class="absolute w-full h-[130%] top-[-15%] object-cover will-change-transform"
                  loading="lazy" />
              </div>
            </div>
          </div>
          <!-- Card 5 (Center, Darker) -->
          <div class="g-img-wrap g-img-wrap-5 absolute will-change-transform"
            style="width: 24.8vw; height: 29.6vw; max-width: 499px; max-height: 604px;">
            <div class="g-img w-full h-full rounded-2xl overflow-hidden shadow-[0_20px_40px_rgba(0,0,0,0.3)]">
              <div class="g-img-inner w-full h-full relative bg-slate-900">
                <img :src="pexels2"
                  class="absolute w-full h-[130%] top-[-15%] object-cover will-change-transform brightness-75"
                  loading="lazy" />
              </div>
            </div>
          </div>
        </div>

        <!-- Text Group 2 -->
        <div class="gallery-text-2 absolute top-[50%] z-20 text-center w-full px-6 opacity-0 pointer-events-none">
          <h3 class="text-3xl md:text-5xl font-light leading-relaxed text-cn" v-html="$t('home.galleryText2')">
          </h3>
        </div>

        <!-- Thought Text (Group 3) -->
        <div class="gallery-text-3 absolute top-[50%] z-20 text-center w-full px-6 opacity-0 pointer-events-none">
          <h2 class="text-4xl md:text-6xl font-light mb-6 text-cn">{{ $t('home.galleryText3Title') }}</h2>
          <p class="text-xl md:text-2xl font-light text-cn">{{ $t('home.galleryText3Sub') }}</p>
        </div>
      </section>
    </div>

    <!-- ===== AI Banner (Showreel Replica) ===== -->
    <section class="showreel bg-slate-900 text-white h-[200vh] relative">
      <div class="showreel-container h-screen sticky top-0 overflow-hidden">

        <div class="showreel-text absolute inset-0 flex items-center justify-center z-20 pointer-events-none">
          <!-- Top Left Label -->
          <h2
            class="showreel-label absolute top-[10vw] left-[5vw] md:top-[3.2vw] md:left-[8.33vw] inline-flex items-center text-[3.5vw] md:text-[1vw] tracking-wider font-light m-0">
            <svg viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"
              class="w-[3vw] h-[3vw] md:w-[0.8vw] md:h-[0.8vw] mr-[2vw] md:mr-[0.5vw]">
              <path
                d="M7.41908 4.56679L6.13722 0L4.85418 4.92566L0 6L4.85418 7.25435L6.13722 12L7.3276 7.25435L12 6L7.41908 4.56679Z"
                fill="currentColor"></path>
            </svg>
            <div class="label-text text-cn">AI-Driven Platform</div>
          </h2>

          <!-- Main Title -->
          <h2
            class="showreel-title w-full flex justify-center text-[16vw] leading-none tracking-tight md:text-[10vw] md:tracking-[-0.2vw] font-light m-0 whitespace-nowrap overflow-hidden text-[#e4e0db]">
            <div class="title-mask title-left flex items-center justify-center will-change-transform px-[1vw]">
              <div class="title-line uppercase">AI</div>
            </div>
            <div class="title-mask title-right flex items-center justify-center will-change-transform px-[1vw]">
              <div class="title-line uppercase">Empower</div>
            </div>
          </h2>

          <!-- Smaller Text at Bottom -->
          <div
            class="showreel-body absolute bottom-[10vw] md:bottom-[3.2vw] w-[80vw] md:w-[35vw] text-center text-[3.5vw] md:text-[1.2vw] leading-relaxed font-light text-slate-400">
            <p class="text-cn">{{ $t('home.aiDesc') }}</p>
            <div class="mt-6 pointer-events-auto">
              <RouterLink to="/smart-procurement"
                class="inline-flex items-center gap-2 px-8 py-3 rounded-full bg-white/10 backdrop-blur-lg text-white font-medium hover:bg-white/20 transition-all shadow-[0_4px_24px_rgba(0,0,0,0.1)] hover:shadow-[0_4px_32px_rgba(255,255,255,0.15)] text-sm md:text-base hover:scale-105 duration-300 border border-white/20 group">
                {{ $t('home.aiBtnExplore') }}
                <ArrowRight class="w-4 h-4 md:w-5 md:h-5 group-hover:translate-x-1 transition-transform" />
              </RouterLink>
            </div>
          </div>
        </div>

        <!-- Video / Image Background -->
        <div
          class="showreel-video absolute top-1/2 left-1/2 w-[83.5vw] h-[46.6vw] md:w-full md:h-[100vh] -translate-x-1/2 -translate-y-1/2 scale-[0.25] opacity-30 z-10 will-change-transform overflow-hidden">
          <video autoplay loop muted playsinline src="@/assets/vedio2.mp4" class="w-full h-full object-cover" />
        </div>

        <!-- Overlay to ensure text readability when image scales up -->
        <div class="showreel-overlay absolute inset-0 bg-slate-900/60 z-10 opacity-100 pointer-events-none"></div>
      </div>
    </section>

    <!-- ===== Global Sourcing ===== -->
    <section class="global-sourcing-section relative h-screen flex items-center justify-center overflow-hidden">
      <!-- 背景变暗遮罩 -->
      <div class="global-sourcing-overlay absolute inset-0 bg-black/0 z-10 pointer-events-none"></div>
      <div class="text-center px-6 max-w-5xl mx-auto opacity-0 global-sourcing-text relative z-20">
        <h2 class="text-5xl md:text-7xl font-bold mb-8 text-white text-cn">{{ $t('home.globalSourcingTitle') }}</h2>
        <p class="text-lg md:text-2xl font-light leading-relaxed text-slate-200 text-cn">
          {{ $t('home.globalSourcingDesc') }}
        </p>
      </div>
    </section>

    <!-- ===== Pillars ===== -->
    <section class="py-20 px-6 bg-[#F5F5F7]">
      <div
        class="grid grid-cols-1 md:grid-cols-3 gap-12 divide-y md:divide-y-0 md:divide-x divide-slate-200 w-[85%] mx-auto">
        <div v-for="(pillar, idx) in PILLARS" :key="idx" v-motion :initial="{ opacity: 0, y: 30 }"
          :visible="{ opacity: 1, y: 0, transition: { duration: 600, delay: idx * 100 } }" :visible-once="true"
          class="pt-12 md:pt-0 md:px-12 flex flex-col items-center text-center">
          <div class="mb-6 text-slate-800">
            <component :is="pillar.icon" class="w-12 h-12" stroke-width="1.5" />
          </div>
          <h3 class="text-card-title font-semibold text-slate-900 text-cn">{{ $t(pillar.titleKey) }}</h3>
        </div>
      </div>
    </section>

    <!-- ===== Capabilities (Scroll-Driven Gallery) ===== -->
    <div ref="capWrap" class="capabilities-wrap bg-[#020617] text-white">
      <div class="capabilities-main">
        <div class="cap-slide">
          <div class="cap-slide-list">
            <div v-for="(cap, idx) in CAPABILITIES" :key="idx" class="cap-slide-item"
              :class="{ active: activeCapIndex === idx }">
              <!-- Background Thumbnail -->
              <div class="cap-thumb">
                <div class="cap-thumb-inner">
                  <Transition name="cap-bg-fade">
                    <img :key="cap.thumb" :src="cap.thumb" :alt="$t(cap.titleKey)" class="img-cover cap-bg-img"
                      loading="lazy" />
                  </Transition>
                </div>
                <div class="cap-thumb-overlay"></div>
              </div>

              <!-- Foreground Content -->
              <div class="cap-inner-grid">
                <div class="cap-text">
                  <h3 class="cap-title text-cn whitespace-nowrap">{{ $t(cap.titleKey) }}</h3>
                  <p class="cap-desc text-cn">{{ $t(cap.descKey) }}</p>
                  <RouterLink :to="cap.link" class="cap-link text-cn">
                    {{ $t('home.capLearnMore') }}
                    <ArrowRight class="w-4 h-4" />
                  </RouterLink>
                </div>

                <div class="cap-gallery">
                  <div class="cap-gallery-list">
                    <div v-for="(g, gi) in cap.gallery" :key="gi" class="cap-gallery-item"
                      @click="swapCapImage(idx, gi)">
                      <div class="cap-gallery-item-inner">
                        <Transition name="cap-bg-fade">
                          <img :key="g" :src="g" class="img-cover cap-bg-img" :alt="`${$t(cap.titleKey)}-${gi + 1}`"
                            loading="lazy" />
                        </Transition>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Bottom Progress Bar -->
        <div class="cap-bar">
          <div class="cap-bar-list">
            <div v-for="(cap, idx) in CAPABILITIES" :key="idx" class="cap-bar-item"
              :class="{ active: idx <= activeCapIndex }" @click="scrollToCap(idx)">
              <div class="cap-bar-item-inner">
                <div class="cap-bar-order text-cn">0{{ idx + 1 }}</div>
                <div class="cap-bar-prog">
                  <div class="cap-bar-prog-inner"></div>
                </div>
                <div class="cap-bar-name text-cn whitespace-nowrap">{{ $t(cap.titleKey) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Stats ===== -->
    <section class="py-14 px-6 w-full mx-auto bg-white relative overflow-hidden">
      <!-- 装饰性背景元素 -->
      <div
        class="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-5xl h-px bg-gradient-to-r from-transparent via-slate-200 to-transparent">
      </div>

      <div class="max-w-7xl mx-auto">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-12 md:gap-8">
          <div v-for="(stat, idx) in STATS" :key="idx" v-motion :initial="{ opacity: 0, y: 30 }"
            :visible="{ opacity: 1, y: 0, transition: { duration: 800, delay: idx * 150, ease: [0.16, 1, 0.3, 1] } }"
            :visible-once="true" class="relative flex flex-col items-center justify-center">

            <div v-if="idx !== STATS.length - 1"
              class="hidden md:block absolute right-0 top-1/2 -translate-y-1/2 w-px h-12 bg-slate-200">
            </div>

            <div class="text-title font-bold text-slate-800 mb-3">
              {{ stat.num }}
            </div>

            <div class="text-sm md:text-base font-medium text-slate-500 text-cn whitespace-nowrap">
              {{ $t(stat.labelKey) }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== Risk Map ===== -->
    <RiskDashboard />

    <!-- ===== CTA ===== -->
    <section class="py-32 px-6 text-center">
      <div v-motion :initial="{ opacity: 0, y: 30 }" :visible="{ opacity: 1, y: 0, transition: { duration: 600 } }"
        :visible-once="true" class="max-w-3xl mx-auto">
        <h2 class="text-title font-semibold text-white mb-6 text-cn">{{ $t('home.ctaTitle') }}</h2>
        <p class="text-content text-white/80 font-light mb-10 text-cn">{{ $t('home.ctaSub') }}</p>
        <RouterLink to="/join"
          class="inline-flex items-center gap-2 px-10 py-5 rounded-full bg-slate-900 text-white font-medium text-lg hover:bg-slate-800 hover:scale-105 active:scale-95 transition-all duration-300 shadow-xl shadow-slate-900/20">
          {{ $t('home.ctaBtn') }}
          <ArrowRight class="w-5 h-5" />
        </RouterLink>
      </div>
    </section>

  </div>
</template>

<script setup>
import { ref, reactive, computed, markRaw, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight, TrendingDown, ShieldCheck, Cpu, Zap
} from 'lucide-vue-next'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import heroVideo from '@/assets/vedio.mp4'
import bgIsland from '@/assets/689eb59b898d833188934eaa_73e849ea88e9080045a65a237f0aae56_Group2029-p-2000.jpg'
import pexels1 from '@/assets/pexels-ahmed-akacha-3313934-7152830.jpg'
import pexels2 from '@/assets/pexels-alexandrep-junior-12027846.jpg'
import pexels3 from '@/assets/pexels-karola-g-4386345.jpg'
import pexels4 from '@/assets/pexels-tomfisk-5470451.jpg'
import pexels5 from '@/assets/pexels-tiger-lily-4483608.jpg'
import pexels6 from '@/assets/pexels-thomas-parker-1272388137-24246926.jpg'
import capMatching from '@/assets/cap-matching.jpg'
import capCompliance from '@/assets/cap-compliance.jpg'
import capDelivery from '@/assets/cap-delivery.jpg'
import capRisk from '@/assets/cap-risk.jpg'
import capData from '@/assets/cap-data.jpg'
import capGallery1 from '@/assets/cap-gallery-1.jpg'
import capGallery2 from '@/assets/cap-gallery-2.jpg'
import capGallery3 from '@/assets/cap-gallery-3.jpg'
import capGallery4 from '@/assets/cap-gallery-4.jpg'
import capGallery5 from '@/assets/cap-gallery-5.jpg'
import RiskDashboard from '@/components/home/RiskDashboard.vue'

gsap.registerPlugin(ScrollTrigger)

const crisisSection = ref(null)
const capWrap = ref(null)
const activeCapIndex = ref(0)

function scrollToCap(idx) {
  const el = capWrap.value
  if (!el) return
  const total = CAPABILITIES.length
  const wrapTop = el.getBoundingClientRect().top + window.scrollY
  const scrollable = el.offsetHeight - window.innerHeight
  const target = wrapTop + (scrollable * idx) / total + scrollable * 0.02
  window.scrollTo({ top: target, behavior: 'smooth' })
}

onMounted(() => {
  // ===== Polycrisis Gallery Animation =====
  const galleryWrap = document.querySelector('.home-gallery-wrap');

  if (galleryWrap) {
    const tl = gsap.timeline({
      scrollTrigger: {
        trigger: galleryWrap,
        start: 'top top',
        end: 'bottom bottom',
        scrub: true
      }
    });

    // 0. Initial Setup for Images (Start from center, stacked)
    gsap.set('.g-img-wrap-1', { x: '-15vw', y: 0, rotation: -8, opacity: 0, zIndex: 1 });
    gsap.set('.g-img-wrap-2', { x: '15vw', y: 0, rotation: 8, opacity: 0, zIndex: 2 });
    gsap.set('.g-img-wrap-3', { x: '-10vw', y: 0, rotation: -5, opacity: 0, zIndex: 3 });
    gsap.set('.g-img-wrap-4', { x: '10vw', y: 0, rotation: 5, opacity: 0, zIndex: 4 });
    gsap.set('.g-img-wrap-5', { x: '-5vw', y: 0, rotation: -2, opacity: 0, zIndex: 5 });
    gsap.set('.g-img-wrap img', { scale: 1.5 }); // Start with images scaled up inside the card

    // 1. Phase 1: Scroll the long background image to the bottom & Fade IN Intro Text
    tl.add('phase1');
    tl.fromTo('.gallery-bg-img',
      { objectPosition: "50% 0%" },
      { objectPosition: "50% 100%", ease: 'none', duration: 4 },
      'phase1'
    );
    // Intro Text fades IN concurrently with the background scroll
    tl.fromTo('.gallery-text-0',
      { opacity: 0, scale: 1.04, yPercent: -50, y: 30 },
      { opacity: 1, scale: 1, yPercent: -50, y: 0, duration: 4, ease: 'none' },
      'phase1'
    );

    // 2. Phase 2: Intro Text fades OUT
    tl.add('phase2');
    tl.to('.gallery-text-0', { y: '-=30', opacity: 0, scale: 0.96, duration: 1.5, ease: 'power2.inOut' }, 'phase2');

    // Blur & Darken the background
    tl.to('.gallery-bg-img', { scale: 1.05, ease: 'power2.inOut', duration: 2 }, 'phase2');
    tl.to('.gallery-bg-blur', { opacity: 1, ease: 'power2.inOut', duration: 2 }, 'phase2');
    tl.to('.gallery-bg-overlay', { opacity: 0.8, ease: 'power2.inOut', duration: 2 }, 'phase2');

    // 3. Phase 3: Text 1 IN & All 5 Cards Spread
    tl.add('phase3', 'phase2+=1.0');

    // Text 1 IN (Slow fade in, scale down slightly)
    tl.fromTo('.gallery-text-1',
      { opacity: 0, scale: 1.04, yPercent: -50, y: 30, filter: 'blur(6px)', color: '#64748b' },
      { opacity: 1, scale: 1, yPercent: -50, y: 0, filter: 'blur(0px)', color: '#ffffff', duration: 1.5, ease: 'power2.out' },
      'phase3'
    );

    // Cards Fade IN
    tl.to(['.g-img-wrap-1', '.g-img-wrap-2', '.g-img-wrap-3', '.g-img-wrap-4', '.g-img-wrap-5'], { opacity: 1, duration: 0.5 }, 'phase3');

    // Cards Spread to Grid Positions (Quickly)
    tl.to('.g-img-wrap-1', { x: '-38vw', y: '-25vh', rotation: 0, duration: 2, ease: 'power3.out' }, 'phase3');
    tl.to('.g-img-wrap-2', { x: '38vw', y: '-35vh', rotation: 0, duration: 2, ease: 'power3.out' }, 'phase3');
    tl.to('.g-img-wrap-3', { x: '-42vw', y: '40vh', rotation: 0, duration: 2, ease: 'power3.out' }, 'phase3');
    tl.to('.g-img-wrap-4', { x: '42vw', y: '30vh', rotation: 0, duration: 2, ease: 'power3.out' }, 'phase3');
    tl.to('.g-img-wrap-5', { x: '0vw', y: '50vh', rotation: 0, duration: 2, ease: 'power3.out' }, 'phase3');

    // Cards Continuous Upward Movement (Parallax)
    tl.to('.g-img-wrap-1', { y: '-165vh', duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-2', { y: '-180vh', duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-3', { y: '-95vh', duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-4', { y: '-100vh', duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-5', { y: '-80vh', duration: 12, ease: 'none' }, 'phase3+=0.5');

    // Inner Image Parallax & Scale Down (moving opposite and shrinking)
    tl.to('.g-img-wrap-1 img', { yPercent: 15, scale: 1, duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-2 img', { yPercent: 25, scale: 1, duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-3 img', { yPercent: 10, scale: 1, duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-4 img', { yPercent: 20, scale: 1, duration: 12, ease: 'none' }, 'phase3+=0.5');
    tl.to('.g-img-wrap-5 img', { yPercent: 30, scale: 1, duration: 12, ease: 'none' }, 'phase3+=0.5');

    // Text 1 OUT
    tl.add('text1Out', 'phase3+=4.0');
    tl.to('.gallery-text-1', { y: '-=30', opacity: 0, scale: 0.96, duration: 1.5, ease: 'power2.inOut' }, 'text1Out');

    // 4. Phase 4: Text 2 IN
    tl.add('phase4', 'text1Out+=1.0');
    tl.fromTo('.gallery-text-2',
      { opacity: 0, scale: 1.04, yPercent: -50, y: 30, filter: 'blur(6px)', color: '#64748b' },
      { opacity: 1, scale: 1, yPercent: -50, y: 0, filter: 'blur(0px)', color: '#ffffff', duration: 1.5, ease: 'power2.out' },
      'phase4'
    );

    // Text 2 OUT & All Cards Fade OUT
    tl.add('phase4Out', 'phase4+=4.0');
    tl.to('.gallery-text-2', { y: '-=30', opacity: 0, scale: 0.96, duration: 1.5, ease: 'power2.inOut' }, 'phase4Out');
    tl.to(['.g-img-wrap-1', '.g-img-wrap-2', '.g-img-wrap-3', '.g-img-wrap-4', '.g-img-wrap-5'], { opacity: 0, duration: 1.5 }, 'phase4Out');

    // 5. Phase 5: Thought Text (Group 3) fades IN
    tl.fromTo('.gallery-text-3',
      { opacity: 0, scale: 1.04, yPercent: -50, y: 30, filter: 'blur(6px)', color: '#64748b' },
      { opacity: 1, scale: 1, yPercent: -50, y: 0, filter: 'blur(0px)', color: '#ffffff', duration: 2, ease: 'power2.out' },
      'phase4Out+=1.5'
    );

    // Hold at the end before scrolling to the next section
    tl.to({}, { duration: 1 });
  }

  // GSAP Showreel Animation
  const tl = gsap.timeline({
    scrollTrigger: {
      trigger: ".showreel",
      start: "top top",
      end: "bottom bottom",
      scrub: true,
    }
  });

  // Start apart and move to center
  tl.fromTo(".title-left", {
    x: "-15vw"
  }, {
    x: "0vw",
    ease: "none"
  }, 0);

  tl.fromTo(".title-right", {
    x: "15vw"
  }, {
    x: "0vw",
    ease: "none"
  }, 0);

  tl.to(".showreel-video", {
    scale: 1,
    opacity: 1,
    ease: "power1.inOut"
  }, 0);

  // Fade IN the small text elements as the video gets bigger
  tl.fromTo([".showreel-label", ".showreel-body"], {
    opacity: 0,
    color: "#94a3b8" // slate-400
  }, {
    opacity: 1,
    color: "#ffffff", // white
    ease: "power2.out"
  }, 0);

  tl.to(".showreel-overlay", {
    opacity: 0,
    ease: "power1.inOut"
  }, 0);

  // ===== Global Sourcing 文字淡入 + 背景变暗 =====
  const gsSection = document.querySelector('.global-sourcing-section')
  const gsOverlay = document.querySelector('.global-sourcing-overlay')
  const gsText = document.querySelector('.global-sourcing-text')

  if (gsSection && gsOverlay && gsText) {
    const tlGs = gsap.timeline({
      scrollTrigger: {
        trigger: gsSection,
        start: 'top top',
        end: '+=120%',
        scrub: true,
        pin: true,
      }
    })

    tlGs
      .fromTo(gsOverlay,
        { backgroundColor: 'rgba(0,0,0,0)' },
        { backgroundColor: 'rgba(0,0,0,0.55)', ease: 'none' },
        0
      )
      .fromTo(gsText,
        { opacity: 0, y: 30 },
        { opacity: 1, y: 0, ease: 'power2.inOut' },
        0
      )
  }

  // ===== Capabilities Scroll-Driven Gallery =====
  const capEl = capWrap.value
  if (capEl) {
    const progInners = capEl.querySelectorAll('.cap-bar-prog-inner')

    const capTl = gsap.timeline({
      scrollTrigger: {
        trigger: capEl,
        start: 'top top',
        end: 'bottom bottom',
        scrub: 1
      }
    })

    progInners.forEach((inner, idx) => {
      capTl.fromTo(inner, { scaleX: 0 }, {
        scaleX: 1,
        ease: 'none',
        duration: 0.8,
        onStart: () => { activeCapIndex.value = idx },
        onReverseComplete: () => { activeCapIndex.value = Math.max(0, idx - 1) }
      })
    })
  }
})

onUnmounted(() => {
  ScrollTrigger.getAll().forEach(st => st.kill())
})

const CRISIS_DATA = [
  {
    title: "地缘冲突与航运中断",
    sub: "战争与对峙，切断全球关键航道",
    bullets: [
      "红海危机：胡塞武装袭击商船，主力班轮绕行好望角，每航次增加 10-14 天",
      "霍尔木兹海峡：全球 20% 石油运输咽喉，美伊对峙持续升级",
      "俄乌冲突：黑海航运保险费用飙升，全球谷物出口通道受阻"
    ],
    img: "https://images.unsplash.com/photo-1605745341112-85968b19335b?auto=format&fit=crop&w=1400&q=85"
  },
  {
    title: "极端天气与自然灾害",
    sub: "一场洪水、一次飓风，就能掐断全球关键节点",
    bullets: [
      "泰国洪水（2011）：淹没全球 25% 硬盘产能，价格暴涨 180%，持续两年才恢复",
      "美国德州暴雪（2021）：三星/恩智浦/英飞凌三大芯片厂停产，汽车产业再遭重击",
      "全球极端天气频率较 1980 年代增加超 3 倍，每年造成供应链损失超 2,000 亿美元"
    ],
    img: "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=1400&q=85"
  },
  {
    title: "供应中断与单点依赖",
    sub: "一根钉子折了，整条产线就得停",
    bullets: [
      "日本 311 地震（2011）：全球汽车 MCU 芯片断供，丰田/本田停产数月，波及全球",
      "美国东海岸港口罢工（2024）：3 天损失超 15 亿美元，36 个港口同时瘫痪",
      "台积电一家占全球先进芯片产能 90%，地震、缺水随时可能触发全球断供"
    ],
    img: "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1400&q=85"
  },
  {
    title: "关税与贸易壁垒",
    sub: "关税一日三变，到岸成本随时失控",
    bullets: [
      "美国对华 301 关税覆盖约 3,700 亿美元商品，平均税率达 19.3%",
      "欧盟 CBAM 碳边境机制 2026 年正式征收，钢铁铝制品首当其冲",
      "芯片出口管制持续扩大：从光刻机延伸至半导体设备与 EDA 软件"
    ],
    img: "https://images.unsplash.com/photo-1681505531034-8d67054e07f6?auto=format&fit=crop&w=1400&q=85"
  },
  {
    title: "港口拥堵与物流瘫痪",
    sub: "一个堵点，全球货物在海面漂着回不了家",
    bullets: [
      "苏伊士运河\"长赐号\"搁浅（2021）：全球贸易每日损失 96 亿美元，422 艘船滞留",
      "美国西海岸港口罢工：洛杉矶/长滩港积压超 100 艘，货值超 240 亿美元",
      "巴拿马运河因干旱限行：日通行量从 36 艘骤降至 22 艘，运价应声暴涨"
    ],
    img: "https://images.unsplash.com/photo-1494412651409-8963ce7935a7?auto=format&fit=crop&w=1400&q=85"
  },
  {
    title: "需求剧烈波动",
    sub: "终端一点微动，上游就是一场海啸",
    bullets: [
      "全球芯片短缺（2020-2022）：汽车产业损失超 2,100 亿美元，交货周期长达 52 周",
      "疫情期间\"牛鞭效应\"：零售商恐慌囤货→制造商过度扩产→全链路库存崩盘",
      "集装箱运价单周暴涨 300%（2024）：需求预测完全失准，现货舱位一票难求"
    ],
    img: "https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1400&q=85"
  }
]

const PILLARS = [
  { titleKey: 'home.pillarCostTitle', icon: markRaw(TrendingDown) },
  { titleKey: 'home.pillarEfficiencyTitle', icon: markRaw(Zap) },
  { titleKey: 'home.pillarRiskTitle', icon: markRaw(ShieldCheck) }
]

const CAPABILITIES = reactive([
  {
    titleKey: 'home.capMatching',
    descKey: 'home.capMatchingDesc',
    thumb: capMatching,
    gallery: [capGallery1, capGallery3],
    link: "/smart-procurement"
  },
  {
    titleKey: 'home.capCompliance',
    descKey: 'home.capComplianceDesc',
    thumb: capCompliance,
    gallery: [capGallery4, capGallery3],
    link: "/smart-procurement"
  },
  {
    titleKey: 'home.capDelivery',
    descKey: 'home.capDeliveryDesc',
    thumb: capDelivery,
    gallery: [capGallery2, capGallery1],
    link: "/trusted-delivery"
  },
  {
    titleKey: 'home.capRisk',
    descKey: 'home.capRiskDesc',
    thumb: capRisk,
    gallery: [capGallery5, capGallery3],
    link: "/smart-procurement"
  },
  {
    titleKey: 'home.capData',
    descKey: 'home.capDataDesc',
    thumb: capData,
    gallery: [capGallery5, capGallery1],
    link: "/smart-procurement"
  }
])

function swapCapImage(slideIdx, galleryIdx) {
  const cap = CAPABILITIES[slideIdx]
  const clicked = cap.gallery[galleryIdx]
  cap.gallery[galleryIdx] = cap.thumb
  cap.thumb = clicked
}

const STATS = [
  { num: "1,300+", labelKey: 'home.statPartners' },
  { num: "50+", labelKey: 'home.statCountries' },
  { num: "17", labelKey: 'home.statYears' },
  { num: "10,000+", labelKey: 'home.statCases' }
]

</script>

<style scoped>
/* ===== Polycrisis Gallery Animation ===== */
.home-gallery-wrap {
  position: relative;
  height: 950vh;
  background: #020617;
}

.g-img-wrap {
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

.home-gallery {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== Capabilities Scroll Gallery ===== */
.img-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.capabilities-wrap {
  position: relative;
  height: 600vh;
}

.capabilities-main {
  z-index: 2;
  height: 100vh;
  position: sticky;
  top: 0;
  overflow: hidden;
}

.cap-slide {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.cap-slide-list {
  display: grid;
  grid-template-rows: auto;
  grid-template-columns: minmax(1px, 1fr);
  grid-auto-columns: 1fr;
  width: 100%;
  height: 100%;
}

.cap-slide-item {
  grid-area: 1 / 1 / 2 / 2;
  width: 100%;
  height: 100%;
  position: relative;
  pointer-events: none;
}

.cap-slide-item.active {
  pointer-events: auto;
}

/* Background thumbnail */
.cap-thumb {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
}

.cap-thumb-inner {
  width: 100%;
  height: 100%;
  opacity: 0;
  transform: scale(1.12);
  transition: transform 1.4s cubic-bezier(.445, .05, .55, .95),
    opacity 1.2s cubic-bezier(.445, .05, .55, .95);
}

.cap-slide-item.active .cap-thumb-inner {
  opacity: 1;
  transform: scale(1);
}

.cap-bg-img {
  position: absolute;
  inset: 0;
}

/* Crossfade when the background image is swapped */
.cap-bg-fade-enter-active,
.cap-bg-fade-leave-active {
  transition: opacity .8s cubic-bezier(.445, .05, .55, .95),
    transform .9s cubic-bezier(.445, .05, .55, .95);
}

.cap-bg-fade-enter-from {
  opacity: 0;
  transform: scale(1.06);
}

.cap-bg-fade-leave-to {
  opacity: 0;
}

.cap-bg-fade-leave-active {
  z-index: 1;
}

.cap-thumb-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: linear-gradient(to right, rgba(2, 6, 23, 0.55) 0%, rgba(2, 6, 23, 0.3) 45%, rgba(2, 6, 23, 0.1) 100%);
}

/* Foreground grid */
.cap-inner-grid {
  position: relative;
  z-index: 3;
  height: 100%;
  width: 100%;
  max-width: 90rem;
  margin: 0 auto;
  padding: 0 2rem;
  display: grid;
  grid-template-columns: repeat(16, 1fr);
  column-gap: 2rem;
}

/* Text column */
.cap-text {
  grid-column: 1 / 8;
  align-self: center;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-top: -6vh;
}

.cap-title {
  font-size: clamp(2.2rem, 3.4vw + 1rem, 4rem);
  font-weight: 600;
  line-height: 1.1;
  color: #fff;
  margin-bottom: 1.8rem;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.45);
  opacity: 0;
  transform: translateY(40px);
  transition: opacity .8s cubic-bezier(.445, .05, .55, .95),
    transform .8s cubic-bezier(.445, .05, .55, .95);
}

.cap-desc {
  font-size: clamp(1rem, 0.6vw + 0.85rem, 1.2125rem);
  line-height: 1.85;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.9);
  max-width: 34rem;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.5);
  opacity: 0;
  transform: translateY(40px);
  transition: opacity .8s cubic-bezier(.445, .05, .55, .95),
    transform .8s cubic-bezier(.445, .05, .55, .95);
}

.cap-link {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 2.4rem;
  padding: 0.85rem 1.75rem;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(8px);
  color: #fff;
  font-weight: 500;
  opacity: 0;
  transform: translateY(40px);
  transition: opacity .8s cubic-bezier(.445, .05, .55, .95),
    transform .8s cubic-bezier(.445, .05, .55, .95),
    background-color .3s ease;
}

.cap-link:hover {
  background: rgba(255, 255, 255, 0.18);
}

.cap-slide-item.active .cap-title {
  opacity: 1;
  transform: none;
  transition-delay: .15s;
}

.cap-slide-item.active .cap-desc {
  opacity: 1;
  transform: none;
  transition-delay: .32s;
}

.cap-slide-item.active .cap-link {
  opacity: 1;
  transform: none;
  transition-delay: .44s;
}

/* Gallery column */
.cap-gallery {
  grid-column: 9 / 17;
  align-self: center;
  height: min(15rem, 24vh);
  margin-top: -4vh;
}

.cap-gallery-list {
  display: flex;
  align-items: stretch;
  justify-content: flex-start;
  height: 100%;
  overflow: hidden;
  column-gap: 1.5rem;
}

.cap-gallery-item {
  flex: 1 1 0;
  min-width: 0;
  height: 100%;
  cursor: pointer;
  transform: translateY(110%);
  transition: transform 1s cubic-bezier(.445, .05, .55, .95);
}

.cap-gallery-item-inner {
  transition: transform .5s cubic-bezier(.445, .05, .55, .95),
    box-shadow .5s cubic-bezier(.445, .05, .55, .95);
}

.cap-gallery-item:hover .cap-gallery-item-inner {
  transform: translateY(-8px);
  box-shadow: 0 28px 50px rgba(0, 0, 0, 0.45);
}

.cap-slide-item.active .cap-gallery-item {
  transform: translateY(0%);
}

.cap-slide-item.active .cap-gallery-list .cap-gallery-item:nth-child(1) {
  transition-duration: .6s;
}

.cap-slide-item.active .cap-gallery-list .cap-gallery-item:nth-child(2) {
  transition-duration: .78s;
}

.cap-slide-item.active .cap-gallery-list .cap-gallery-item:nth-child(3) {
  transition-duration: .96s;
}

.cap-gallery-item-inner {
  width: 100%;
  height: 100%;
  border-radius: 0.6rem;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.35);
}

/* Bottom progress bar */
.cap-bar {
  position: absolute;
  bottom: 3rem;
  left: 0;
  width: 100%;
  z-index: 4;
}

.cap-bar-list {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  column-gap: 1.5rem;
  max-width: 90rem;
  margin: 0 auto;
  padding: 0 2rem;
}

.cap-bar-item {
  cursor: pointer;
  color: #fff;
}

.cap-bar-item-inner {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  row-gap: 1rem;
}

.cap-bar-order {
  font-size: 0.95rem;
  opacity: .45;
  transition: opacity .35s;
}

.cap-bar-prog {
  width: 100%;
  height: 2px;
  background: rgba(255, 255, 255, 0.2);
  overflow: hidden;
}

.cap-bar-prog-inner {
  width: 100%;
  height: 100%;
  background: #fff;
  transform-origin: 0% 50%;
  transform: scaleX(0);
}

.cap-bar-name {
  font-size: clamp(1rem, 1vw + 0.6rem, 1.35rem);
  font-weight: 500;
  opacity: .45;
  transition: opacity .35s;
}

.cap-bar-item.active .cap-bar-order,
.cap-bar-item.active .cap-bar-name {
  opacity: 1;
}

/* Responsive */
@media (max-width: 767px) {
  .cap-inner-grid {
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding: 0 1.5rem;
    padding-bottom: 14vh;
  }

  .cap-text {
    margin-top: 0;
    align-self: flex-start;
  }

  .cap-desc {
    max-width: none;
  }

  .cap-gallery {
    margin-top: 2.5rem;
    height: min(9rem, 18vh);
  }

  .cap-gallery-list {
    column-gap: 1rem;
  }

  .cap-bar {
    bottom: 1.5rem;
  }

  .cap-bar-list {
    column-gap: 0.75rem;
    padding: 0 1.5rem;
  }

  .cap-bar-name {
    font-size: 0.8rem;
  }

  .cap-bar-order {
    font-size: 0.75rem;
  }
}
</style>
