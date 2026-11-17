<template>
  <el-card>
    <template #header>
      <div class="toolbar">
        <b>维修知识库</b>
        <div class="filters">
          <el-input v-model="keyword" placeholder="标题 / 内容关键词" clearable style="width: 220px"
                    @keyup.enter="reload" />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 120px" @change="reload">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
            <el-option label="草稿" :value="2" />
          </el-select>
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

    <el-row class="stat-row" :gutter="10">
      <el-col :span="6">
        <div class="stat">
          <b>{{ stats.selfServiceRate == null ? '—' : pct(stats.selfServiceRate) }}</b>
          <span>自助解决率（点击口径）</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat">
          <b>{{ stats.solved }} / {{ stats.unsolved }}</b>
          <span>已解决 / 未解决去报修</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat">
          <b>{{ stats.searchMiss }}</b>
          <span>搜索无结果次数</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat stat-keywords">
          <b>内容缺口</b>
          <span v-if="!stats.searchMissKeywords?.length">暂无</span>
          <span v-else class="keyword-list">
            <el-tag v-for="k in stats.searchMissKeywords.slice(0, 5)" :key="k.keyword" size="small" type="info">
              {{ k.keyword }} · {{ k.count }}
            </el-tag>
          </span>
        </div>
      </el-col>
    </el-row>

    <el-table :data="list" border>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="120">
        <template #default="{ row }">
          <span v-if="row.sourceOrderId" class="muted">工单 #{{ row.sourceOrderId }}</span>
          <span v-else class="muted">上传</span>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览量" width="90" />
      <el-table-column prop="updatedAt" label="更新时间" width="165" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">
            {{ row.status === 2 ? '编辑草稿' : '查看' }}
          </el-button>
          <el-button v-if="row.status === 2" link type="success" @click="publish(row)">发布</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.isDraft ? '编辑知识库草稿' : '查看文章'" width="760px" top="6vh">
      <el-form label-width="64px">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item v-if="!form.isDraft" label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item v-else label="状态">
          <el-tag type="warning" size="small">草稿（仅管理端可见，发布后才进向量库）</el-tag>
        </el-form-item>
      </el-form>

      <el-input v-if="form.isDraft" v-model="form.content" type="textarea" :rows="18"
                placeholder="编辑 Markdown 正文，确认无个人信息后再发布" />
      <div v-else class="md-wrap">
        <div class="md-body" v-html="previewHtml"></div>
      </div>

      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="form.isDraft" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="form.isDraft" type="success" :loading="publishing" @click="publishCurrent">
          发布到知识库
        </el-button>
        <el-button v-else type="primary" @click="submit">保存标题 / 状态</el-button>
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
const statusFilter = ref(null)
const dialog = ref(false)
const previewHtml = ref('')
const form = reactive({ id: null, title: '', enabled: true, content: '', isDraft: false })
const stats = ref({ solved: 0, unsolved: 0, searchMiss: 0, selfServiceRate: null, searchMissKeywords: [] })
const reindexing = ref(false)
const importing = ref(false)
const saving = ref(false)
const publishing = ref(false)

function pct(v) {
  return v == null ? '—' : (v * 100).toFixed(0) + '%'
}

function statusText(status) {
  if (status === 1) return '启用'
  if (status === 2) return '草稿'
  return '停用'
}

function statusTag(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'warning'
  return 'info'
}

async function load() {
  const d = await api.get('/kb/articles/manage', {
    params: {
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value == null ? undefined : statusFilter.value,
      page: page.value, pageSize,
    },
  })
  list.value = d.list
  total.value = d.total
}

async function loadStats() {
  stats.value = await api.get('/kb/articles/stats', { params: { days: 30 } })
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
  form.enabled = row.status === 1
  form.content = row.content || ''
  form.isDraft = row.status === 2
  previewHtml.value = form.isDraft ? '' : renderMarkdown(row.content)
  dialog.value = true
}

async function submit() {
  if (!form.id) return
  if (!form.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  await api.put(`/kb/articles/${form.id}`, {
    title: form.title.trim(),
    status: form.enabled ? 1 : 0,
  })
  ElMessage.success('已保存')
  dialog.value = false
  await Promise.all([load(), loadStats()])
}

async function saveDraft() {
  if (!form.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  saving.value = true
  try {
    await api.put(`/kb/articles/${form.id}/draft`, {
      title: form.title.trim(),
      content: form.content,
    })
    ElMessage.success('草稿已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function publishCurrent() {
  publishing.value = true
  try {
    const r = await api.post(`/kb/articles/${form.id}/publish`)
    if (r.warning) {
      ElMessage.warning(r.warning)
    } else {
      ElMessage.success(`已发布，写入 ${r.chunks} 个切片`)
    }
    dialog.value = false
    await Promise.all([load(), loadStats()])
  } finally {
    publishing.value = false
  }
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(`确认发布《${row.title}》？发布后小程序可见并写入向量库。`, '发布草稿',
      { type: 'warning', confirmButtonText: '发布' })
  } catch { return }
  const r = await api.post(`/kb/articles/${row.id}/publish`)
  if (r.warning) {
    ElMessage.warning(r.warning)
  } else {
    ElMessage.success(`已发布，写入 ${r.chunks} 个切片`)
  }
  await Promise.all([load(), loadStats()])
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确定删除《${row.title}》？向量库中对应的切片也会一并清理。`,
    '删除文章',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await api.delete(`/kb/articles/${row.id}`)
  ElMessage.success('已删除')
  await Promise.all([load(), loadStats()])
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
    await Promise.all([load(), loadStats()])
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

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.inline-hint { color: #909399; font-size: 12px; }
.inline-upload { display: inline-flex; }
.muted { color: var(--muted); font-size: 12px; }

.stat-row { margin-bottom: 12px; }
.stat {
  display: flex; flex-direction: column; gap: 3px;
  padding: 10px 14px; border: 1px solid var(--line); border-radius: 10px;
  background: #fbfcfc; min-height: 62px;
}
.stat b { font-size: 18px; color: var(--teal-deep); font-variant-numeric: tabular-nums; }
.stat span { font-size: 11.5px; color: var(--muted); }
.stat-keywords { overflow: hidden; }
.keyword-list { display: flex; gap: 4px; flex-wrap: wrap; }

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
