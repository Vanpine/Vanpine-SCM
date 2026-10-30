<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <!-- Hero Section -->
    <div
      class="relative pt-32 pb-20 lg:pt-48 lg:pb-32 overflow-hidden bg-gradient-to-b from-[#0a0f1c] via-[#111827] to-slate-900">
      <div class="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(59,130,246,0.08),transparent_50%)]"></div>
      <div class="relative z-10 max-w-6xl mx-auto px-6 text-center">
        <h1 class="text-6xl md:text-8xl font-semibold tracking-tight text-white mb-6 text-cn">
          {{ $t('joinUs.heroTitle') }}
        </h1>
        <p class="text-xl md:text-3xl text-white/70 font-light mt-12 mb-10 tracking-wide max-w-3xl mx-auto text-cn">
          {{ $t('joinUs.heroSub') }}
        </p>
      </div>
    </div>

    <!-- Main Content -->
    <div ref="formSectionRef"
      class="relative z-20 flex-1 bg-slate-50 rounded-t-[3rem] -mt-16 pt-24 pb-16 md:pb-28 px-6 shadow-[0_-20px_40px_rgba(0,0,0,0.1)]">
      <div class="max-w-4xl mx-auto">

        <!-- Progress Bar -->
        <div class="flex items-center justify-center gap-0 mb-12">

          <div class="flex flex-col items-center">
            <div
              :class="['w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold border-2 transition-all', currentStep > 1 ? 'bg-blue-600 border-blue-600 text-white' : currentStep === 1 ? 'border-blue-600 bg-blue-50 text-blue-600' : 'border-slate-300 bg-white text-slate-400']">
              <CheckCircle2 v-if="currentStep > 1" class="w-5 h-5" />
              <span v-else>1</span>
            </div>
            <span :class="['text-sm font-medium mt-1.5', currentStep >= 1 ? 'text-blue-600' : 'text-slate-400']">{{
              $t('joinUs.stepPassport') }}</span>
          </div>

          <div :class="['w-16 h-0.5 mx-3 transition-colors', currentStep >= 2 ? 'bg-blue-600' : 'bg-slate-200']"></div>

          <div class="flex flex-col items-center">
            <div
              :class="['w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold border-2 transition-all', currentStep > 2 ? 'bg-blue-600 border-blue-600 text-white' : currentStep === 2 ? 'border-blue-600 bg-blue-50 text-blue-600' : 'border-slate-300 bg-white text-slate-400']">
              <CheckCircle2 v-if="currentStep > 2" class="w-5 h-5" />
              <span v-else>2</span>
            </div>
            <span :class="['text-sm font-medium mt-1.5', currentStep >= 2 ? 'text-blue-600' : 'text-slate-400']">{{
              $t('joinUs.stepRole') }}</span>
          </div>

          <div :class="['w-16 h-0.5 mx-3 transition-colors', currentStep >= 3 ? 'bg-blue-600' : 'bg-slate-200']"></div>

          <div class="flex flex-col items-center">
            <div
              :class="['w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold border-2 transition-all', currentStep === 3 ? 'border-blue-600 bg-blue-50 text-blue-600' : 'border-slate-300 bg-white text-slate-400']">
              3
            </div>
            <span :class="['text-sm font-medium mt-1.5', currentStep >= 3 ? 'text-blue-600' : 'text-slate-400']">{{
              $t('joinUs.stepCompany') }}</span>
          </div>
        </div>

        <!-- Form Area -->
        <div class="bg-white rounded-3xl shadow-sm border border-slate-200 p-8 md:p-14">

          <!-- ==================== Step 1: 护照认证 ==================== -->
          <div v-if="currentStep === 1" class="animate-in fade-in slide-in-from-bottom-4 duration-500">
            <div class="text-center mb-8">
              <div class="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <ShieldCheck class="w-8 h-8 text-blue-600" />
              </div>
              <h3 class="text-card-title font-bold text-slate-900 mb-2 text-cn">{{ $t('joinUs.passportTitle') }}</h3>
              <p class="text-content text-slate-500 max-w-lg mx-auto text-cn">
                {{ $t('joinUs.passportDesc') }}
              </p>
            </div>

            <!-- 未认证：登录按钮 -->
            <div v-if="!passportLoading && !passportUser"
              class="bg-slate-50 rounded-2xl p-6 md:p-8 border border-slate-200 mb-8">
              <h4 class="text-lg font-bold text-slate-900 mb-2">{{ $t('joinUs.passportTip') }}</h4>
              <p class="text-sm text-slate-600 mb-6">{{ $t('joinUs.passportHint') }}</p>

              <a :href="passportAuthUrl"
                class="flex items-center justify-center gap-2 w-full py-4 rounded-xl bg-slate-900 text-white font-medium hover:bg-slate-800 transition-colors">
                <LogIn class="w-5 h-5" />
                {{ $t('joinUs.passportLoginBtn') }}
              </a>
            </div>

            <!-- 加载中 -->
            <div v-if="passportLoading" class="text-center py-12">
              <div class="w-10 h-10 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin mx-auto mb-4">
              </div>
              <p class="text-content text-slate-500 text-cn">{{ $t('joinUs.passportLoading') }}</p>
            </div>

            <!-- 错误 -->
            <div v-if="passportError" class="bg-red-50 border border-red-200 rounded-2xl p-6 mb-8 text-center">
              <p class="text-red-700 mb-4">{{ passportError }}</p>
              <button @click="handlePassportAuth"
                class="px-6 py-2.5 rounded-xl bg-red-600 text-white font-medium hover:bg-red-700 transition-colors">
                {{ $t('joinUs.passportReauth') }}
              </button>
            </div>

            <!-- 已认证：用户信息确认 -->
            <div v-if="passportUser && !passportLoading" class="animate-in fade-in duration-300">
              <div class="bg-green-50 border border-green-200 rounded-2xl p-6 mb-8">
                <div class="flex items-center gap-3 mb-4">
                  <div class="w-10 h-10 bg-green-500 rounded-full flex items-center justify-center">
                    <CheckCircle2 class="w-6 h-6 text-white" />
                  </div>
                  <div>
                    <p class="font-semibold text-green-800">{{ $t('joinUs.passportSuccess') }}</p>
                    <p class="text-sm text-green-600">{{ $t('joinUs.passportVerified') }}</p>
                  </div>
                </div>
                <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                  <div class="bg-white rounded-xl p-3">
                    <span class="text-slate-400">{{ $t('joinUs.passportAccount') }}</span>
                    <p class="font-medium text-slate-800 truncate">{{ passportUser.username || '-' }}</p>
                  </div>
                  <div class="bg-white rounded-xl p-3">
                    <span class="text-slate-400">{{ $t('joinUs.passportNickname') }}</span>
                    <p class="font-medium text-slate-800 truncate">{{ passportUser.nickname || '-' }}</p>
                  </div>
                  <div class="bg-white rounded-xl p-3">
                    <span class="text-slate-400">{{ $t('joinUs.passportEmail') }}</span>
                    <p class="font-medium text-slate-800 truncate">{{ passportUser.email || '-' }}</p>
                  </div>
                  <div class="bg-white rounded-xl p-3">
                    <span class="text-slate-400">{{ $t('joinUs.passportPhone') }}</span>
                    <p class="font-medium text-slate-800 truncate">{{ passportUser.phone || '-' }}</p>
                  </div>
                </div>
              </div>

              <button @click="currentStep = 2"
                class="w-full flex items-center justify-center gap-2 py-4 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 transition-colors shadow-lg shadow-blue-600/20">
                {{ $t('joinUs.passportConfirm') }}
                <ArrowRight class="w-4 h-4" />
              </button>
            </div>
          </div>

          <!-- ==================== Step 2: 选择身份 ==================== -->
          <div v-else-if="currentStep === 2" class="animate-in fade-in slide-in-from-right-8 duration-500">
            <button @click="currentStep = 1"
              class="flex items-center gap-1.5 text-sm text-slate-500 hover:text-blue-600 transition-colors mb-6">
              <ChevronLeft class="w-4 h-4" /> {{ $t('joinUs.roleBack') }}
            </button>

            <div class="mb-8 text-center">
              <h3 class="text-card-title font-bold text-slate-900 mb-2 text-cn">{{ $t('joinUs.roleTitle') }}</h3>
              <p class="text-content text-slate-500 text-cn">{{ $t('joinUs.roleDesc') }}</p>
            </div>

            <div class="grid md:grid-cols-2 gap-6 mb-8">
              <div @click="toggleRole('buyer')"
                :class="['relative p-8 md:p-10 rounded-2xl border-2 cursor-pointer transition-all duration-300', selectedRoles.has('buyer') ? 'border-blue-600 bg-blue-50/50 shadow-md shadow-blue-100' : 'border-slate-200 hover:border-blue-300 hover:bg-slate-50']">
                <div
                  :class="['absolute top-4 right-4 w-7 h-7 rounded-full border-2 flex items-center justify-center transition-colors', selectedRoles.has('buyer') ? 'border-blue-600 bg-blue-600' : 'border-slate-300']">
                  <CheckCircle2 v-if="selectedRoles.has('buyer')" class="w-4 h-4 text-white" />
                </div>
                <div class="w-14 h-14 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center mb-5">
                  <Briefcase class="w-7 h-7" />
                </div>
                <h4 class="text-card-title font-bold text-slate-900 mb-3 text-cn">{{ $t('joinUs.roleBuyer') }}</h4>
                <p class="text-content text-slate-600 text-cn mb-5 leading-relaxed">{{ $t('joinUs.roleBuyerIntro') }}
                </p>
                <ul class="text-content text-slate-500 text-cn space-y-3">
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleBuyerDesc1') }}
                  </li>
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleBuyerDesc2') }}
                  </li>
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleBuyerDesc3') }}
                  </li>
                </ul>
              </div>

              <div @click="toggleRole('supplier')"
                :class="['relative p-8 md:p-10 rounded-2xl border-2 cursor-pointer transition-all duration-300', selectedRoles.has('supplier') ? 'border-blue-600 bg-blue-50/50 shadow-md shadow-blue-100' : 'border-slate-200 hover:border-blue-300 hover:bg-slate-50']">
                <div
                  :class="['absolute top-4 right-4 w-7 h-7 rounded-full border-2 flex items-center justify-center transition-colors', selectedRoles.has('supplier') ? 'border-blue-600 bg-blue-600' : 'border-slate-300']">
                  <CheckCircle2 v-if="selectedRoles.has('supplier')" class="w-4 h-4 text-white" />
                </div>
                <div class="w-14 h-14 rounded-xl bg-indigo-100 text-indigo-600 flex items-center justify-center mb-5">
                  <Factory class="w-7 h-7" />
                </div>
                <h4 class="text-card-title font-bold text-slate-900 mb-3 text-cn">{{ $t('joinUs.roleSupplier') }}</h4>
                <p class="text-content text-slate-600 text-cn mb-5 leading-relaxed">{{ $t('joinUs.roleSupplierIntro') }}
                </p>
                <ul class="text-content text-slate-500 text-cn space-y-3">
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleSupplierDesc1') }}
                  </li>
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleSupplierDesc2') }}
                  </li>
                  <li class="flex items-start gap-3">
                    <div class="w-1.5 h-1.5 rounded-full bg-slate-300 mt-2 shrink-0"></div> {{
                      $t('joinUs.roleSupplierDesc3') }}
                  </li>
                </ul>
              </div>
            </div>

            <button @click="confirmRoles"
              class="w-full flex items-center justify-center gap-2 py-4 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 transition-colors shadow-lg shadow-blue-600/20">
              {{ $t('joinUs.roleConfirm') }}
              <ArrowRight class="w-4 h-4" />
            </button>
          </div>

          <!-- ==================== Step 3: 企业资料 ==================== -->
          <div v-else-if="currentStep === 3" class="animate-in fade-in slide-in-from-right-8 duration-500">
            <button @click="currentStep = 2"
              class="flex items-center gap-1.5 text-sm text-slate-500 hover:text-blue-600 transition-colors mb-6">
              <ChevronLeft class="w-4 h-4" /> {{ $t('joinUs.companyBack') }}
            </button>

            <div class="mb-8">
              <h3 class="text-card-title font-bold text-slate-900 mb-2 text-cn">{{ step3Title }}</h3>
              <p class="text-content text-slate-500 text-cn">{{ $t('joinUs.requiredHint') }}</p>
            </div>

            <form class="space-y-8" @submit.prevent="submitApplication">
              <!-- 公司信息 -->
              <div>
                <h4
                  class="text-sm font-semibold uppercase tracking-wider text-slate-400 mb-4 border-b border-slate-100 pb-2">
                  {{ $t('joinUs.companySection') }}</h4>
                <div class="grid md:grid-cols-2 gap-6">
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.companyName') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.company" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCompanyName')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.companyCountry') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.country" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCountry')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.companyRegNumber') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.regNumber" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderRegNumber')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.companyEstYear') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.established" type="number" min="1900" max="2026"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderEstYear')" />
                  </div>
                </div>
              </div>

              <!-- 采购商字段 -->
              <div v-if="selectedRoles.has('buyer')" class="animate-in fade-in duration-300">
                <h4
                  class="text-sm font-semibold uppercase tracking-wider text-slate-400 mb-4 border-b border-slate-100 pb-2">
                  {{ $t('joinUs.buyerSection') }}</h4>
                <div class="grid md:grid-cols-2 gap-6">
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.buyerCategory') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.buyCategory" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCategory')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.buyerVolume') }}<span
                        class="text-red-500">*</span></label>
                    <select v-model="formData.buyVolume"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all bg-white">
                      <option v-for="opt in annualVolumeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}
                      </option>
                    </select>
                  </div>
                  <div class="space-y-1.5 md:col-span-2">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.buyerMarkets') }}</label>
                    <input v-model="formData.buyMarket" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderMarkets')" />
                  </div>
                </div>
              </div>

              <!-- 供应商字段 -->
              <div v-if="selectedRoles.has('supplier')" class="animate-in fade-in duration-300">
                <h4
                  class="text-sm font-semibold uppercase tracking-wider text-slate-400 mb-4 border-b border-slate-100 pb-2">
                  {{ $t('joinUs.supplierSection') }}</h4>
                <div class="grid md:grid-cols-2 gap-6">
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.supplierCategory') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.supCategory" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCategory')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.supplierCapacity') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.supCapacity" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCapacity')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.supplierCertifications') }}</label>
                    <input v-model="formData.supCerts" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderCertifications')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.supplierExport') }}</label>
                    <select v-model="formData.supExport"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all bg-white">
                      <option v-for="opt in exportExperienceOptions" :key="opt.value" :value="opt.value">{{ opt.label }}
                      </option>
                    </select>
                  </div>
                </div>
              </div>

              <!-- 联系人信息（从护照预填） -->
              <div>
                <h4
                  class="text-sm font-semibold uppercase tracking-wider text-slate-400 mb-4 border-b border-slate-100 pb-2">
                  {{ $t('joinUs.contactSection') }}</h4>
                <p class="text-xs text-slate-400 mb-4">{{ $t('joinUs.contactPrefillNote') }}</p>
                <div class="grid md:grid-cols-2 gap-6">
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.contactName') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.contactName" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all bg-slate-50"
                      :placeholder="$t('joinUs.placeholderName')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.contactTitle') }}</label>
                    <input v-model="formData.contactTitle" type="text"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all"
                      :placeholder="$t('joinUs.placeholderTitle')" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.contactEmail') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.email" type="email"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all bg-slate-50"
                      placeholder="name@company.com" />
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-sm font-medium text-slate-700">{{ $t('joinUs.contactPhone') }}<span
                        class="text-red-500">*</span></label>
                    <input v-model="formData.phone" type="tel"
                      class="w-full px-4 py-2.5 rounded-xl border border-slate-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none transition-all bg-slate-50"
                      placeholder="+86 138-xxxx-xxxx" />
                  </div>
                </div>
              </div>

              <!-- 提交按钮 -->
              <div class="pt-4">
                <button type="submit" :disabled="submitting"
                  class="w-full flex items-center justify-center gap-2 py-4 rounded-xl bg-blue-600 text-white font-medium hover:bg-blue-700 transition-colors shadow-lg shadow-blue-600/20 disabled:opacity-60 disabled:cursor-not-allowed">
                  <Loader2 v-if="submitting" class="w-5 h-5 animate-spin" />
                  {{ submitting ? $t('joinUs.submittingBtn') : $t('joinUs.submitBtn') }}
                </button>
              </div>
            </form>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircle2, ArrowRight, Briefcase, Factory,
  ChevronLeft, ShieldCheck, LogIn, Loader2
} from 'lucide-vue-next'


const { t } = useI18n()

// ==================== API 基础路径 ====================
// 生产环境: https://api.gsc.greensmartplanet.cn, 开发环境: 空字符串 (走 Vite 代理)
const API_BASE = import.meta.env.VITE_API_BASE || ''

// ==================== 护照配置 ====================
const PASSPORT_AUTH_URL = 'https://login.aiplusglobal.cn/oauth/login'
const PASSPORT_CLIENT_ID = '40ee317d4dd396d8'
const PASSPORT_SCOPES = 'personal_basic realname_kyc phone email'

// ==================== 状态 ====================
const formSectionRef = ref(null)
const currentStep = ref(1)
const selectedRoles = ref(new Set())
const passportUser = ref(null)
const passportLoading = ref(false)
const passportError = ref('')
const submitting = ref(false)

const formData = reactive({
  company: '',
  country: '',
  regNumber: '',
  established: '',
  buyCategory: '',
  buyVolume: '',
  buyMarket: '',
  supCategory: '',
  supCapacity: '',
  supCerts: '',
  supExport: '',
  contactName: '',
  contactTitle: '',
  email: '',
  phone: '',
})

// ==================== OAuth2 跳转链接 ====================
const redirectUri = computed(() => `${window.location.origin}/oauth/callback`)
// 切换步骤时自动滚动到表单区域
watch(currentStep, () => {
  nextTick(() => scrollToForm())
})

const passportAuthUrl = computed(() => {
  const params = new URLSearchParams({
    client_id: PASSPORT_CLIENT_ID,
    scope: PASSPORT_SCOPES,
    redirect_uri: redirectUri.value,
    entry: 'login',
  })
  return `${PASSPORT_AUTH_URL}?${params.toString()}`
})

// ==================== 从 URL 解析 access_token ====================
function getAccessTokenFromUrl() {
  const params = new URLSearchParams(window.location.search)
  return params.get('access_token')
}

// ==================== 调用护照 /me 接口 ====================
async function fetchPassportUser(token) {
  const resp = await fetch(`${API_BASE}/api/passport/me`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ access_token: token }),
  })
  if (!resp.ok) {
    throw new Error(`获取用户信息失败 (${resp.status})`)
  }
  const json = await resp.json()
  // 兼容 Node.js ({status:true}) 和 Spring Boot ({code:200}) 两种响应格式
  const ok = json.status === true || json.code === 200
  if (!ok || !json.data) {
    throw new Error(json.message || '返回数据异常')
  }
  return json.data
}

// ==================== 预填联系人字段 ====================
function prefillContactFromPassport(user) {
  formData.contactName = user.profile?.realName || user.nickname || user.username || ''
  formData.email = user.email || ''
  formData.phone = user.phone || ''
  // 从 profile.country 可以推断默认国家，但这里先留空让用户填
}

// ==================== 处理 OAuth2 回调 ====================
async function handlePassportCallback() {
  const token = getAccessTokenFromUrl()
  if (!token) return false

  passportLoading.value = true
  // 清除 URL 中的 token，避免泄露和刷新重复处理
  const url = new URL(window.location)
  url.searchParams.delete('access_token')
  url.searchParams.delete('token_type')
  url.searchParams.delete('expires_in')
  url.searchParams.delete('scope')
  url.searchParams.delete('lang')
  url.searchParams.delete('state')
  window.history.replaceState({}, '', url.toString())

  try {
    const user = await fetchPassportUser(token)
    passportUser.value = user
    prefillContactFromPassport(user)
    // 存入 sessionStorage，防止刷新丢失
    sessionStorage.setItem('passportUser', JSON.stringify(user))
    return true
  } catch (err) {
    console.error('获取护照用户信息失败:', err)
    passportError.value = t('joinUs.errorPassportFailed')
    return false
  } finally {
    passportLoading.value = false
  }
}

// ==================== 登录按钮 ====================
function handlePassportAuth() {
  window.location.href = passportAuthUrl.value
}

// ==================== 角色选择 ====================
function toggleRole(role) {
  const newRoles = new Set(selectedRoles.value)
  if (newRoles.has(role)) {
    newRoles.delete(role)
  } else {
    newRoles.add(role)
  }
  selectedRoles.value = newRoles
}

function confirmRoles() {
  if (selectedRoles.value.size === 0) {
    alert(t('joinUs.errorRoleRequired'))
    return
  }
  currentStep.value = 3
}

const step3Title = computed(() => {
  if (selectedRoles.value.size === 2) return t('joinUs.step3TitleBoth')
  if (selectedRoles.value.has('buyer')) return t('joinUs.step3TitleBuyer')
  return t('joinUs.step3TitleSupplier')
})

// ==================== 下拉选项（从 i18n 翻译） ====================
const annualVolumeOptions = computed(() => [
  { value: '', label: t('joinUs.titleEmpty') },
  { value: '1', label: t('joinUs.annualVolumeRanges.under1m') },
  { value: '2', label: t('joinUs.annualVolumeRanges.1to5m') },
  { value: '3', label: t('joinUs.annualVolumeRanges.5to20m') },
  { value: '4', label: t('joinUs.annualVolumeRanges.20to100m') },
  { value: '5', label: t('joinUs.annualVolumeRanges.over100m') },
])

const exportExperienceOptions = computed(() => [
  { value: '', label: t('joinUs.titleEmpty') },
  { value: '0', label: t('joinUs.exportRanges.none') },
  { value: '1', label: t('joinUs.exportRanges.under3y') },
  { value: '2', label: t('joinUs.exportRanges.3to5y') },
  { value: '3', label: t('joinUs.exportRanges.5to10y') },
  { value: '4', label: t('joinUs.exportRanges.over10y') },
])

// ==================== 表单校验 ====================
function validateForm() {
  const required = [
    'company', 'country', 'regNumber', 'established',
    'contactName', 'email', 'phone',
  ]
  if (selectedRoles.value.has('buyer')) {
    required.push('buyCategory', 'buyVolume')
  }
  if (selectedRoles.value.has('supplier')) {
    required.push('supCategory', 'supCapacity')
  }

  const missing = required.filter(field => !formData[field])
  if (missing.length > 0) {
    alert(t('joinUs.errorFieldsRequired'))
    return false
  }
  return true
}

// ==================== 构建提交数据 ====================
function buildSubmitPayload() {
  const payload = {
    passportUuid: passportUser.value?.profile?.uuid || passportUser.value?.uuid,
    role: Array.from(selectedRoles.value),
    company: {
      name: formData.company,
      country: formData.country,
      regNumber: formData.regNumber,
      establishedYear: Number(formData.established),
    },
    contact: {
      name: formData.contactName,
      title: formData.contactTitle,
      email: formData.email,
      phone: formData.phone,
    },
  }

  if (selectedRoles.value.has('buyer')) {
    payload.buyerProfile = {
      category: formData.buyCategory,
      annualVolume: formData.buyVolume,
      targetMarkets: formData.buyMarket,
    }
  }

  if (selectedRoles.value.has('supplier')) {
    payload.supplierProfile = {
      category: formData.supCategory,
      annualCapacity: formData.supCapacity,
      certifications: formData.supCerts,
      exportExperience: formData.supExport,
    }
  }

  return payload
}

// ==================== 提交 ====================
async function submitApplication() {
  if (!validateForm()) return

  const payload = buildSubmitPayload()
  console.log('提交数据:', JSON.stringify(payload, null, 2))

  submitting.value = true
  try {
    const resp = await fetch(`${API_BASE}/api/applications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    const result = await resp.json()

    if (result.code !== 200) {
      alert(result.message || t('joinUs.errorSubmitFailed'))
      // 重复提交也重置表单
      if (result.code === 6003) {
        resetForm()
      }
      return
    }

    alert(t('joinUs.successMessage', { id: result.data.applicationId }))
    resetForm()
  } catch (err) {
    console.error('提交失败:', err)
    alert(t('joinUs.errorNetworkFailed'))
  } finally {
    submitting.value = false
  }
}

function scrollToForm() {
  formSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function resetForm() {
  currentStep.value = 1
  selectedRoles.value = new Set()
  passportUser.value = null
  passportError.value = ''
  Object.assign(formData, {
    company: '', country: '', regNumber: '', established: '',
    buyCategory: '', buyVolume: '', buyMarket: '',
    supCategory: '', supCapacity: '', supCerts: '', supExport: '',
    contactName: '', contactTitle: '', email: '', phone: '',
  })
  sessionStorage.removeItem('passportUser')
  scrollToForm()
}

// ==================== 页面初始化 ====================
onMounted(async () => {
  // 1. 尝试从 sessionStorage 恢复用户信息（防止刷新丢失）
  const cached = sessionStorage.getItem('passportUser')
  if (cached) {
    try {
      passportUser.value = JSON.parse(cached)
      prefillContactFromPassport(passportUser.value)
    } catch {
      sessionStorage.removeItem('passportUser')
    }
  }

  // 2. 处理 OAuth2 回调（URL 中有 access_token）
  const handled = await handlePassportCallback()

  // 3. 已经认证过，直接跳到步骤 2
  if (passportUser.value) {
    currentStep.value = handled ? 2 : 2
  }
})
</script>
