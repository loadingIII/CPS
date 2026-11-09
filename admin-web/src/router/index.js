import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 路由表。meta.roles 为空 = 登录即可；管理端业务页面仅 ADMIN/SYS_ADMIN。
 */
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'audit', component: () => import('../views/AuditWorkbench.vue'), meta: { title: '审核工作台', roles: ['ADMIN', 'SYS_ADMIN'] } },
      { path: 'dispatch', component: () => import('../views/DispatchWorkbench.vue'), meta: { title: '派单工作台', roles: ['ADMIN', 'SYS_ADMIN'] } },
      { path: 'orders', component: () => import('../views/OrderManage.vue'), meta: { title: '工单管理', roles: ['ADMIN', 'SYS_ADMIN'] } },
      { path: 'skills', redirect: '/workers' },
      { path: 'workers', component: () => import('../views/WorkerManage.vue'), meta: { title: '维修工管理', roles: ['ADMIN', 'SYS_ADMIN'] } },
      { path: 'users', component: () => import('../views/UserManage.vue'), meta: { title: '用户管理', roles: ['SYS_ADMIN'] } },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(to => {
  const auth = useAuthStore()
  if (to.path === '/login') return true
  if (!auth.isLoggedIn) return '/login'
  const roles = to.meta?.roles
  if (roles && !roles.includes(auth.role)) {
    return '/dashboard'
  }
  return true
})

export default router
