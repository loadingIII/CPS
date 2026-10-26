<template>
  <el-row :gutter="16">
    <!-- 左：待派单列表 -->
    <el-col :xs="24" :md="10" class="col-left">
      <el-card>
        <template #header><b>待派单工单（待处理）</b></template>
        <el-table :data="list" border highlight-current-row max-height="620"
                  @current-change="selectOrder">
          <el-table-column label="工单号" width="150">
            <template #default="{ row }">
              <span :class="{ urgent: row.urgency === 'URGENT' }">{{ row.orderNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="locationText" label="位置" min-width="150" show-overflow-tooltip />
          <el-table-column prop="faultType" label="类型" width="100" />
          <el-table-column label="紧急" width="70">
            <template #default="{ row }">
              <el-tag v-if="row.urgency === 'URGENT'" type="danger" size="small">紧急</el-tag>
              <span v-else class="muted">普通</span>
            </template>
          </el-table-column>
          <el-table-column prop="workerName" label="当前师傅" width="90" />
        </el-table>
        <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                       :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
      </el-card>
    </el-col>

    <!-- 右：维修工看板 -->
    <el-col :xs="24" :md="14">
      <el-card>
        <template #header>
          <b>维修工看板</b>
          <span v-if="selected" class="muted" style="margin-left: 8px">
            {{ selected.orderNo }} · {{ selected.locationText }} · {{ selected.faultType }}
            <template v-if="selected.workerName">（当前已派：{{ selected.workerName }}，可改派）</template>
          </span>
        </template>
        <el-empty v-if="!selected" description="← 请先选择左侧工单" :image-size="90" />
        <template v-else>
          <div v-if="selectedDetail" class="sel-detail">
            <div class="sel-desc">
              {{ selectedDetail.description }}
              <a v-if="selectedDetail.locationLng" class="map-link" target="_blank"
                 :href="`https://uri.amap.com/marker?position=${selectedDetail.locationLng},${selectedDetail.locationLat}&name=${encodeURIComponent(selectedDetail.locationText)}`">
                在地图中查看
              </a>
            </div>
            <div class="sel-imgs" v-if="selectedDetail.reportImages?.length">
              <el-image v-for="(img, i) in selectedDetail.reportImages" :key="i" :src="img"
                        :preview-src-list="selectedDetail.reportImages" fit="cover" class="sel-img" />
            </div>
          </div>
          <el-empty v-if="!workers.length" description="暂无维修工数据" :image-size="90" />
          <div v-else class="cards">
          <el-card v-for="w in workers" :key="w.workerId" shadow="hover"
                   :class="['worker-card', { disabled: !w.eligible }]">
            <div class="head">
              <b>{{ w.name }}</b>
              <el-tag :type="w.eligible ? 'success' : 'info'" size="small">
                {{ w.eligible ? '可派单' : '不可派' }}
              </el-tag>
            </div>
            <div class="row">技能：<el-tag v-for="s in w.skills" :key="s" size="small" style="margin-right:4px">{{ s }}</el-tag>
              <span v-if="!w.skills.length" class="muted">无</span>
            </div>
            <div class="row">范围：<span class="muted">{{ w.scopes.join('、') || '未配置' }}</span></div>
            <div class="row">
              负载：
              <el-progress style="flex: 1" :percentage="Math.min(100, Math.round((w.currentLoad / (w.loadLimit || 5)) * 100))"
                           :color="w.currentLoad >= w.loadLimit ? '#f56c6c' : '#67c23a'"
                           :format="() => `${w.currentLoad}/${w.loadLimit}`" />
            </div>
            <div class="row">评分：<b>{{ w.score }}</b>
              <span class="muted" style="margin-left: 8px">{{ w.onDuty ? '在岗' : '离岗' }}</span>
            </div>
            <div v-if="!w.eligible" class="reasons">
              <div v-for="r in w.failReasons" :key="r" class="reason">⚠ {{ r }}</div>
            </div>
            <el-button type="primary" style="width: 100%" :disabled="!w.eligible" @click="assign(w)">
              {{ selected.workerName === w.name ? '重新派单' : '派单给 ' + w.name }}
            </el-button>
          </el-card>
        </div>
        </template>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const selected = ref(null)
const selectedDetail = ref(null)
const workers = ref([])

async function load() {
  const d = await api.get('/orders', { params: { status: 'PENDING', page: page.value, pageSize } })
  list.value = d.list
  total.value = d.total
}

async function selectOrder(row) {
  selected.value = row
  if (!row) {
    workers.value = []
    selectedDetail.value = null
    return
  }
  selectedDetail.value = await api.get(`/orders/${row.id}`)
  workers.value = await api.get(`/orders/${row.id}/eligible-workers`)
}

async function assign(w) {
  await ElMessageBox.confirm(`确认派单给「${w.name}」？`, '派单确认', { type: 'primary' })
  await api.post(`/orders/${selected.value.id}/assign`, { workerId: w.workerId })
  ElMessage.success(`已派单给 ${w.name}，等待师傅接单`)
  await load()
  const row = list.value.find(o => o.id === selected.value.id)
  if (row) {
    selected.value = row
    workers.value = await api.get(`/orders/${row.id}/eligible-workers`)
  }
}

onMounted(load)
</script>

<style scoped>
.urgent { color: #f56c6c; font-weight: 600; }
.muted { color: #909399; font-size: 12px; }
.sel-detail {
  background: #f5f7fa; border-radius: 8px; padding: 10px 14px; margin-bottom: 14px;
}
.sel-desc { font-size: 13px; color: #333; line-height: 1.6; }
.map-link { color: #409eff; margin-left: 8px; font-size: 12px; }
.sel-imgs { margin-top: 8px; }
.sel-img {
  width: 64px; height: 64px; border-radius: 6px; margin-right: 8px; display: block;
}
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 12px; }
.col-left { margin-bottom: 16px; }
@media (min-width: 768px) {
  .col-left { margin-bottom: 0; }
}
.worker-card.disabled { opacity: 0.62; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.row { display: flex; align-items: center; gap: 4px; font-size: 13px; margin: 4px 0; }
.reasons { margin: 6px 0; }
.reason { color: #e6a23c; font-size: 12px; }
</style>
