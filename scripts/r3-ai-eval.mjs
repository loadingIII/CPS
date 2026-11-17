/**
 * R3 AI 报修建议评测：60 条自建标注样本（7 类 × 8 条 + 4 条紧急场景）。
 *
 * 运行前提：后端已启动，且已配置可用的对话模型（AGENT_ENABLED=true、AI_API_KEY）。
 * 运行方式：node r3-ai-eval.mjs
 * 输出：控制台汇总 + ../docs/R3-AI评测记录.md
 *
 * 说明：样本为课程自建，不代表生产分布；结果只用于本版本回归与答辩说明。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE || 'http://localhost:8080/api';
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const CASES = [
  // 水电 8
  ['宿舍卫生间水龙头一直滴水，关不紧', '水电', 'NORMAL'],
  ['教学楼走廊灯不亮，开关按了没反应', '水电', 'NORMAL'],
  ['办公室插座没电，电脑充不上电', '水电', 'NORMAL'],
  ['宿舍马桶堵塞，冲水后水位上涨', '水电', 'NORMAL'],
  ['洗手池下水管漏水，柜子里都是水', '水电', 'NORMAL'],
  ['房间跳闸了，配电箱开关推不上去', '水电', 'NORMAL'],
  ['楼道消防栓接口渗水', '水电', 'NORMAL'],
  ['热水器不出热水，只有冷水', '水电', 'NORMAL'],
  // 木工门窗 8
  ['宿舍门锁卡住，钥匙插进去转不动', '木工门窗', 'NORMAL'],
  ['教室窗户关不严，风吹得响', '木工门窗', 'NORMAL'],
  ['办公桌抽屉滑轨坏了，拉不开', '木工门窗', 'NORMAL'],
  ['衣柜门合页松动，门板歪了', '木工门窗', 'NORMAL'],
  ['实验室柜门玻璃裂了但没碎', '木工门窗', 'NORMAL'],
  ['宿舍床板断裂，睡上去晃动', '木工门窗', 'NORMAL'],
  ['教室门把手掉了，关不上门', '木工门窗', 'NORMAL'],
  ['图书馆椅子腿断了', '木工门窗', 'NORMAL'],
  // 网络多媒体 8
  ['宿舍 wifi 连不上，提示密码错误', '网络多媒体', 'NORMAL'],
  ['教学楼讲台电脑开不了机', '网络多媒体', 'NORMAL'],
  ['教室投影仪没有画面，只有蓝屏', '网络多媒体', 'NORMAL'],
  ['办公室网线接口松动，网络时断时续', '网络多媒体', 'NORMAL'],
  ['多媒体音响没有声音', '网络多媒体', 'NORMAL'],
  ['会议室麦克风有杂音', '网络多媒体', 'NORMAL'],
  ['实验室电脑无法登录校园网', '网络多媒体', 'NORMAL'],
  ['教室电子白板触摸没反应', '网络多媒体', 'NORMAL'],
  // 空调家电 8
  ['宿舍空调不制冷，出风是常温', '空调家电', 'NORMAL'],
  ['空调外机噪音很大，像拖拉机', '空调家电', 'NORMAL'],
  ['空调遥控器按了没反应，电池换了', '空调家电', 'NORMAL'],
  ['空调漏水，墙上湿了一片', '空调家电', 'NORMAL'],
  ['宿舍冰箱不制冷，里面的东西化了', '空调家电', 'NORMAL'],
  ['洗衣机脱水时剧烈晃动', '空调家电', 'NORMAL'],
  ['热水器显示故障代码，打不着火', '空调家电', 'NORMAL'],
  ['办公室空调制热没效果', '空调家电', 'NORMAL'],
  // 电梯 8
  ['教学楼电梯门关不上，一直反复开合', '电梯', 'NORMAL'],
  ['电梯运行时有异响，像金属摩擦', '电梯', 'NORMAL'],
  ['电梯停在楼层之间，门打不开', '电梯', 'NORMAL'],
  ['电梯按钮不亮，按了没反应', '电梯', 'NORMAL'],
  ['电梯轿厢内照明灯坏了', '电梯', 'NORMAL'],
  ['电梯超载报警一直响', '电梯', 'NORMAL'],
  ['电梯到楼层后不平层，有台阶', '电梯', 'NORMAL'],
  ['电梯困人，有人在里面', '电梯', 'URGENT'],
  // 实验设备 8
  ['实验室离心机启动后剧烈震动', '实验设备', 'NORMAL'],
  ['显微镜目镜模糊，调焦没用', '实验设备', 'NORMAL'],
  ['实验台电源模块烧了，有焦味', '实验设备', 'NORMAL'],
  ['恒温箱温度不稳定，显示跳变', '实验设备', 'NORMAL'],
  ['分析天平称重数值一直漂', '实验设备', 'NORMAL'],
  ['实验室通风柜风机不转', '实验设备', 'NORMAL'],
  ['纯水机不出水，压力为零', '实验设备', 'NORMAL'],
  ['实验用冰箱温度报警', '实验设备', 'NORMAL'],
  // 其他 8
  ['图书馆自习室桌椅松动', '其他', 'NORMAL'],
  ['操场看台栏杆晃动', '其他', 'NORMAL'],
  ['食堂餐桌桌面开裂', '其他', 'NORMAL'],
  ['公共卫生间门锁坏了', '其他', 'NORMAL'],
  ['校园路灯白天也亮着', '其他', 'NORMAL'],
  ['垃圾桶盖子丢了', '其他', 'NORMAL'],
  ['体育馆篮球架篮板松动', '其他', 'NORMAL'],
  ['宿舍楼道声控灯不亮', '其他', 'NORMAL'],
  // 紧急 4（含类型）
  ['教学楼三楼配电箱冒烟，有焦味，好像漏电了', '水电', 'URGENT'],
  ['实验楼水管爆裂，水漫到走廊了', '水电', 'URGENT'],
  ['宿舍阳台玻璃破裂，碎片掉到楼下', '木工门窗', 'URGENT'],
  ['食堂后厨燃气管道有煤气味', '其他', 'URGENT'],
];

async function login() {
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jobNo: 'student01', password: '123456', role: 'REPORTER' }),
  });
  const json = await res.json();
  if (json.code !== 0) throw new Error('登录失败：' + json.message);
  return json.data.token;
}

async function suggest(token, description) {
  const res = await fetch(BASE + '/ai/report-suggestion', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    body: JSON.stringify({ description }),
  });
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

const token = await login();
const rows = [];
let typeCorrect = 0;
let urgentHit = 0;
let urgentTotal = 0;
let normalTotal = 0;
let urgentFalsePositive = 0;
let degraded = 0;
const confusion = new Map();

for (let i = 0; i < CASES.length; i += 4) {
  const batch = CASES.slice(i, i + 4);
  const results = await Promise.all(batch.map(([desc]) => suggest(token, desc)));
  results.forEach((r, j) => {
    const [desc, expectedType, expectedUrgency] = batch[j];
    const typeOk = r.faultType === expectedType;
    if (typeOk) typeCorrect++;
    if (expectedUrgency === 'URGENT') {
      urgentTotal++;
      if (r.urgency === 'URGENT') urgentHit++;
    } else {
      normalTotal++;
      if (r.urgency === 'URGENT') urgentFalsePositive++;
    }
    if (r.degraded) degraded++;
    if (!typeOk) {
      const key = expectedType + ' → ' + (r.faultType || 'null');
      confusion.set(key, (confusion.get(key) || 0) + 1);
    }
    rows.push({
      no: rows.length + 1,
      desc,
      expectedType,
      expectedUrgency,
      actualType: r.faultType,
      actualUrgency: r.urgency,
      confidence: r.confidence,
      typeOk,
      urgentOk: expectedUrgency !== 'URGENT' || r.urgency === 'URGENT',
      degraded: r.degraded,
    });
  });
  process.stdout.write(`\r已评测 ${rows.length}/${CASES.length}`);
  await new Promise(r => setTimeout(r, 250));
}
console.log('');

const typeAccuracy = typeCorrect / CASES.length;
const urgentRecall = urgentTotal ? urgentHit / urgentTotal : null;
const md = [
  '# R3 AI 报修建议评测记录',
  '',
  `> 执行时间：${new Date().toLocaleString('zh-CN')} ｜ 样本：${CASES.length} 条自建标注样本 ｜ 模型：backend/.env 当前配置`,
  '',
  '## 汇总',
  '',
  '| 指标 | 结果 |',
  '|---|---|',
  `| 类型准确率 | ${(typeAccuracy * 100).toFixed(1)}%（${typeCorrect}/${CASES.length}） |`,
  `| 紧急场景召回 | ${urgentRecall == null ? '—' : (urgentRecall * 100).toFixed(0) + '%'}（${urgentHit}/${urgentTotal}） |`,
  `| 紧急误报 | ${urgentFalsePositive}/${normalTotal}（NORMAL 样本被判 URGENT，安全优先） |`,
  `| 关键词兜底条数 | ${degraded} |`,
  '',
  '> 样本为课程自建，不代表生产分布；准确率只用于本版本回归与答辩说明，不宣称生产准确率。',
  '',
  '## 混淆情况',
  '',
  confusion.size
    ? ['| 期望 → 实际 | 次数 |', '|---|---|', ...[...confusion.entries()].map(([k, v]) => `| ${k} | ${v} |`)].join('\n')
    : '无',
  '',
  '## 明细',
  '',
  '| # | 描述 | 期望类型 | 实际类型 | 期望紧急度 | 实际紧急度 | 置信度 | 类型 | 紧急 | 兜底 |',
  '|---|---|---|---|---|---|---|---|---|---|',
  ...rows.map(r => `| ${r.no} | ${r.desc} | ${r.expectedType} | ${r.actualType || '—'} | ${r.expectedUrgency} | ${r.actualUrgency} | ${r.confidence ?? '—'} | ${r.typeOk ? '✓' : '✗'} | ${r.urgentOk ? '✓' : '✗'} | ${r.degraded ? '是' : '否'} |`),
  '',
].join('\n');

fs.writeFileSync(path.join(__dirname, '../docs/R3-AI评测记录.md'), md);
console.log(`类型准确率 ${(typeAccuracy * 100).toFixed(1)}%（${typeCorrect}/${CASES.length}）`);
console.log(`紧急召回 ${urgentRecall == null ? '—' : (urgentRecall * 100).toFixed(0) + '%'}（${urgentHit}/${urgentTotal}）`);
console.log(`紧急误报 ${urgentFalsePositive}/${normalTotal}（NORMAL 被判 URGENT，安全优先）`);
console.log(`兜底 ${degraded} 条；明细已写入 docs/R3-AI评测记录.md`);
process.exit(typeAccuracy >= 0.85 ? 0 : 1);
