/**
 * 派单调度验证（v2.1）
 * 覆盖：管理端待派单/已派待接队列拆分、负载上限（容量锁）、派单与抢单并发竞争、抢单详情权限。
 * 运行前提：后端已启动（8080），管理员/维修工账号可用。
 */
const BASE = 'http://localhost:8080/api'

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  return { status: res.status, json: await res.json().catch(() => null) }
}

let pass = 0, fail = 0
const check = (name, cond, detail) => {
  if (cond) { pass++; console.log('  PASS ' + name) }
  else { fail++; console.log('  FAIL ' + name + (detail ? '  => ' + JSON.stringify(detail).slice(0, 240) : '')) }
}

const login = async (jobNo) => (await req('POST', '/auth/login', { jobNo, password: '123456' })).json.data.token
const student = await login('student01')
const admin = await login('admin1')
const w01 = await login('worker01') // 张强：水电主修 + 空调兼修，大学城校区
const w02 = await login('worker02') // 李国栋：水电主修，大学城校区
const w03 = await login('worker03') // 赵敏：网络/实验，龙洞校区

const workers = (await req('GET', '/workers?page=1&pageSize=200', null, admin)).json.data.list
const byName = (n) => workers.find(w => w.name === n)
const zhang = byName('张强')
const li = byName('李国栋')

async function setLimit(id, limit) {
  await req('PUT', '/workers/' + id, { loadLimit: limit }, admin)
}
async function submitAudit(urgency = 'NORMAL') {
  const o = (await req('POST', '/orders', {
    campus: 'UNIVERSITY_TOWN', locationDetail: '派单验证 ' + Date.now(), faultType: '水电', urgency,
    description: '派单调度验证用例：走廊照明回路故障，请检修',
  }, student)).json.data
  await req('POST', '/orders/' + o.orderId + '/audit', { pass: true }, admin)
  return o.orderId
}
async function cleanup(ids) {
  for (const id of ids) {
    await req('POST', '/orders/' + id + '/void', null, admin)
  }
}

const created = []

// ---------- 1. 队列拆分：待派单不含已派单 ----------
const queueOrder = await submitAudit('NORMAL')
created.push(queueOrder)
await req('POST', '/orders/' + queueOrder + '/assign', { workerId: li.id }, admin)
const pool = (await req('GET', '/orders?status=PENDING&dispatchState=POOL&page=1&pageSize=100', null, admin)).json.data
const offered = (await req('GET', '/orders?status=PENDING&dispatchState=OFFERED&page=1&pageSize=100', null, admin)).json.data
check('待派单队列不含已派单工单', !(pool.list || []).some(o => o.workerId), pool)
check('已派待接队列全部有承接人', (offered.list || []).every(o => o.workerId), offered)
check('已派单出现在已派待接队列', (offered.list || []).some(o => o.id === queueOrder), offered)

// ---------- 2. 负载上限：容量锁防超派 ----------
const liNow = (await req('GET', '/workers/' + li.id, null, admin)).json.data
const cap = liNow.currentLoad + 2
await setLimit(li.id, cap)
const capOrders = []
for (let i = 0; i < 3; i++) {
  const id = await submitAudit('NORMAL')
  capOrders.push(id)
  created.push(id)
}
const results = []
for (const id of capOrders) {
  const r = await req('POST', '/orders/' + id + '/assign', { workerId: li.id }, admin)
  results.push(r.json?.code)
}
const okCount = results.filter(c => c === 0).length
const conflictCount = results.filter(c => c === 40901).length
check('负载上限：只成功 2 单', okCount === 2, results)
check('负载上限：第 3 单被拒(40901)', conflictCount === 1, results)

// ---------- 3. 派单与抢单并发竞争 ----------
await setLimit(li.id, 10)
await setLimit(zhang.id, 10)
const raceOrder = await submitAudit('NORMAL')
created.push(raceOrder)
await req('POST', '/orders/' + raceOrder + '/broadcast', null, admin)
const [assignRes, claimRes] = await Promise.all([
  req('POST', '/orders/' + raceOrder + '/assign', { workerId: zhang.id }, admin),
  req('POST', '/orders/' + raceOrder + '/claim', null, w02),
])
const success = [assignRes, claimRes].filter(r => r.json?.code === 0).length
check('派单/抢单并发：只有一方成功', success === 1, { assign: assignRes.json, claim: claimRes.json })
const raceDetail = (await req('GET', '/orders/' + raceOrder, null, admin)).json.data
check('竞争后工单只有一个承接人', raceDetail.workerId != null, raceDetail)

// ---------- 4. 抢单详情权限与电话脱敏 ----------
const previewOrder = await submitAudit('URGENT')
created.push(previewOrder)
const eligibleDetail = await req('GET', '/orders/' + previewOrder, null, w02)
check('合格师傅可预览待派单详情', eligibleDetail.json?.code === 0, eligibleDetail.json)
check('预览时不暴露报修人电话', !eligibleDetail.json?.data?.reporterPhone, eligibleDetail.json?.data)
const outsiderDetail = await req('GET', '/orders/' + previewOrder, null, w03)
check('不合格师傅看不到详情(40401)', outsiderDetail.json?.code === 40401, outsiderDetail.json)

// ---------- 5. 接单倒计时字段 ----------
const info = (await req('GET', '/orders/' + previewOrder + '/dispatch-info', null, admin)).json.data
check('派单信息返回子状态', info?.dispatchState === 'POOL', info)
const assignedInfo = (await req('GET', '/orders/' + queueOrder + '/dispatch-info', null, admin)).json.data
check('已派单子状态为 OFFERED', assignedInfo?.dispatchState === 'OFFERED', assignedInfo)
check('已派单有剩余接单时间', assignedInfo?.acceptRemainingSeconds > 0, assignedInfo)

await cleanup(created)
await setLimit(li.id, 5)
await setLimit(zhang.id, 5)

console.log(`\n派单调度结果: ${pass} 通过, ${fail} 失败`)
process.exit(fail > 0 ? 1 : 0)
