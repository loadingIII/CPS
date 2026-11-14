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
        <div class="actions">
          <span class="inline-hint">上传 .md 后自动切分并写入向量库</span>
          <el-button plain :loading="reindexing" @click="reindex">重建向量索引</el-button>
          <el-upload class="inline-upload" :show-file-list="false" :disabled="importing"
                     :http-request="importMd" :before-upload="beforeImport"
                     accept=".md,.markdown">
            <el-button type="success" :loading="importing">上传 Markdown</el-button>
          </el-upload>
        </div>
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
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialog" title="编辑文章" width="640px">
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
                    placeholder="Markdown 正文，保存后按标题分节 + 滑动窗口切片" />
          <div class="hint">保存后会自动切片写入向量库，智能助手即可检索到本文。</div>
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
import { ElMessage, ElMessageBox } from 'element-plus'
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
const reindexing = ref(false)
const importing = ref(false)

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

function openEdit(row) {
  form.id = row.id
  form.title = row.title
  form.faultType = row.faultType
  form.content = row.content
  form.status = !!row.status
  dialog.value = true
}

function beforeImport(file) {
  if (!/\.(md|markdown)$/i.test(file.name)) {
    ElMessage.warning('只支持 .md / .markdown 文件')
    return false
  }
  if (file.size > 1024 * 1024) {
    ElMessage.warning('文件不要超过 1MB')
    return false
  }
  return true
}

/** 上传即入库：后端解析 front-matter、切片、向量化 */
async function importMd(options) {
  const data = new FormData()
  data.append('file', options.file)
  importing.value = true
  try {
    const r = await api.post('/kb/articles/import', data)
    if (r.warning) {
      ElMessage.warning(r.warning)
    } else {
      ElMessage.success(`已导入《${r.title}》：${r.chunks} 个切片已写入向量库`)
    }
    await load()
    options.onSuccess && options.onSuccess(r)
  } catch (e) {
    options.onError && options.onError(e)
  } finally {
    importing.value = false
  }
}

async function submit() {
  if (!form.id) return
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
  await api.put(`/kb/articles/${form.id}`, body)
  ElMessage.success('已保存，向量索引已更新')
  dialog.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确定删除《${row.title}》？向量库中对应的切片也会一并清理。`,
    '删除文章',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await api.delete(`/kb/articles/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function reindex() {
  reindexing.value = true
  try {
    const r = await api.post('/kb/articles/reindex')
    const failed = r.failed ? `，失败 ${r.failed} 篇` : ''
    ElMessage.success(`已重建：${r.indexed}/${r.articles} 篇，共 ${r.chunks} 个切片${failed}`)
  } finally {
    reindexing.value = false
  }
}

onMounted(async () => {
  load()
  skills.value = await api.get('/skills')
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.inline-hint { color: #909399; font-size: 12px; }
.inline-upload { display: inline-flex; }
.hint { margin-top: 6px; color: #909399; font-size: 12px; line-height: 1.6; }
</style>
