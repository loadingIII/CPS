/**
 * 接单超时自动回收验证（v2.1）
 * 流程：派单 → 直接把 assign_expires_at 改到过去 → 等待定时任务回收 → 工单回到待派单池。
 * 依赖本机 mysql CLI（与后端同一数据库配置）。
 */
import { execFileSync } from 'node:child_process'

const BASE = 'http://localhost:8080/api'

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, json: await res.json().catch(() => null) }
}

const login = async (jobNo) => (await req('POST', '/auth/login', { jobNo, password: '123456' })).json.data.token
const student = await login('student01')
const admin = await login('admin1')

const workers = (await req('GET', '/workers?page=1&pageSize=200', null, admin)).json.data.list
const li = workers.find(w => w.name === '李国栋')

const order = (await req('POST', '/orders', {
  campus: 'UNIVERSITY_TOWN', locationDetail: '超时回收验证 ' + Date.now(), faultType: '水电', urgency: 'NORMAL',
  description: '超时回收验证用例：配电箱异响，请安排检修',
}, student)).json.data
await req('POST', '/orders/' + order.orderId + '/audit', { pass: true }, admin)
await req('POST', '/orders/' + order.orderId + '/assign', { workerId: li.id }, admin)

const before = (await req('GET', '/orders/' + order.orderId, null, admin)).json.data
console.log(`派单后：state=${before.dispatchState} worker=${before.workerName}`)

execFileSync('mysql', ['-uroot', '-p123321', 'campus_repair', '-e',
  `UPDATE repair_order SET assign_expires_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ${order.orderId}`],
  { stdio: 'ignore' })

let after = null
for (let i = 0; i < 18; i++) {
  await new Promise(r => setTimeout(r, 5000))
  after = (await req('GET', '/orders/' + order.orderId, null, admin)).json.data
  if (after.dispatchState === 'POOL') break
}

let pass = 0, fail = 0
const check = (name, cond, detail) => {
  if (cond) { pass++; console.log('  PASS ' + name) }
  else { fail++; console.log('  FAIL ' + name + (detail ? '  => ' + JSON.stringify(detail).slice(0, 200) : '')) }
}

check('超时后自动回到待派单池', after?.dispatchState === 'POOL', after)
check('回收后清空承接人', after?.workerId == null, after)
check('回收计数 +1', (after?.rejectCount || 0) >= 1, after)
check('回收后重新开放抢单', !!after?.grabOpenAt, after)
const logs = (await req('GET', '/orders/' + order.orderId + '/logs', null, admin)).json.data
check('时间线含「超时回收」', (logs || []).some(l => l.action === '超时回收'), logs)

await req('POST', '/orders/' + order.orderId + '/void', null, admin)
console.log(`\n超时回收结果: ${pass} 通过, ${fail} 失败`)
process.exit(fail > 0 ? 1 : 0)
