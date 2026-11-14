<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>差评回访</b>
        <el-radio-group v-model="status" size="small" @change="reload">
          <el-radio-button value="PENDING">待回访</el-radio-button>
          <el-radio-button value="HANDLED">已回访</el-radio-button>
          <el-radio-button value="CLOSED">已关闭</el-radio-button>
        </el-radio-group>
        <span class="muted">评价 ≤2 星自动生成，处理结果与说明会留痕</span>
      </div>
    </template>

    <el-table :data="list" border>
      <el-table-column prop="orderNo" label="工单号" width="165" />
      <el-table-column prop="locationText" label="位置" min-width="170" show-overflow-tooltip />
      <el-table-column prop="workerName" label="维修工" width="100" />
      <el-table-column label="星级" width="150">
        <template #default="{ row }">
          <span class="stars">{{ '★'.repeat(row.rating) }}{{ '☆'.repeat(5 - row.rating) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="evaluationContent" label="评价内容" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="生成时间" width="165" />
      <el-table-column prop="remark" label="回访说明" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goOrder(row)">详情</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="primary" @click="openHandle(row)">处理</el-button>
          <span v-else class="muted">{{ row.status === 'HANDLED' ? '已回访' : '已关闭' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialog" title="处理回访任务" width="460px">
      <p class="muted" style="margin-bottom: 10px">
        工单 {{ target?.orderNo }} · {{ target?.workerName }} · {{ target?.rating }} 星
      </p>
      <el-radio-group v-model="result">
        <el-radio value="HANDLED">已回访，问题已解决</el-radio>
        <el-radio value="CLOSED">关闭，无需继续</el-radio>
      </el-radio-group>
      <el-input v-model="remark" type="textarea" :rows="3" style="margin-top: 12px"
                placeholder="回访说明（选填），如：已联系报修人，师傅次日返工修复" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()

const status = ref('PENDING')
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const dialog = ref(false)
const target = ref(null)
const result = ref('HANDLED')
const remark = ref('')

async function load() {
  const d = await api.get('/revisits', { params: { status: status.value, page: page.value, pageSize } })
  list.value = d.list
  total.value = d.total
}

function reload() {
  page.value = 1
  load()
}

function openHandle(row) {
  target.value = row
  result.value = 'HANDLED'
  remark.value = ''
  dialog.value = true
}

function goOrder(row) {
  router.push({ path: '/orders', query: { orderId: row.orderId } })
}

async function submit() {
  await api.put(`/revisits/${target.value.id}`, { status: result.value, remark: remark.value.trim() || null })
  ElMessage.success('已记录回访结果')
  dialog.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: var(--muted); font-size: 12px; }
.stars { color: #e6a23c; letter-spacing: 1px; }
</style>
