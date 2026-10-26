<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>位置管理（校区 / 片区 / 楼栋 / 房间点位）</b>
        <el-button type="primary" @click="openCreate">新增位置</el-button>
      </div>
    </template>
    <el-table :data="tree" row-key="id" border default-expand-all
              :tree-props="{ children: 'children' }">
      <el-table-column prop="name" label="名称" min-width="220" />
      <el-table-column prop="type" label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="TYPE_TAG[row.type]" size="small">{{ TYPE_LABEL[row.type] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column label="启用" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑位置' : '新增位置'" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="父节点" v-if="!form.id">
          <el-cascader v-model="parentPath" :options="parentOptions" placeholder="留空 = 新增校区"
                       clearable style="width: 100%" :props="{ checkStrictly: true, emitPath: true }" />
        </el-form-item>
        <el-form-item label="类型" v-if="!form.id">
          <el-tag>{{ TYPE_LABEL[form.type] }}</el-tag>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如：东校区 / 教学区 / 一教 / 101" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" placeholder="房间号/路段（可空）" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const TYPE_LABEL = { CAMPUS: '校区', AREA: '片区', BUILDING: '楼栋', SPOT: '房间/点位' }
const TYPE_TAG = { CAMPUS: 'danger', AREA: 'warning', BUILDING: 'primary', SPOT: 'success' }
const CHILD_TYPE = { CAMPUS: 'AREA', AREA: 'BUILDING', BUILDING: 'SPOT' }

const tree = ref([])
const dialog = ref(false)
const form = ref({ id: null, type: 'CAMPUS', parentId: null, name: '', code: '' })
const parentPath = ref([])
const parentOptions = ref([])

async function load() {
  tree.value = await api.get('/places/tree', { params: { includeDisabled: true } })
  parentOptions.value = stripSpots(tree.value)
}

/** 父节点只能是 校区/片区/楼栋，剔除 SPOT */
function stripSpots(nodes) {
  return (nodes || [])
    .filter(n => n.type !== 'SPOT')
    .map(n => ({ value: n.id, label: `${n.name}（${TYPE_LABEL[n.type]}）`, children: stripSpots(n.children) }))
}

function findPlace(nodes, id) {
  for (const n of nodes || []) {
    if (n.id === id) return n
    const found = findPlace(n.children, id)
    if (found) return found
  }
  return null
}

function openCreate() {
  form.value = { id: null, type: 'CAMPUS', parentId: null, name: '', code: '' }
  parentPath.value = []
  dialog.value = true
}

function onParentChange() {
  const last = parentPath.value?.[parentPath.value.length - 1]
  if (!last) {
    form.value.type = 'CAMPUS'
    form.value.parentId = null
  } else {
    const p = findPlace(tree.value, last)
    form.value.type = CHILD_TYPE[p.type]
    form.value.parentId = p.id
  }
}

import { watch } from 'vue'
watch(parentPath, onParentChange, { deep: true })

function openEdit(row) {
  form.value = { id: row.id, name: row.name, code: row.code, type: row.type }
  dialog.value = true
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写名称')
    return
  }
  if (form.value.id) {
    await api.put(`/places/${form.value.id}`, { name: form.value.name, code: form.value.code })
  } else {
    await api.post('/places', { type: form.value.type, parentId: form.value.parentId, name: form.value.name, code: form.value.code })
  }
  ElMessage.success('保存成功')
  dialog.value = false
  await load()
}

async function toggle(row) {
  if (row.enabled) {
    await ElMessageBox.confirm(`确认禁用「${row.name}」？`, '提示', { type: 'warning' })
  }
  await api.put(`/places/${row.id}`, { enabled: !row.enabled })
  ElMessage.success('操作成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
</style>
