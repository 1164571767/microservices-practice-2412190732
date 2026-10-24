<script setup lang="ts">
import { reactive, computed, ref } from 'vue'
import { saveClient, savePet, archive } from './api'
import { useArchiveWrite } from './useArchiveWrite'
import { today, type Client, type Pet } from './model'
const props = defineProps<{ kind: 'client' | 'pet' | 'archive-client' | 'archive-pet'; client?: Client; pet?: Pet; ownerId?: string }>()
const emit = defineEmits<{ close: []; saved: [result: Client | Pet] }>()
const snapshotClient = props.client, snapshotPet = props.pet, snapshotOwnerId = props.ownerId
const { busy, message, uncertain, run } = useArchiveWrite()
const reason = ref('')
const form = reactive({ name: props.kind.includes('client') ? props.client?.name ?? '' : props.pet?.name ?? '', phone: props.client?.phone ?? '', address: props.client?.address ?? '', species: props.pet?.species ?? '', birthDate: props.pet?.birthDate ?? '', weightKg: props.pet?.weightKg ?? 1, birthDateEstimated: props.pet?.birthDateEstimated ?? false })
const archiving = props.kind.startsWith('archive-')
const title = computed(() => archiving ? `归档${props.kind === 'archive-client' ? '客户' : '宠物'}` : `${props.kind === 'client' ? props.client ? '编辑客户' : '新建客户' : props.pet ? '编辑宠物' : '新增宠物'}`)
function submit() {
  void run(async () => {
    if (props.kind === 'archive-client' && snapshotClient) return archive('client', snapshotClient.id, snapshotClient.version, reason.value.trim())
    if (props.kind === 'archive-pet' && snapshotPet) return archive('pet', snapshotPet.id, snapshotPet.version, reason.value.trim())
    if (props.kind === 'client') return saveClient({ name: form.name.trim(), phone: form.phone.trim(), address: form.address.trim() }, snapshotClient?.id, snapshotClient?.version)
    return savePet({ name: form.name.trim(), species: form.species.trim(), birthDate: form.birthDate, weightKg: Number(form.weightKg), birthDateEstimated: form.birthDateEstimated }, snapshotOwnerId ?? snapshotPet?.ownerId ?? '', snapshotPet?.id, snapshotPet?.version)
  }, (result) => emit('saved', result))
}
</script>

<template>
  <ElDialog :model-value="true" :title="title" width="min(560px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <form class="archive-form" @submit.prevent="submit">
      <p v-if="archiving" class="archive-note">归档后保留全部历史，禁止新增预约和健康资料。本轮不提供恢复操作。</p>
      <fieldset :disabled="busy || uncertain">
        <label v-if="archiving">归档原因<textarea v-model="reason" required maxlength="255" rows="3" /></label>
        <template v-else>
          <label>{{ kind === 'client' ? '客户姓名' : '宠物名字' }}<input v-model="form.name" required :maxlength="kind === 'client' ? 100 : 50" pattern=".*\S.*" autofocus /></label>
          <template v-if="kind === 'client'">
            <label>联系电话<input v-model="form.phone" required inputmode="tel" pattern="\+?[0-9]{6,20}" maxlength="21" /></label>
            <small>允许同号客户，请核对姓名，避免重复建档。</small>
            <label>联系地址（选填）<textarea v-model="form.address" maxlength="255" rows="2" /></label>
          </template>
          <template v-else>
            <label>物种<input v-model="form.species" required maxlength="50" pattern=".*\S.*" placeholder="如：猫、犬、兔" /></label>
            <div class="archive-form__row"><label>出生日期<input v-model="form.birthDate" type="date" required :max="today()" /></label><label>体重（kg）<input v-model="form.weightKg" type="number" required min="0.01" max="500" step="0.01" /></label></div>
            <label class="archive-check"><input v-model="form.birthDateEstimated" type="checkbox" />出生日期为估算</label>
            <small>不知道准确生日时填写估算日期，并勾选标记；年龄会显示“约”。</small>
          </template>
        </template>
      </fieldset>
      <p v-if="message" class="archive-feedback" role="alert" data-testid="archive-write-error">{{ message }}</p>
      <div class="archive-form__actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并核实' : '取消' }}</ElButton><ElButton native-type="submit" :type="archiving ? 'danger' : 'primary'" :loading="busy" :disabled="uncertain">{{ archiving ? '确认归档' : '保存档案' }}</ElButton></div>
    </form>
  </ElDialog>
</template>
