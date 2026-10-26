<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>技能 / 故障类型字典</b>
        <el-button type="primary" @click="openCreate">新增技能</el-button>
      </div>
    </template>
    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="技能名称" min-width="200" />
      <el-table-column label="启用" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">改名</el-button>
          <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggle(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新增技能" width="380px">
      <el-input v-model="name" placeholder="如：水电 / 木工门窗" />
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

const list = ref([])
const dialog = ref(false)
const name = ref('')

async function load() {
  list.value = await api.get('/skills', { params: { includeDisabled: true } })
}

function openCreate() {
  name.value = ''
  dialog.value = true
}

async function save() {
  await api.post('/skills', { name: name.value })
  ElMessage.success('新增成功')
  dialog.value = false
  await load()
}

function openEdit(row) {
  ElMessageBox.prompt('修改技能名称', '提示', { inputValue: row.name })
    .then(async ({ value }) => {
      await api.put(`/skills/${row.id}`, { name: value })
      ElMessage.success('修改成功')
      await load()
    })
    .catch(() => {})
}

async function toggle(row) {
  if (row.enabled) {
    await ElMessageBox.confirm(`确认禁用「${row.name}」？被工单引用的技能无法禁用。`, '提示', { type: 'warning' })
  }
  await api.put(`/skills/${row.id}`, { enabled: !row.enabled })
  ElMessage.success('操作成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
</style>
