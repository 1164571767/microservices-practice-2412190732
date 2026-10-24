<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ title: string; dates: string[]; series: { name: string; values: number[]; color: string; dashed?: boolean }[]; currency?: boolean }>()
const bounds = computed(() => {
  const values = props.series.flatMap(s => s.values)
  const min = Math.min(0, ...values), max = Math.max(0, ...values)
  return { min, max: props.currency ? (max === min ? min + 1 : max) : Math.max(3, Math.ceil(max / 3) * 3) }
})
const x = (i: number) => 62 + i / Math.max(1, props.dates.length - 1) * 790
const y = (v: number) => 204 - (v - bounds.value.min) / (bounds.value.max - bounds.value.min) * 174
const points = (values: number[]) => values.map((v, i) => `${x(i)},${y(v)}`).join(' ')
const ticks = computed(() => Array.from({ length: 4 }, (_, i) => bounds.value.min + (bounds.value.max - bounds.value.min) * i / 3))
const label = (v: number) => new Intl.NumberFormat('zh-CN', { notation: Math.abs(v) >= 10000 ? 'compact' : 'standard', maximumFractionDigits: props.currency ? 2 : 0 }).format(v)
</script>
<template>
  <div class="report-chart">
    <p class="report-chart-hint">图表可横向滑动，每日精确值可在下方明细查看。</p>
    <ul class="report-legend" aria-label="图例"><li v-for="s in series" :key="s.name"><span :style="{ background: s.color }" />{{ s.name }}<small v-if="s.dashed">（虚线）</small></li></ul>
    <svg viewBox="0 0 880 244" role="img" :aria-label="`${title}，${dates[0]} 至 ${dates.at(-1)}，每日精确值见下方明细`">
      <title>{{ title }}</title>
      <g v-for="(tick, i) in ticks" :key="i"><line x1="62" x2="852" :y1="y(tick)" :y2="y(tick)" stroke="#e6ebf0" /><text x="52" :y="y(tick) + 4" text-anchor="end">{{ label(tick) }}</text></g>
      <line x1="62" x2="852" :y1="y(0)" :y2="y(0)" stroke="#a9b9c4" />
      <g v-for="s in series" :key="s.name"><polyline :points="points(s.values)" fill="none" :stroke="s.color" stroke-width="2.5" :stroke-dasharray="s.dashed ? '7 5' : undefined" stroke-linejoin="round" /><circle v-if="s.values.length === 1" :cx="x(0)" :cy="y(s.values[0] ?? 0)" r="4" :fill="s.color" /></g>
      <text x="62" y="232">{{ dates[0]?.slice(5) }}</text><text x="852" y="232" text-anchor="end">{{ dates.at(-1)?.slice(5) }}</text>
    </svg>
  </div>
</template>
