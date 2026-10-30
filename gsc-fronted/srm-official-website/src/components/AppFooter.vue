<template>
  <footer class="relative bg-[#0a0d16] text-[#8b93a3] pt-20 pb-9 text-[15px] mt-auto">
    <div class="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[rgba(108,178,255,0.45)] to-transparent"></div>

    <div class="w-[75%] mx-auto px-6 lg:px-8">
      <div class="flex flex-col md:flex-row md:justify-between gap-y-12 md:gap-x-8 lg:gap-14 pb-12 border-b border-white/10">

        <div class="flex flex-col">
          <RouterLink to="/" class="text-[19px] font-bold text-white mb-3 flex items-center gap-[9px]" aria-label="Home">
            <ImageWithFallback
              :src="logo2"
              alt="Logo"
              class="h-8 w-auto object-contain"
            />
          </RouterLink>
          <div class="text-[16px] font-semibold text-[#aab6cc] mb-5">{{ $t('footer.tagline') }}</div>
          <p class="leading-[1.7] max-w-[340px] mb-6">
            {{ $t('footer.description') }}
          </p>
        </div>

        <div class="flex gap-x-8 lg:gap-40">
          <div>
            <h5 class="text-white text-[18px] mb-5 font-medium">{{ $t('footer.solutions') }}</h5>
            <ul class="space-y-0">
              <li class="py-[9px]"><RouterLink to="/smart-procurement" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.smartProcurement') }}</RouterLink></li>
              <li class="py-[9px]"><RouterLink to="/trusted-delivery" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.trustedDelivery') }}</RouterLink></li>
              <li class="py-[9px]"><RouterLink to="/decision-center" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.decisionCenter') }}</RouterLink></li>
            </ul>
          </div>

          <div>
            <h5 class="text-white text-[18px] mb-5 font-medium">{{ $t('footer.platformCapabilities') }}</h5>
            <ul class="space-y-0">
              <li class="py-[9px]"><RouterLink to="/smart-procurement" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.smartProcurement') }}</RouterLink></li>
              <li class="py-[9px]"><RouterLink to="/trusted-delivery" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.fullTracking') }}</RouterLink></li>
              <li class="py-[9px]"><RouterLink to="/decision-center" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.dataAnalysis') }}</RouterLink></li>
            </ul>
          </div>

          <div>
            <h5 class="text-white text-[18px] mb-5 font-medium">{{ $t('footer.company') }}</h5>
            <ul class="space-y-0">
              <li class="py-[9px]"><a href="#" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.aboutUs') }}</a></li>
              <li class="py-[9px]"><a href="#" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.contactUs') }}</a></li>
              <li class="py-[9px]"><a href="#" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.newsInfo') }}</a></li>
              <li class="py-[9px]"><a href="#" class="text-[#8b93a3] hover:text-white transition-colors duration-200">{{ $t('footer.joinUs') }}</a></li>
            </ul>
          </div>
        </div>
      </div>

      <div class="flex flex-col md:flex-row justify-between items-center pt-[30px] text-[14px] text-[#6e6e73] flex-wrap gap-4">
        <span class="text-center md:text-left leading-relaxed">
          {{ $t('footer.copyright') }}
          <span class="hidden md:inline"> | </span>
          <br class="md:hidden" />
          <a href="#" class="text-[#6e6e73] hover:text-white transition-colors duration-200 inline-block mt-1 md:mt-0">{{ $t('footer.beian1') }}</a>
          <span class="mx-1.5 inline-block">|</span>
          <a href="#" class="text-[#6e6e73] hover:text-white transition-colors duration-200 inline-block mt-1 md:mt-0">{{ $t('footer.beian2') }}</a>
        </span>
        <span class="inline-flex gap-4 flex-wrap justify-center">
          <a href="#" class="text-[#6e6e73] hover:text-white transition-colors duration-200">{{ $t('footer.privacyPolicy') }}</a>
          <a href="#" class="text-[#6e6e73] hover:text-white transition-colors duration-200">{{ $t('footer.userAgreement') }}</a>
          <a href="#" class="text-[#6e6e73] hover:text-white transition-colors duration-200">{{ $t('footer.behaviorPolicy') }}</a>
        </span>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import logo2 from '@/assets/logo-2.webp'
</script>
