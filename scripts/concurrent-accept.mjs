/**
 * TC06 并发接单幂等测试：同一维修工并发双击接单，仅一人(一次)成功。
 * 前置：种子数据已存在。
 */
const BASE = 'http://localhost:8080/api';

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, json: await res.json().catch(() => null) };
}

const admin = (await req('POST', '/auth/login', { jobNo: 'admin1', password: '123456' })).json.data.token;
const student = (await req('POST', '/auth/login', { jobNo: 'student01', password: '123456' })).json.data.token;
const worker = (await req('POST', '/auth/login', { jobNo: 'worker02', password: '123456' })).json.data.token;

// 提交 + 审核 + 派给 worker02（从看板取 id）
const submit = (await req('POST', '/orders', {
  campus: 'UNIVERSITY_TOWN', locationDetail: '一教 101', faultType: '水电', urgency: 'NORMAL',
  description: '并发接单测试：走廊灯不亮，请检修',
}, student)).json.data;
await req('POST', '/orders/' + submit.orderId + '/audit', { pass: true }, admin);
const board = (await req('GET', '/orders/' + submit.orderId + '/eligible-workers', null, admin)).json.data;
const w02 = board.find(w => w.name === '李国栋');
await req('POST', '/orders/' + submit.orderId + '/assign', { workerId: w02.workerId }, admin);

// 并发 5 次接单
const results = await Promise.all(
  Array.from({ length: 5 }, () => req('POST', '/orders/' + submit.orderId + '/accept', null, worker))
);
const ok = results.filter(r => r.json?.code === 0).length;
const conflict = results.filter(r => r.json?.code === 40901).length;
console.log(`并发 5 次接单: ${ok} 次成功, ${conflict} 次 40901 冲突`);
const detail = (await req('GET', '/orders/' + submit.orderId, null, admin)).json.data;
console.log(`最终状态: ${detail.status}, 乐观锁 version=${detail.version}, accepted_at=${detail.acceptedAt}`);
if (ok === 1 && conflict === 4 && detail.status === 'PROCESSING') {
  console.log('PASS TC06 并发接单幂等');
} else {
  console.log('FAIL TC06 并发接单幂等');
  process.exit(1);
}
