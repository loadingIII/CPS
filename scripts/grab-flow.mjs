/**
 * 抢单流程冒烟测试（v2.1 抢单即接单）
 * 覆盖：资格过滤、抢单开放时间（紧急立即 / 普通延迟）、抢到即接单、重复抢单被拒、管理员开放抢单。
 * 运行前提：后端已重启（含 /grab-pool、/claim、/broadcast）且种子数据已初始化。
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
  else { fail++; console.log('  FAIL ' + name + (detail ? '  => ' + JSON.stringify(detail).slice(0, 240) : '')); }
};

const student = (await req('POST', '/auth/login', { jobNo: 'student01', password: '123456' })).json.data.token;
const admin = (await req('POST', '/auth/login', { jobNo: 'admin1', password: '123456' })).json.data.token;
const worker01 = (await req('POST', '/auth/login', { jobNo: 'worker01', password: '123456' })).json.data.token; // 水电主修+空调兼修，东校区
const workerOut = (await req('POST', '/auth/login', { jobNo: 'worker03', password: '123456' })).json.data.token; // 网络+实验，西校区

// 1. 紧急单：审核通过即开放抢单
const urgent = (await req('POST', '/orders', {
  campus: 'EAST', locationDetail: '1号宿舍楼 101', faultType: '水电', urgency: 'URGENT',
  description: '抢单流程测试：宿舍水龙头爆裂漏水，请立即处理',
}, student)).json.data;
check('提交紧急工单', !!urgent?.orderId, urgent);
await req('POST', '/orders/' + urgent.orderId + '/audit', { pass: true }, admin);

// 2. 跨校区/技能不符的师傅看不到
const poolOut = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, workerOut)).json.data;
check('资格过滤：跨校区维修工看不到该单', !(poolOut?.list || []).some(o => o.id === urgent.orderId), poolOut);

// 3. 合格师傅能看到，并带匹配分
const pool01 = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, worker01)).json.data;
const target = (pool01?.list || []).find(o => o.id === urgent.orderId);
check('抢单大厅包含紧急单', !!target, pool01);
check('抢单列表带匹配分', target?.matchScore != null && target.matchScore > 0, target);
check('抢单列表 total 与分页一致', pool01?.total >= (pool01?.list || []).length, pool01);

// 4. 抢到即接单
const claim = await req('POST', '/orders/' + urgent.orderId + '/claim', null, worker01);
check('抢单成功', claim.json?.code === 0, claim.json);
const detail = (await req('GET', '/orders/' + urgent.orderId, null, admin)).json.data;
check('抢单后直接进入处理中', detail?.status === 'PROCESSING', detail);
check('抢单后写入接单时间', !!detail?.acceptedAt, detail);
check('抢单后写入处理截止时间', !!detail?.dueAt, detail);
check('派单来源为 GRAB', detail?.dispatchSource === 'GRAB', detail);

// 5. 进入「在办」而不是「待接单」
const processing = (await req('GET', '/orders/mine-assigned?status=PROCESSING&page=1&pageSize=20', null, worker01)).json.data;
check('进入在办列表', (processing?.list || []).some(o => o.id === urgent.orderId), processing);
const pending = (await req('GET', '/orders/mine-assigned?status=PENDING&page=1&pageSize=20', null, worker01)).json.data;
check('不再出现在待接单列表', !(pending?.list || []).some(o => o.id === urgent.orderId), pending);

// 6. 重复抢单被拒
const claimAgain = await req('POST', '/orders/' + urgent.orderId + '/claim', null, worker01);
check('重复抢单被拒(40901)', claimAgain.json?.code === 40901, claimAgain.json);

// 7. 时间线有记录
const logs = (await req('GET', '/orders/' + urgent.orderId + '/logs', null, admin)).json.data;
check('时间线含「抢单」', (logs || []).some(l => l.action === '抢单'), logs);

// 8. 普通单延迟开放，管理员可手动开放
const normal = (await req('POST', '/orders', {
  campus: 'EAST', locationDetail: '一教 201', faultType: '水电', urgency: 'NORMAL',
  description: '抢单流程测试：一教201走廊灯不亮，请检修',
}, student)).json.data;
await req('POST', '/orders/' + normal.orderId + '/audit', { pass: true }, admin);
const poolBefore = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, worker01)).json.data;
check('普通单审核后不立即开放抢单', !(poolBefore?.list || []).some(o => o.id === normal.orderId), poolBefore);
await req('POST', '/orders/' + normal.orderId + '/broadcast', null, admin);
const poolAfter = (await req('GET', '/orders/grab-pool?page=1&pageSize=50', null, worker01)).json.data;
check('管理员开放后进入抢单池', (poolAfter?.list || []).some(o => o.id === normal.orderId), poolAfter);

console.log(`\n抢单流程结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
