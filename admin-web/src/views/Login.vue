<template>
  <div class="login-bg">
    <el-card class="login-card">
      <h2 class="title">校园报修管理系统</h2>
      <p class="subtitle">后勤管理端</p>
      <el-form :model="form" @keyup.enter="onLogin">
        <el-form-item>
          <el-input v-model="form.jobNo" placeholder="学工号" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" size="large">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">
          登 录
        </el-button>
      </el-form>
      <p class="tip">管理员账号由系统管理员分配（演示：admin1 / 123456）</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ jobNo: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.jobNo || !form.password) {
    ElMessage.warning('请输入学工号和密码')
    return
  }
  loading.value = true
  try {
    const user = await auth.login(form.jobNo, form.password)
    ElMessage.success(`欢迎，${user.name}`)
    router.push('/dashboard')
  } catch (e) { /* 拦截器已提示 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-bg {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1d2b36 0%, #2c5364 100%);
}
.login-card { width: 380px; max-width: calc(100vw - 32px); padding: 12px 8px; }
.title { text-align: center; margin: 8px 0 4px; color: #1d2b36; }
.subtitle { text-align: center; color: #909399; margin: 0 0 20px; font-size: 13px; }
.tip { color: #c0c4cc; font-size: 12px; text-align: center; margin-top: 14px; }
</style>
