<template>
  <div class="wb">
    <el-row :gutter="16" class="wb-row">
      <!-- 左：待派单列表 -->
      <el-col :xs="24" :md="12" class="wb-col">
        <el-card class="pane" shadow="never">
          <template #header>
            <div class="pane-head">
              <b>待派单工单（待处理）</b>
              <el-tag size="small" type="info" effect="plain">共 {{ total }} 条</el-tag>
            </div>
          </template>
          <div class="pane-body">
            <div ref="tableWrap" class="table-wrap">
              <el-table :data="list" border highlight-current-row :height="tableHeight"
                        empty-text="暂无待派单工单" @current-change="selectOrder">
                <el-table-column label="工单 / 位置" min-width="132">
                  <template #default="{ row }">
                    <div class="order-cell">
                      <span :class="['order-no', { urgent: row.urgency === 'URGENT' }]">{{ row.orderNo }}</span>
                      <span class="order-loc" :title="row.locationText">{{ row.locationText }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="faultType" label="类型" min-width="84" show-overflow-tooltip />
                <el-table-column label="紧急" width="72">
                  <template #default="{ row }">
                    <el-tag v-if="row.urgency === 'URGENT'" type="danger" size="small">紧急</el-tag>
                    <span v-else class="muted">普通</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="!compact" label="当前师傅" min-width="72" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.workerName">{{ row.workerName }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <el-pagination class="pager" layout="prev, pager, next"
                           :total="total" :page-size="pageSize" v-model:current-page="page"
                           @current-change="load" />
          </div>
        </el-card>
      </el-col>

      <!-- 右：维修工看板 -->
      <el-col :xs="24" :md="12" class="wb-col">
        <el-card class="pane" shadow="never">
          <template #header>
            <div class="pane-head">
              <b>维修工看板</b>
              <el-tag v-if="!selected" size="small" type="info" effect="plain">未选工单</el-tag>
              <span v-else class="muted">按所选工单判定派单资格</span>
            </div>
          </template>

          <div class="pane-body">
            <!-- 已选工单：展示工单上下文 -->
            <div v-if="selected" class="ctx">
              <div class="ctx-line">
                <b :class="{ urgent: selected.urgency === 'URGENT' }">{{ selected.orderNo }}</b>
                <span class="ctx-text">{{ selected.locationText }}</span>
                <el-tag size="small" effect="plain">{{ selected.faultType }}</el-tag>
                <el-tag v-if="selected.urgency === 'URGENT'" size="small" type="danger" effect="dark">紧急</el-tag>
                <el-tag v-if="selected.workerName" size="small" type="warning" effect="plain">
                  已派：{{ selected.workerName }}（可改派）
                </el-tag>
              </div>
              <div v-if="selectedDetail" class="ctx-desc">
                {{ selectedDetail.description }}
                <a v-if="selectedDetail.locationLng" class="map-link" target="_blank"
                   :href="`https://uri.amap.com/marker?position=${selectedDetail.locationLng},${selectedDetail.locationLat}&name=${encodeURIComponent(selectedDetail.locationText)}`">
                  在地图中查看
                </a>
              </div>
              <div v-if="selectedDetail?.reportImages?.length" class="ctx-imgs">
                <el-image v-for="(img, i) in selectedDetail.reportImages" :key="i" :src="img"
                          :preview-src-list="selectedDetail.reportImages" fit="cover" class="ctx-img" />
              </div>
            </div>

            <!-- 未选工单：说明当前为总体概况 -->
            <div v-else class="hint">
              <el-icon><InfoFilled /></el-icon>
              <span>未选择工单，下方为全部 {{ allWorkers.length }} 位维修工概况（在岗 {{ onDutyCount }} 位）；
                选择左侧工单后自动判定派单资格。</span>
            </div>

            <div class="cards-scroll">
              <el-empty v-if="!board.length" description="暂无维修工数据" :image-size="80" />
              <div v-else class="cards">
                <el-card v-for="w in board" :key="w.workerId" shadow="hover"
                         :class="['worker-card', { disabled: selected && !w.eligible }]">
                  <div class="head">
                    <b>{{ w.name }}</b>
                    <el-tag v-if="selected" :type="w.eligible ? 'success' : 'info'" size="small">
                      {{ w.eligible ? '可派单' : '不可派' }}
                    </el-tag>
                    <el-tag v-else :type="w.onDuty ? 'success' : 'info'" size="small" effect="plain">
                      {{ w.onDuty ? '在岗' : '离岗' }}
                    </el-tag>
                  </div>
                  <div class="row">技能：
                    <el-tag v-for="s in w.skills" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
                    <span v-if="!w.skills.length" class="muted">无</span>
                  </div>
                  <div class="row">校区：<span class="muted">{{ (w.campuses || []).join('、') || '未配置' }}</span></div>
                  <div class="row">
                    负载：
                    <el-progress style="flex: 1"
                                 :percentage="Math.min(100, Math.round((w.currentLoad / (w.loadLimit || 5)) * 100))"
                                 :color="w.currentLoad >= w.loadLimit ? 'var(--el-color-danger)' : 'var(--el-color-success)'"
                                 :format="() => `${w.currentLoad}/${w.loadLimit}`" />
                  </div>
                  <div class="row">评分：<b>{{ w.score }}</b>
                    <span v-if="selected" class="muted" style="margin-left: 8px">{{ w.onDuty ? '在岗' : '离岗' }}</span>
                  </div>
                  <div v-if="selected && !w.eligible" class="reasons">
                    <div v-for="r in w.failReasons" :key="r" class="reason">注意：{{ r }}</div>
                  </div>
                  <el-button type="primary" style="width: 100%"
                             :disabled="!selected || !w.eligible" @click="assign(w)">
                    {{ !selected ? '请先选择工单'
                      : (selected.workerName === w.name ? '重新派单' : '派单给 ' + w.name) }}
                  </el-button>
                </el-card>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import api from '../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const selected = ref(null)
const selectedDetail = ref(null)
const workers = ref([])       // 已选工单：按资格判定的维修工
const allWorkers = ref([])    // 未选工单：全部维修工概况

// 看板数据源：选工单看资格判定结果，未选工单看总体概况
const board = computed(() => (selected.value ? workers.value : allWorkers.value))
const onDutyCount = computed(() => allWorkers.value.filter(w => w.onDuty).length)

// 表格高度跟随容器，保证左右两栏等高、内部滚动
const tableWrap = ref(null)
const tableHeight = ref(420)
let ro = null

// 窄屏（两栏各半时表格很挤）隐藏“当前师傅”列，避免表头换行
const compact = ref(window.innerWidth < 1100)
const onResize = () => { compact.value = window.innerWidth < 1100 }

async function load() {
  const d = await api.get('/orders', { params: { status: 'PENDING', page: page.value, pageSize } })
  list.value = d.list
  total.value = d.total
}

/** 全部维修工概况（负责校区来自 /workers 与 /eligible-workers，字段一致） */
async function loadAllWorkers() {
  const d = await api.get('/workers', { params: { page: 1, pageSize: 200 } })
  allWorkers.value = (d.list || [])
    .map(w => ({
      workerId: w.id,
      name: w.name,
      skills: w.skills || [],
      campuses: w.campuses || [],
      currentLoad: w.currentLoad ?? 0,
      loadLimit: w.loadLimit ?? 5,
      onDuty: !!w.onDuty,
      score: w.score ?? 0,
      eligible: null,
      failReasons: [],
    }))
    .sort((a, b) => (Number(b.onDuty) - Number(a.onDuty))
      || (b.score - a.score)
      || (a.currentLoad - b.currentLoad))
}

function clearSelection() {
  selected.value = null
  selectedDetail.value = null
  workers.value = []
}

async function selectOrder(row) {
  if (!row) {
    clearSelection()
    return
  }
  selected.value = row
  workers.value = []
  selectedDetail.value = null
  selectedDetail.value = await api.get(`/orders/${row.id}`)
  workers.value = await api.get(`/orders/${row.id}/eligible-workers`)
}

async function assign(w) {
  const target = selected.value
  if (!target) {
    ElMessage.warning('请先在左侧选择工单')
    return
  }
  await ElMessageBox.confirm(`确认将工单 ${target.orderNo} 派给「${w.name}」？`, '派单确认', { type: 'primary' })
  await api.post(`/orders/${target.id}/assign`, { workerId: w.workerId })
  ElMessage.success(`已派单给 ${w.name}，等待师傅接单`)
  await load()
  await loadAllWorkers()
  const row = list.value.find(o => o.id === target.id)
  if (row) {
    selected.value = row
    selectedDetail.value = await api.get(`/orders/${row.id}`)
    workers.value = await api.get(`/orders/${row.id}/eligible-workers`)
  } else {
    // 已派单的工单离开待派单池，回到“未选工单”的总体看板
    clearSelection()
  }
}

onMounted(async () => {
  ro = new ResizeObserver(entries => {
    const h = entries[0]?.contentRect?.height
    if (h) tableHeight.value = Math.max(200, Math.round(h))
  })
  if (tableWrap.value) ro.observe(tableWrap.value)
  window.addEventListener('resize', onResize)
  await Promise.all([load(), loadAllWorkers()])
})

onUnmounted(() => {
  ro?.disconnect()
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.wb { display: flex; flex-direction: column; }
.wb-row { align-items: stretch; }
.wb-col { display: flex; }
.pane { width: 100%; height: 100%; display: flex; flex-direction: column; }
.pane > :deep(.el-card__header) { padding: 12px 16px; }
.pane > :deep(.el-card__body) {
  flex: 1; min-height: 0; padding: 12px 16px; display: flex; flex-direction: column;
}
.pane-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pane-body { flex: 1; min-height: 0; display: flex; flex-direction: column; }

/* 左栏：表格撑满，分页固定在底部 */
.table-wrap { flex: 1; min-height: 0; overflow: hidden; }
.pager { margin-top: 10px; justify-content: flex-end; flex-shrink: 0; }
.order-cell { display: flex; flex-direction: column; line-height: 1.35; min-width: 0; }
.order-no { font-size: 13px; }
.order-loc {
  font-size: 12px; color: var(--muted);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 右栏：工单上下文 / 提示条固定，卡片区独立滚动 */
.ctx { background: var(--paper); border: 1px dashed var(--line); border-radius: 10px; padding: 10px 12px; margin-bottom: 10px; flex-shrink: 0; }
.ctx-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; }
.ctx-text { color: #536568; }
.ctx-desc { font-size: 13px; color: var(--ink); line-height: 1.6; margin-top: 4px; }
.map-link { color: var(--teal-deep); margin-left: 8px; font-size: 12px; text-decoration-color: rgba(10, 95, 98, .35); text-underline-offset: 3px; }
.ctx-imgs { margin-top: 8px; }
.ctx-img { width: 56px; height: 56px; border-radius: 8px; margin-right: 8px; display: inline-block; }
.hint {
  display: flex; align-items: flex-start; gap: 6px; flex-shrink: 0; margin-bottom: 10px;
  background: var(--paper-deep); color: #5b6e70; font-size: 12px; line-height: 1.6;
  border: 1px dashed var(--line); border-radius: 9px; padding: 8px 12px;
}
.hint .el-icon { margin-top: 2px; color: var(--teal); }
.cards-scroll {
  flex: 1; min-height: 0; overflow: auto; padding-right: 2px;
  scrollbar-width: thin; scrollbar-color: #c6cad0 transparent;
}
.cards-scroll::-webkit-scrollbar { width: 8px; }
.cards-scroll::-webkit-scrollbar-thumb { background: #c6cad0; border-radius: 999px; }
.cards-scroll::-webkit-scrollbar-track { background: transparent; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
/* 卡片等高：按钮统一贴底，行内按钮对齐 */
.worker-card { height: 100%; display: flex; flex-direction: column; }
.worker-card > :deep(.el-card__body) {
  flex: 1; padding: 10px 12px; display: flex; flex-direction: column;
}
.worker-card :deep(.el-button) { margin-top: auto; }
.worker-card.disabled { opacity: 0.62; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.row { display: flex; align-items: center; gap: 4px; font-size: 13px; margin: 3px 0; }
.reasons { margin: 6px 0; }
.reason { color: var(--el-color-warning); font-size: 12px; }

.urgent { color: var(--el-color-danger); font-weight: 700; }
.muted { color: var(--muted); font-size: 12px; }

/* 宽屏：工作台铺满视口高度，左右两栏等宽等高、各自内部滚动 */
@media (min-width: 992px) {
  .wb { height: calc(100dvh - 102px); min-height: 520px; }
  .wb-row { height: 100%; }
  .wb-col { height: 100%; min-height: 0; }
}
@media (max-width: 991px) {
  .wb-col + .wb-col { margin-top: 16px; }
}
</style>
