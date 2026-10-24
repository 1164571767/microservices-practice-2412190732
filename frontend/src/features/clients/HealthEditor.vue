<script setup lang="ts">
import { reactive } from 'vue'
import { saveHealth } from './api'
import { useArchiveWrite } from './useArchiveWrite'
import { today, type Vaccination, type Allergy } from './model'
const props = defineProps<{ petId: string; kind: 'vaccinations' | 'allergies'; record?: Vaccination | Allergy }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { busy, message, uncertain, run } = useArchiveWrite()
const vaccination = props.record && 'vaccineName' in props.record ? props.record : undefined
const allergy = props.record && 'allergen' in props.record ? props.record : undefined
const form = reactive({ vaccineName: vaccination?.vaccineName ?? '', administeredOn: vaccination?.administeredOn ?? today(), nextDueOn: vaccination?.nextDueOn ?? '', allergen: allergy?.allergen ?? '', severity: allergy?.severity ?? 'MILD', note: props.record?.note ?? '', reason: '' })
function submit() {
  const record = props.kind === 'vaccinations'
    ? { vaccineName: form.vaccineName.trim(), administeredOn: form.administeredOn, nextDueOn: form.nextDueOn || null, note: form.note.trim() || null }
    : { allergen: form.allergen.trim(), severity: form.severity, note: form.note.trim() || null }
  void run(() => saveHealth(props.kind, props.petId, record, props.record?.id, form.reason.trim()), () => emit('saved'))
}
</script>
<template>
  <ElDialog :model-value="true" :title="`${record ? '留痕更正' : '新增'}${kind === 'vaccinations' ? '疫苗记录' : '过敏记录'}`" width="min(560px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <form class="archive-form" @submit.prevent="submit">
      <p class="archive-note">{{ record ? '更正会追加新记录，保留原文及每次更正的操作人、时间和原因。' : '系统自动记录当前操作人和保存时间。' }}</p>
      <fieldset :disabled="busy || uncertain">
        <template v-if="kind === 'vaccinations'">
          <label>疫苗名称<input v-model="form.vaccineName" required maxlength="100" pattern=".*\S.*" /></label>
          <label>接种日期<input v-model="form.administeredOn" type="date" required :max="today()" /></label>
          <label>下次应接种日期（选填）<input v-model="form.nextDueOn" type="date" :min="form.administeredOn" /></label>
        </template>
        <template v-else><label>过敏原<input v-model="form.allergen" required maxlength="100" pattern=".*\S.*" /></label><label>严重程度<select v-model="form.severity"><option value="MILD">轻微</option><option value="MODERATE">中度</option><option value="SEVERE">严重</option></select></label></template>
        <label>备注（选填）<textarea v-model="form.note" maxlength="255" rows="2" /></label>
        <label v-if="record">更正原因<textarea v-model="form.reason" required maxlength="255" pattern=".*\S.*" rows="2" /></label>
      </fieldset>
      <p v-if="message" class="archive-feedback" role="alert">{{ message }}</p>
      <div class="archive-form__actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并核实' : '取消' }}</ElButton><ElButton type="primary" native-type="submit" :disabled="uncertain" :loading="busy">{{ record ? '提交更正' : '保存记录' }}</ElButton></div>
    </form>
  </ElDialog>
</template>
