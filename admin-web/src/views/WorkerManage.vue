<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>维修工管理</b>
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
      <el-table-column label="技能" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="s in row.skills" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="服务范围" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="s in row.scopes" :key="s" size="small" type="info" style="margin-right: 4px">{{ s }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前负载" width="140">
        <template #default="{ row }">
          <el-progress :percentage="Math.min(100, Math.round((row.currentLoad / (row.loadLimit || 5)) * 100))"
                       :color="row.currentLoad >= row.loadLimit ? '#f56c6c' : '#67c23a'"
                       :format="() => `${row.currentLoad}/${row.loadLimit}`" />
        </template>
      </el-table-column>
      <el-table-column prop="score" label="评分" width="80" />
      <el-table-column label="在岗" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.onDuty" @change="v => toggleDuty(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.pageSize" v-model:current-page="query.page" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑维修工' : '新增维修工'" width="560px">
      <el-form :model="form" label-width="90px">
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
        </el-row>
        <el-form-item label="技能">
          <el-select v-model="form.skillIds" multiple placeholder="选择技能" style="width: 100%">
            <el-option v-for="s in skillOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务范围">
          <el-select v-model="form.scopeIds" multiple placeholder="选择负责的片区/楼栋" style="width: 100%">
            <el-option v-for="p in scopeOptions" :key="p.id" :label="`${p.name}（${p.typeLabel}）`" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const TYPE_LABEL = { CAMPUS: '校区', AREA: '片区', BUILDING: '楼栋', SPOT: '点位' }

const list = ref([])
const total = ref(0)
const query = ref({ keyword: '', onDuty: null, page: 1, pageSize: 10 })
const skillOptions = ref([])
const scopeOptions = ref([])
const dialog = ref(false)
const form = ref({})

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

async function loadOptions() {
  skillOptions.value = await api.get('/skills')
  const tree = await api.get('/places/tree', { params: { includeDisabled: false } })
  const opts = []
  for (const campus of tree) {
    for (const area of campus.children || []) {
      opts.push({ id: area.id, name: area.name, typeLabel: TYPE_LABEL[area.type] })
      for (const b of area.children || []) {
        opts.push({ id: b.id, name: b.name, typeLabel: TYPE_LABEL[b.type] })
      }
    }
  }
  scopeOptions.value = opts
}

function openCreate() {
  form.value = { jobNo: '', name: '', phone: '', password: '123456', skillIds: [], scopeIds: [], loadLimit: 5 }
  dialog.value = true
}

function openEdit(row) {
  form.value = {
    id: row.id, jobNo: row.jobNo, name: row.name, phone: row.phone,
    skillIds: [...row.skillIds], scopeIds: [...row.scopeIds], loadLimit: row.loadLimit,
  }
  dialog.value = true
}

async function save() {
  if (form.value.id) {
    await api.put(`/workers/${form.value.id}`, {
      name: form.value.name, phone: form.value.phone,
      skillIds: form.value.skillIds, scopeIds: form.value.scopeIds, loadLimit: form.value.loadLimit,
    })
  } else {
    await api.post('/workers', form.value)
  }
  ElMessage.success('保存成功')
  dialog.value = false
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
</style>
