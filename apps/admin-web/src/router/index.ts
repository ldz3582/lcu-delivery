import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      redirect: '/overview',
      children: [
        {
          path: 'overview',
          name: 'overview',
          component: () => import('@/views/OverviewView.vue'),
          meta: { title: '概览' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'shops',
          name: 'shops',
          component: () => import('@/views/ShopsView.vue'),
          meta: { title: '商家审核' },
        },
        {
          path: 'riders',
          name: 'riders',
          component: () => import('@/views/RidersView.vue'),
          meta: { title: '骑手审核' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: '订单' },
        },
        {
          path: 'settlements',
          name: 'settlements',
          component: () => import('@/views/SettlementsView.vue'),
          meta: { title: '结算' },
        },
        {
          path: 'configs',
          name: 'configs',
          component: () => import('@/views/ConfigsView.vue'),
          meta: { title: '平台配置' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const logged = Boolean(localStorage.getItem(TOKEN_KEY))
  if (!to.meta.public && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && logged) {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 校园外卖管理后台` : '校园外卖管理后台'
})

export default router
