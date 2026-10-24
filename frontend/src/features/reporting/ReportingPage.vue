<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import { DataAnalysis, RefreshRight, Calendar, Document } from '@element-plus/icons-vue'
import { visits, revenue, consumption, reportingKey } from './api'
import { presetPeriod, periodError, visitDays, revenueDays, doctorLabel, money, type DrugLine } from './model'
import { useAuthStore } from '../auth/store'
import { ApiError, errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
import TrendChart from './TrendChart.vue'
import './reporting.css'

const auth = useAuthStore()
const tab = ref<'visits' | 'revenue' | 'drugs'>('visits')
const period = ref(presetPeriod('month')), from = ref(period.value.from), to = ref(period.value.to), validation = ref('')
const doctorId = ref(''), drug = ref<Pick<DrugLine, 'drugId' | 'drugName'>>(), page = ref(0)
const admin = computed(() => !!auth.user?.roles.includes('ADMIN'))
const key = computed(() => [...reportingKey, auth.user?.id, 'ADMIN'])
const allVisits = useQuery({ queryKey: computed(() => [...key.value, 'visits', period.value, '']), queryFn: ({ signal }) => visits(period.value, '', signal), enabled: computed(() => admin.value && tab.value === 'visits') })
const doctorVisits = useQuery({ queryKey: computed(() => [...key.value, 'visits', period.value, doctorId.value]), queryFn: ({ signal }) => visits(period.value, doctorId.value, signal), enabled: computed(() => admin.value && tab.value === 'visits' && !!doctorId.value) })
const revenueQuery = useQuery({ queryKey: computed(() => [...key.value, 'revenue', period.value]), queryFn: ({ signal }) => revenue(period.value, signal), enabled: computed(() => admin.value && tab.value === 'revenue') })
const drugQuery = useQuery({ queryKey: computed(() => [...key.value, 'drugs', period.value, drug.value?.drugId ?? '', page.value]), queryFn: ({ signal }) => consumption(period.value, page.value, drug.value?.drugId, signal), enabled: computed(() => admin.value && tab.value === 'drugs') })
const visitQuery = computed(() => doctorId.value ? doctorVisits : allVisits)
const current = computed(() => tab.value === 'visits' ? visitQuery.value : tab.value === 'revenue' ? revenueQuery : drugQuery)
const v = computed(() => visitQuery.value.data.value), r = computed(() => revenueQuery.data.value), d = computed(() => drugQuery.data.value)
const visitRows = computed(() => v.value ? visitDays(v.value) : [])
const revenueRows = computed(() => r.value ? revenueDays(r.value) : [])
const retrieved = computed(() => current.value.dataUpdatedAt.value ? new Date(current.value.dataUpdatedAt.value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '')
const error = computed(() => {
  const e = current.value.error.value
  return e instanceof ApiError && e.code === 'REQUEST_TIMEOUT' ? '报表读取超时，请重试查询。' : errorMessage(e)
})
function apply() {
  const next = { from: from.value, to: to.value }
  validation.value = periodError(next)
  if (validation.value) return
  if (next.from === period.value.from && next.to === period.value.to) { void current.value.refetch(); return }
  doctorId.value = ''; drug.value = undefined; page.value = 0; period.value = next
}
function preset(value: 'today' | 'week' | 'month') { const p = presetPeriod(value); from.value = p.from; to.value = p.to; apply() }
function filterDrug(line?: DrugLine) { drug.value = line; page.value = 0 }
</script>

<template>
  <AppState v-if="!admin" kind="error" title="当前账号无报表查询权限" description="报表仅对管理员开放，请联系医院管理员核实权限。" />
  <div v-else class="reporting-page">
    <div class="page-heading"><div><div class="page-eyebrow">CLINIC INSIGHTS</div><h1>数据报表</h1><p>看见每一份照护，读懂医院的日常。</p></div><ElButton :icon="RefreshRight" :loading="current.isFetching.value" @click="current.refetch()">刷新报表</ElButton></div>
    <section class="report-welcome"><div><span><DataAnalysis /> 医院经营概览</span><h2>让每一次照护，清晰可见</h2><p>从门诊工作量到收支与用药，以清晰数据支持日常管理。</p><small>管理员专属 · 只读统计 · 上海时间</small></div><img src="/images/reporting-care.png" alt="" /></section>
    <section class="surface report-controls" aria-label="报表查询">
      <nav class="report-tabs" aria-label="报表类型"><button :aria-pressed="tab === 'visits'" @click="tab = 'visits'">门诊与医生工作量</button><button :aria-pressed="tab === 'revenue'" @click="tab = 'revenue'">收入统计</button><button :aria-pressed="tab === 'drugs'" @click="tab = 'drugs'">药品消耗</button></nav>
      <form class="report-filters" @submit.prevent="apply"><label>开始日期<input v-model="from" type="date" required aria-describedby="report-date-help" /></label><span class="report-date-separator">至</span><label>结束日期<input v-model="to" type="date" required aria-describedby="report-date-help" /></label><ElButton native-type="submit" type="primary" :icon="Calendar">查询报表</ElButton><div class="report-presets"><button type="button" @click="preset('today')">今天</button><button type="button" @click="preset('week')">近7天</button><button type="button" @click="preset('month')">本月至今</button></div></form>
      <p id="report-date-help" class="report-muted">日期含首尾，最多 366 天；统计日按 Asia/Shanghai 切分。</p><p v-if="validation" role="alert" class="report-validation">{{ validation }}</p>
      <div class="report-applied"><span>当前查询：<strong>{{ period.from }} — {{ period.to }}</strong></span><label v-if="tab === 'visits'">医生范围<select v-model="doctorId" aria-label="医生范围"><option value="">全部医生</option><option v-for="doctor in allVisits.data.value?.doctors" :key="doctor.doctorId" :value="doctor.doctorId">{{ doctorLabel(doctor) }}</option></select></label><span v-if="tab === 'drugs' && drug" class="report-drug-filter">仅看：{{ drug.drugName }}<button @click="filterDrug()">清除药品筛选</button></span></div>
      <p v-if="tab === 'visits' && allVisits.isError.value && doctorId" class="report-validation" role="alert">全部医生范围读取失败，当前仍按已选医生查询。<button class="text-button" @click="allVisits.refetch()">重试范围</button></p>
    </section>
    <div class="report-grid">
      <section class="report-results" aria-label="报表结果" :aria-busy="current.isFetching.value">
        <AppState v-if="current.isPending.value" kind="loading" title="正在读取统计报表" />
        <AppState v-else-if="current.isError.value" kind="error" title="报表读取失败" :description="error" action-label="重试报表" @action="current.refetch()" />
        <template v-else>
          <div class="report-data-stamp" role="status"><span>{{ current.isFetching.value ? '正在重新读取…' : '查询已完成' }}</span><span>读取时间：{{ retrieved }} · 上海时间</span></div>
          <template v-if="tab === 'visits' && v">
            <div class="report-metrics"><article class="surface report-metric"><span>门诊量</span><strong data-testid="total-visits">{{ v.totalVisits.toLocaleString('zh-CN') }}<small>次</small></strong><p>按实际就诊日计入的病历数</p></article><article class="surface report-metric"><span>有就诊记录的医生</span><strong>{{ v.doctors.length }}<small>位</small></strong><p>{{ doctorId ? '当前医生范围' : '全部医生范围' }}</p></article><article class="surface report-metric"><span>区间日均门诊量</span><strong>{{ (v.totalVisits / Math.max(1, visitRows.length)).toFixed(1) }}<small>次 / 天</small></strong><p>含区间内无记录的日期</p></article></div>
            <AppState v-if="!v.totalVisits" kind="empty" title="该区间暂无门诊统计记录" description="可调整日期或医生范围。统计记录可能延迟或缺失，请到业务页面核实实际就诊情况。" />
            <template v-else>
<section class="surface report-panel"><div class="report-panel-heading"><h2>每日门诊趋势</h2><span>单位：次</span></div><TrendChart title="每日门诊趋势" :dates="visitRows.map(row => row.date)" :series="[{ name: '门诊量', color: '#008b8b', values: visitRows.map(row => row.visits) }]" /><details class="report-daily"><summary>查看每日明细（{{ visitRows.length }} 天）</summary><div class="report-table-wrap"><table class="report-table"><thead><tr><th>日期</th><th>门诊量（次）</th></tr></thead><tbody><tr v-for="row in visitRows" :key="row.date"><td>{{ row.date }}</td><td>{{ row.visits }}</td></tr></tbody></table></div></details></section>
            <section class="surface report-panel"><div class="report-panel-heading"><h2>医生工作量</h2><span>按门诊量排序</span></div><div class="report-table-wrap"><table class="report-table"><thead><tr><th>医生</th><th>门诊量（次）</th><th>占当前范围门诊量</th><th>工作量分布</th></tr></thead><tbody><tr v-for="doctor in v.doctors" :key="doctor.doctorId"><td><strong>{{ doctorLabel(doctor) }}</strong></td><td>{{ doctor.visits }}</td><td>{{ (doctor.visits / v.totalVisits * 100).toFixed(1) }}%</td><td><span class="report-bar"><i :style="{ width: `${doctor.visits / v.totalVisits * 100}%` }" /></span></td></tr></tbody></table></div></section>
</template>
          </template>
          <template v-if="tab === 'revenue' && r">
            <div class="report-metrics"><article class="surface report-metric"><span>支付金额</span><strong data-testid="paid-amount">{{ money(r.paidAmount) }}</strong><p>{{ r.paidCount }} 笔支付 · 按支付发生日</p></article><article class="surface report-metric"><span>退款金额</span><strong>{{ money(r.refundedAmount) }}</strong><p>{{ r.refundedCount }} 笔退款 · 按退款发生日</p></article><article class="surface report-metric" :class="{ 'is-negative': r.netAmount < 0 }"><span>净收入</span><strong data-testid="net-amount">{{ money(r.netAmount) }}</strong><p>支付金额 − 退款金额 · CNY</p></article></div>
            <AppState v-if="!r.paidCount && !r.refundedCount" kind="empty" title="该区间暂无收支统计记录" description="可调整日期。统计记录可能延迟或缺失，请到收费页面核实实际收款与退款。" />
            <section v-else class="surface report-panel"><div class="report-panel-heading"><h2>每日收支趋势</h2><span>单位：元（CNY）</span></div><TrendChart title="每日收支趋势" :dates="revenueRows.map(row => row.date)" currency :series="[{ name: '支付', color: '#008b8b', values: revenueRows.map(row => row.paidAmount) }, { name: '退款', color: '#c27d26', dashed: true, values: revenueRows.map(row => row.refundedAmount) }, { name: '净收入', color: '#536b9e', values: revenueRows.map(row => row.netAmount) }]" /><details class="report-daily"><summary>查看每日明细（{{ revenueRows.length }} 天）</summary><div class="report-table-wrap"><table class="report-table"><thead><tr><th>日期</th><th>支付金额</th><th>退款金额</th><th>净收入</th></tr></thead><tbody><tr v-for="row in revenueRows" :key="row.date"><td>{{ row.date }}</td><td>{{ money(row.paidAmount) }}</td><td>{{ money(row.refundedAmount) }}</td><td :class="{ 'report-negative': row.netAmount < 0 }">{{ money(row.netAmount) }}</td></tr></tbody></table></div></details></section>
          </template>
          <template v-if="tab === 'drugs' && d">
            <div class="report-metrics"><article class="surface report-metric"><span>发药单数</span><strong data-testid="total-dispenses">{{ d.totalDispenses.toLocaleString('zh-CN') }}<small>单</small></strong><p>整个查询区间内去重统计</p></article><article class="surface report-metric"><span>药品快照记录</span><strong>{{ d.totalElements.toLocaleString('zh-CN') }}<small>项</small></strong><p>同一药品更名或改规格后分行保留</p></article><article class="surface report-metric report-metric-note"><span>消耗统计口径</span><strong>按原单位分列</strong><p>盒、片、瓶等单位不同，不合并比较总数量。</p></article></div>
            <AppState v-if="!d.items.length" kind="empty" :title="page ? '该页暂无药品记录' : '该区间暂无药品消耗记录'" description="可调整日期或清除药品筛选。统计空值不能用于判断实际是否发药。" :action-label="page ? '返回第一页' : ''" @action="page = 0" />
            <section v-else class="surface report-panel"><div class="report-panel-heading"><h2>药品消耗明细</h2><span>按发药时快照统计</span></div><div class="report-table-wrap"><table class="report-table"><thead><tr><th>药品 / 规格</th><th>消耗数量</th><th>涉及发药单数</th><th>范围</th></tr></thead><tbody><tr v-for="line in d.items" :key="JSON.stringify([line.drugId, line.drugName, line.specification, line.unit])"><td><strong>{{ line.drugName }}</strong><small>{{ line.specification }}</small></td><td class="report-quantity">{{ line.quantity.toLocaleString('zh-CN') }} <small>{{ line.unit }}</small></td><td>{{ line.dispenseCount }} 单</td><td><ElButton v-if="!drug" text type="primary" @click="filterDrug(line)">仅看此药</ElButton><span v-else class="report-muted">当前药品</span></td></tr></tbody></table></div></section>
            <div v-if="d.totalElements" class="surface report-pages"><span>共 {{ d.totalElements }} 项 · 汇总不随分页改变</span><ElPagination :current-page="page + 1" :page-size="10" :total="d.totalElements" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div>
          </template>
        </template>
      </section>
      <aside class="surface report-guide"><h2><Document /> 读懂统计口径</h2><ol><li><strong>门诊看实际就诊日</strong><p>一份病历计一次门诊。补录病历计入实际就诊日，不按预约或建档日期统计。</p></li><li><strong>收支看资金发生日</strong><p>跨月退款计入退款当月；净收入可能为负，不回改上月收入。</p></li><li><strong>消耗看实际发药</strong><p>保留发药时的药名、规格和单位；不含人工入库或报损调整。同单可含多种药品，逐行单数不能直接相加。</p></li></ol><div class="report-consistency"><strong>统计可能延迟或缺失</strong><p>报表来自事件投影。刷新仅重新查询，不能补齐漏记；读取时间不代表业务数据已同步。请以诊疗、账单和发药凭证核实业务结果。</p></div></aside>
    </div>
  </div>
</template>
