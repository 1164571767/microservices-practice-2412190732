<script setup lang="ts">
import { reactive, ref } from 'vue'
import { saveDrug } from './api'
import { isUncertain, nonnegative, type Drug } from './model'
import { errorMessage } from '../../shared/api/client'
const props = defineProps<{ drug?: Drug }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const form = reactive({ name: props.drug?.name ?? '', specification: props.drug?.specification ?? '', unit: props.drug?.unit ?? '', lowStockThreshold: props.drug?.lowStockThreshold ?? 5, initialQuantity: 0, active: props.drug?.active ?? true })
const busy = ref(false), uncertain = ref(false), message = ref('')
async function submit() {
  if (busy.value || uncertain.value) return
  if (!form.name.trim() || !form.specification.trim() || !form.unit.trim() || !nonnegative(form.lowStockThreshold) || !nonnegative(form.initialQuantity)) { message.value = '请填写药品名称、规格、单位，以及有效的非负整数。'; return }
  busy.value = true; message.value = ''
  try { await saveDrug({ ...form, name: form.name.trim(), specification: form.specification.trim(), unit: form.unit.trim() }, props.drug?.id); emit('saved') }
  catch (e) { uncertain.value = isUncertain(e); message.value = uncertain.value ? '保存结果未确认。输入已保留，请关闭后查询目录核实，勿直接重复保存。' : errorMessage(e) }
  finally { busy.value = false }
}
</script>
<template>
  <ElDialog :model-value="true" :title="drug ? '编辑药品' : '新增药品'" width="min(500px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <form class="pharmacy-form" @submit.prevent="submit"><fieldset :disabled="busy || uncertain"><label>药品名称<input v-model="form.name" required maxlength="100" /></label><label>规格<input v-model="form.specification" required maxlength="100" placeholder="例如 0.5g × 20片" /></label><label>库存单位<input v-model="form.unit" required maxlength="20" placeholder="例如 盒、瓶、片" /></label><label>低库存阈值<input v-model.number="form.lowStockThreshold" type="number" min="0" max="2147483647" step="1" required /></label><label v-if="!drug">初始库存<input v-model.number="form.initialQuantity" type="number" min="0" max="2147483647" step="1" required /></label><label v-else class="pharmacy-check"><input v-model="form.active" type="checkbox" />启用此药品</label><p class="pharmacy-muted">低于阈值时预警，等于阈值不预警。单位和规格需与处方及实物一致。</p><p v-if="drug" class="pharmacy-alert">历史处方按名称和规格匹配目录。建议停用旧药后新建药品，避免改名或改规格使历史待发处方无法匹配。停用后保留库存及历史，不能发药。</p></fieldset><p v-if="message" role="alert" class="pharmacy-alert">{{ message }}</p><div class="pharmacy-actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并核实目录' : '返回' }}</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="uncertain">保存药品</ElButton></div></form>
  </ElDialog>
</template>
