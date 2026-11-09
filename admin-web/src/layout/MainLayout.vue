<template>
  <el-container class="layout">
    <el-aside :width="isMobile ? '64px' : '210px'" class="aside">
      <div class="logo">
        <span class="logo-mark"></span><span v-if="!isMobile">校园报修管理</span>
      </div>
      <el-menu :default-active="$route.path" router background-color="#1d2b36" text-color="#a7b1c2"
               active-text-color="#ffffff">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><span v-if="!isMobile">工作台</span>
        </el-menu-item>
        <template v-for="item in menus" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!isMobile">{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="title">{{ $route.meta?.title || '' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><User /></el-icon>&nbsp;{{ auth.user?.name }}（{{ roleLabel }}）
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

// 窄屏（内嵌面板/小窗口）时侧边栏收成图标栏
const isMobile = ref(window.matchMedia('(max-width: 768px)').matches)
let mq = null
const onMqChange = e => { isMobile.value = e.matches }
onMounted(() => {
  mq = window.matchMedia('(max-width: 768px)')
  mq.addEventListener('change', onMqChange)
})
onUnmounted(() => {
  if (mq) mq.removeEventListener('change', onMqChange)
})

const allMenus = [
  { path: '/audit', title: '审核工作台', icon: 'Checked', roles: ['ADMIN', 'SYS_ADMIN'] },
  { path: '/dispatch', title: '派单工作台', icon: 'Share', roles: ['ADMIN', 'SYS_ADMIN'] },
  { path: '/orders', title: '工单管理', icon: 'Tickets', roles: ['ADMIN', 'SYS_ADMIN'] },
  { path: '/workers', title: '维修工管理', icon: 'Avatar', roles: ['ADMIN', 'SYS_ADMIN'] },
  { path: '/users', title: '用户管理', icon: 'UserFilled', roles: ['SYS_ADMIN'] },
]

const menus = computed(() => allMenus.filter(m => m.roles.includes(auth.role)))
const roleLabel = computed(() =>
  ({ ADMIN: '后勤管理员', SYS_ADMIN: '系统管理员' }[auth.role] || auth.role))

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #1d2b36; transition: width 0.2s ease; }
.logo {
  color: #fff; font-size: 15px; font-weight: 600; height: 60px;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap; overflow: hidden;
}
.logo-mark {
  width: 18px; height: 18px; border-radius: 6px; flex-shrink: 0;
  background: linear-gradient(135deg, #E8A33D, #D96A3B);
}
.aside :deep(.el-menu) { border-right: none; }
.header {
  background: #fff; display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #e6e8eb; height: 56px;
}
.title { font-size: 16px; font-weight: 600; }
.user { cursor: pointer; display: flex; align-items: center; color: #333; font-size: 14px; }
.main { padding: 16px; overflow: auto; }
@media (max-width: 768px) {
  .main { padding: 8px; }
}
</style>
