// Reproduce labeled report excerpts from recorded real results, not fabricated terminal screenshots.
import { readFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(resolve(root, 'frontend/package.json'))
const { chromium } = require('@playwright/test')
const report = JSON.parse(readFileSync(resolve(root, 'docs/homework/week-07/verification-results.json'), 'utf8'))
const out = resolve(root, 'docs/homework/week-07/screenshots')
mkdirSync(out, { recursive: true })
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const pretty = value => JSON.stringify(value, null, 2)
const logs = report.commandOutput
const source = `实际验证时间：${report.recordedAt} · VMware Docker Compose · 以下为真实结果摘录`
const instanceLines = report.registered.services.map(service =>
  `${service.service.padEnd(24)} ${service.instances.map(instance => `${instance.ip}:${instance.port} healthy=${instance.healthy}`).join(', ')}`,
).join('\n')
const compactTrace = logs.discoveredAddresses.split('\n').filter(Boolean).slice(-3).map(line => {
  const match = line.match(/(\d{4}-\d\d-\d\dT\S+).*?(Discovered service=.*)/)
  return match ? `${match[1]} ${match[2]}` : line
}).join('\n')
const normalBusiness = logs.normal.split('\n').filter(line =>
  line.includes('POST /api/appointment/appointments') || line.startsWith('PASS real HTTP') || line.startsWith('PASS missing'),
).join('\n')
const count = report.appointments
const tests = Object.entries(report.build.tests).map(([name, row]) =>
  `${name.padEnd(24)} tests=${String(row.tests).padEnd(3)} failures=${row.failures} errors=${row.errors} skipped=${row.skipped}`,
).join('\n')
const sections = [
  ['03-discovery-and-booking.png', '第 07 周 · 服务发现与预约正常调用', [
    ['实际注册 API 查询结果', `namespace=${report.namespace}  group=${report.group}\n${instanceLines}`],
    ['诊疗服务实际发现日志（保留时间与 URI）', compactTrace],
    ['实际预约请求 · POST /api/appointment/appointments', pretty(report.businessRequest)],
    ['实际业务响应与数据断言', normalBusiness],
  ]],
  ['04-down-and-restored.png', '第 07 周 · 下线失败与恢复成功', [
    ['停止患者服务后的实际实例列表', pretty(report.unregistered)],
    ['新有效时段的实际故障请求', pretty(report.failedAndRecoveredRequest)],
    ['实际 503 与诊疗自身健康', `${logs.down}\nclinic /actuator/health: ${pretty(report.clinicHealthWhilePatientDown)}\n诊疗库全表预约数：${count.beforeFailure} → ${count.afterFailure}`],
    ['实际恢复注册与重新预约', `恢复实例：${pretty(report.restored.services[0].instances)}\n${logs.restored}\n诊疗库全表预约数：${count.afterFailure} → ${count.afterRecovery}`],
  ]],
  ['05-smoke-and-tests.png', '第 07 周 · 冒烟退出码与自动测试', [
    ['实际命令：bash scripts/smoke-test.sh --health-only · 恢复后退出码 0', logs.health],
    [`实际命令：bash scripts/smoke-test.sh --health-only · 患者停止时退出码 ${report.expectedFailedHealthExit}`, logs.expectedHealthFailure],
    [`实际命令：${report.build.command} · 共 ${report.build.totalTests} 项测试通过`, tests],
    ['Maven 实际执行结果摘录', logs.mavenVerifySummary],
  ]],
]
const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
  for (const [filename, title, blocks] of sections) {
    await page.setContent(`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${escape(title)}</title>
      <style>body{margin:32px 44px;color:#17212b;background:white;font:17px/1.5 'Segoe UI','Microsoft YaHei',sans-serif}h1{font-size:28px;margin:0 0 8px}h2{font-size:19px;margin:24px 0 8px}p{color:#4b5563}pre{font:16px/1.5 Consolas,'Microsoft YaHei',monospace;white-space:pre-wrap;overflow-wrap:anywhere;background:#f5f7f9;border:1px solid #d8dee5;padding:16px;margin:0}</style>
      <h1>${escape(title)}</h1><p>${escape(source)}</p>
      ${blocks.map(([heading, content]) => `<h2>${escape(heading)}</h2><pre>${escape(content.trim())}</pre>`).join('')}
      <p>来源：docs/homework/week-07/verification-results.json；此图为真实记录排版，不是终端或控制台画面。</p></html>`)
    await page.screenshot({ path: resolve(out, filename), fullPage: true })
  }
} finally {
  await browser.close()
}
console.log('Rendered three labeled screenshots from recorded real verification results.')
