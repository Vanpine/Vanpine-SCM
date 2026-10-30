<template>
  <div class="bg-[#F5F5F7] min-h-screen text-slate-900 selection:bg-blue-200">
    <!-- Hero -->
    <section
      class="relative h-screen min-h-[800px] w-full flex items-center justify-center overflow-hidden bg-[#020617]">
      <!-- Background Elements -->
      <div class="absolute inset-0 z-0">
        <!-- High-tech Node/Network Image -->
        <img
          src="https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=2500&q=80"
          alt="Smart Procurement Network"
          class="w-full h-full object-cover object-center opacity-70 mix-blend-screen scale-105" />
        <!-- Gradient Overlays -->
        <div class="absolute inset-0 bg-gradient-to-b from-[#020617]/30 via-transparent to-[#020617]/60"></div>

        <!-- Glowing Orbs for Tech Feel -->
        <div class="absolute top-1/4 left-1/4 w-[40rem] h-[40rem] bg-blue-600/20 rounded-full blur-[120px]"></div>
        <div class="absolute bottom-1/4 right-1/4 w-[40rem] h-[40rem] bg-indigo-600/20 rounded-full blur-[120px]"></div>

        <!-- Grid Pattern -->
        <div
          class="absolute inset-0 bg-[linear-gradient(to_right,rgba(255,255,255,0.03)_1px,transparent_1px),linear-gradient(to_bottom,rgba(255,255,255,0.03)_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_80%_50%_at_50%_50%,#000_70%,transparent_100%)]">
        </div>
      </div>

      <div class="relative z-10 max-w-6xl mx-auto px-6 text-center -mt-20">
        <h1 v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 100, ease: [0.16, 1, 0.3, 1] } }"
          class="text-6xl md:text-8xl font-semibold tracking-tight text-white mb-6 text-cn">
          {{ $t('smartProcurement.heroTitle') }}
        </h1>

        <p v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 200, ease: [0.16, 1, 0.3, 1] } }"
          class="text-xl md:text-3xl text-white/90 font-light mt-12 mb-10 tracking-wide max-w-3xl mx-auto text-cn">
          {{ $t('smartProcurement.heroSub') }}
        </p>
      </div>

      </section>

      <!-- Content -->
      <div class="relative z-20 bg-[#F5F5F7] pt-40 pb-0">

      <!-- Pipeline -->
      <section class="w-full mb-40">
        <div class="text-center mb-24 px-6">
          <h2 class="text-title font-medium text-slate-900 text-cn">{{ $t('smartProcurement.pipelineTitle') }}</h2>
        </div>

        <!-- Carousel -->
        <div ref="carouselRef"
          @mousedown="onMouseDown" @mousemove="onMouseMove" @mouseup="onMouseUp" @mouseleave="onMouseLeave" @mouseenter="onMouseEnter"
          class="flex gap-5 overflow-x-auto cursor-grab active:cursor-grabbing select-none pb-4 pipeline-carousel">
          <div v-for="(stage, index) in [...pipelineStages, ...pipelineStages]" :key="index"
            class="shrink-0 w-[85vw] md:w-[calc(50%-0.625rem)] lg:w-[calc(33.333%-0.834rem)] group/card relative rounded-[2rem] overflow-hidden shadow-[0_2px_12px_rgba(0,0,0,0.03)] hover:shadow-[0_16px_40px_rgba(0,0,0,0.08)] border border-slate-100 transition-all duration-500 h-[650px] flex flex-col">

            <!-- Full-bleed Background Image -->
            <img :src="stage.image" :alt="stage.title" draggable="false"
              class="absolute inset-0 w-full h-full object-cover pointer-events-none group-hover/card:scale-105 transition-transform duration-1000 ease-[cubic-bezier(0.16,1,0.3,1)]" loading="lazy" />

            <!-- Semi-transparent text panel -->
            <div class="relative z-10 mt-auto p-8 md:p-10 bg-slate-900/30 backdrop-blur-xl border-t border-white/10">
              <h3 class="text-3xl md:text-4xl font-bold text-white mb-8 text-cn">{{ $t(stage.titleKey) }}</h3>
              <ul class="space-y-4">
                <li v-for="(detail, idx) in stage.bulletKeys" :key="idx" class="flex items-start">
                  <div class="mt-2.5 mr-4 shrink-0 w-2 h-2 rounded-full bg-white/40"></div>
                  <span class="text-lg text-white/90 leading-relaxed text-cn">{{ $t(detail) }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <!-- Core Tech Intro Transition -->
      <div class="w-full h-screen core-tech-intro-wrap relative z-20">
        <div class="w-full h-full core-tech-intro-inner overflow-hidden relative">
          <img
            :src="pexelsTomfisk"
            alt="Core Tech"
            class="w-full h-full object-cover core-tech-intro-img"
            loading="lazy"
          />
          <div class="absolute inset-0 bg-slate-900/25"></div>
        </div>
      </div>

      <!-- Core Tech (Animation Layout) -->
      <div class="relative bg-[#F5F5F7] z-10 core-tech-wrap">
        <!-- 1. 产生 Sticky 停留空间的空块 -->
        <div class="h-[100vh]"></div>
        
        <section class="sticky bottom-0 pt-32 bg-[#F5F5F7] overflow-hidden">
          <div class="text-center mb-24">
            <h2 class="text-title font-medium text-slate-900 text-cn">{{ $t('smartProcurement.coreTechTitle') }}</h2>
          </div>

          <div class="flex flex-col gap-[15rem] mb-[20rem] max-w-[1400px] mx-auto px-8">
            <!-- Item 1 -->
            <div class="flex items-center justify-between gap-12 md:gap-32 lg:gap-40 flex-col md:flex-row core-tech-item">
              <div class="w-full md:w-[45%] max-w-[550px]">
                <div class="rounded-2xl overflow-hidden relative aspect-[3/4] shadow-[0_20px_50px_rgba(0,0,0,0.1)]">
                  <img :src="coreCapabilities[0].image" :alt="$t(coreCapabilities[0].titleKey)" class="w-full h-[130%] object-cover absolute left-0 top-0 parallax-img" loading="lazy" />
                </div>
              </div>
              <div class="w-full md:w-[55%] max-w-[600px]">
                <h3 class="text-3xl md:text-4xl font-bold mb-6 text-slate-900 text-cn">{{ $t(coreCapabilities[0].titleKey) }}</h3>
                <p class="text-lg md:text-xl leading-relaxed text-slate-600 text-cn text-justify">{{ $t(coreCapabilities[0].descKey) }}</p>
              </div>
            </div>

            <!-- Item 2 -->
            <div class="flex items-center justify-between gap-12 md:gap-32 lg:gap-40 flex-col md:flex-row-reverse core-tech-item">
              <div class="w-full md:w-[45%] max-w-[550px]">
                <div class="rounded-2xl overflow-hidden relative aspect-[3/4] shadow-[0_20px_50px_rgba(0,0,0,0.1)]">
                  <img :src="coreCapabilities[1].image" :alt="$t(coreCapabilities[1].titleKey)" class="w-full h-[130%] object-cover absolute left-0 top-0 parallax-img" loading="lazy" />
                </div>
              </div>
              <div class="w-full md:w-[55%] max-w-[600px]">
                <h3 class="text-3xl md:text-4xl font-bold mb-6 text-slate-900 text-cn">{{ $t(coreCapabilities[1].titleKey) }}</h3>
                <p class="text-lg md:text-xl leading-relaxed text-slate-600 text-cn text-justify">{{ $t(coreCapabilities[1].descKey) }}</p>
              </div>
            </div>

            <!-- Item 3 -->
            <div class="flex items-center justify-between gap-12 md:gap-32 lg:gap-40 flex-col md:flex-row core-tech-item">
              <div class="w-full md:w-[45%] max-w-[550px]">
                <div class="rounded-2xl overflow-hidden relative aspect-[3/4] shadow-[0_20px_50px_rgba(0,0,0,0.1)]">
                  <img :src="coreCapabilities[2].image" :alt="$t(coreCapabilities[2].titleKey)" class="w-full h-[130%] object-cover absolute left-0 top-0 parallax-img" loading="lazy" />
                </div>
              </div>
              <div class="w-full md:w-[55%] max-w-[600px]">
                <h3 class="text-3xl md:text-4xl font-bold mb-6 text-slate-900 text-cn">{{ $t(coreCapabilities[2].titleKey) }}</h3>
                <p class="text-lg md:text-xl leading-relaxed text-slate-600 text-cn text-justify">{{ $t(coreCapabilities[2].descKey) }}</p>
              </div>
            </div>
          </div>

          <!-- Item 4 (Thumb) -->
          <div class="core-tech-thumb h-[100vh] overflow-hidden relative">
            <div class="core-tech-thumb-inner w-full h-full" style="clip-path: inset(14% 37.35% 14% 37.35% round 128px);">
              <img :src="coreCapabilities[3].image" :alt="$t(coreCapabilities[3].titleKey)" class="w-full h-full object-cover scale-[1.2] origin-top thumb-img" loading="lazy" />
              <div class="absolute inset-0 bg-slate-900/60 flex flex-col items-center justify-center text-center px-6 opacity-0 thumb-text">
                <h3 class="text-4xl md:text-5xl font-bold mb-6 text-white text-cn">{{ $t(coreCapabilities[3].titleKey) }}</h3>
                <p class="text-xl md:text-2xl leading-relaxed text-slate-200 max-w-3xl text-cn">{{ $t(coreCapabilities[3].descKey) }}</p>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- AI Smart Procurement Story -->
      <section class="relative bg-slate-900 z-20 story-main-wrap pt-32 pb-0 overflow-clip">
        <div class="text-center mb-24 relative z-10">
          <h2 class="text-title font-medium text-white text-cn">{{ $t('smartProcurement.aiStoryTitle') }}</h2>
        </div>

        <div class="story-main">
          <div class="max-w-[1400px] mx-auto px-8 relative">
            <!-- 进度条 -->
            <div class="absolute left-1/2 top-0 -translate-x-1/2 w-[2px] h-full bg-slate-700/50 z-0 flex flex-col items-center story-main-prog">
              <div class="w-full h-full bg-blue-500 origin-top scale-y-0 story-main-prog-inner"></div>
            </div>

            <div class="flex flex-col gap-[25rem] pt-[10rem] pb-[10rem] story-main-list relative z-10">
              <!-- Item 1 -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-12 md:gap-32 lg:gap-40 items-center story-main-item">
                <div class="story-main-item-img rounded-[4rem] overflow-hidden relative z-10">
                  <div class="aspect-[4/3] rounded-md overflow-hidden relative story-main-item-img-inner">
                    <img :src="aiStoryData[0].image" class="w-full h-full object-cover block" loading="lazy" />
                  </div>
                </div>
                <div class="flex flex-col justify-start items-start mt-8 story-main-item-content">
                  <h3 class="text-4xl font-bold text-white mb-6 text-cn">{{ $t(aiStoryData[0].titleKey) }}</h3>
                  <p class="text-xl leading-relaxed text-slate-400 max-w-[500px] text-cn">{{ $t(aiStoryData[0].descKey) }}</p>
                </div>
              </div>

              <!-- Item 2 (Reverse) -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-12 md:gap-32 lg:gap-40 items-center story-main-item">
                <div class="flex flex-col justify-start items-start md:items-end text-left md:text-right mt-8 story-main-item-content order-2 md:order-1">
                  <h3 class="text-4xl font-bold text-white mb-6 text-cn">{{ $t(aiStoryData[1].titleKey) }}</h3>
                  <p class="text-xl leading-relaxed text-slate-400 max-w-[500px] text-cn">{{ $t(aiStoryData[1].descKey) }}</p>
                </div>
                <div class="story-main-item-img rounded-[4rem] overflow-hidden relative z-10 order-1 md:order-2">
                  <div class="aspect-[4/3] rounded-md overflow-hidden relative story-main-item-img-inner">
                    <img :src="aiStoryData[1].image" class="w-full h-full object-cover block" loading="lazy" />
                  </div>
                </div>
              </div>

              <!-- Item 3 -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-12 md:gap-32 lg:gap-40 items-center story-main-item">
                <div class="story-main-item-img rounded-[4rem] overflow-hidden relative z-10">
                  <div class="aspect-[4/3] rounded-md overflow-hidden relative story-main-item-img-inner">
                    <img :src="aiStoryData[2].image" class="w-full h-full object-cover block" loading="lazy" />
                  </div>
                </div>
                <div class="flex flex-col justify-start items-start mt-8 story-main-item-content">
                  <h3 class="text-4xl font-bold text-white mb-6 text-cn">{{ $t(aiStoryData[2].titleKey) }}</h3>
                  <p class="text-xl leading-relaxed text-slate-400 max-w-[500px] text-cn">{{ $t(aiStoryData[2].descKey) }}</p>
                </div>
              </div>

              <!-- Item 4 (Reverse) -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-12 md:gap-32 lg:gap-40 items-center story-main-item">
                <div class="flex flex-col justify-start items-start md:items-end text-left md:text-right mt-8 story-main-item-content order-2 md:order-1">
                  <h3 class="text-4xl font-bold text-white mb-6 text-cn">{{ $t(aiStoryData[3].titleKey) }}</h3>
                  <p class="text-xl leading-relaxed text-slate-400 max-w-[500px] text-cn">{{ $t(aiStoryData[3].descKey) }}</p>
                </div>
                <div class="story-main-item-img rounded-[4rem] overflow-hidden relative z-10 order-1 md:order-2">
                  <div class="aspect-[4/3] rounded-md overflow-hidden relative story-main-item-img-inner">
                    <img :src="aiStoryData[3].image" class="w-full h-full object-cover block" loading="lazy" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 压轴大图 + CTA -->
        <div class="w-full h-screen story-final-img-wrap relative z-10">
          <div class="w-full h-full story-final-img-inner overflow-hidden relative">
            <img src="https://images.unsplash.com/photo-1505228395891-9a51e7e86bf6?auto=format&fit=crop&w=2000&q=80" class="w-full h-full object-cover story-final-img" loading="lazy" />
            <div class="absolute inset-0 bg-slate-900/30"></div>
            <div class="absolute inset-0 z-10 flex flex-col items-center justify-center text-center px-6 story-final-text">
              <h2 class="text-4xl md:text-6xl lg:text-7xl font-semibold text-white mb-8 text-cn">
                {{ $t('smartProcurement.ctaTitle') }}
              </h2>
              <p class="text-xl md:text-2xl text-white/90 font-light mb-10 text-cn">
                {{ $t('smartProcurement.ctaSub') }}
              </p>
              <RouterLink to="/join"
                class="inline-flex items-center gap-2 px-10 py-5 rounded-full bg-white/10 backdrop-blur-md border border-white/20 text-white font-medium text-lg hover:bg-white/20 hover:scale-105 active:scale-95 transition-all duration-300 shadow-xl shadow-black/10">
                {{ $t('smartProcurement.ctaBtn') }}
                <ArrowRight class="w-5 h-5" />
              </RouterLink>
            </div>
          </div>
        </div>
      </section>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Clock, Zap } from 'lucide-vue-next'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import znzc1 from '@/assets/znzc1.jpg'
import znzc2 from '@/assets/znzc2.jpg'
import pexelsTomfisk from '@/assets/pexels-tomfisk-5470451.jpg'

gsap.registerPlugin(ScrollTrigger)

let ctx

onMounted(() => {
  ctx = gsap.context(() => {
    // 1. Core Tech Parallax Images
    const parallaxImages = document.querySelectorAll('.parallax-img')
    parallaxImages.forEach(img => {
      gsap.fromTo(img, 
        { yPercent: -15, scale: 1.4 },
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

    // 1.5 Core Tech Intro Transition
    const introWrap = document.querySelector('.core-tech-intro-wrap')
    if (introWrap) {
      const tlIntro = gsap.timeline({
        scrollTrigger: {
          trigger: introWrap,
          start: 'top bottom',
          end: 'bottom top',
          scrub: true,
        }
      })

      tlIntro.fromTo('.core-tech-intro-inner',
        { clipPath: 'inset(22% 28% 22% 28% round 2.5rem)' },
        { clipPath: 'inset(0% 0% 0% 0% round 0px)', ease: 'none' }
      )
      .fromTo('.core-tech-intro-img',
        { scale: 1.35 },
        { scale: 1, ease: 'none' },
        '<=0'
      )
      .to('.intro-subtitle',
        { opacity: 1, scale: 1, ease: 'power2.out', duration: 0.6 },
        '<=0.5'
      )
    }

    // 2. Core Tech Thumb Reveal
    const thumb = document.querySelector('.core-tech-thumb')
    const thumbInner = document.querySelector('.core-tech-thumb-inner')
    const thumbImg = document.querySelector('.thumb-img')
    const thumbText = document.querySelector('.thumb-text')

    if (thumb && thumbInner && thumbImg) {
      const remToPx = 8 * 16 // 8rem border radius
      const tlScaleImage = gsap.timeline({
        scrollTrigger: {
          trigger: thumb,
          start: "top bottom",
          end: () => `bottom+=${window.innerHeight * 1.3} bottom`,
          scrub: true
        }
      })

      tlScaleImage
        .fromTo(thumbInner,
          { clipPath: `inset(14% 37.35% 14% 37.35% round ${remToPx}px)` },
          { clipPath: `inset(0% 0% 0% 0% round 0px)` }
        )
        .fromTo(thumbImg,
          { scale: 1.2, transformOrigin: 'top' },
          { scale: 1, transformOrigin: 'top' },
          "<=0"
        )
        .fromTo(thumbText,
          { opacity: 0 },
          { opacity: 1 },
          "<=0.5"
        )
    }
    // 3. AI Story Animation
    const storyItems = document.querySelectorAll('.story-main-item')
    const rem = (val) => val * 16

    storyItems.forEach((el, idx) => {
      const imgContainer = el.querySelector('.story-main-item-img')
      const img = el.querySelector('.story-main-item-img img')
      const content = el.querySelector('.story-main-item-content')

      const isEven = idx % 2 === 0
      const clipPathFrom = isEven
        ? `inset(0% 0% 100% 100% round ${rem(4)}px)`
        : `inset(0% 100% 100% 0% round ${rem(4)}px)`

      const transformFrom = isEven
        ? { xPercent: 25, yPercent: -25 }
        : { xPercent: -25, yPercent: -25 }

      // 预置初始状态，杜绝闪烁
      gsap.set(imgContainer, { clipPath: clipPathFrom })
      gsap.set(img, { ...transformFrom, scale: 1.2 })
      gsap.set(content, { opacity: 0, y: 40 })

      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: el,
          start: 'top 60%',
          end: 'center center',
          scrub: 1
        }
      })

      tl.to(imgContainer,
        { clipPath: `inset(0% 0% 0% 0% round ${rem(4)}px)`, ease: 'power1.inOut', duration: 1 }
      )
      .to(img,
        { xPercent: 0, yPercent: 0, scale: 1, ease: 'power1.inOut', duration: 1 },
        '<=0'
      )
      .to(content,
        { opacity: 1, y: 0, ease: 'power2.out', duration: 0.8 },
        '<=0.3'
      )
    })

    // Final Background Image Scaling
    const finalWrap = document.querySelector('.story-final-img-wrap')
    if (finalWrap) {
      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: finalWrap,
          start: `top top`,
          end: `+=150%`, // 页面在这里“暂停”并滚动1.5个屏幕高度来执行动画
          scrub: true,
          pin: true, // 开启钉住效果！
        }
      })

      tl.fromTo('.story-final-img-inner',
        { clipPath: `inset(20% 25% 20% 25% round 2rem)` },
        { clipPath: `inset(0% 0% 0% 0% round 0px)`, ease: 'none' }
      )
      .fromTo('.story-final-img',
        { scale: 1.3 },
        { scale: 1, ease: 'none' },
        "<=0"
      )
      .fromTo('.story-final-text',
        { opacity: 0, y: 30 },
        { opacity: 1, y: 0, ease: 'power2.out' },
        "<=0.5"
      )
    }

    // Progress line
    gsap.fromTo('.story-main-prog-inner', 
      { scaleY: 0 }, 
      { 
        scaleY: 1, 
        ease: 'none',
        scrollTrigger: {
          trigger: '.story-main-prog',
          start: 'top 60%',
          end: 'bottom 15%',
          scrub: true
        }
      }
    )
  })

  // Keep existing IntersectionObserver for other elements if any
  const els = document.querySelectorAll('[data-reveal]')
  const obs = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible')
        obs.unobserve(entry.target)
      }
    })
  }, { threshold: 0.1 })
  els.forEach(el => obs.observe(el))

  startAutoScroll()
})

onUnmounted(() => {
  if (ctx) ctx.revert()
  if (autoScrollId) cancelAnimationFrame(autoScrollId)
})

// Carousel drag-to-scroll & Auto-play
const carouselRef = ref(null)
let isDown = false, startX = 0, scrollLeftVal = 0
let autoScrollId = null
let isHovering = false

function onMouseDown(e) {
  isDown = true
  startX = e.pageX - carouselRef.value.offsetLeft
  scrollLeftVal = carouselRef.value.scrollLeft
  carouselRef.value.style.cursor = 'grabbing'
}
function onMouseMove(e) {
  if (!isDown) return
  e.preventDefault()
  const x = e.pageX - carouselRef.value.offsetLeft
  const walk = (x - startX) * 1.5
  carouselRef.value.scrollLeft = scrollLeftVal - walk
}
function onMouseUp() {
  isDown = false
  carouselRef.value.style.cursor = 'grab'
}
function onMouseEnter() {
  isHovering = true
}
function onMouseLeave() {
  isDown = false
  carouselRef.value.style.cursor = 'grab'
  isHovering = false
}

const startAutoScroll = () => {
  const scroll = () => {
    if (carouselRef.value && !isHovering && !isDown) {
      carouselRef.value.scrollLeft += 1 // 滚动速度
      
      // 无缝循环逻辑：计算第一组卡片的精确总宽度
      if (carouselRef.value.children.length > 4) {
        const firstItem = carouselRef.value.children[0]
        const fifthItem = carouselRef.value.children[4]
        const loopWidth = fifthItem.offsetLeft - firstItem.offsetLeft
        
        if (carouselRef.value.scrollLeft >= loopWidth) {
          carouselRef.value.scrollLeft -= loopWidth
        }
      }
    }
    autoScrollId = requestAnimationFrame(scroll)
  }
  autoScrollId = requestAnimationFrame(scroll)
}

const pipelineStages = [
  {
    id: "01",
    titleKey: "smartProcurement.stageSourcing",
    subtitle: "Intelligent Sourcing",
    bulletKeys: [
      "smartProcurement.sourcingBullet1",
      "smartProcurement.sourcingBullet2",
      "smartProcurement.sourcingBullet3"
    ],
    image: "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1200&q=80"
  },
  {
    id: "02",
    titleKey: "smartProcurement.stageInquiry",
    subtitle: "Automated Inquiry",
    bulletKeys: [
      "smartProcurement.inquiryBullet1",
      "smartProcurement.inquiryBullet2",
      "smartProcurement.inquiryBullet3"
    ],
    image: "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=1200&q=80"
  },
  {
    id: "03",
    titleKey: "smartProcurement.stageComparison",
    subtitle: "Smart Price Comparison",
    bulletKeys: [
      "smartProcurement.comparisonBullet1",
      "smartProcurement.comparisonBullet2",
      "smartProcurement.comparisonBullet3"
    ],
    image: "https://images.unsplash.com/photo-1642104704074-907c0698cbd9?auto=format&fit=crop&w=1200&q=80"
  },
  {
    id: "04",
    titleKey: "smartProcurement.stageContract",
    subtitle: "Contract Assistance",
    bulletKeys: [
      "smartProcurement.contractBullet1",
      "smartProcurement.contractBullet2",
      "smartProcurement.contractBullet3"
    ],
    image: "https://images.unsplash.com/photo-1639322537504-6427a16b0a28?auto=format&fit=crop&w=1200&q=80"
  },
]

const coreCapabilities = [
  {
    titleKey: 'smartProcurement.coreDataView',
    descKey: 'smartProcurement.coreDataViewDesc',
    image: znzc1
  },
  {
    titleKey: 'smartProcurement.coreAIEngine',
    descKey: 'smartProcurement.coreAIEngineDesc',
    image: 'https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&w=800&q=80'
  },
  {
    titleKey: 'smartProcurement.coreRiskBlock',
    descKey: 'smartProcurement.coreRiskBlockDesc',
    image: 'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=800&q=80'
  },
  {
    titleKey: 'smartProcurement.coreSupplierCollab',
    descKey: 'smartProcurement.coreSupplierCollabDesc',
    image: znzc2
  }
]

const aiStoryData = [
  {
    titleKey: "smartProcurement.storyMatching",
    descKey: "smartProcurement.storyMatchingDesc",
    image: "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80"
  },
  {
    titleKey: "smartProcurement.storyInquiry",
    descKey: "smartProcurement.storyInquiryDesc",
    image: "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=800&q=80"
  },
  {
    titleKey: "smartProcurement.storyComparison",
    descKey: "smartProcurement.storyComparisonDesc",
    image: "https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80"
  },
  {
    titleKey: "smartProcurement.storyFulfillment",
    descKey: "smartProcurement.storyFulfillmentDesc",
    image: "https://images.unsplash.com/photo-1639322537504-6427a16b0a28?auto=format&fit=crop&w=1600&q=80"
  }
]
</script>

<style scoped>
.reveal-item {
  opacity: 0;
  transform: translateY(20px);
  transition: opacity 0.5s ease, transform 0.5s ease;
}

.reveal-item.visible {
  opacity: 1;
  transform: translateY(0);
}

.pipeline-carousel {
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.pipeline-carousel::-webkit-scrollbar {
  display: none;
}

@keyframes heroBreathe {
  0% { transform: scale(1.05); }
  50% { transform: scale(1.15); }
  100% { transform: scale(1.05); }
}

.animate-hero-breathe {
  animation: heroBreathe 15s ease-in-out infinite;
}
</style>
