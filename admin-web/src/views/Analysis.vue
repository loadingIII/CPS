<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <div>
          <b>数据分析</b>
          <span class="sub">校区维度 · 异常工单 · AI 建议</span>
        </div>
        <div class="filters">
          <el-select v-model="days" style="width: 120px" @change="loadAll">
            <el-option label="近 7 天" :value="7" />
            <el-option label="近 30 天" :value="30" />
            <el-option label="近 90 天" :value="90" />
          </el-select>
          <el-button plain :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab">
      <el-tab-pane label="校区维度" name="campus">
        <el-table :data="campus" border v-loading="loading">
          <el-table-column prop="campusName" label="校区" width="130" />
          <el-table-column prop="total" label="工单量" width="90" sortable />
          <el-table-column prop="completed" label="完工量" width="90" sortable />
          <el-table-column label="按时率" width="100">
            <template #default="{ row }">{{ pct(row.onTimeRate) }}</template>
          </el-table-column>
          <el-table-column label="平均处理" width="110">
            <template #default="{ row }">{{ row.avgHours == null ? '—' : row.avgHours + ' h' }}</template>
          </el-table-column>
          <el-table-column label="紧急占比" width="100">
            <template #default="{ row }">{{ pct(row.urgentRate) }}</template>
          </el-table-column>
          <el-table-column label="故障类型 Top3" min-width="200">
            <template #default="{ row }">
              <el-tag v-for="t in row.topFaultTypes" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
              <span v-if="!row.topFaultTypes?.length" class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <p class="muted note">
          工单量按提交时间统计；完工量、按时率、平均处理时长按完工时间统计。
          平均处理 = 接单到完工，仅统计两字段都非空的完工单；转单/回收按最终承接人归属。
        </p>
      </el-tab-pane>

      <el-tab-pane label="异常工单" name="anomaly">
        <div class="anomaly-head">
          <el-radio-group v-model="anomalyType" size="small">
            <el-radio-button label="">全部 {{ anomalies.length }}</el-radio-button>
            <el-radio-button v-for="t in anomalyTypes" :key="t.type" :label="t.type">
              {{ t.label }} {{ t.count }}
            </el-radio-button>
          </el-radio-group>
          <span class="muted">只读看板，不改变工单状态</span>
        </div>
        <el-table :data="filteredAnomalies" border v-loading="loading" max-height="560">
          <el-table-column prop="typeLabel" label="异常类型" width="130" />
          <el-table-column label="工单号" width="170">
            <template #default="{ row }">
              <el-button link type="primary" @click="goOrder(row)">{{ row.orderNo }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="campusName" label="校区" width="110" />
          <el-table-column prop="locationText" label="位置" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="85" />
          <el-table-column prop="workerName" label="承接人" width="85" />
          <el-table-column label="停留" width="90">
            <template #default="{ row }">{{ row.ageHours }} h</template>
          </el-table-column>
          <el-table-column label="派单/超时" width="100">
            <template #default="{ row }">{{ row.dispatchCount ?? 0 }} / {{ row.rejectCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="relatedOrderNo" label="关联原单" width="150">
            <template #default="{ row }">{{ row.relatedOrderNo || '—' }}</template>
          </el-table-column>
        </el-table>
        <p class="muted note">
          长期挂起：待审核 &gt;24h、待派单 &gt;12h、已派待接/处理超过截止时间；
          多次派单：派单次数 ≥3；多次超时：拒单/回收 ≥2 或预警 ≥2；返修：同位置同类型完工后 7 天内再次报修。
          返修不等于维修质量问题，可能是同一设施再次故障。
        </p>
      </el-tab-pane>

      <el-tab-pane label="AI 建议" name="ai">
        <el-row :gutter="10" class="ai-cards">
          <el-col :span="8">
            <div class="ai-card">
              <b>{{ ai.suggested }}</b>
              <span>有建议的工单</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="ai-card">
              <b>{{ ai.used }}</b>
              <span>点击采纳</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="ai-card">
              <b>{{ pct(ai.acceptanceRate) }}</b>
              <span>采纳率</span>
            </div>
          </el-col>
        </el-row>
        <el-table :data="ai.byType" border style="margin-top: 12px">
          <el-table-column prop="type" label="建议类型" width="140" />
          <el-table-column prop="suggested" label="建议数" width="100" />
          <el-table-column prop="used" label="采纳数" width="100" />
          <el-table-column label="采纳率" width="100">
            <template #default="{ row }">{{ pct(row.suggested ? row.used / row.suggested : null) }}</template>
          </el-table-column>
        </el-table>
        <p class="muted note">
          采纳标记由客户端回传，用于课程演示与趋势观察；生产环境应改为服务端签发建议 id。
          类型准确率见《第三阶段计划书》评测记录，不在此页面宣称生产准确率。
        </p>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const tab = ref('campus')
const days = ref(30)
const campus = ref([])
const anomalies = ref([])
const anomalyType = ref('')
const ai = ref({ suggested: 0, used: 0, acceptanceRate: null, byType: [] })

const anomalyTypes = computed(() => {
  const map = new Map()
  for (const a of anomalies.value) {
    const item = map.get(a.type) || { type: a.type, label: a.typeLabel, count: 0 }
    item.count++
    map.set(a.type, item)
  }
  return [...map.values()]
})

const filteredAnomalies = computed(() =>
  anomalyType.value ? anomalies.value.filter(a => a.type === anomalyType.value) : anomalies.value)

function pct(v) {
  return v == null ? '—' : (v * 100).toFixed(0) + '%'
}

function goOrder(row) {
  router.push({ path: '/orders', query: { orderId: row.orderId } })
}

async function loadAll() {
  loading.value = true
  try {
    const [c, a, s] = await Promise.all([
      api.get('/stats/campus', { params: { days: days.value } }),
      api.get('/stats/anomalies', { params: { days: days.value } }),
      api.get('/stats/ai-suggestions', { params: { days: days.value } }),
    ])
    campus.value = c || []
    anomalies.value = a || []
    ai.value = s || { suggested: 0, used: 0, acceptanceRate: null, byType: [] }
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.sub { margin-left: 10px; font-size: 12px; color: var(--muted); font-weight: 400; }
.muted { color: var(--muted); font-size: 12px; }
.note { margin-top: 10px; line-height: 1.7; }
.anomaly-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.ai-cards { margin-bottom: 4px; }
.ai-card {
  display: flex; flex-direction: column; gap: 2px;
  padding: 14px 16px; border: 1px solid var(--line); border-radius: 10px;
  background: #fbfcfc;
}
.ai-card b { font-size: 24px; color: var(--teal-deep); font-variant-numeric: tabular-nums; }
.ai-card span { font-size: 12px; color: var(--muted); }
</style>
