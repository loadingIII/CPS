<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>超时预警</b>
        <el-radio-group v-model="handled" size="small" @change="reload">
          <el-radio-button :value="false">未处理</el-radio-button>
          <el-radio-button :value="true">已处理</el-radio-button>
        </el-radio-group>
        <span class="muted">超时是工单标记位，预警事件独立落库，可逐单核对覆盖率</span>
      </div>
    </template>

    <el-table :data="list" border>
      <el-table-column label="级别" width="86">
        <template #default="{ row }">
          <el-tag :type="row.level >= 2 ? 'danger' : 'warning'" size="small" effect="dark">
            {{ row.level }} 级
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阶段" width="110">
        <template #default="{ row }">
          {{ row.stage === 'ACCEPT' ? '接单超时' : '处理超时' }}
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="工单号" width="165" />
      <el-table-column prop="locationText" label="位置" min-width="180" show-overflow-tooltip />
      <el-table-column prop="faultType" label="类型" width="110" />
      <el-table-column label="紧急度" width="88">
        <template #default="{ row }">
          <el-tag :type="row.urgency === 'URGENT' ? 'danger' : 'info'" size="small">
            {{ row.urgency === 'URGENT' ? '紧急' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="预警时间" width="165" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goOrder(row)">详情</el-button>
          <el-button v-if="!row.handled" link type="primary" @click="markHandled(row)">标记已处理</el-button>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const router = useRouter()

const handled = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10

async function load() {
  const d = await api.get('/alerts', { params: { handled: handled.value, page: page.value, pageSize } })
  list.value = d.list
  total.value = d.total
}

function reload() {
  page.value = 1
  load()
}

function goOrder(row) {
  router.push({ path: '/orders', query: { orderId: row.orderId } })
}

async function markHandled(row) {
  await ElMessageBox.confirm(`确认已将工单 ${row.orderNo} 的超时预警处理完毕？`, '标记已处理', { type: 'warning' })
  await api.put(`/alerts/${row.id}/handle`)
  ElMessage.success('已处理')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: var(--muted); font-size: 12px; }
</style>
