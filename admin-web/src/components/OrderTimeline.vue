<template>
  <el-timeline>
    <el-timeline-item v-for="(l, i) in logs" :key="i"
                      :timestamp="l.createdAt" :type="i === logs.length - 1 ? 'primary' : ''"
                      :color="COLORS[l.action] || 'var(--muted)'">
      <div class="line">
        <b>{{ l.action }}</b>
        <span class="op">{{ l.operatorName || '系统' }}（{{ roleLabel(l.operatorRole) }}）</span>
      </div>
      <div v-if="l.remark" class="remark">{{ l.remark }}</div>
    </el-timeline-item>
  </el-timeline>
</template>

<script setup>
defineProps({ logs: { type: Array, default: () => [] } })

const COLORS = {
  提交: '#0f7b7e', 审核通过: '#3c8b72', 驳回: '#b98527', 派单: '#0f7b7e',
  接单: '#3c8b72', 拒单: '#c66575', 转单: '#b98527', 完工: '#3c8b72',
  评价: '#71888c', 取消: '#8b9a9c', 放弃: '#8b9a9c', 作废: '#c66575',
}

function roleLabel(r) {
  return { REPORTER: '报修人', ADMIN: '管理员', REPAIRER: '维修工', SYS_ADMIN: '系统管理员', SYSTEM: '系统' }[r] || r
}
</script>

<style scoped>
.line { display: flex; gap: 8px; align-items: center; }
.op { color: var(--muted); font-size: 12px; }
.remark { color: var(--ink); font-size: 13px; margin-top: 6px; background: var(--paper-deep); padding: 7px 10px; border: 1px dashed var(--line); border-radius: 8px; }
</style>
