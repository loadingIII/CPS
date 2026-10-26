<template>
  <el-timeline>
    <el-timeline-item v-for="(l, i) in logs" :key="i"
                      :timestamp="l.createdAt" :type="i === logs.length - 1 ? 'primary' : ''"
                      :color="COLORS[l.action] || '#909399'">
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
  提交: '#409eff', 审核通过: '#67c23a', 驳回: '#e6a23c', 派单: '#409eff',
  接单: '#67c23a', 拒单: '#f56c6c', 转单: '#e6a23c', 完工: '#67c23a',
  评价: '#909399', 取消: '#c0c4cc', 放弃: '#c0c4cc', 作废: '#f56c6c',
}

function roleLabel(r) {
  return { REPORTER: '报修人', ADMIN: '管理员', REPAIRER: '维修工', SYS_ADMIN: '系统管理员', SYSTEM: '系统' }[r] || r
}
</script>

<style scoped>
.line { display: flex; gap: 8px; align-items: center; }
.op { color: #909399; font-size: 12px; }
.remark { color: #606266; font-size: 13px; margin-top: 4px; background: #f5f7fa; padding: 6px 10px; border-radius: 4px; }
</style>
