/**
 * R2（第二阶段）增量接口联调脚本
 * 覆盖：知识库、按时率/绩效、通知、催单限频、配件权限、批量审核、合并校验、重复报修提示、
 *      重报修改关键字段、完工带配件、差评回访。
 *
 * 运行前提：后端已启动、种子数据已初始化、已执行 design/migration_v2.3_r2.sql。
 * 运行方式：node r2-flow.mjs        （默认 http://localhost:8080/api）
 *          BASE=http://localhost:18080/api node r2-flow.mjs
 *
 * 注意：脚本会创建 2 张测试工单并产生 1 条回访任务，不会自动删除。
 */
const BASE = process.env.BASE || 'http://localhost:8080/api';

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

async function login(jobNo) {
  const r = await req('POST', '/auth/login', { jobNo, password: '123456' });
  return r.json?.data?.token;
}

const admin = await login('admin1');
const student = await login('student01');
const worker = await login('worker01');
check('R2-01 三角色登录', !!admin && !!student && !!worker);

// ---------- 知识库 ----------
const kb = await req('GET', '/kb/articles?page=1&pageSize=10', null, student);
check('R2-02 知识库列表', kb.json?.code === 0 && kb.json?.data?.total >= 1, kb.json);
check('R2-03 列表不返回全文', (kb.json?.data?.list || []).every(a => a.content == null), kb.json?.data?.list?.[0]);
const article = kb.json?.data?.list?.[0];
if (article) {
  const d1 = await req('GET', `/kb/articles/${article.id}`, null, student);
  const d2 = await req('GET', `/kb/articles/${article.id}`, null, student);
  check('R2-04 详情浏览量递增',
    d2.json?.data?.viewCount === (d1.json?.data?.viewCount || 0) + 1, { d1: d1.json, d2: d2.json });
}
const kbManage = await req('GET', '/kb/articles/manage?page=1&pageSize=5', null, admin);
check('R2-05 管理端知识库', kbManage.json?.code === 0, kbManage.json);

// ---------- 统计 ----------
const ontime = await req('GET', '/stats/ontime?days=14', null, admin);
check('R2-06 按时率趋势 14 天', ontime.json?.code === 0 && ontime.json?.data?.length === 14, ontime.json);
const perf = await req('GET', '/stats/workers?days=30', null, admin);
check('R2-07 绩效排行可访问', perf.json?.code === 0 && Array.isArray(perf.json?.data), perf.json);
const stats = await req('GET', '/orders/statistics', null, admin);
check('R2-08 看板含真实待派单数', stats.json?.code === 0 && typeof stats.json?.data?.pendingPool === 'number', stats.json);

// ---------- 通知与预警 ----------
const unreadBefore = await req('GET', '/notifications/unread-count', null, admin);
check('R2-09 未读通知数', unreadBefore.json?.code === 0, unreadBefore.json);
const alerts = await req('GET', '/alerts?page=1&pageSize=5', null, admin);
check('R2-10 预警列表', alerts.json?.code === 0, alerts.json);

// ---------- 造单 A：查重 → 审核 → 派单 → 催单 → 接单 → 完工带配件 → 差评回访 ----------
const created = await req('POST', '/orders', {
  campus: 'UNIVERSITY_TOWN',
  locationDetail: 'R2联调楼 101',
  faultType: '空调家电',
  urgency: 'NORMAL',
  description: 'R2 联调脚本自动创建：空调不制冷，可手动清理',
  imageUrls: [],
}, student);
const orderA = created.json?.data?.orderId;
check('R2-11 创建测试工单 A', created.json?.code === 0 && !!orderA, created.json);

const dupQuery = new URLSearchParams({
  campus: 'UNIVERSITY_TOWN', faultType: '空调家电', locationDetail: 'R2联调楼 101',
});
const dup = await req('GET', '/orders/duplicates?' + dupQuery, null, student);
const dupFirst = (dup.json?.data || [])[0];
check('R2-12 重复报修提示命中', dup.json?.code === 0 && (dup.json?.data || []).length >= 1, dup.json);
check('R2-13 查重不泄露他人描述/姓名',
  !!dupFirst && dupFirst.description === undefined && dupFirst.reporterName === undefined
    && typeof dupFirst.statusLabel === 'string', dupFirst);

const audit = await req('POST', `/orders/${orderA}/audit`, { pass: true }, admin);
check('R2-14 审核通过', audit.json?.code === 0, audit.json);

const workers = await req('GET', `/orders/${orderA}/eligible-workers`, null, admin);
const target = (workers.json?.data || []).find(w => w.eligible);
check('R2-15 存在可派单候选', !!target, workers.json);
let targetToken = null;
if (target) {
  // 按候选 id 反查工号，用该师傅账号完成接单/完工，避免写死 worker01 的负载状态
  const workerList = await req('GET', '/workers?page=1&pageSize=200', null, admin);
  const targetUser = (workerList.json?.data?.list || []).find(u => u.id === target.workerId);
  targetToken = targetUser ? await login(targetUser.jobNo) : null;
  check('R2-15b 目标维修工可登录', !!targetToken, targetUser);
  const assign = await req('POST', `/orders/${orderA}/assign`, { workerId: target.workerId }, admin);
  check('R2-16 派单成功', assign.json?.code === 0, assign.json);
}

const urge1 = await req('POST', `/orders/${orderA}/urge`, null, student);
check('R2-17 首次催单成功', urge1.json?.code === 0, urge1.json);
const urge2 = await req('POST', `/orders/${orderA}/urge`, null, student);
check('R2-18 2 小时内重复催单被拒(40901)', urge2.json?.code === 40901, urge2.json);

const partByReporter = await req('POST', `/orders/${orderA}/parts`, { partName: '测试件', quantity: 1 }, student);
check('R2-19 报修人登记配件被拒(40301)', partByReporter.json?.code === 40301, partByReporter.json);

if (target && targetToken) {
  const accept = await req('POST', `/orders/${orderA}/accept`, null, targetToken);
  check('R2-20 维修工接单', accept.json?.code === 0, accept.json);

  const finish = await req('POST', `/orders/${orderA}/finish`, {
    summary: '更换启动电容后空调恢复制冷，运行电流正常',
    finishImageUrls: ['/uploads/r2-test-finish.jpg'],
    parts: [{ partName: '启动电容', quantity: 1 }],
  }, targetToken);
  check('R2-21 完工带配件一次提交', finish.json?.code === 0, finish.json);

  const detailA = await req('GET', `/orders/${orderA}`, null, student);
  check('R2-22 完工后配件已落库',
    detailA.json?.data?.status === 'COMPLETED'
      && (detailA.json?.data?.parts || []).some(p => p.partName === '启动电容'), detailA.json?.data);

  const latePart = await req('POST', `/orders/${orderA}/parts`, { partName: '后补件', quantity: 1 }, targetToken);
  check('R2-23 完工后补录配件被拒', latePart.json?.code === 40901, latePart.json);

  const evaluate = await req('POST', `/orders/${orderA}/evaluation`, {
    rating: 2, content: '等待时间偏长', tags: ['需改进'],
  }, student);
  check('R2-24 差评提交', evaluate.json?.code === 0, evaluate.json);

  const revisits = await req('GET', '/revisits?status=PENDING&page=1&pageSize=5', null, admin);
  check('R2-25 差评自动生成回访任务',
    revisits.json?.code === 0 && (revisits.json?.data?.list || []).some(r => r.orderId === orderA),
    revisits.json);
}

// ---------- 造单 B：驳回 → 重报时修改位置/类型/紧急度 ----------
const createdB = await req('POST', '/orders', {
  campus: 'UNIVERSITY_TOWN',
  locationDetail: '旧位置 201',
  faultType: '水电',
  urgency: 'NORMAL',
  description: 'R2 联调脚本：重报字段修改测试',
  imageUrls: [],
}, student);
const orderB = createdB.json?.data?.orderId;
check('R2-26 创建测试工单 B', !!orderB, createdB.json);

const reject = await req('POST', `/orders/${orderB}/audit`, { pass: false, reason: '位置与类型填写有误，请修正' }, admin);
check('R2-27 驳回工单 B', reject.json?.code === 0, reject.json);

const resubmit = await req('POST', `/orders/${orderB}/resubmit`, {
  campus: 'LONGDONG',
  locationDetail: '新位置 305',
  faultType: '木工门窗',
  urgency: 'URGENT',
  description: '已按驳回意见修正位置与类型，门锁无法闭合',
  imageUrls: [],
}, student);
check('R2-28 重报可改位置/类型/紧急度', resubmit.json?.code === 0, resubmit.json);

const detailB = await req('GET', `/orders/${orderB}`, null, student);
check('R2-29 重报字段已生效',
  detailB.json?.data?.campus === 'LONGDONG'
    && detailB.json?.data?.faultType === '木工门窗'
    && detailB.json?.data?.urgency === 'URGENT'
    && detailB.json?.data?.locationText?.includes('新位置 305'), detailB.json?.data);

// ---------- 批量审核与合并校验 ----------
const batch = await req('POST', '/orders/audit-batch', { ids: [999999], pass: true }, admin);
check('R2-30 批量审核逐条返回失败', batch.json?.code === 0
  && batch.json?.data?.[0]?.success === false, batch.json);

const mergeSelf = await req('POST', `/orders/${orderB}/merge`, { mainOrderId: orderB }, admin);
check('R2-31 合并到自己被拒(40001)', mergeSelf.json?.code === 40001, mergeSelf.json);

console.log(`\nR2 联调结果：${pass} 通过 / ${fail} 失败（测试工单 A=${orderA}，B=${orderB}，可手动清理）`);
process.exit(fail ? 1 : 0);
