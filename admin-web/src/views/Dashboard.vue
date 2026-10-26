<template>
  <div>
    <el-card>
      <template #header><b>工作台概览</b></template>
      <p>欢迎，{{ auth.user?.name }}！全校工单实时概览如下。</p>
      <el-row :gutter="12">
        <el-col :xs="12" :sm="8" :md="6" v-for="s in STATUS" :key="s.key">
          <el-card shadow="hover" class="stat" @click="$router.push(s.path || '/orders')">
            <div class="stat-num" :style="{ color: s.color }">{{ counts[s.key] ?? '-' }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
    <el-card style="margin-top: 16px">
      <template #header>
        <div class="toolbar">
          <b>最新工单</b>
          <el-button link type="primary" @click="$router.push('/orders')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recent" size="small">
        <el-table-column prop="orderNo" label="工单号" width="160" />
        <el-table-column prop="locationText" label="位置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="faultType" label="类型" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG[row.status]">{{ STATUS_LABEL[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="报修人" width="90" />
        <el-table-column prop="submittedAt" label="提交时间" width="160" />
      </el-table>
    </el-card>
    <el-card style="margin-top: 16px">
      <template #header><b>快捷入口</b></template>
      <el-space wrap>
        <el-button type="primary" @click="$router.push('/audit')">待审核处理</el-button>
        <el-button type="success" @click="$router.push('/dispatch')">待派单处理</el-button>
        <el-button @click="$router.push('/orders')">全部工单</el-button>
        <el-button @click="$router.push('/workers')">维修工管理</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const counts = ref({})
const recent = ref([])

const STATUS = [
  { key: 'PENDING_AUDIT', label: '待审核', color: '#e6a23c', path: '/audit' },
  { key: 'PENDING', label: '待处理(待派单)', color: '#f56c6c', path: '/dispatch' },
  { key: 'PROCESSING', label: '处理中', color: '#409eff', path: '/orders' },
  { key: 'COMPLETED', label: '待评价', color: '#67c23a', path: '/orders' },
  { key: 'EVALUATED', label: '已评价', color: '#909399', path: '/orders' },
  { key: 'REJECTED', label: '已驳回', color: '#b88230', path: '/orders' },
  { key: 'CANCELLED', label: '已取消', color: '#c0c4cc', path: '/orders' },
]
const STATUS_LABEL = {
  PENDING_AUDIT: '待审核', PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成',
  EVALUATED: '已评价', REJECTED: '已驳回', CANCELLED: '已取消',
}
const STATUS_TAG = {
  PENDING_AUDIT: 'warning', PENDING: 'danger', PROCESSING: 'primary', COMPLETED: 'success',
  EVALUATED: 'info', REJECTED: 'warning', CANCELLED: 'info',
}

onMounted(async () => {
  await Promise.all(STATUS.map(async s => {
    try {
      const d = await api.get('/orders', { params: { status: s.key, page: 1, pageSize: 1 } })
      counts.value[s.key] = d.total
    } catch { counts.value[s.key] = '-' }
  }))
  try {
    const d = await api.get('/orders', { params: { page: 1, pageSize: 8 } })
    recent.value = d.list
  } catch { /* 忽略 */ }
})
</script>

<style scoped>
.stat { text-align: center; cursor: pointer; margin-bottom: 12px; }
.stat-num { font-size: 32px; font-weight: 700; }
.stat-label { color: #909399; font-size: 13px; margin-top: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
</style>
