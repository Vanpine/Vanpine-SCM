<template>
  <div class="min-h-screen bg-[#Fbfbfd] text-slate-900 font-sans selection:bg-blue-200">

    <!-- ===== Hero Section ===== -->
    <section class="relative h-screen min-h-[800px] flex items-center justify-center overflow-hidden">
      <div class="absolute inset-0 z-0">
        <ImageWithFallback
          src="https://images.unsplash.com/photo-1644088379091-d574269d422f?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxhYnN0cmFjdCUyMGdsb3dpbmclMjBuZXR3b3JrJTIwZGF0YSUyMHN0cmVhbXN8ZW58MXx8fHwxNzgyMjkxNzg2fDA&ixlib=rb-4.1.0&q=80&w=1080"
          alt="AI Data Streams" class="w-full h-full object-cover" />
        <div class="absolute inset-0 bg-gradient-to-b from-slate-900/60 via-slate-900/80 to-[#Fbfbfd]"></div>
      </div>

      <div class="relative z-10 max-w-6xl mx-auto px-6 text-center -mt-20">
        <h1 v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 100, ease: [0.16, 1, 0.3, 1] } }"
          class="text-6xl md:text-8xl font-semibold tracking-tight text-white mb-6 text-cn">
          {{ $t('decisionCenter.heroTitle') }}
        </h1>
        <p v-motion :initial="{ opacity: 0, y: 30 }"
          :enter="{ opacity: 1, y: 0, transition: { duration: 800, delay: 200, ease: [0.16, 1, 0.3, 1] } }"
          class="text-xl md:text-3xl text-white/90 font-light mt-12 mb-10 tracking-wide max-w-3xl mx-auto text-cn">
          {{ $t('decisionCenter.heroSub') }}
        </p>
      </div>
    </section>

    <!-- ===== KPI Cards Section ===== -->
    <section class="relative z-20 -mt-32 max-w-7xl mx-auto px-6 md:px-8">
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 md:gap-6">
        <div v-for="(kpi, idx) in kpis" :key="idx"
          class="bg-white/70 backdrop-blur-2xl border border-white p-6 md:p-8 rounded-3xl shadow-[0_8px_30px_rgba(0,0,0,0.04)] hover:shadow-[0_20px_40px_rgba(0,0,0,0.08)] hover:-translate-y-1 transition-all duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] group">
          <div
            class="w-10 h-10 rounded-xl bg-white text-slate-500 flex items-center justify-center mb-4 shadow-sm group-hover:scale-110 transition-transform duration-500">
            <svg v-if="idx === 0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" class="w-5 h-5">
              <circle cx="12" cy="12" r="10" />
              <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10" />
            </svg>
            <svg v-else-if="idx === 1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" class="w-5 h-5">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <svg v-else-if="idx === 2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
              stroke-linecap="round" stroke-linejoin="round" class="w-5 h-5">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
              stroke-linejoin="round" class="w-5 h-5">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
          <div class="text-3xl md:text-4xl font-bold tracking-tight text-slate-900 mb-1">{{ kpi.value }}</div>
          <div class="text-base md:text-lg font-medium text-slate-500 text-cn">{{ $t(kpi.titleKey) }}</div>
        </div>
      </div>
    </section>

    <!-- ===== Core Capabilities (Bento Grid) ===== -->
    <section class="w-[85%] mx-auto px-6 md:px-8 py-12 md:py-24 mt-16 pb-32">
      <div class="mb-16">
        <h2 class="text-title font-bold tracking-tight text-slate-900 mb-4 text-cn">{{ $t('decisionCenter.sectionTitle') }}</h2>
        <p class="text-content text-slate-500 font-light text-cn">{{ $t('decisionCenter.sectionDesc') }}</p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-6 auto-rows-[280px]">

        <div
          class="md:col-span-2 row-span-2 group relative overflow-hidden rounded-[2rem] bg-white border border-slate-100 shadow-sm hover:shadow-xl transition-all duration-700 ease-out flex flex-col md:flex-row">
          <div class="flex-1 flex flex-col justify-between p-8 md:p-12 relative z-10">
            <div>
              <h3 class="text-3xl md:text-4xl font-bold tracking-tight text-slate-900 mb-4 text-cn">
                {{ $t('decisionCenter.capSignal') }}
              </h3>
              <p class="text-content text-slate-500 leading-relaxed max-w-lg text-cn">
                {{ $t('decisionCenter.capSignalDesc') }}
              </p>
            </div>
            <div class="mt-8 flex gap-4">
              <div class="flex-1 bg-slate-50 rounded-2xl p-4 border border-slate-100/50">
                <div class="text-xs font-semibold text-slate-400 mb-1 uppercase tracking-wider">地缘风险监控</div>
                <div class="text-2xl font-bold text-slate-800">200+</div>
                <div class="w-full h-1.5 bg-slate-200 rounded-full mt-3 overflow-hidden">
                  <div class="h-full bg-slate-400 rounded-full w-[85%]"></div>
                </div>
              </div>
              <div class="flex-1 bg-slate-50 rounded-2xl p-4 border border-slate-100/50">
                <div class="text-xs font-semibold text-slate-400 mb-1 uppercase tracking-wider">数据源接入</div>
                <div class="text-2xl font-bold text-slate-800">50+</div>
                <div class="w-full h-1.5 bg-slate-200 rounded-full mt-3 overflow-hidden">
                  <div class="h-full bg-slate-500 rounded-full w-[90%]"></div>
                </div>
              </div>
            </div>
          </div>
          <div class="relative w-full md:w-[45%] h-64 md:h-auto overflow-hidden">
            <img src="https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=800&q=80"
              alt="Global Signals"
              class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-[cubic-bezier(0.16,1,0.3,1)]" loading="lazy" />
            <div class="absolute inset-0 bg-gradient-to-l from-transparent via-white/10 to-white/50"></div>
          </div>
        </div>

        <div
          class="md:col-span-1 row-span-2 group relative overflow-hidden rounded-[2rem] bg-slate-900 text-white shadow-xl hover:shadow-2xl hover:shadow-indigo-500/20 transition-all duration-700 ease-out p-8 md:p-10 flex flex-col">
          <div
            class="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1551288049-bebda4e38f71?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080')] bg-cover bg-center opacity-10 group-hover:opacity-20 transition-opacity duration-700 mix-blend-luminosity">
          </div>
          <div class="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/80 to-slate-900/40"></div>
          <div class="relative z-10 flex-1">
            <h3 class="text-3xl md:text-4xl font-bold tracking-tight mb-4 text-cn">{{ $t('decisionCenter.capPredict') }}</h3>
            <p class="text-content text-slate-300 leading-relaxed mb-8 text-cn">
              {{ $t('decisionCenter.capPredictDesc') }}
            </p>
          </div>
          <div class="relative z-10 space-y-3">
            <div class="bg-white/5 border border-white/10 rounded-2xl p-4 backdrop-blur-md">
              <div class="flex justify-between items-center mb-2"><span
                  class="text-sm font-medium text-slate-300">需求预测准确率</span><span
                  class="text-sm font-bold text-slate-300">95.8%</span></div>
              <div class="w-full h-1 bg-white/10 rounded-full">
                <div class="h-full bg-slate-400 rounded-full w-[95.8%]"></div>
              </div>
            </div>
            <div class="bg-white/5 border border-white/10 rounded-2xl p-4 backdrop-blur-md">
              <div class="flex justify-between items-center mb-2"><span
                  class="text-sm font-medium text-slate-300">模型自校正频率</span><span
                  class="text-sm font-bold text-slate-400">实时</span></div>
              <div class="w-full h-1 bg-white/10 rounded-full">
                <div class="h-full bg-slate-500 rounded-full w-full"></div>
              </div>
            </div>
          </div>
        </div>

        <div
          class="md:col-span-1 row-span-1 group relative overflow-hidden rounded-[2rem] shadow-sm hover:shadow-xl transition-all duration-700 ease-out p-8 flex flex-col justify-center">
          <img src="https://images.unsplash.com/photo-1639322537228-f710d846310a?auto=format&fit=crop&w=800&q=80"
            alt="Simulation"
            class="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-[cubic-bezier(0.16,1,0.3,1)]" loading="lazy" />
          <div class="absolute inset-0 bg-gradient-to-t from-slate-900/90 via-slate-900/60 to-slate-900/40"></div>
          <div class="relative z-10">
            <h3 class="text-3xl md:text-4xl font-bold tracking-tight text-white mb-4 text-cn">
              {{ $t('decisionCenter.capSandbox') }}</h3>
            <p class="text-content text-slate-200 leading-relaxed text-cn">
              {{ $t('decisionCenter.capSandboxDesc') }}
            </p>
          </div>
        </div>

        <div
          class="md:col-span-2 row-span-1 group relative overflow-hidden rounded-[2rem] bg-white border border-slate-100 shadow-sm hover:shadow-xl transition-all duration-700 ease-out p-8 flex flex-col md:flex-row items-center gap-8">
          <div class="flex-1">
            <h3 class="text-3xl md:text-4xl font-bold tracking-tight text-slate-900 mb-3 text-cn">
              {{ $t('decisionCenter.capCollaborate') }}</h3>
            <p class="text-content text-slate-500 leading-relaxed text-cn">{{ $t('decisionCenter.capCollaborateDesc') }}</p>
          </div>
          <div class="w-full md:w-64 bg-slate-50 rounded-2xl p-6 border border-slate-100 shrink-0 space-y-4">
            <div>
              <div class="flex items-center justify-between text-sm mb-1"><span class="text-slate-500">决策建议生成</span><span
                  class="font-bold text-slate-900">&lt; 3s</span></div>
              <div class="w-full h-1 bg-slate-200 rounded-full overflow-hidden">
                <div class="h-full bg-slate-400 rounded-full w-[92%]"></div>
              </div>
            </div>
            <div>
              <div class="flex items-center justify-between text-sm mb-1"><span class="text-slate-500">预案匹配准确率</span><span
                  class="font-bold text-slate-900">96%</span></div>
              <div class="w-full h-1 bg-slate-200 rounded-full overflow-hidden">
                <div class="h-full bg-slate-500 rounded-full w-[96%]"></div>
              </div>
            </div>
            <div class="text-xs text-slate-400 pt-1 border-t border-slate-200">已对接主流 ERP / WMS 系统</div>
          </div>
        </div>

      </div>
    </section>


    <!-- ===== Decision Loop ===== -->
    <section class="py-32 overflow-hidden bg-[#fafafa]">
      <div class="text-center mb-24 md:mb-32">
        <h2 class="text-title font-semibold text-slate-900 mb-4 text-cn">{{ $t('decisionCenter.loopTitle') }}</h2>
        <p class="text-content text-slate-500 font-light text-cn">{{ $t('decisionCenter.loopSub') }}</p>
      </div>

      <div class="w-[85%] mx-auto px-6 lg:px-8 space-y-32">
        
        <!-- Step 1 -->
        <div class="flex flex-col lg:flex-row items-center gap-12 lg:gap-24" v-motion :initial="{ opacity: 0, y: 50 }" :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true">
          <div class="flex-1 space-y-8 w-full">
            <div>
              <h2 class="text-4xl md:text-5xl lg:text-6xl font-semibold text-slate-900 mb-6 text-cn">{{ $t('decisionCenter.stepPerceive') }}</h2>
              <p class="text-xl md:text-2xl text-slate-500 font-light leading-relaxed text-cn">{{ $t('decisionCenter.stepPerceiveDesc') }}</p>
            </div>
            <ul class="space-y-6 mt-8">
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.perceiveItem1Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.perceiveItem1Desc') }}</p>
                </div>
              </li>
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.perceiveItem2Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.perceiveItem2Desc') }}</p>
                </div>
              </li>
            </ul>
          </div>

          <div class="flex-1 w-full">
            <div class="relative rounded-[2.5rem] overflow-hidden aspect-[4/3] bg-slate-200 shadow-2xl shadow-slate-900/10">
              <img src="https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&q=80&w=1200" alt="全域感知" class="w-full h-full object-cover decision-zoom-img" loading="lazy" />
            </div>
          </div>
        </div>

        <!-- Step 2 -->
        <div class="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24" v-motion :initial="{ opacity: 0, y: 50 }" :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true">
          <div class="flex-1 space-y-8 w-full">
            <div>
              <h2 class="text-4xl md:text-5xl lg:text-6xl font-semibold text-slate-900 mb-6 text-cn">{{ $t('decisionCenter.stepAnalyze') }}</h2>
              <p class="text-xl md:text-2xl text-slate-500 font-light leading-relaxed text-cn">{{ $t('decisionCenter.stepAnalyzeDesc') }}</p>
            </div>
            <ul class="space-y-6 mt-8">
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.analyzeItem1Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.analyzeItem1Desc') }}</p>
                </div>
              </li>
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.analyzeItem2Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.analyzeItem2Desc') }}</p>
                </div>
              </li>
            </ul>
          </div>

          <div class="flex-1 w-full">
            <div class="relative rounded-[2.5rem] overflow-hidden aspect-[4/3] bg-slate-200 shadow-2xl shadow-slate-900/10">
              <img src="https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&q=80&w=1200" alt="智能分析" class="w-full h-full object-cover decision-zoom-img" loading="lazy" />
            </div>
          </div>
        </div>

        <!-- Step 3 -->
        <div class="flex flex-col lg:flex-row items-center gap-12 lg:gap-24" v-motion :initial="{ opacity: 0, y: 50 }" :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true">
          <div class="flex-1 space-y-8 w-full">
            <div>
              <h2 class="text-4xl md:text-5xl lg:text-6xl font-semibold text-slate-900 mb-6 text-cn">{{ $t('decisionCenter.stepDecide') }}</h2>
              <p class="text-xl md:text-2xl text-slate-500 font-light leading-relaxed text-cn">{{ $t('decisionCenter.stepDecideDesc') }}</p>
            </div>
            <ul class="space-y-6 mt-8">
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.decideItem1Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.decideItem1Desc') }}</p>
                </div>
              </li>
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.decideItem2Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.decideItem2Desc') }}</p>
                </div>
              </li>
            </ul>
          </div>

          <div class="flex-1 w-full">
            <div class="relative rounded-[2.5rem] overflow-hidden aspect-[4/3] bg-slate-200 shadow-2xl shadow-slate-900/10">
              <img src="https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&q=80&w=1200" alt="最优决策" class="w-full h-full object-cover decision-zoom-img" loading="lazy" />
            </div>
          </div>
        </div>

        <!-- Step 4 -->
        <div class="flex flex-col lg:flex-row-reverse items-center gap-12 lg:gap-24" v-motion :initial="{ opacity: 0, y: 50 }" :visible="{ opacity: 1, y: 0, transition: { duration: 800, ease: [0.16, 1, 0.3, 1] } }" :visible-once="true">
          <div class="flex-1 space-y-8 w-full">
            <div>
              <h2 class="text-4xl md:text-5xl lg:text-6xl font-semibold text-slate-900 mb-6 text-cn">{{ $t('decisionCenter.stepExecute') }}</h2>
              <p class="text-xl md:text-2xl text-slate-500 font-light leading-relaxed text-cn">{{ $t('decisionCenter.stepExecuteDesc') }}</p>
            </div>
            <ul class="space-y-6 mt-8">
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.executeItem1Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.executeItem1Desc') }}</p>
                </div>
              </li>
              <li class="flex items-start gap-4">
                <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0 mt-2.5"></div>
                <div>
                  <h4 class="text-lg md:text-xl font-medium text-slate-900 text-cn">{{ $t('decisionCenter.executeItem2Title') }}</h4>
                  <p class="text-base md:text-lg text-slate-500 text-cn mt-2">{{ $t('decisionCenter.executeItem2Desc') }}</p>
                </div>
              </li>
            </ul>
          </div>

          <div class="flex-1 w-full">
            <div class="relative rounded-[2.5rem] overflow-hidden aspect-[4/3] bg-slate-200 shadow-2xl shadow-slate-900/10">
              <img src="https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&q=80&w=1200" alt="无缝协同" class="w-full h-full object-cover decision-zoom-img" loading="lazy" />
            </div>
          </div>
        </div>

      </div>
    </section>

    <!-- ===== AI Command Center ===== -->
    <section class="w-full mx-auto px-6 md:px-8 py-16 md:py-24 bg-slate-900 text-white relative overflow-hidden">
      <div
        class="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1451187580459-43490279c0fa?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=2000')] bg-cover bg-center opacity-10 mix-blend-screen">
      </div>
      <div class="absolute inset-0 bg-gradient-to-b from-slate-900 via-slate-900/90 to-slate-900"></div>
      <div class="max-w-7xl mx-auto relative z-10">
        <div class="text-center mb-16">
<h2 class="text-title font-bold tracking-tight mb-6 text-cn">{{ $t('decisionCenter.aiCommandTitle') }}</h2>
          <p class="text-content text-slate-400 font-light max-w-3xl mx-auto text-cn">{{ $t('decisionCenter.aiCommandSub') }}</p>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div
            class="lg:col-span-4 bg-black/40 border border-white/10 rounded-[2rem] p-6 backdrop-blur-xl flex flex-col h-[600px] shadow-2xl relative overflow-hidden group">
            <div
              class="absolute top-0 right-0 w-64 h-64 bg-blue-500/10 rounded-full blur-[80px] pointer-events-none group-hover:bg-blue-500/20 transition-colors duration-700">
            </div>
            <div class="flex justify-between items-center mb-6 pb-4 border-b border-white/10 relative z-10">
              <div class="flex items-center gap-3">
                <div
                  class="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-[0_0_15px_rgba(59,130,246,0.3)]">
                  <Cpu class="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 class="font-bold text-slate-100 tracking-wide">AI 决策引擎</h3>
                  <div class="flex items-center gap-1.5 mt-0.5"><span class="relative flex h-2 w-2"><span
                        class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span><span
                        class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span></span><span
                      class="text-[10px] text-emerald-400 font-mono uppercase tracking-wider">System Online</span></div>
                </div>
              </div>
              <div
                class="px-2.5 py-1 rounded-lg bg-white/5 border border-white/10 text-[10px] text-slate-400 font-mono">
                ID: NODE-77A</div>
            </div>
            <div class="flex-1 overflow-y-auto space-y-6 pr-2 custom-scrollbar relative z-10 pb-4">
              <div class="flex gap-3 w-11/12">
                <div
                  class="w-8 h-8 rounded-full bg-red-500/20 border border-red-500/30 flex items-center justify-center shrink-0 shadow-[0_0_10px_rgba(239,68,68,0.2)]">
                  <AlertTriangle class="w-4 h-4 text-red-400" />
                </div>
                <div class="space-y-1.5 w-full">
                  <div class="text-[10px] text-slate-500 font-mono ml-1 flex items-center gap-2">
                    <span>SYSTEM_ALERT</span><span class="w-1 h-1 rounded-full bg-slate-600"></span><span>08:42
                      AM</span>
                  </div>
                  <div
                    class="bg-gradient-to-br from-red-500/10 to-transparent border border-red-500/20 rounded-2xl rounded-tl-none p-4 text-sm text-red-100/90 leading-relaxed">
                    预测到下季度欧洲市场核心 <span class="font-bold text-red-400">MCU 组件需求将激增 40%</span>，按现有采购计划，库存将在 <span
                      class="font-bold text-red-400">15 天后击穿</span>。是否需要生成产能保障策略？</div>
                </div>
              </div>
              <div class="flex gap-3 w-10/12 self-end ml-auto flex-row-reverse">
                <div
                  class="w-8 h-8 rounded-full bg-slate-800 border border-slate-600 flex items-center justify-center shrink-0">
                  <span class="text-[10px] font-black text-slate-300">CMD</span>
                </div>
                <div class="space-y-1.5 w-full flex flex-col items-end">
                  <div class="text-[10px] text-slate-500 font-mono mr-1 flex items-center gap-2"><span>08:45
                      AM</span><span class="w-1 h-1 rounded-full bg-slate-600"></span><span>DIRECTOR_INPUT</span></div>
                  <div
                    class="bg-gradient-to-r from-blue-600 to-indigo-600 shadow-[0_5px_15px_rgba(37,99,235,0.2)] rounded-2xl rounded-tr-none p-4 text-sm text-white leading-relaxed text-left w-full">
                    列出备选方案，并对比调拨与紧急采购的资金占用。</div>
                </div>
              </div>
              <div class="flex gap-3 w-11/12">
                <div
                  class="w-8 h-8 rounded-full bg-blue-500/20 border border-blue-500/30 flex items-center justify-center shrink-0 shadow-[0_0_10px_rgba(59,130,246,0.2)]">
                  <Bot class="w-4 h-4 text-blue-400" />
                </div>
                <div class="space-y-1.5 w-full">
                  <div class="text-[10px] text-slate-500 font-mono ml-1 flex items-center gap-2">
                    <span>AI_ANALYSIS</span><span class="w-1 h-1 rounded-full bg-slate-600"></span><span>08:46 AM</span>
                  </div>
                  <div
                    class="bg-blue-500/5 border border-blue-500/20 rounded-2xl rounded-tl-none p-4 text-sm text-slate-300 space-y-4">
                    <p class="flex items-center gap-2 text-blue-300 font-medium">
                      <Zap class="w-4 h-4 text-blue-400" /> 已为您测算 2 套最优方案：
                    </p>
                    <div
                      class="bg-[#0a0f1c] rounded-xl p-4 border border-emerald-500/40 relative overflow-hidden group/opt shadow-[0_0_15px_rgba(16,185,129,0.1)]">
                      <div
                        class="absolute inset-0 bg-emerald-500/5 group-hover/opt:bg-emerald-500/10 transition-colors">
                      </div>
                      <div class="relative">
                        <div class="flex justify-between items-center mb-3"><span
                            class="font-bold text-emerald-400 flex items-center gap-2">
                        <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0"></div> 方案 A：亚太仓调拨
                          </span><span
                            class="text-[10px] px-2 py-1 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 tracking-wide">最优选</span>
                        </div>
                        <div class="grid grid-cols-2 gap-3 mt-3">
                          <div class="bg-white/5 border border-white/5 rounded-lg p-2.5">
                            <div class="text-[10px] text-slate-400 mb-1">时效预估</div>
                            <div class="text-xs font-mono text-white">5-7 DAYS</div>
                          </div>
                          <div class="bg-white/5 border border-white/5 rounded-lg p-2.5">
                            <div class="text-[10px] text-slate-400 mb-1">成本变化</div>
                            <div class="text-xs font-mono text-emerald-400">+3.5% ($12K)</div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="bg-[#0a0f1c]/50 rounded-xl p-4 border border-white/10 opacity-80">
                      <div class="flex justify-between items-center mb-3"><span class="font-bold text-slate-400">方案
                          B：现货紧急寻源</span></div>
                      <div class="grid grid-cols-2 gap-3 mt-3">
                        <div class="bg-white/5 border border-white/5 rounded-lg p-2.5">
                          <div class="text-[10px] text-slate-400 mb-1">时效预估</div>
                          <div class="text-xs font-mono text-slate-300">10-12 DAYS</div>
                        </div>
                        <div class="bg-white/5 border border-white/5 rounded-lg p-2.5">
                          <div class="text-[10px] text-slate-400 mb-1">成本变化</div>
                          <div class="text-xs font-mono text-red-400">+18.0% ($65K)</div>
                        </div>
                      </div>
                    </div>
                    <div class="pt-2"><button
                        class="w-full py-3 rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 text-white text-sm font-semibold tracking-wide transition-all shadow-[0_0_20px_rgba(37,99,235,0.4)] pointer-events-none flex items-center justify-center gap-2">
                    <div class="w-2 h-2 rounded-full bg-slate-300 shrink-0"></div> 确认执行方案 A
                      </button></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt-2 pt-5 border-t border-white/10 relative select-none pointer-events-none z-10">
              <div
                class="w-full bg-[#0a0f1c] border border-white/10 rounded-xl py-3.5 px-4 flex items-center justify-between shadow-inner">
                <div class="flex items-center gap-3"><span
                    class="text-blue-500 font-mono text-lg leading-none font-bold">></span><span
                    class="text-xs font-mono text-slate-400 tracking-wider flex items-center uppercase">Awaiting_Decision<span
                      class="animate-pulse ml-2 w-1.5 h-3.5 bg-blue-500 rounded-sm inline-block"></span></span></div>
                <div class="flex gap-1.5 opacity-50">
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500"></div>
                </div>
              </div>
            </div>
          </div>
          <div
            class="lg:col-span-8 bg-black/40 border border-white/10 rounded-[2rem] p-6 backdrop-blur-xl relative overflow-hidden h-[600px] flex flex-col">
            <div class="flex justify-between items-center mb-6">
              <h3 class="font-semibold text-slate-200 text-lg">全局供需与库存态势图</h3>
              <div class="flex gap-2"><span
                  class="px-3 py-1 rounded-full bg-green-500/20 text-green-400 text-xs border border-green-500/30">{{ $t('decisionCenter.supplyDemand') }}
                  92%</span><span
                  class="px-3 py-1 rounded-full bg-yellow-500/20 text-yellow-400 text-xs border border-yellow-500/30">{{ $t('decisionCenter.inventoryAlert') }}
                  5项</span><span
                  class="px-3 py-1 rounded-full bg-indigo-500/20 text-indigo-400 text-xs border border-indigo-500/30">{{ $t('decisionCenter.productionLoad') }}
                  2项</span></div>
            </div>
            <div class="flex-1 relative rounded-xl overflow-hidden border border-white/5 bg-slate-900/50">
              <img
                src="https://images.unsplash.com/photo-1524661135-423995f22d0b?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080"
                alt="Dashboard" class="absolute inset-0 w-full h-full object-cover opacity-30 mix-blend-luminosity" loading="lazy" />
              <div v-for="node in mapNodes" :key="node.id" @click="activeNode = node"
                class="absolute w-4 h-4 rounded-full cursor-pointer z-20 group transition-transform hover:scale-125"
                :class="node.colorBase"
                :style="`top: ${node.top}; left: ${node.left}; box-shadow: 0 0 15px ${node.shadow};`">
                <div :class="['absolute inset-0 rounded-full animate-ping opacity-75', node.colorPing]"></div>
                <div
                  :class="['absolute -top-7 left-1/2 -translate-x-1/2 text-[10px] font-bold bg-black/50 px-2 py-0.5 rounded whitespace-nowrap pointer-events-none transition-all', node.colorText, activeNode.id === node.id ? 'opacity-100 scale-110' : 'opacity-80 group-hover:opacity-100']">
                  {{ $t(node.nameKey) }} - {{ $t(node.statusKey) }}</div>
                <div v-if="activeNode.id === node.id"
                  class="absolute -inset-2 rounded-full border border-white/50 animate-[spin_3s_linear_infinite]"></div>
              </div>
              <svg class="absolute inset-0 w-full h-full pointer-events-none"
                style="filter: drop-shadow(0 0 4px rgba(59,130,246,0.5));">
                <path d="M 65% 35% Q 55% 20% 45% 25%" fill="none" stroke="rgba(16,185,129,0.6)" stroke-width="2"
                  stroke-dasharray="4 4" class="animate-[dash_15s_linear_infinite]" />
                <path d="M 65% 35% Q 45% 50% 25% 45%" fill="none" stroke="rgba(59,130,246,0.6)" stroke-width="2"
                  stroke-dasharray="4 4" class="animate-[dash_15s_linear_infinite]" />
              </svg>
              <transition enter-active-class="transition duration-300 ease-out"
                enter-from-class="transform translate-y-4 opacity-0"
                enter-to-class="transform translate-y-0 opacity-100"
                leave-active-class="transition duration-200 ease-in"
                leave-from-class="transform translate-y-0 opacity-100"
                leave-to-class="transform translate-y-4 opacity-0" mode="out-in">
                <div :key="activeNode.id"
                  class="absolute bottom-4 right-4 bg-black/70 backdrop-blur-xl border border-white/10 rounded-xl p-5 w-72 z-30 shadow-2xl">
                  <div class="flex items-center gap-2 mb-3">
                    <div :class="['w-2 h-2 rounded-full animate-pulse', activeNode.colorBase]"></div>
                    <div class="text-sm font-semibold text-white">{{ $t(activeNode.panelTitleKey) }}</div>
                  </div>
                  <div class="text-xs text-slate-400 mb-4">{{ $t(activeNode.descKey) }}</div>
                  <div class="space-y-3">
                    <div v-for="(metric, idx) in activeNode.metrics" :key="idx" class="relative">
                      <div class="flex justify-between items-end mb-1.5">
                        <div class="text-xs text-slate-400">{{ metric.label }}</div>
                        <div :class="['text-sm font-bold', metric.valueColor]">{{ metric.value }}</div>
                      </div>
                      <div v-if="metric.progress" class="w-full bg-white/10 h-1.5 rounded-full overflow-hidden">
                        <div :class="['h-full rounded-full', metric.barColor]" :style="{ width: metric.progress }">
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import {
  AlertTriangle,
  Bot,
  Cpu, Zap
} from 'lucide-vue-next'
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import gsap from 'gsap'
import ScrollTrigger from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

let ctx

onMounted(() => {
  ctx = gsap.context(() => {
    const zoomImages = document.querySelectorAll('.decision-zoom-img')
    zoomImages.forEach(img => {
      gsap.fromTo(img,
        { scale: 1 },
        {
          scale: 1.25,
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
  })
})

onUnmounted(() => {
  if (ctx) ctx.revert()
})

const mapNodes = ref([
  { id: 'apac', nameKey: 'decisionCenter.nodeAPAC', top: '35%', left: '65%', colorBase: 'bg-emerald-500', colorPing: 'bg-emerald-400', colorText: 'text-emerald-300', shadow: 'rgba(16,185,129,1)', statusKey: 'decisionCenter.statusStable', panelTitleKey: 'decisionCenter.nodePanelAPAC', descKey: 'decisionCenter.nodeDescAPAC', metrics: [{ label: '当前库存量', value: '1,250,000 件', valueColor: 'text-emerald-400', progress: '90%', barColor: 'bg-emerald-500' }] },
  { id: 'na', nameKey: 'decisionCenter.nodeNA', top: '45%', left: '25%', colorBase: 'bg-yellow-500', colorPing: 'bg-yellow-400', colorText: 'text-yellow-300', shadow: 'rgba(234,179,8,1)', statusKey: 'decisionCenter.statusWarning', panelTitleKey: 'decisionCenter.nodePanelNA', descKey: 'decisionCenter.nodeDescNA', metrics: [{ label: '产线负荷率', value: '98.5%', valueColor: 'text-yellow-400', progress: '98.5%', barColor: 'bg-yellow-500' }] },
  { id: 'eu', nameKey: 'decisionCenter.nodeEU', top: '25%', left: '45%', colorBase: 'bg-red-500', colorPing: 'bg-red-400', colorText: 'text-red-300', shadow: 'rgba(239,68,68,1)', statusKey: 'decisionCenter.statusHighLoad', panelTitleKey: 'decisionCenter.nodePanelEU', descKey: 'decisionCenter.nodeDescEU', metrics: [{ label: '预计缺口', value: '50,000 件', valueColor: 'text-red-400', progress: '85%', barColor: 'bg-red-500' }] }
])
const activeNode = ref(mapNodes.value[2])

const kpis = [
  { titleKey: "decisionCenter.kpiDataSources", value: "50+" },
  { titleKey: "decisionCenter.kpiAccuracy", value: ">95%" },
  { titleKey: "decisionCenter.kpiResponse", value: "< 3s" },
  { titleKey: "decisionCenter.kpiIntegration", value: "ERP/WMS" }
]
</script>

<style scoped>
</style>
