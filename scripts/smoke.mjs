/**
 * R1 全接口冒烟测试（计划书 §6 T40 用例的接口级自动化版）
 * 运行前提：后端已启动（localhost:8080），种子数据已初始化。
 * 运行方式：node smoke.mjs
 */
const BASE = 'http://localhost:8080/api';

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try { json = await res.json(); } catch { /* ignore */ }
  return { status: res.status, json };
}

let pass = 0, fail = 0;
function check(name, cond, detail) {
  if (cond) { pass++; console.log('  PASS ' + name); }
  else {
    fail++;
    console.log('  FAIL ' + name + (detail ? '  => ' + JSON.stringify(detail).slice(0, 300) : ''));
  }
}

// ---------- TC01 登录 ----------
const adminLogin = await req('POST', '/auth/login', { jobNo: 'admin1', password: '123456' });
check('TC01 管理员登录', adminLogin.status === 200 && adminLogin.json?.code === 0 && !!adminLogin.json?.data?.token, adminLogin.json);
const adminToken = adminLogin.json?.data?.token;

const badLogin = await req('POST', '/auth/login', { jobNo: 'admin1', password: 'wrong' });
check('TC01b 错误密码被拒(40001)', badLogin.json?.code === 40001, badLogin.json);

const studentLogin = await req('POST', '/auth/login', { jobNo: 'student01', password: '123456' });
const studentToken = studentLogin.json?.data?.token;
check('TC01c 学生登录', !!studentToken, studentLogin.json);

const workerLogin = await req('POST', '/auth/login', { jobNo: 'worker02', password: '123456' });
const workerToken = workerLogin.json?.data?.token;
check('TC01d 维修工登录', !!workerToken, workerLogin.json);

// ---------- TC02 RBAC ----------
const forbidden = await req('GET', '/users', null, studentToken);
check('TC02 学生访问账号管理被拒(403)', forbidden.status === 403 && forbidden.json?.code === 40301, forbidden.json);
const noAuth = await req('GET', '/auth/me');
check('TC02b 未登录访问被拒(401)', noAuth.status === 401, noAuth.json);

// ---------- TC03/04 基础数据 ----------
const tree = await req('GET', '/places/tree', null, studentToken);
check('TC03 位置树四级结构', tree.json?.code === 0 && tree.json?.data?.length > 0, tree.json);
let spot = null;
for (const campus of tree.json?.data || []) {
  for (const area of campus.children || []) {
    for (const b of area.children || []) {
      for (const s of b.children || []) {
        if (s.name === '一教101') spot = s;
      }
    }
  }
}
check('TC03b 找到点位「一教101」', !!spot, tree.json?.data?.[0]);

const skills = await req('GET', '/skills', null, studentToken);
check('TC04 技能列表 ≥7 项', skills.json?.code === 0 && (skills.json?.data?.length ?? 0) >= 7, skills.json);

// ---------- TC05 提交 ----------
const desc = '冒烟测试：一教101讲台插座接触不良，偶发打火，请尽快检修';
const clientToken = 'smoke-' + Date.now();
const submit = await req('POST', '/orders', {
  locationId: spot.id, faultType: '水电', urgency: 'URGENT', description: desc, clientToken,
}, studentToken);
check('TC05 提交工单(BX工单号)', submit.json?.code === 0 && submit.json?.data?.orderNo?.startsWith('BX'), submit.json);
const orderId = submit.json?.data?.orderId;

const submitDup = await req('POST', '/orders', {
  locationId: spot.id, faultType: '水电', urgency: 'URGENT', description: desc, clientToken,
}, studentToken);
check('TC05b 幂等：同 token 不产生新单', submitDup.json?.data?.orderId === orderId, submitDup.json);

const badSubmit = await req('POST', '/orders', {
  locationId: spot.id, faultType: '水电', urgency: 'URGENT', description: '短',
}, studentToken);
check('TC05c 描述过短被拒(40001)', badSubmit.json?.code === 40001, badSubmit.json);

// ---------- TC06/07 查询 ----------
const my = await req('GET', '/orders/my', null, studentToken);
check('TC06 我的报修列表', my.json?.code === 0 && my.json?.data?.total >= 1, my.json?.data);

const detail = await req('GET', '/orders/' + orderId, null, studentToken);
check('TC07 详情状态=待审核', detail.json?.data?.status === 'PENDING_AUDIT', detail.json);
const logs = await req('GET', '/orders/' + orderId + '/logs', null, studentToken);
check('TC07b 时间线含「提交」', logs.json?.data?.length === 1 && logs.json?.data?.[0]?.action === '提交', logs.json);

// ---------- TC08/09 审核 ----------
const auditByStudent = await req('POST', '/orders/' + orderId + '/audit', { pass: true }, studentToken);
check('TC08 学生审核被拒(403)', auditByStudent.status === 403, auditByStudent.json);
const rejectNoReason = await req('POST', '/orders/' + orderId + '/audit', { pass: false, reason: '' }, adminToken);
check('TC08b 驳回无理由被拒(40001)', rejectNoReason.json?.code === 40001, rejectNoReason.json);
const audit = await req('POST', '/orders/' + orderId + '/audit', { pass: true }, adminToken);
check('TC09 审核通过', audit.json?.code === 0, audit.json);

// ---------- TC10 派单看板 ----------
const board = await req('GET', '/orders/' + orderId + '/eligible-workers', null, adminToken);
check('TC10 看板返回维修工', board.json?.code === 0 && (board.json?.data?.length ?? 0) >= 6, board.json);
const w02 = (board.json?.data || []).find(w => w.name === '李国栋');
check('TC10b 李国栋合格(水电技能+教学区范围)', w02?.eligible === true, w02);
const w01 = (board.json?.data || []).find(w => w.name === '张强');
check('TC10c 张强不合格(范围在生活区)', w01?.eligible === false && (w01?.failReasons || []).some(r => r.includes('范围')), w01);

// ---------- TC11/12 派单 ----------
const assign = await req('POST', '/orders/' + orderId + '/assign', { workerId: w02.workerId }, adminToken);
check('TC11 派单成功', assign.json?.code === 0, assign.json);
const wrongAccept = await req('POST', '/orders/' + orderId + '/accept', null, studentToken);
check('TC12 学生接单被拒(403)', wrongAccept.status === 403, wrongAccept.json);

// ---------- TC13/14 接单 ----------
const mineAssigned = await req('GET', '/orders/mine-assigned?status=PENDING', null, workerToken);
check('TC13 维修工待接列表含新单', (mineAssigned.json?.data?.list || []).some(o => o.id === orderId), mineAssigned.json?.data);
const accept = await req('POST', '/orders/' + orderId + '/accept', null, workerToken);
check('TC14 接单成功', accept.json?.code === 0, accept.json);

// ---------- TC15 完工 ----------
const finish = await req('POST', '/orders/' + orderId + '/finish', {
  summary: '更换插座面板并紧固接线，测试正常通电',
  finishImageUrls: ['/uploads/202610/smoke-finish.jpg'],
}, workerToken);
check('TC15 完工上报', finish.json?.code === 0, finish.json);

// ---------- TC16 评价 ----------
const rate = await req('POST', '/orders/' + orderId + '/evaluation', { rating: 5, content: '冒烟测试评价：非常快' }, studentToken);
check('TC16 评价成功', rate.json?.code === 0, rate.json);
const rateAgain = await req('POST', '/orders/' + orderId + '/evaluation', { rating: 4 }, studentToken);
check('TC16b 重复评价被拒(40901)', rateAgain.json?.code === 40901, rateAgain.json);

// ---------- TC17 时间线完整性 ----------
const finalLogs = await req('GET', '/orders/' + orderId + '/logs', null, studentToken);
const actions = (finalLogs.json?.data || []).map(l => l.action);
check('TC17 时间线完整(提交/审核通过/派单/接单/完工/评价)',
  ['提交', '审核通过', '派单', '接单', '完工', '评价'].every(a => actions.includes(a)), actions);

// ---------- TC18 非法转移 ----------
const finishAgain = await req('POST', '/orders/' + orderId + '/finish', {
  summary: '重复完工测试'.repeat(3), finishImageUrls: ['/uploads/x.jpg'],
}, workerToken);
check('TC18 已评价单再完工被拒(40901)', finishAgain.json?.code === 40901, finishAgain.json);

// ---------- TC19 维修工档案 ----------
const workers = await req('GET', '/workers?keyword=' + encodeURIComponent('李国栋'), null, adminToken);
check('TC19 维修工列表含负载/评分', workers.json?.data?.list?.[0]?.currentLoad !== undefined, workers.json?.data);

// ---------- TC20 种子数据状态覆盖 ----------
const all = await req('GET', '/orders?page=1&pageSize=50', null, adminToken);
const statuses = new Set((all.json?.data?.list || []).map(o => o.status));
check('TC20 种子数据覆盖 7 种状态',
  ['PENDING_AUDIT', 'PENDING', 'PROCESSING', 'COMPLETED', 'EVALUATED', 'REJECTED', 'CANCELLED']
    .every(s => statuses.has(s)), [...statuses]);

console.log(`\n冒烟结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
