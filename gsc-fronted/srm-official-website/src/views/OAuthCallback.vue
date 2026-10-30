<template>
  <main class="flex min-h-screen items-center justify-center bg-slate-50 px-6">
    <div class="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
      <Loader2 v-if="loading" class="mx-auto h-10 w-10 animate-spin text-blue-600" />
      <AlertCircle v-else class="mx-auto h-10 w-10 text-red-500" />

      <h1 class="mt-5 text-xl font-semibold text-slate-900">
        {{ loading ? $t('oauthCallback.processing') : $t('oauthCallback.failed') }}
      </h1>
      <p class="mt-2 text-sm leading-6 text-slate-600">
        {{ loading ? $t('oauthCallback.processingDesc') : $t('oauthCallback.failedDesc') }}
      </p>

      <div v-if="!loading" class="mt-7 flex flex-col gap-3">
        <button
          type="button"
          class="w-full rounded-xl bg-blue-600 px-4 py-3 font-medium text-white transition-colors hover:bg-blue-700"
          @click="retry"
        >
          {{ $t('oauthCallback.retry') }}
        </button>
        <RouterLink
          to="/join"
          class="w-full rounded-xl border border-slate-200 px-4 py-3 font-medium text-slate-700 transition-colors hover:bg-slate-50"
        >
          {{ $t('oauthCallback.backToJoin') }}
        </RouterLink>
      </div>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlertCircle, Loader2 } from 'lucide-vue-next'

const router = useRouter()
const loading = ref(true)

const API_BASE = import.meta.env.VITE_API_BASE || ''

function getAccessToken() {
  const queryToken = new URLSearchParams(window.location.search).get('access_token')
  if (queryToken) return queryToken

  return new URLSearchParams(window.location.hash.replace(/^#/, '')).get('access_token')
}

function clearTokenFromUrl() {
  const url = new URL(window.location)
  const searchParams = new URLSearchParams(url.search)
  const hashParams = new URLSearchParams(url.hash.replace(/^#/, ''))

  ;['access_token', 'token_type', 'expires_in', 'scope', 'lang', 'state'].forEach((key) => {
    searchParams.delete(key)
    hashParams.delete(key)
  })

  url.search = searchParams.toString()
  url.hash = hashParams.toString()
  window.history.replaceState({}, '', url.toString())
}

async function fetchPassportUser(token) {
  const response = await fetch(`${API_BASE}/api/passport/me`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ access_token: token }),
  })

  if (!response.ok) {
    throw new Error(`Passport user request failed (${response.status})`)
  }

  const result = await response.json()
  const ok = result.status === true || result.code === 200
  if (!ok || !result.data) {
    throw new Error(result.message || 'Invalid passport response')
  }

  return result.data
}

async function processCallback() {
  loading.value = true
  const token = getAccessToken()

  if (!token) {
    loading.value = false
    return
  }

  clearTokenFromUrl()

  try {
    const passportUser = await fetchPassportUser(token)
    sessionStorage.setItem('passportUser', JSON.stringify(passportUser))
    await router.replace('/join')
  } catch (error) {
    console.error('AI World Passport callback failed:', error)
    loading.value = false
  }
}

function retry() {
  window.location.href = `${window.location.origin}/join`
}

onMounted(processCallback)
</script>
