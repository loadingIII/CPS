<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>审核工作台</b>
        <el-radio-group v-model="status" @change="load">
          <el-radio-button value="PENDING_AUDIT">待审核</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
          <el-radio-button value="">全部</el-radio-button>
        </el-radio-group>
        <div class="batch-actions">
          <span class="muted">已选 {{ selectedRows.length }} 条</span>
          <el-button size="small" type="success" plain :disabled="!selectedRows.length" @click="batchApprove">
            批量通过
          </el-button>
          <el-button size="small" type="warning" plain :disabled="!selectedRows.length" @click="openBatchReject">
            批量驳回
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" border :row-class-name="rowClass" @selection-change="onSelect">
      <el-table-column type="selection" width="42" :selectable="row => row.status === 'PENDING_AUDIT'" />
      <el-table-column label="工单号" width="170">
        <template #default="{ row }">
          <span :class="{ urgent: row.urgency === 'URGENT' }">{{ row.orderNo }}</span>
          <el-tag v-if="row.isOverdue" type="danger" size="small" effect="dark" style="margin-left: 4px">超时</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="紧急度" width="90">
        <template #default="{ row }">
          <el-tag :type="row.urgency === 'URGENT' ? 'danger' : 'info'" size="small">
            {{ row.urgency === 'URGENT' ? '紧急' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="locationText" label="位置" min-width="180" show-overflow-tooltip />
      <el-table-column prop="faultType" label="类型" width="110" />
      <el-table-column prop="reporterName" label="报修人" width="90" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="submittedAt" label="提交时间" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="STATUS_TAG[row.status]">{{ STATUS_LABEL[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <template v-if="row.status === 'PENDING_AUDIT'">
            <el-button link type="success" @click="approve(row)">通过</el-button>
            <el-button link type="warning" @click="openReject(row)">驳回</el-button>
            <el-button link type="danger" @click="voidOrder(row)">作废</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-drawer v-model="drawer" title="工单详情" :size="drawerSize">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detail.locationText }}</el-descriptions-item>
          <el-descriptions-item label="类型/紧急度">
            {{ detail.faultType }} / {{ detail.urgency === 'URGENT' ? '紧急' : '普通' }}
          </el-descriptions-item>
          <el-descriptions-item label="报修人">{{ detail.reporterName }} {{ detail.reporterPhone }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="报修图片">
            <el-image v-for="(img, i) in detail.reportImages" :key="i" :src="img"
                      :preview-src-list="detail.reportImages" fit="cover"
                      style="width: 90px; height: 90px; margin-right: 6px; border-radius: 4px" />
            <span v-if="!detail.reportImages?.length" class="muted">未上传</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.status === 'PENDING_AUDIT'" style="margin-top: 16px">
          <el-button type="success" @click="approve(detail)">审核通过</el-button>
          <el-button type="warning" @click="openReject(detail)">驳回</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="rejectDialog" title="驳回（必填理由）" width="420px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="说明需要补充的内容，如：照片无法判断故障位置" />
      <template #footer>
        <el-button @click="rejectDialog = false">取消</el-button>
        <el-button type="warning" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchRejectDialog" title="批量驳回（必填理由）" width="440px">
      <p class="muted" style="margin-bottom: 8px">将对已选 {{ selectedRows.length }} 条待审核工单执行驳回，逐条返回结果。</p>
      <el-input v-model="batchReason" type="textarea" :rows="3" placeholder="统一驳回理由，如：报修信息不完整，请补充照片" />
      <template #footer>
        <el-button @click="batchRejectDialog = false">取消</el-button>
        <el-button type="warning" @click="doBatchReject">确认批量驳回</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const STATUS_LABEL = {
  PENDING_AUDIT: '待审核', PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成',
  EVALUATED: '已评价', REJECTED: '已驳回', CANCELLED: '已取消',
}
const STATUS_TAG = {
  PENDING_AUDIT: 'warning', PENDING: 'danger', PROCESSING: 'primary', COMPLETED: 'success',
  EVALUATED: 'info', REJECTED: 'warning', CANCELLED: 'info',
}

const status = ref('PENDING_AUDIT')
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const drawer = ref(false)
const drawerSize = ref(window.innerWidth < 768 ? '92%' : '420px')
const detail = ref(null)
const rejectDialog = ref(false)
const rejectReason = ref('')
let rejectTarget = null
const selectedRows = ref([])
const batchRejectDialog = ref(false)
const batchReason = ref('')

function onSelect(rows) {
  selectedRows.value = rows
}

/** 批量审核：逐条返回结果，失败的单独提示，不影响已成功的工单 */
async function submitBatch(pass, reason) {
  const ids = selectedRows.value.map(r => r.id)
  const results = await api.post('/orders/audit-batch', { ids, pass, reason })
  const failed = results.filter(r => !r.success)
  if (failed.length) {
    ElMessage.warning(`${results.length - failed.length} 条成功，${failed.length} 条失败：${failed[0].message}`)
  } else {
    ElMessage.success(`${results.length} 条已${pass ? '通过' : '驳回'}`)
  }
  selectedRows.value = []
  await load()
}

async function batchApprove() {
  await ElMessageBox.confirm(`确认通过已选 ${selectedRows.value.length} 条工单？`, '批量通过', { type: 'success' })
  await submitBatch(true, null)
}

function openBatchReject() {
  batchReason.value = ''
  batchRejectDialog.value = true
}

async function doBatchReject() {
  if (!batchReason.value.trim()) {
    ElMessage.warning('驳回理由不能为空')
    return
  }
  batchRejectDialog.value = false
  await submitBatch(false, batchReason.value.trim())
}

async function load() {
  const d = await api.get('/orders', {
    params: { status: status.value || undefined, page: page.value, pageSize },
  })
  list.value = d.list
  total.value = d.total
}

function rowClass({ row }) {
  return row.urgency === 'URGENT' && row.status === 'PENDING_AUDIT' ? 'urgent-row' : ''
}

async function showDetail(row) {
  detail.value = await api.get(`/orders/${row.id}`)
  drawer.value = true
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过工单 ${row.orderNo}？通过后进入待派单池。`, '审核通过', { type: 'success' })
  await api.post(`/orders/${row.id}/audit`, { pass: true })
  ElMessage.success('已通过')
  drawer.value = false
  await load()
}

function openReject(row) {
  rejectTarget = row
  rejectReason.value = ''
  rejectDialog.value = true
}

async function doReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回理由不能为空')
    return
  }
  await api.post(`/orders/${rejectTarget.id}/audit`, { pass: false, reason: rejectReason.value })
  ElMessage.success('已驳回')
  rejectDialog.value = false
  drawer.value = false
  await load()
}

async function voidOrder(row) {
  await ElMessageBox.confirm(`确认作废工单 ${row.orderNo}？作废后不可恢复。`, '作废', { type: 'warning' })
  await api.post(`/orders/${row.id}/void`)
  ElMessage.success('已作废')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.batch-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.urgent { color: var(--el-color-danger); font-weight: 700; }
:deep(.urgent-row) { background: var(--blush-soft); }
.muted { color: var(--muted); font-size: 13px; }
</style>
