<template>
  <el-container class="layout">
    <el-aside :width="isMobile ? '76px' : '248px'" class="aside">
      <div class="logo">
        <span class="logo-mark">修</span><span v-if="!isMobile">校园报修管理</span>
      </div>
      <el-menu :default-active="$route.path" router background-color="#e9ebed" text-color="#5b6e70"
               active-text-color="#f5f6f7">
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
.layout { min-height: 100dvh; height: 100dvh; }
.aside {
  background: var(--paper-deep);
  border-right: 1.5px dashed var(--line);
  transition: width 0.2s ease;
}
.logo {
  color: var(--ink); font-size: 15px; font-weight: 800; height: 68px;
  display: flex; align-items: center; justify-content: flex-start; gap: 13px;
  padding: 0 20px; border-bottom: 1.5px dashed var(--line);
  white-space: nowrap; overflow: hidden; letter-spacing: .03em;
}
.logo-mark {
  display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  color: #fff; background: var(--teal); border: 2px solid #fff;
  box-shadow: 0 0 0 1.5px var(--teal), 3px 3px 0 var(--ink);
  font-family: "KaiTi", "STKaiti", serif; font-size: 17px; transform: rotate(-5deg);
}
.aside :deep(.el-menu) { border-right: none; padding: 14px 9px; background: transparent !important; }
.aside :deep(.el-menu-item) {
  height: 44px; line-height: 44px; margin: 3px 0; padding: 0 15px !important;
  border-radius: 10px; color: #5b6e70 !important; font-size: 13px; font-weight: 600;
  transition: color .16s ease, background .16s ease, transform .16s ease;
}
.aside :deep(.el-menu-item .el-icon) { color: inherit; font-size: 16px; }
.aside :deep(.el-menu-item:hover) { color: var(--teal-deep) !important; background: rgba(15, 123, 126, .08) !important; }
.aside :deep(.el-menu-item.is-active) {
  color: var(--paper) !important; background: var(--ink) !important;
  box-shadow: 3px 3px 0 var(--teal);
}
.aside :deep(.el-menu-item.is-active .el-icon) { color: var(--butter); }
.header {
  position: sticky; top: 0; z-index: 10;
  background: rgba(245, 246, 247, .9); backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1.5px dashed var(--line); height: 62px;
}
.title { color: var(--ink); font-size: 15px; font-weight: 800; letter-spacing: .04em; }
.user {
  min-height: 36px; padding: 0 12px; border: 1.5px solid var(--line); border-radius: 999px;
  background: #fff; cursor: pointer; display: flex; align-items: center; gap: 6px;
  color: #536568; font-size: 12px; transition: border-color .18s ease, color .18s ease;
}
.user:hover { border-color: var(--teal); color: var(--teal-deep); }
.main { padding: 20px 22px; overflow: auto; background: transparent; }
@media (max-width: 768px) {
  .logo { justify-content: center; padding: 0 8px; }
  .aside :deep(.el-menu) { padding: 12px 7px; }
  .aside :deep(.el-menu-item) { justify-content: center; padding: 0 !important; }
  .main { padding: 12px 10px; }
}
</style>
