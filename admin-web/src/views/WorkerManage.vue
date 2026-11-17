<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <div>
          <b>维修工管理</b>
          <span class="sub">档案 · 技能 / 故障类型 · 服务范围</span>
        </div>
        <div class="filters">
          <el-input v-model="query.keyword" placeholder="姓名/工号" clearable style="width: 180px" @keyup.enter="load" />
          <el-select v-model="query.onDuty" placeholder="在岗状态" clearable style="width: 120px">
            <el-option label="在岗" :value="true" />
            <el-option label="离岗" :value="false" />
          </el-select>
          <el-button type="primary" plain @click="load">查询</el-button>
          <el-button type="primary" @click="openCreate">新增维修工</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" border>
      <el-table-column prop="name" label="姓名" width="90" />
      <el-table-column prop="jobNo" label="工号" width="110" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column label="技能 / 故障类型" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="s in row.skills" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责校区" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="c in row.campuses" :key="c" size="small" type="info" style="margin-right: 4px">{{ c }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前负载" width="140">
        <template #default="{ row }">
          <el-progress :percentage="Math.min(100, Math.round((row.currentLoad / (row.loadLimit || 5)) * 100))"
                       :color="row.currentLoad >= row.loadLimit ? 'var(--el-color-danger)' : 'var(--el-color-success)'"
                       :format="() => `${row.currentLoad}/${row.loadLimit}`" />
        </template>
      </el-table-column>
      <el-table-column prop="score" label="评分" width="80" />
      <el-table-column label="在岗" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.onDuty" @change="v => toggleDuty(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">详情</el-button>
          <el-button link type="success" @click="openProfile(row)">画像</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.pageSize" v-model:current-page="query.page" @current-change="load" />

    <!-- 维修工详情：技能/故障类型字典在此一并维护，不再单列页面 -->
    <el-drawer v-model="drawer" :title="form.id ? `维修工详情 · ${form.name}` : '新增维修工'"
               size="620px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <div class="section-title">基本信息</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="工号"><el-input v-model="form.jobNo" :disabled="!!form.id" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="初始密码" v-if="!form.id">
              <el-input v-model="form.password" placeholder="默认 123456" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负载上限">
              <el-input-number v-model="form.loadLimit" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.id">
            <el-form-item label="综合评分"><el-input :model-value="form.score" disabled /></el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">
          技能 / 故障类型
          <span class="section-note">既是维修工的技能标签，也是师生报修时可选的故障类型</span>
        </div>
        <el-form-item label="已选类型">
          <div class="skill-picker">
            <el-select v-model="form.skillIds" multiple filterable placeholder="选择已有类型" style="width: 100%">
              <el-option v-for="s in allSkills" :key="s.id"
                         :label="s.enabled ? s.name : `${s.name}（已停用）`"
                         :value="s.id" :disabled="!s.enabled" />
            </el-select>
            <el-button class="skill-add" @click="addSkill">+ 新增类型</el-button>
          </div>
        </el-form-item>
        <el-collapse class="dict-collapse">
          <el-collapse-item name="dict">
            <template #title>
              <span>管理类型字典（{{ allSkills.length }} 项）</span>
            </template>
            <div class="dict-hint">改名不会改写已有工单中的故障类型文本；停用后该类型不再出现在报修端。</div>
            <div v-for="s in allSkills" :key="s.id" class="dict-row">
              <span class="dict-name">{{ s.name }}</span>
              <el-tag :type="s.enabled ? 'success' : 'info'" size="small">{{ s.enabled ? '启用' : '停用' }}</el-tag>
              <el-button link type="primary" @click="renameSkill(s)">改名</el-button>
              <el-button link :type="s.enabled ? 'danger' : 'success'" @click="toggleSkill(s)">
                {{ s.enabled ? '停用' : '启用' }}
              </el-button>
            </div>
          </el-collapse-item>
        </el-collapse>

        <div class="section-title">负责校区</div>
        <el-form-item label="可接单校区">
          <el-select v-model="form.campusCodes" multiple placeholder="选择负责的校区" style="width: 100%">
            <el-option v-for="c in campusOptions" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="profileDrawer" :title="`维修工画像 · ${profile?.name || ''}`" size="620px">
      <template v-if="profile">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工号">{{ profile.jobNo }}</el-descriptions-item>
          <el-descriptions-item label="在岗">{{ profile.onDuty ? '在岗' : '离岗' }}</el-descriptions-item>
          <el-descriptions-item label="技能">{{ profile.skills?.join(' / ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责校区">{{ profile.campuses?.join(' / ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前负载">{{ profile.currentLoad }}/{{ profile.loadLimit }}</el-descriptions-item>
          <el-descriptions-item label="综合评分">{{ profile.score ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-row class="metric-row" :gutter="10">
          <el-col :span="6"><div class="metric"><b>{{ profile.done }}</b><span>近 90 天完工</span></div></el-col>
          <el-col :span="6"><div class="metric"><b>{{ pct(profile.onTimeRate) }}</b><span>按时率</span></div></el-col>
          <el-col :span="6"><div class="metric"><b>{{ profile.avgRating ?? '—' }}</b><span>满意度</span></div></el-col>
          <el-col :span="6"><div class="metric"><b>{{ pct(profile.repairRate) }}</b><span>返修率</span></div></el-col>
        </el-row>
        <el-row class="metric-row" :gutter="10">
          <el-col :span="8"><div class="metric"><b>{{ profile.avgHours ?? '—' }}</b><span>平均处理（小时）</span></div></el-col>
          <el-col :span="8"><div class="metric"><b>{{ profile.overdueRecycles }}</b><span>处理超时回收</span></div></el-col>
          <el-col :span="8"><div class="metric"><b>{{ profile.sampleEnough ? '可排名' : '样本不足' }}</b><span>样本判定</span></div></el-col>
        </el-row>
        <p class="muted">返修率 = 其完工单中 7 天内同位置同类型再次报修的单 / 完工单；只作参考，不直接用于绩效扣分。</p>

        <h4 style="margin: 16px 0 8px">最近完工</h4>
        <el-table :data="profile.recentOrders" border size="small" max-height="260">
          <el-table-column prop="orderNo" label="工单号" width="145" />
          <el-table-column prop="faultType" label="类型" width="80" />
          <el-table-column prop="locationText" label="位置" min-width="110" show-overflow-tooltip />
          <el-table-column prop="completedAt" label="完工时间" width="135" />
          <el-table-column label="超时" width="55">
            <template #default="{ row }">{{ row.isOverdue ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="rating" label="星级" width="55" />
        </el-table>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const total = ref(0)
const query = ref({ keyword: '', onDuty: null, page: 1, pageSize: 10 })
const allSkills = ref([])
const campusOptions = ref([])
const drawer = ref(false)
const form = ref({})
const profileDrawer = ref(false)
const profile = ref(null)

function pct(v) {
  return v == null ? '—' : (v * 100).toFixed(0) + '%'
}

async function openProfile(row) {
  profile.value = await api.get(`/stats/workers/${row.id}`, { params: { days: 90 } })
  profileDrawer.value = true
}

async function load() {
  const d = await api.get('/workers', {
    params: {
      keyword: query.value.keyword || undefined,
      onDuty: query.value.onDuty === '' ? undefined : query.value.onDuty,
      page: query.value.page, pageSize: query.value.pageSize,
    },
  })
  list.value = d.list
  total.value = d.total
}

async function loadSkills() {
  // 含停用项：编辑历史维修工时仍能看到他曾绑定的类型
  allSkills.value = await api.get('/skills', { params: { includeDisabled: true } })
}

async function loadOptions() {
  await loadSkills()
  campusOptions.value = await api.get('/campuses')
}

function openCreate() {
  form.value = { jobNo: '', name: '', phone: '', password: '123456', skillIds: [], campusCodes: [], loadLimit: 5 }
  drawer.value = true
}

function openEdit(row) {
  form.value = {
    id: row.id, jobNo: row.jobNo, name: row.name, phone: row.phone, score: row.score,
    skillIds: [...row.skillIds], campusCodes: [...row.campusCodes], loadLimit: row.loadLimit,
  }
  drawer.value = true
}

/** 在详情里直接新增故障类型：入库后自动选中，并刷新字典 */
async function addSkill() {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(
      '新类型会立即出现在报修端的故障类型选项中', '新增故障类型',
      { inputPlaceholder: '如：消防器材', inputValidator: v => (v && v.trim() ? true : '名称不能为空') }))
  } catch { return }
  try {
    const id = await api.post('/skills', { name: value.trim() })
    await loadSkills()
    form.value.skillIds = [...(form.value.skillIds || []), id]
    ElMessage.success('已新增并选中该类型')
  } catch (e) { /* 拦截器已提示 */ }
}

async function renameSkill(s) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt(
      '改名不会自动改写已有工单中的故障类型文本', '修改类型名称',
      { inputValue: s.name, inputValidator: v => (v && v.trim() ? true : '名称不能为空') }))
  } catch { return }
  try {
    await api.put(`/skills/${s.id}`, { name: value.trim() })
    await loadSkills()
    await load()
    ElMessage.success('修改成功')
  } catch (e) { /* 拦截器已提示 */ }
}

async function toggleSkill(s) {
  if (s.enabled) {
    try {
      await ElMessageBox.confirm(`停用「${s.name}」后，报修端将不再出现该类型。`, '提示', { type: 'warning' })
    } catch { return }
  }
  try {
    await api.put(`/skills/${s.id}`, { enabled: !s.enabled })
    await loadSkills()
    await load()
    ElMessage.success('操作成功')
  } catch (e) { /* 拦截器已提示 */ }
}

async function save() {
  if (form.value.id) {
    await api.put(`/workers/${form.value.id}`, {
      name: form.value.name, phone: form.value.phone,
      skillIds: form.value.skillIds, campusCodes: form.value.campusCodes, loadLimit: form.value.loadLimit,
    })
  } else {
    await api.post('/workers', form.value)
  }
  ElMessage.success('保存成功')
  drawer.value = false
  await load()
}

async function toggleDuty(row, v) {
  await api.put(`/workers/${row.id}/on-duty`, { onDuty: v })
  ElMessage.success(v ? '已置为在岗' : '已置为离岗')
  await load()
}

onMounted(() => { load(); loadOptions() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.sub { margin-left: 10px; font-size: 12px; color: var(--muted); font-weight: 400; }
.section-title {
  font-size: 14px; font-weight: 700; color: var(--ink);
  border-left: 3px solid var(--teal); padding-left: 8px; margin: 8px 0 14px;
}
.section-note { margin-left: 10px; font-size: 12px; font-weight: 400; color: var(--muted); }
.skill-picker { display: flex; gap: 8px; width: 100%; }
.skill-add { flex-shrink: 0; }
.dict-collapse { margin: 0 0 18px 90px; }
.dict-hint { font-size: 12px; color: var(--muted); margin-bottom: 8px; line-height: 1.6; }
.dict-row {
  display: flex; align-items: center; gap: 10px;
  padding: 7px 0; border-top: 1px dashed var(--line); font-size: 13px;
}
.dict-name { flex: 1; min-width: 0; }
.metric-row { margin-top: 12px; }
.metric {
  display: flex; flex-direction: column; gap: 2px;
  padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px;
  background: #fbfcfc;
}
.metric b { font-size: 18px; color: var(--teal-deep); font-variant-numeric: tabular-nums; }
.metric span { font-size: 11.5px; color: var(--muted); }
</style>
