import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import Home from '@/views/Home.vue'
import TrustedDelivery from '@/views/TrustedDelivery.vue'
import SmartProcurement from '@/views/SmartProcurement.vue'
import DecisionCenter from '@/views/DecisionCenter.vue'
import JoinUs from '@/views/JoinUs.vue'
import Intel from '@/views/Intel.vue'
import OAuthCallback from '@/views/OAuthCallback.vue'

const routes = [
  {
    path: '/oauth/callback',
    name: 'oauth-callback',
    component: OAuthCallback,
  },
  {
    path: '/',
    component: DefaultLayout,
    children: [
      { path: '', name: 'home', component: Home },
      { path: 'trusted-delivery', name: 'trusted-delivery', component: TrustedDelivery },
      { path: 'smart-procurement', name: 'smart-procurement', component: SmartProcurement },
      { path: 'decision-center', name: 'decision-center', component: DecisionCenter },
      { path: 'join', name: 'join', component: JoinUs },
      { path: 'news', name: 'news', component: Intel },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router
