<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>工单管理</b>
        <div class="filters">
          <el-select v-model="query.urgency" placeholder="紧急度" clearable style="width: 110px">
            <el-option label="紧急" value="URGENT" />
            <el-option label="普通" value="NORMAL" />
          </el-select>
          <el-select v-model="query.faultType" placeholder="故障类型" clearable style="width: 140px">
            <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.name" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="工单号/位置/报修人" clearable style="width: 190px"
                    @keyup.enter="search" />
          <el-button type="primary" plain @click="search">查询</el-button>
          <el-button plain :loading="exporting" @click="exportExcel">导出 Excel</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="status" @tab-change="search">
      <el-tab-pane v-for="t in TABS" :key="t.value" :label="t.label" :name="t.value" />
    </el-tabs>

    <el-table :data="list" border>
      <el-table-column label="工单号" width="175">
        <template #default="{ row }">
          <span :class="{ urgent: row.urgency === 'URGENT' }">{{ row.orderNo }}</span>
          <el-tag v-if="row.isOverdue" type="danger" size="small" effect="dark" style="margin-left: 4px">超时</el-tag>
          <el-tag v-if="row.mainOrderId" type="info" size="small" effect="plain" style="margin-left: 4px">已合并</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="locationText" label="位置" min-width="170" show-overflow-tooltip />
      <el-table-column prop="faultType" label="类型" width="100" />
      <el-table-column label="紧急度" width="85">
        <template #default="{ row }">
          <el-tag :type="row.urgency === 'URGENT' ? 'danger' : 'info'" size="small">
            {{ row.urgency === 'URGENT' ? '紧急' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="STATUS_TAG[row.status]">{{ STATUS_LABEL[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="报修人" width="90" />
      <el-table-column prop="workerName" label="维修工" width="90" />
      <el-table-column prop="submittedAt" label="提交时间" width="160" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button v-if="row.status !== 'CANCELLED' && row.status !== 'EVALUATED' && !row.mainOrderId"
                     link type="warning" @click="openMerge(row)">合并</el-button>
          <el-button v-if="row.status !== 'CANCELLED' && row.status !== 'EVALUATED'"
                     link type="danger" @click="voidOrder(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.pageSize" v-model:current-page="query.page" @current-change="load" />

    <el-drawer v-model="drawer" title="工单详情" :size="drawerSize">
      <template v-if="detail">
        <div v-if="detail.status === 'COMPLETED' || detail.status === 'EVALUATED'"
             class="drawer-actions">
          <el-button type="success" plain :loading="sedimenting" @click="sedimentKb">
            沉淀为知识库草稿
          </el-button>
        </div>
        <el-descriptions :column="1" border size="small" title="基本信息">
          <el-descriptions-item label="工单号">
            <span :class="{ urgent: detail.urgency === 'URGENT' }">{{ detail.orderNo }}</span>
            <el-tag v-if="detail.isOverdue" type="danger" size="small" effect="dark"
                    style="margin-left: 6px">超时</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="STATUS_TAG[detail.status]">{{ STATUS_LABEL[detail.status] }}</el-tag>
            <span class="muted" style="margin-left: 8px">{{ DISPATCH_LABEL[detail.dispatchState] || '' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="校区">{{ detail.campusName || detail.campus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detail.locationText }}</el-descriptions-item>
          <el-descriptions-item label="类型 / 紧急度">
            {{ detail.faultType || '-' }} / {{ detail.urgency === 'URGENT' ? '紧急' : '普通' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.sourceKbTitle" label="来源知识库">
            {{ detail.sourceKbTitle }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.aiSuggestedType" label="AI 建议">
            {{ detail.aiSuggestedType }} / {{ detail.aiSuggestedUrgency === 'URGENT' ? '紧急' : '普通' }}
            <el-tag size="small" :type="detail.aiSuggestionUsed ? 'success' : 'info'" style="margin-left: 6px">
              {{ detail.aiSuggestionUsed ? '已采纳' : '未采纳' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.submittedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border size="small" title="报修人" class="desc-gap">
          <el-descriptions-item label="姓名">{{ detail.reporterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="学工号">{{ detail.reporterJobNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">
            <span v-if="detail.reporterPhone">{{ detail.reporterPhone }}</span>
            <span v-else class="muted">未填写</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border size="small" title="维修工" class="desc-gap">
          <el-descriptions-item label="姓名">{{ detail.workerName || '未派单' }}</el-descriptions-item>
          <el-descriptions-item label="工号">{{ detail.workerJobNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">
            <span v-if="detail.workerPhone">{{ detail.workerPhone }}</span>
            <span v-else class="muted">-</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border size="small" title="处理进度" class="desc-gap">
          <el-descriptions-item label="审核时间">{{ detail.auditedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="派单时间">{{ detail.assignedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="接单时间">{{ detail.acceptedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理截止">
            <span :class="{ urgent: detail.isOverdue }">{{ detail.dueAt || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="完工时间">{{ detail.completedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="评价时间">{{ detail.ratedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="派单信息">
            {{ detail.dispatchSource ? SOURCE_LABEL[detail.dispatchSource] : '-' }}
            <span class="muted" style="margin-left: 8px">派单 {{ detail.dispatchCount || 0 }} 次 · 拒单 {{ detail.rejectCount || 0 }} 次</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border size="small" title="报修内容" class="desc-gap">
          <el-descriptions-item label="描述">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报修图片">
            <el-image v-for="(img, i) in detail.reportImages" :key="'r' + i" :src="img"
                      :preview-src-list="detail.reportImages" fit="cover"
                      style="width: 84px; height: 84px; margin-right: 6px; border-radius: 4px" />
            <span v-if="!detail.reportImages?.length" class="muted">未上传</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.finishSummary" label="完工说明">{{ detail.finishSummary }}</el-descriptions-item>
          <el-descriptions-item label="完工图片">
            <el-image v-for="(img, i) in detail.finishImages" :key="'f' + i" :src="img"
                      :preview-src-list="detail.finishImages" fit="cover"
                      style="width: 84px; height: 84px; margin-right: 6px; border-radius: 4px" />
            <span v-if="!detail.finishImages?.length" class="muted">未上传</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.parts?.length" label="配件消耗">
            <span v-for="p in detail.parts" :key="p.id" style="margin-right: 10px">
              {{ p.partName }} × {{ p.quantity }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="detail.mainOrderNo || detail.evaluation"
                         :column="1" border size="small" title="其他" class="desc-gap">
          <el-descriptions-item v-if="detail.mainOrderNo" label="合并">
            已并入主单 {{ detail.mainOrderNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.evaluation" label="评价">
            {{ '★'.repeat(detail.evaluation.rating) }}{{ '☆'.repeat(5 - detail.evaluation.rating) }}
            <span style="margin-left: 6px">{{ detail.evaluation.content }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 16px 0 8px">同位置历史报修（近 90 天）</h4>
        <el-table :data="similar" border size="small" max-height="220">
          <el-table-column prop="orderNo" label="工单号" width="160" />
          <el-table-column prop="locationText" label="位置" min-width="150" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="80" />
          <el-table-column prop="workerName" label="维修工" width="80" />
          <el-table-column prop="completedAt" label="完工时间" width="150" />
          <el-table-column prop="rating" label="星级" width="70" />
        </el-table>
        <p v-if="!similar.length" class="muted" style="margin: 6px 0 0">暂无同位置同类型的历史工单</p>

        <h4 style="margin: 16px 0 8px">状态时间线</h4>
        <OrderTimeline :logs="logs" />
      </template>
    </el-drawer>

    <el-dialog v-model="mergeDialog" title="合并重复工单" width="560px">
      <p class="muted" style="margin-bottom: 10px">
        当前单：<b>{{ mergeTarget?.orderNo }}</b>（{{ mergeTarget?.locationText }}）。合并后当前单作废并关联主单，报修人会收到通知。
      </p>
      <div class="merge-search">
        <el-input v-model="mergeKeyword" placeholder="输入主单工单号 / 位置搜索" clearable style="width: 300px"
                  @keyup.enter="searchMain" />
        <el-button type="primary" plain @click="searchMain">搜索主单</el-button>
      </div>
      <el-table :data="mergeCandidates" border size="small" style="margin-top: 10px" max-height="240"
                highlight-current-row @current-change="pickMain">
        <el-table-column prop="orderNo" label="工单号" width="160" />
        <el-table-column prop="locationText" label="位置" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">{{ STATUS_LABEL[row.status] }}</template>
        </el-table-column>
      </el-table>
      <el-input v-model="mergeReason" type="textarea" :rows="2" style="margin-top: 10px"
                placeholder="合并说明（选填），如：同一处空调故障重复报修" />
      <template #footer>
        <el-button @click="mergeDialog = false">取消</el-button>
        <el-button type="warning" :disabled="!mergeMain" @click="doMerge">
          合并到 {{ mergeMain?.orderNo || '未选择主单' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import OrderTimeline from '../components/OrderTimeline.vue'

const route = useRoute()

const STATUS_LABEL = {
  PENDING_AUDIT: '待审核', PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成',
  EVALUATED: '已评价', REJECTED: '已驳回', CANCELLED: '已取消',
}
const DISPATCH_LABEL = {
  NONE: '',
  POOL: '待派单',
  OFFERED: '已派待接',
  TAKEN: '已承接',
}
const SOURCE_LABEL = {
  MANUAL: '人工派单',
  GRAB: '抢单（历史）',
  AUTO: '自动派单',
}
const STATUS_TAG = {
  PENDING_AUDIT: 'warning', PENDING: 'danger', PROCESSING: 'primary', COMPLETED: 'success',
  EVALUATED: 'info', REJECTED: 'warning', CANCELLED: 'info',
}
const TABS = [
  { value: '', label: '全部' },
  { value: 'PENDING_AUDIT', label: '待审核' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'EVALUATED', label: '已评价' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
]

const skills = ref([])
const list = ref([])
const total = ref(0)
const status = ref('')
const query = ref({ urgency: null, faultType: null, keyword: '', page: 1, pageSize: 10 })
const drawer = ref(false)
const drawerSize = ref(window.innerWidth < 768 ? '94%' : '560px')
const detail = ref(null)
const logs = ref([])
const similar = ref([])
const exporting = ref(false)
const sedimenting = ref(false)

const mergeDialog = ref(false)
const mergeTarget = ref(null)
const mergeKeyword = ref('')
const mergeCandidates = ref([])
const mergeMain = ref(null)
const mergeReason = ref('')

function openMerge(row) {  mergeTarget.value = row
  mergeKeyword.value = ''
  mergeCandidates.value = []
  mergeMain.value = null
  mergeReason.value = ''
  mergeDialog.value = true
}

async function searchMain() {
  if (!mergeKeyword.value.trim()) {
    ElMessage.warning('请输入工单号或位置关键词')
    return
  }
  const d = await api.get('/orders', {
    params: { keyword: mergeKeyword.value.trim(), page: 1, pageSize: 10 },
  })
  mergeCandidates.value = (d.list || []).filter(o =>
    o.id !== mergeTarget.value.id
    && o.status !== 'CANCELLED'
    && o.status !== 'EVALUATED')
  if (!mergeCandidates.value.length) ElMessage.info('没有可作为主单的工单')
}

function pickMain(row) {
  if (row) mergeMain.value = row
}

async function doMerge() {
  if (!mergeMain.value) return
  await ElMessageBox.confirm(
    `确认把 ${mergeTarget.value.orderNo} 合并到主单 ${mergeMain.value.orderNo}？当前单将作废。`,
    '合并确认', { type: 'warning' })
  await api.post(`/orders/${mergeTarget.value.id}/merge`, {
    mainOrderId: mergeMain.value.id,
    reason: mergeReason.value.trim() || null,
  })
  ElMessage.success('已合并')
  mergeDialog.value = false
  await load()
}

async function voidOrder(row) {
  await ElMessageBox.confirm(
    `确认作废工单 ${row.orderNo}？作废后不可恢复，已承接的师傅会收到通知。`,
    '作废工单', { type: 'warning' })
  await api.post(`/orders/${row.id}/void`)
  ElMessage.success('已作废')
  await load()
}

async function load() {
  const d = await api.get('/orders', {
    params: {
      status: status.value || undefined,
      urgency: query.value.urgency || undefined,
      faultType: query.value.faultType || undefined,
      keyword: query.value.keyword || undefined,
      page: query.value.page, pageSize: query.value.pageSize,
    },
  })
  list.value = d.list
  total.value = d.total
}

function search() {
  query.value.page = 1
  load()
}

async function showDetail(row) {
  detail.value = await api.get(`/orders/${row.id}`)
  logs.value = await api.get(`/orders/${row.id}/logs`)
  similar.value = await api.get(`/orders/${row.id}/similar-history`, { params: { days: 90 } })
    .catch(() => [])
  drawer.value = true
}

/** 导出当前筛选条件下的工单明细；用 fetch 直接拿 blob，避免 axios 拦截器把二进制当 Result */
async function exportExcel() {
  exporting.value = true
  try {
    const params = new URLSearchParams()
    if (status.value) params.set('status', status.value)
    if (query.value.urgency) params.set('urgency', query.value.urgency)
    if (query.value.faultType) params.set('faultType', query.value.faultType)
    if (query.value.keyword) params.set('keyword', query.value.keyword)
    const res = await fetch('/api/orders/export?' + params.toString(), {
      headers: { Authorization: 'Bearer ' + localStorage.getItem('token') },
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.message || '导出失败')
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `工单导出_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function sedimentKb() {
  sedimenting.value = true
  try {
    const r = await api.post(`/kb/articles/from-order/${detail.value.id}`)
    if (r.status === 1) {
      ElMessage.info(`该工单已沉淀为《${r.title}》，当前已发布`)
    } else {
      ElMessage.success(`已生成知识库草稿《${r.title}》，请到「维修知识库」编辑后发布`)
    }
  } finally {
    sedimenting.value = false
  }
}

onMounted(async () => {
  load()
  skills.value = await api.get('/skills')
  // 从通知/预警/回访跳进来时，直接打开对应工单详情
  const orderId = route.query.orderId
  if (orderId) {
    detail.value = await api.get(`/orders/${orderId}`)
    logs.value = await api.get(`/orders/${orderId}/logs`)
    similar.value = await api.get(`/orders/${orderId}/similar-history`, { params: { days: 90 } })
      .catch(() => [])
    drawer.value = true
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.urgent { color: var(--el-color-danger); font-weight: 700; }
.muted { color: var(--muted); font-size: 13px; }
.desc-gap { margin-top: 14px; }
.drawer-actions { display: flex; justify-content: flex-end; margin-bottom: 10px; }
.merge-search { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
