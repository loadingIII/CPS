<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>维修知识库</b>
        <div class="filters">
          <el-input v-model="keyword" placeholder="标题 / 内容关键词" clearable style="width: 240px"
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
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览量" width="90" />
      <el-table-column prop="updatedAt" label="更新时间" width="165" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">查看</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialog" title="查看文章" width="720px" top="6vh">
      <el-form label-width="64px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div class="md-wrap">
        <div class="md-body" v-html="previewHtml"></div>
      </div>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button type="primary" @click="submit">保存标题 / 状态</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import api from '../api'

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const keyword = ref('')
const dialog = ref(false)
const previewHtml = ref('')
const form = reactive({ id: null, title: '', status: true })
const reindexing = ref(false)
const importing = ref(false)

async function load() {
  const d = await api.get('/kb/articles/manage', {
    params: {
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

/** 用 marked 渲染后用 DOMPurify 清洗，避免上传文件里的脚本被执行 */
function renderMarkdown(text) {
  const html = marked.parse(String(text || ''), { gfm: true, breaks: true })
  return DOMPurify.sanitize(html)
}

function openView(row) {
  form.id = row.id
  form.title = row.title
  form.status = !!row.status
  previewHtml.value = renderMarkdown(row.content)
  dialog.value = true
}

async function submit() {
  if (!form.id) return
  if (!form.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  await api.put(`/kb/articles/${form.id}`, { title: form.title.trim(), status: form.status })
  ElMessage.success('已保存')
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

/** 上传即入库：后端解析、切片、向量化 */
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

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.inline-hint { color: #909399; font-size: 12px; }
.inline-upload { display: inline-flex; }

.md-wrap {
  max-height: 56vh; overflow: auto; padding: 4px 18px 8px;
  border: 1px solid #ebeef5; border-radius: 8px; background: #fcfdfd;
}
.md-body { color: #33464a; font-size: 14px; line-height: 1.8; word-break: break-word; }
.md-body :deep(h1) { font-size: 22px; font-weight: 800; margin: 18px 0 12px; color: #22343a; }
.md-body :deep(h2) { font-size: 19px; font-weight: 800; margin: 18px 0 10px; color: #22343a; }
.md-body :deep(h3) { font-size: 16.5px; font-weight: 700; margin: 16px 0 8px; }
.md-body :deep(h4), .md-body :deep(h5), .md-body :deep(h6) { font-size: 15px; font-weight: 700; margin: 14px 0 6px; }
.md-body :deep(p) { margin: 0 0 10px; }
.md-body :deep(blockquote) {
  margin: 10px 0; padding: 8px 14px; color: #52686b;
  border-left: 3px solid #bcdcdc; background: #f6fbfb; border-radius: 0 8px 8px 0;
}
.md-body :deep(code) {
  font-family: Menlo, Consolas, monospace; font-size: 13px; color: #0a5f62;
  background: #eef4f4; padding: 1px 5px; border-radius: 4px;
}
.md-body :deep(pre) {
  background: #f5f8f8; border: 1px solid #e6ecec; border-radius: 8px;
  padding: 12px; overflow: auto;
}
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(ul), .md-body :deep(ol) { margin: 6px 0 10px; padding-left: 22px; }
.md-body :deep(li) { margin: 4px 0; }
.md-body :deep(table) { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 13px; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #e6ecec; padding: 7px 9px; vertical-align: top; }
.md-body :deep(th) { background: #f0f6f6; text-align: left; font-weight: 700; }
.md-body :deep(img) { max-width: 100%; border-radius: 8px; }
.md-body :deep(hr) { border: none; border-top: 1px dashed #d8e0e0; margin: 16px 0; }
</style>
