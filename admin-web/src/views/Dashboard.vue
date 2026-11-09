<template>
  <main class="dashboard">
    <header class="dashboard-head">
      <div>
        <p class="eyebrow">后勤服务中心 · 运行概况</p>
        <h1>校园报修数据看板</h1>
        <p class="welcome">欢迎，{{ auth.user?.name }}。查看工单趋势、状态构成与报修类型。</p>
      </div>
      <div class="head-actions">
        <span class="range-note">状态为全量累计，趋势近 7 日，类型近 30 日</span>
        <el-button type="primary" plain :loading="loading" @click="load">刷新数据</el-button>
      </div>
    </header>

    <section v-if="loading && !statistics" class="metric-grid" aria-label="数据加载中">
      <div v-for="item in 4" :key="item" class="metric-skeleton">
        <span></span><b></b><i></i>
      </div>
    </section>

    <section v-else-if="statisticsError" class="error-state" role="alert">
      <b>看板数据暂时无法加载</b>
      <span>{{ statisticsError }}</span>
      <el-button type="primary" plain @click="load">重试</el-button>
    </section>

    <template v-else-if="statistics">
      <section class="metric-grid" aria-label="工单关键指标">
        <button class="metric-card metric-total" type="button" @click="go('/orders')">
          <span class="metric-top"><span>累计工单</span><span class="metric-mark">全量</span></span>
          <strong>{{ statistics.total }}</strong>
          <span class="metric-foot">系统内全部工单记录</span>
        </button>
        <button class="metric-card metric-review" type="button" @click="go('/audit')">
          <span class="metric-top"><span>待审核</span><span class="metric-mark">需核验</span></span>
          <strong>{{ countOf('PENDING_AUDIT') }}</strong>
          <span class="metric-foot">等待管理员审核报修信息</span>
        </button>
        <button class="metric-card metric-assign" type="button" @click="go('/dispatch')">
          <span class="metric-top"><span>待派单</span><span class="metric-mark">需分配</span></span>
          <strong>{{ countOf('PENDING') }}</strong>
          <span class="metric-foot">匹配维修工技能与服务校区</span>
        </button>
        <button class="metric-card metric-working" type="button" @click="go('/orders')">
          <span class="metric-top"><span>维修处理中</span><span class="metric-mark">进行中</span></span>
          <strong>{{ countOf('PROCESSING') }}</strong>
          <span class="metric-foot">正在由维修工跟进</span>
        </button>
      </section>

      <section class="charts-grid" aria-label="工单数据图表">
        <el-card class="chart-card trend-card" shadow="never">
          <template #header>
            <div class="panel-heading">
              <div><h2>工单提交趋势</h2><p>按提交日期统计，覆盖最近 7 个自然日</p></div>
              <span class="chart-total">近 7 日 <b>{{ recentWeekTotal }}</b> 单</span>
            </div>
          </template>
          <div v-if="!statistics.daily?.length" class="chart-empty">暂无趋势数据</div>
          <svg v-else class="trend-chart" viewBox="0 0 720 250" role="img" :aria-label="trendAriaLabel">
            <defs>
              <linearGradient id="trend-area" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#0f7b7e" stop-opacity=".22" />
                <stop offset="100%" stop-color="#0f7b7e" stop-opacity=".015" />
              </linearGradient>
            </defs>
            <g class="grid-lines">
              <template v-for="tick in trendTicks" :key="tick.y">
                <line x1="50" :y1="tick.y" x2="700" :y2="tick.y" />
                <text x="38" :y="tick.y + 4" text-anchor="end">{{ tick.value }}</text>
              </template>
            </g>
            <path class="trend-area" :d="trendAreaPath" />
            <polyline class="trend-line" :points="trendPolyline" />
            <g v-for="point in trendPoints" :key="point.day">
              <circle class="trend-point" :cx="point.x" :cy="point.y" r="4.5">
                <title>{{ point.day }}：{{ point.count }} 单</title>
              </circle>
              <text class="day-label" :x="point.x" y="232" text-anchor="middle">{{ point.label }}</text>
            </g>
          </svg>
          <div class="chart-foot"><span>工单数</span><span>数据来源：真实报修提交记录</span></div>
        </el-card>

        <el-card class="chart-card status-card" shadow="never">
          <template #header>
            <div class="panel-heading">
              <div><h2>工单状态分布</h2><p>按当前状态统计全部工单</p></div>
              <el-button link type="primary" @click="go('/orders')">工单管理</el-button>
            </div>
          </template>
          <div v-if="statistics.total === 0" class="chart-empty status-empty">暂无工单记录</div>
          <div v-else class="status-layout">
            <svg class="status-donut" viewBox="0 0 120 120" role="img" :aria-label="statusAriaLabel">
              <circle class="donut-track" cx="60" cy="60" r="45" />
              <circle
                v-for="segment in statusSegments"
                :key="segment.status"
                class="donut-segment"
                cx="60" cy="60" r="45"
                :stroke="segment.color"
                :stroke-dasharray="segment.dasharray"
                :stroke-dashoffset="segment.offset"
              />
              <text class="donut-value" x="60" y="57" text-anchor="middle">{{ statistics.total }}</text>
              <text class="donut-caption" x="60" y="73" text-anchor="middle">累计工单</text>
            </svg>
            <div class="status-legend">
              <button
                v-for="item in statusLegend"
                :key="item.status"
                class="legend-item"
                type="button"
                :disabled="item.count === 0"
                @click="go(item.path)"
              >
                <span class="legend-swatch" :style="{ backgroundColor: item.color }"></span>
                <span class="legend-label">{{ item.label }}</span>
                <b>{{ item.count }}</b>
                <small>{{ item.percent }}%</small>
              </button>
            </div>
          </div>
        </el-card>

        <el-card class="chart-card category-card" shadow="never">
          <template #header>
            <div class="panel-heading">
              <div><h2>报修类型排行</h2><p>近 30 日提交量，按故障类型汇总</p></div>
              <span class="chart-total">{{ categoryTotal }} 单</span>
            </div>
          </template>
          <div v-if="categoryRows.length" class="category-list">
            <div v-for="item in categoryRows" :key="item.faultType" class="category-row">
              <span class="category-label" :title="item.faultType">{{ item.faultType }}</span>
              <div class="category-track" role="img" :aria-label="`${item.faultType} ${item.count} 单`">
                <span class="category-fill" :style="{ width: `${item.width}%` }"></span>
              </div>
              <b class="category-count">{{ item.count }}</b>
            </div>
          </div>
          <div v-else class="chart-empty">近 30 日暂无报修记录</div>
          <div class="chart-foot"><span>故障类型</span><span>只统计近 30 日有提交记录的类型</span></div>
        </el-card>

        <el-card class="chart-card recent-card" shadow="never">
          <template #header>
            <div class="panel-heading">
              <div><h2>最新工单</h2><p>最近提交的报修记录</p></div>
              <el-button link type="primary" @click="go('/orders')">查看全部</el-button>
            </div>
          </template>
          <el-table v-if="recent.length" :data="recent" size="small" class="recent-table">
            <el-table-column prop="orderNo" label="工单号" width="150" />
            <el-table-column prop="locationText" label="位置" min-width="170" show-overflow-tooltip />
            <el-table-column prop="faultType" label="类型" width="104" show-overflow-tooltip />
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag size="small" :type="STATUS_TAG[row.status] || 'info'">{{ STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reporterName" label="报修人" width="82" />
            <el-table-column prop="submittedAt" label="提交时间" width="158" />
          </el-table>
          <el-empty v-else-if="!ordersLoading" :description="ordersError || '暂无工单记录'" :image-size="64" />
          <div v-else class="table-skeleton" aria-label="工单列表加载中"><i v-for="item in 4" :key="item"></i></div>
        </el-card>
      </section>

      <footer class="dashboard-footer">
        <span>统计口径：状态分布为全部工单，趋势按提交时间，类型排行统计近 30 日。</span>
        <div class="quick-links">
          <el-button type="primary" @click="go('/audit')">待审核处理</el-button>
          <el-button type="success" plain @click="go('/dispatch')">待派单处理</el-button>
          <el-button plain @click="go('/workers')">维修工管理</el-button>
        </div>
      </footer>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const ordersLoading = ref(false)
const statistics = ref(null)
const recent = ref([])
const statisticsError = ref('')
const ordersError = ref('')

const STATUS = [
  { key: 'PENDING_AUDIT', label: '待审核', color: '#d9a53f', path: '/audit' },
  { key: 'PENDING', label: '待处理', color: '#5aa9b0', path: '/dispatch' },
  { key: 'PROCESSING', label: '处理中', color: '#0f7b7e', path: '/orders' },
  { key: 'COMPLETED', label: '待评价', color: '#3c8b72', path: '/orders' },
  { key: 'EVALUATED', label: '已评价', color: '#8b9a9c', path: '/orders' },
  { key: 'REJECTED', label: '已驳回', color: '#d98a98', path: '/orders' },
  { key: 'CANCELLED', label: '已取消', color: '#b9c2c0', path: '/orders' },
]
const STATUS_LABEL = {
  PENDING_AUDIT: '待审核', PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成',
  EVALUATED: '已评价', REJECTED: '已驳回', CANCELLED: '已取消',
}
const STATUS_TAG = {
  PENDING_AUDIT: 'warning', PENDING: 'primary', PROCESSING: 'primary', COMPLETED: 'success',
  EVALUATED: 'info', REJECTED: 'danger', CANCELLED: 'info',
}

function countOf(status) {
  return statistics.value?.statuses?.find(item => item.status === status)?.count ?? 0
}
function go(path) {
  router.push(path)
}

const recentWeekTotal = computed(() => (statistics.value?.daily || []).reduce((sum, item) => sum + item.count, 0))
const statusLegend = computed(() => STATUS.map(item => {
  const count = countOf(item.key)
  const percent = statistics.value?.total ? Math.round(count / statistics.value.total * 100) : 0
  return { status: item.key, label: item.label, path: item.path, color: item.color, count, percent }
}))
const statusSegments = computed(() => {
  const circumference = 2 * Math.PI * 45
  let passed = 0
  return statusLegend.value.filter(item => item.count > 0).map(item => {
    const length = statistics.value.total ? item.count / statistics.value.total * circumference : 0
    const segment = {
      ...item,
      dasharray: `${Math.max(length - 2, 0)} ${circumference}`,
      offset: -passed,
    }
    passed += length
    return segment
  })
})
const statusAriaLabel = computed(() => `工单状态分布：${statusLegend.value.map(item => `${item.label}${item.count}单`).join('，')}`)

const trendPoints = computed(() => {
  const rows = statistics.value?.daily || []
  if (!rows.length) return []
  const maxValue = Math.max(1, ...rows.map(item => item.count))
  const left = 54
  const right = 696
  const top = 22
  const bottom = 190
  const spread = Math.max(rows.length - 1, 1)
  return rows.map((item, index) => {
    const date = new Date(`${item.day}T00:00:00`)
    return {
      day: item.day,
      count: item.count,
      label: `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`,
      x: left + (right - left) * index / spread,
      y: bottom - (bottom - top) * item.count / maxValue,
    }
  })
})
const trendPolyline = computed(() => trendPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const trendAreaPath = computed(() => {
  if (!trendPoints.value.length) return ''
  const points = trendPoints.value
  return `M ${points[0].x} 190 L ${points.map(point => `${point.x} ${point.y}`).join(' L ')} L ${points[points.length - 1].x} 190 Z`
})
const trendTicks = computed(() => {
  const maxValue = Math.max(1, ...(statistics.value?.daily || []).map(item => item.count))
  return [0, 1, 2, 3].map(index => ({
    y: 190 - 168 * index / 3,
    value: Math.round(maxValue * index / 3),
  }))
})
const trendAriaLabel = computed(() => `最近 7 日工单提交趋势，共${recentWeekTotal.value}单`)

const categoryRows = computed(() => {
  const rows = statistics.value?.faultTypes || []
  const topRows = rows.slice(0, 5)
  const otherCount = rows.slice(5).reduce((sum, item) => sum + item.count, 0)
  const normalized = otherCount > 0 ? [...topRows, { faultType: '其他类型', count: otherCount }] : topRows
  const maxCount = Math.max(1, ...normalized.map(item => item.count))
  return normalized.map(item => ({
    ...item,
    width: Math.max(2, item.count / maxCount * 100),
  }))
})
const categoryTotal = computed(() => (statistics.value?.faultTypes || []).reduce((sum, item) => sum + item.count, 0))

async function load() {
  loading.value = true
  ordersLoading.value = true
  statisticsError.value = ''
  ordersError.value = ''
  const [statisticsResult, recentResult] = await Promise.allSettled([
    api.get('/orders/statistics'),
    api.get('/orders', { params: { page: 1, pageSize: 8 } }),
  ])
  if (statisticsResult.status === 'fulfilled') {
    statistics.value = statisticsResult.value
  } else {
    statisticsError.value = '请检查服务连接后重试。'
  }
  if (recentResult.status === 'fulfilled') {
    recent.value = recentResult.value.list || []
  } else {
    recent.value = []
    ordersError.value = '最新工单加载失败'
  }
  loading.value = false
  ordersLoading.value = false
}

onMounted(load)
</script>

<style scoped>
.dashboard { max-width: 1480px; margin: 0 auto; }
.dashboard-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin: 2px 0 17px; }
.dashboard-head h1 { margin: 0; color: var(--ink); font-size: 24px; font-weight: 800; letter-spacing: .02em; }
.eyebrow { margin: 0 0 6px; color: var(--teal); font-size: 10px; font-weight: 700; letter-spacing: .14em; }
.welcome { margin: 6px 0 0; color: var(--muted); font-size: 12px; }
.head-actions { display: flex; align-items: center; gap: 12px; }
.range-note { color: var(--muted); font-size: 10px; white-space: nowrap; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.metric-card { min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 14px 16px; border: 1.5px solid var(--line); border-left: 5px solid var(--metric-accent); border-radius: 12px; background: #fff; color: var(--ink); text-align: left; box-shadow: 0 7px 18px rgba(43, 58, 60, .045); cursor: pointer; transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; }
.metric-card:hover { transform: translateY(-2px); box-shadow: 0 10px 22px rgba(43, 58, 60, .09); }
.metric-card:active { transform: translateY(1px) scale(.99); }
.metric-card:focus-visible { outline: 2px solid var(--teal); outline-offset: 2px; }
.metric-total { --metric-accent: var(--teal); }
.metric-review { --metric-accent: #d9a53f; }
.metric-assign { --metric-accent: #5aa9b0; }
.metric-working { --metric-accent: #3c8b72; }
.metric-top { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--muted); font-size: 11px; }
.metric-mark { padding: 3px 7px; border-radius: 999px; background: var(--paper); color: var(--metric-accent); font-size: 9px; white-space: nowrap; }
.metric-card strong { color: var(--metric-accent); font-family: "Noto Serif CJK SC", "Songti SC", SimSun, serif; font-size: 29px; font-weight: 800; line-height: 1; font-variant-numeric: tabular-nums; }
.metric-foot { overflow: hidden; max-width: 100%; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.charts-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .9fr); gap: 13px; align-items: stretch; }
.chart-card { min-width: 0; border-radius: 12px !important; }
.chart-card :deep(.el-card__header) { min-height: 59px; padding: 12px 16px; }
.chart-card :deep(.el-card__body) { padding: 12px 16px 13px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-width: 0; }
.panel-heading h2 { margin: 0; color: var(--ink); font-size: 13px; font-weight: 800; }
.panel-heading p { margin: 4px 0 0; color: var(--muted); font-size: 10px; font-weight: 400; }
.chart-total { flex: 0 0 auto; padding: 6px 9px; border: 1px solid var(--line); border-radius: 999px; color: var(--muted); background: var(--paper); font-size: 9px; white-space: nowrap; }
.chart-total b { margin-left: 3px; color: var(--teal-deep); font-size: 12px; }
.trend-chart { display: block; width: 100%; height: 220px; overflow: visible; }
.grid-lines line { stroke: #e8ebec; stroke-width: 1; stroke-dasharray: 4 5; }
.grid-lines text, .day-label { fill: var(--muted); font-family: var(--el-font-family); font-size: 10px; }
.trend-area { fill: url(#trend-area); }
.trend-line { fill: none; stroke: var(--teal); stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; }
.trend-point { fill: #fff; stroke: var(--teal); stroke-width: 2.5; transition: r .16s ease, fill .16s ease; }
.trend-point:hover { r: 6; fill: var(--butter); }
.chart-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 4px; padding-top: 10px; border-top: 1px dashed var(--line); color: var(--muted); font-size: 9px; }
.chart-empty { display: grid; min-height: 190px; place-items: center; color: var(--muted); font-size: 12px; }
.status-layout { display: grid; grid-template-columns: minmax(112px, .85fr) minmax(0, 1.2fr); align-items: center; gap: 8px; min-height: 190px; }
.status-donut { width: 100%; max-width: 156px; margin: 0 auto; overflow: visible; }
.donut-track { fill: none; stroke: var(--paper-deep); stroke-width: 14; }
.donut-segment { fill: none; stroke-width: 14; stroke-linecap: butt; transform: rotate(-90deg); transform-origin: 60px 60px; transition: stroke-dasharray .3s ease, stroke-dashoffset .3s ease; }
.donut-value { fill: var(--ink); font-family: "Noto Serif CJK SC", "Songti SC", SimSun, serif; font-size: 18px; font-weight: 800; }
.donut-caption { fill: var(--muted); font-family: var(--el-font-family); font-size: 8px; }
.status-legend { display: grid; gap: 1px; min-width: 0; }
.legend-item { display: grid; grid-template-columns: 8px minmax(0, 1fr) 28px 28px; align-items: center; gap: 6px; min-width: 0; padding: 5px 2px; border: 0; border-radius: 5px; background: transparent; color: var(--ink); text-align: left; }
.legend-item:not(:disabled) { cursor: pointer; }
.legend-item:not(:disabled):hover { background: var(--paper); }
.legend-item:disabled { opacity: .55; }
.legend-swatch { width: 7px; height: 7px; border-radius: 2px; }
.legend-label { overflow: hidden; color: #536568; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.legend-item b { color: var(--ink); font-size: 10px; font-variant-numeric: tabular-nums; text-align: right; }
.legend-item small { color: var(--muted); font-size: 9px; text-align: right; }
.status-empty { min-height: 190px; }
.category-list { display: grid; gap: 13px; min-height: 180px; align-content: center; padding: 12px 4px; }
.category-row { display: grid; grid-template-columns: minmax(72px, 1fr) minmax(70px, 1.8fr) 28px; align-items: center; gap: 10px; }
.category-label { overflow: hidden; color: #536568; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.category-track { height: 9px; overflow: hidden; border-radius: 999px; background: var(--paper-deep); }
.category-fill { display: block; height: 100%; min-width: 3px; border-radius: inherit; background: var(--teal); transition: width .35s ease; }
.category-row:nth-child(2) .category-fill { background: #5aa9b0; }
.category-row:nth-child(3) .category-fill { background: #d9a53f; }
.category-row:nth-child(4) .category-fill { background: #d98a98; }
.category-row:nth-child(5) .category-fill { background: #71888c; }
.category-row:nth-child(6) .category-fill { background: #9abdb6; }
.category-count { color: var(--ink); font-size: 10px; font-variant-numeric: tabular-nums; text-align: right; }
.recent-card { grid-column: 1 / -1; }
.recent-card :deep(.el-card__body) { padding-top: 0; }
.recent-table { width: 100%; }
.error-state { display: grid; justify-items: start; gap: 10px; padding: 28px; border: 1.5px dashed var(--line); border-radius: 12px; color: var(--ink); background: #fff; }
.error-state span { color: var(--muted); font-size: 12px; }
.metric-skeleton { display: grid; gap: 10px; padding: 14px 16px; border: 1.5px solid var(--line); border-radius: 12px; background: #fff; }
.metric-skeleton span, .metric-skeleton b, .metric-skeleton i, .table-skeleton i { display: block; border-radius: 999px; background: linear-gradient(90deg, #edf0f1 25%, #f7f8f8 50%, #edf0f1 75%); background-size: 200% 100%; animation: skeleton 1.35s ease-in-out infinite; }
.metric-skeleton span { width: 44%; height: 10px; }
.metric-skeleton b { width: 30%; height: 25px; }
.metric-skeleton i { width: 70%; height: 8px; }
.table-skeleton { display: grid; gap: 12px; padding: 14px; }
.table-skeleton i { height: 20px; border-radius: 5px; }
@keyframes skeleton { to { background-position-x: -200%; } }
.dashboard-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 13px; padding: 10px 2px 2px; color: var(--muted); font-size: 9px; }
.quick-links { display: flex; flex-wrap: wrap; gap: 7px; }
.quick-links :deep(.el-button) { min-height: 29px; margin: 0; font-size: 10px; }

@media (max-width: 1080px) {
  .charts-grid { grid-template-columns: minmax(0, 1fr) minmax(265px, .9fr); }
  .status-layout { grid-template-columns: 1fr; gap: 1px; }
  .status-donut { width: 135px; }
  .status-legend { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .dashboard-head { align-items: flex-start; flex-direction: column; }
  .dashboard-head h1 { font-size: 21px; }
  .head-actions { width: 100%; justify-content: space-between; }
  .range-note { white-space: normal; line-height: 1.5; }
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .metric-card { padding: 12px; }
  .metric-card strong { font-size: 25px; }
  .metric-foot { font-size: 9px; }
  .charts-grid { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .recent-card { grid-column: auto; }
  .status-layout { grid-template-columns: minmax(112px, .8fr) minmax(0, 1.2fr); }
  .status-legend { grid-template-columns: 1fr; }
  .trend-chart { height: 190px; }
  .dashboard-footer { align-items: flex-start; flex-direction: column; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
