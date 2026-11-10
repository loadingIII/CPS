<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>用户管理</b>
        <div class="filters">
          <el-select v-model="query.role" placeholder="角色" clearable style="width: 160px">
            <el-option v-for="(label, key) in ROLE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="姓名/学工号" clearable style="width: 180px" @keyup.enter="load" />
          <el-button type="primary" plain @click="load">查询</el-button>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" border>
      <el-table-column prop="jobNo" label="学工号" width="120" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === 'SYS_ADMIN' ? 'danger' : row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
            {{ ROLE_LABEL[row.role] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="电话" width="140" />
      <el-table-column prop="department" label="部门" min-width="120" />
      <el-table-column prop="address" label="住址" min-width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ { 1: '启用', 2: '停用', 3: '注销' }[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" class-name="user-actions-column">
        <template #default="{ row }">
          <div class="user-actions">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="warning" @click="resetPwd(row)">重置密码</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.pageSize" v-model:current-page="query.page" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="学工号"><el-input v-model="form.jobNo" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色" v-if="!form.id">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(label, key) in ROLE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="form.department" placeholder="教职工填写" /></el-form-item>
        <el-form-item label="住址"><el-input v-model="form.address" placeholder="学生填写宿舍楼栋·房间" /></el-form-item>
        <el-form-item label="初始密码" v-if="!form.id">
          <el-input v-model="form.password" placeholder="默认 123456" />
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

const ROLE_LABEL = { REPORTER: '报修人(师生)', ADMIN: '后勤管理员', REPAIRER: '维修工', SYS_ADMIN: '系统管理员' }

const list = ref([])
const total = ref(0)
const query = ref({ role: null, keyword: '', page: 1, pageSize: 10 })
const dialog = ref(false)
const form = ref({})

async function load() {
  const d = await api.get('/users', {
    params: {
      role: query.value.role || undefined,
      keyword: query.value.keyword || undefined,
      page: query.value.page, pageSize: query.value.pageSize,
    },
  })
  list.value = d.list
  total.value = d.total
}

function openCreate() {
  form.value = { jobNo: '', name: '', role: 'REPORTER', phone: '', department: '', address: '', password: '123456' }
  dialog.value = true
}

function openEdit(row) {
  form.value = { id: row.id, jobNo: row.jobNo, name: row.name, phone: row.phone, department: row.department, address: row.address }
  dialog.value = true
}

async function save() {
  if (form.value.id) {
    await api.put(`/users/${form.value.id}`, {
      name: form.value.name, phone: form.value.phone,
      department: form.value.department, address: form.value.address,
    })
  } else {
    await api.post('/users', form.value)
  }
  ElMessage.success('保存成功')
  dialog.value = false
  await load()
}

async function toggle(row) {
  await api.put(`/users/${row.id}`, { status: row.status === 1 ? 2 : 1 })
  ElMessage.success('操作成功')
  await load()
}

function resetPwd(row) {
  ElMessageBox.prompt(`重置「${row.name}」的密码`, '提示', { inputValue: '123456' })
    .then(async ({ value }) => {
      await api.put(`/users/${row.id}/reset-password`, { newPassword: value })
      ElMessage.success('密码已重置')
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.user-actions-column :deep(.cell) { overflow: visible; }
.user-actions { display: flex; flex-wrap: nowrap; align-items: center; gap: 6px; white-space: nowrap; }
.user-actions :deep(.el-button) { flex: 0 0 auto; min-height: 22px; margin: 0; padding: 2px 3px; line-height: 1.2; white-space: nowrap; }
</style>
