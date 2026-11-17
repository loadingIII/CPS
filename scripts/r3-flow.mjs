/**
 * R3（第三阶段）增量接口联调脚本
 * 覆盖：AI 报修建议、知识库自助事件与统计、校区/异常/画像/AI 采纳率、同位置历史、
 *      Excel 导出、案例沉淀草稿、权限。
 *
 * 运行前提：后端已启动、已执行 design/migration_v3.0_r3.sql。
 * 运行方式：node r3-flow.mjs
 *          BASE=http://localhost:8080/api node r3-flow.mjs
 *
 * 注意：脚本会创建 1 张测试工单、若干知识库事件，并可能创建 1 篇知识库草稿；
 *      测试工单会在结束时作废，事件与草稿保留用于页面演示。
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
check('R3-01 三角色登录', !!admin && !!student && !!worker);

// ---------- AI 报修建议 ----------
const ai1 = await req('POST', '/ai/report-suggestion',
  { description: '宿舍空调不制冷，遥控器按了没反应，外机也不转' }, student);
check('R3-02 AI 建议返回类型与紧急度',
  ai1.json?.code === 0 && ai1.json?.data?.faultType === '空调家电'
  && ['NORMAL', 'URGENT'].includes(ai1.json?.data?.urgency), ai1.json);

const ai2 = await req('POST', '/ai/report-suggestion',
  { description: '走廊配电箱冒烟，有焦味，好像漏电了' }, student);
check('R3-03 安全关键词强制紧急',
  ai2.json?.code === 0 && ai2.json?.data?.urgency === 'URGENT', ai2.json);

const aiDenied = await req('POST', '/ai/report-suggestion', { description: '空调不制冷' }, worker);
check('R3-04 维修工调 AI 建议被拒', aiDenied.json?.code === 40301 || aiDenied.status === 403, aiDenied);

// ---------- 知识库事件与统计 ----------
const kb = await req('GET', '/kb/articles?page=1&pageSize=5', null, student);
const article = kb.json?.data?.list?.[0];
check('R3-05 知识库有可读文章', kb.json?.code === 0 && !!article, kb.json);

if (article) {
  const solved = await req('POST', `/kb/articles/${article.id}/events`, { eventType: 'SOLVED' }, student);
  check('R3-06 记录已解决事件', solved.json?.code === 0, solved.json);
  const again = await req('POST', `/kb/articles/${article.id}/events`, { eventType: 'UNSOLVED' }, student);
  check('R3-07 同文章 24h 内不重复记反馈', again.json?.code === 0, again.json);
}
const miss = await req('GET', '/kb/articles?keyword=R3%E4%B8%8D%E5%AD%98%E5%9C%A8%E7%9A%84%E8%AF%8D&page=1&pageSize=5', null, student);
check('R3-08 搜索无结果正常返回', miss.json?.code === 0 && miss.json?.data?.total === 0, miss.json);
const kbStats = await req('GET', '/kb/articles/stats?days=30', null, admin);
check('R3-09 自助解决率可计算',
  kbStats.json?.code === 0 && kbStats.json?.data?.selfServiceRate != null
  && kbStats.json?.data?.solved >= 1, kbStats.json);

// ---------- 统计 ----------
const campus = await req('GET', '/stats/campus?days=30', null, admin);
check('R3-10 校区统计返回六校区',
  campus.json?.code === 0 && campus.json?.data?.length === 6, campus.json);
const anomalies = await req('GET', '/stats/anomalies?days=30', null, admin);
check('R3-11 异常看板可访问', anomalies.json?.code === 0 && Array.isArray(anomalies.json?.data), anomalies.json);
const workers = await req('GET', '/workers?page=1&pageSize=5', null, admin);
const workerId = workers.json?.data?.list?.[0]?.id;
check('R3-12 维修工列表可访问', !!workerId, workers.json);
if (workerId) {
  const profile = await req('GET', `/stats/workers/${workerId}?days=90`, null, admin);
  check('R3-13 维修工画像含返修率',
    profile.json?.code === 0 && profile.json?.data?.repairRate !== undefined, profile.json);
}

// ---------- 同位置历史 ----------
const evaluated = await req('GET', '/orders?status=EVALUATED&page=1&pageSize=5', null, admin);
const evalOrder = evaluated.json?.data?.list?.[0];
check('R3-14 有已评价工单可用于沉淀', !!evalOrder, evaluated.json);
if (evalOrder) {
  const similar = await req('GET', `/orders/${evalOrder.id}/similar-history?days=90`, null, admin);
  check('R3-15 同位置历史可访问',
    similar.json?.code === 0 && Array.isArray(similar.json?.data), similar.json);
}

// ---------- 案例沉淀 ----------
if (evalOrder) {
  const draft = await req('POST', `/kb/articles/from-order/${evalOrder.id}`, null, admin);
  const draftId = draft.json?.data?.id;
  check('R3-16 完工单沉淀为草稿或已存在',
    draft.json?.code === 0 && !!draftId, draft.json);
  const dup = await req('POST', `/kb/articles/from-order/${evalOrder.id}`, null, admin);
  check('R3-17 同一工单不重复沉淀', dup.json?.code === 0 && dup.json?.data?.id === draftId, dup.json);
  if (draft.json?.data?.status === 2) {
    const edited = await req('PUT', `/kb/articles/${draftId}/draft`,
      { content: '# R3 测试草稿\n\n## 处理过程\n联调脚本生成' }, admin);
    check('R3-18 草稿可编辑', edited.json?.code === 0, edited.json);
    const published = await req('POST', `/kb/articles/${draftId}/publish`, null, admin);
    check('R3-19 草稿可发布（Chroma 离线时返回 warning）',
      published.json?.code === 0 && (published.json?.data?.indexed || !!published.json?.data?.warning), published.json);
  }
}

// ---------- 导出 ----------
const exportRes = await fetch(BASE + '/orders/export?status=EVALUATED', {
  headers: { Authorization: 'Bearer ' + admin },
});
const buf = Buffer.from(await exportRes.arrayBuffer());
check('R3-20 Excel 导出可下载',
  exportRes.ok && buf.length > 1000
  && String(exportRes.headers.get('content-type')).includes('spreadsheet'), {
  status: exportRes.status, size: buf.length, type: exportRes.headers.get('content-type'),
});

// ---------- 权限 ----------
const denied = await req('GET', '/stats/campus?days=30', null, student);
check('R3-21 报修人调统计接口被拒', denied.json?.code === 40301 || denied.status === 403, denied);
const deniedExport = await fetch(BASE + '/orders/export', {
  headers: { Authorization: 'Bearer ' + worker },
});
check('R3-22 维修工导出被拒', deniedExport.status === 403, { status: deniedExport.status });

// ---------- 提交带 AI/来源的工单 ----------
const created = await req('POST', '/orders', {
  campus: 'UNIVERSITY_TOWN',
  locationDetail: 'R3联调测试点',
  faultType: '空调家电',
  urgency: 'NORMAL',
  description: 'R3 联调：宿舍空调不制冷，用于验证 AI 建议与知识库来源',
  clientToken: 'r3-flow-' + Date.now(),
  sourceKbId: article ? article.id : null,
  aiSuggestedType: '空调家电',
  aiSuggestedUrgency: 'NORMAL',
  aiSuggestionUsed: true,
}, student);
const testOrderId = created.json?.data?.orderId;
check('R3-23 带 AI/来源提交成功', created.json?.code === 0 && !!testOrderId, created.json);

if (testOrderId) {
  const detail = await req('GET', `/orders/${testOrderId}`, null, admin);
  check('R3-24 工单详情保留 AI 与来源字段',
    detail.json?.data?.aiSuggestedType === '空调家电'
    && detail.json?.data?.aiSuggestionUsed === true
    && detail.json?.data?.sourceKbId != null, detail.json?.data);
  const aiStats = await req('GET', '/stats/ai-suggestions?days=30', null, admin);
  check('R3-25 AI 采纳率统计更新',
    aiStats.json?.code === 0 && aiStats.json?.data?.suggested >= 1, aiStats.json);
  // 清理：作废测试工单
  await req('POST', `/orders/${testOrderId}/void`, null, admin);
}

console.log(`\nR3 联调结果：${pass} 通过 / ${fail} 失败`);
process.exit(fail ? 1 : 0);
