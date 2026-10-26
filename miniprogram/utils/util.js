// 共享状态/字典映射
const STATUS_LABEL = {
  PENDING_AUDIT: '待审核',
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  EVALUATED: '已评价',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

const STATUS_TAG = {
  PENDING_AUDIT: 'tag-warning',
  PENDING: 'tag-danger',
  PROCESSING: 'tag-primary',
  COMPLETED: 'tag-success',
  EVALUATED: 'tag-info',
  REJECTED: 'tag-warning',
  CANCELLED: 'tag-info',
}

function role() {
  return (wx.getStorageSync('user') || {}).role || 'REPORTER'
}

function user() {
  return wx.getStorageSync('user') || {}
}

module.exports = { STATUS_LABEL, STATUS_TAG, role, user }
