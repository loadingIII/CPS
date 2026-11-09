/**
 * 抢单流程冒烟测试：提交 → 审核通过 → 维修工查抢单大厅 → 抢单 → 待接列表确认 → 重复抢单被拒
 * 运行前提：后端已重启（含 /grab-pool 与 /claim 新接口）且种子数据已初始化。
 */
const BASE = 'http://localhost:8080/api'

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, json: await res.json().catch(() => null) };
}

let pass = 0, fail = 0;
const check = (name, cond, detail) => {
  if (cond) { pass++; console.log('  PASS ' + name); }
  else { fail++; console.log('  FAIL ' + name + (detail ? '  => ' + JSON.stringify(detail).slice(0, 200) : '')); }
};

const student = (await req('POST', '/auth/login', { jobNo: 'student01', password: '123456' })).json.data.token;
const admin = (await req('POST', '/auth/login', { jobNo: 'admin1', password: '123456' })).json.data.token;
const worker01 = (await req('POST', '/auth/login', { jobNo: 'worker01', password: '123456' })).json.data.token; // 水电+空调，东校区
const workerOut = (await req('POST', '/auth/login', { jobNo: 'worker03', password: '123456' })).json.data.token; // 网络+实验，西校区（范围外）

// 1. 提交一张东校区水电单（worker01 技能/校区均匹配）
const submit = (await req('POST', '/orders', {
  campus: 'EAST', locationDetail: '1号宿舍楼 101', faultType: '水电', urgency: 'NORMAL',
  description: '抢单流程测试：宿舍水龙头漏水，请检修',
}, student)).json.data;
check('提交工单', !!submit?.orderId, submit);

// 2. 审核通过（未派单，进入待处理池）
await req('POST', '/orders/' + submit.orderId + '/audit', { pass: true }, admin);

// 3. worker03（负责西校区）看不到该单（校区不符被过滤）
const pool02 = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, workerOut)).json.data;
check('资格过滤：跨校区维修工看不到该单', !(pool02?.list || []).some(o => o.id === submit.orderId), pool02);

// 4. worker01 能看到并抢单
const pool01 = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, worker01)).json.data;
check('抢单大厅包含该单', (pool01?.list || []).some(o => o.id === submit.orderId), pool01);
const claim = await req('POST', '/orders/' + submit.orderId + '/claim', null, worker01);
check('抢单成功', claim.json?.code === 0, claim.json);

// 5. 抢单后出现在 worker01 待接列表，状态仍为待处理
const mine = (await req('GET', '/orders/mine-assigned?status=PENDING&page=1&pageSize=20', null, worker01)).json.data;
const mineOrder = (mine?.list || []).find(o => o.id === submit.orderId);
check('进入待接列表', !!mineOrder, mine);
check('状态仍为 PENDING（自助派单）', mineOrder?.status === 'PENDING', mineOrder);

// 6. 并发/重复抢单被拒（worker_id 已非空）
const claimAgain = await req('POST', '/orders/' + submit.orderId + '/claim', null, worker01);
check('重复抢单被拒(40901)', claimAgain.json?.code === 40901, claimAgain.json);

// 7. 抢单时间线有记录
const logs = (await req('GET', '/orders/' + submit.orderId + '/logs', null, admin)).json.data;
check('时间线含「抢单」', (logs || []).some(l => l.action === '抢单'), logs);

console.log(`\n抢单流程结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
