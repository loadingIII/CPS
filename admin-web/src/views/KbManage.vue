<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>维修知识库</b>
        <div class="filters">
          <el-select v-model="faultType" placeholder="故障类型" clearable style="width: 150px" @change="reload">
            <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.name" />
          </el-select>
          <el-input v-model="keyword" placeholder="标题 / 内容关键词" clearable style="width: 220px"
                    @keyup.enter="reload" />
          <el-button type="primary" plain @click="reload">查询</el-button>
        </div>
        <el-button type="success" @click="openCreate">新增文章</el-button>
      </div>
    </template>

    <el-table :data="list" border>
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="faultType" label="故障类型" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览量" width="90" />
      <el-table-column prop="updatedAt" label="更新时间" width="165" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑文章' : '新增文章'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.faultType" placeholder="选择故障类型" style="width: 200px">
            <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="10"
                    placeholder="图文步骤说明（纯文本或带图片地址），面向师生自助排查" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const skills = ref([])
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const faultType = ref(null)
const keyword = ref('')
const dialog = ref(false)
const form = reactive({ id: null, title: '', faultType: '', content: '', status: true })

async function load() {
  const d = await api.get('/kb/articles/manage', {
    params: {
      faultType: faultType.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value, pageSize,
    },
  })
  list.value = d.list
  total.value = d.total
}

function reload() {
  page.value = 1
  load()
}

function resetForm() {
  form.id = null
  form.title = ''
  form.faultType = ''
  form.content = ''
  form.status = true
}

function openCreate() {
  resetForm()
  dialog.value = true
}

function openEdit(row) {
  form.id = row.id
  form.title = row.title
  form.faultType = row.faultType
  form.content = row.content
  form.status = !!row.status
  dialog.value = true
}

async function submit() {
  if (!form.title.trim() || !form.faultType || !form.content.trim()) {
    ElMessage.warning('标题、类型、内容都不能为空')
    return
  }
  const body = {
    title: form.title.trim(),
    faultType: form.faultType,
    content: form.content,
    status: form.status,
  }
  if (form.id) {
    await api.put(`/kb/articles/${form.id}`, body)
    ElMessage.success('已保存')
  } else {
    await api.post('/kb/articles', body)
    ElMessage.success('已新增')
  }
  dialog.value = false
  await load()
}

onMounted(async () => {
  load()
  skills.value = await api.get('/skills')
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
