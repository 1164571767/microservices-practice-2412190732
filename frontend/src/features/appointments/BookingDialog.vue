<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { appointmentKey, availability, book, reschedule } from './api'
import { clinicDate, bookingDateRange, isBookingDate, slotLabel, isUncertain, type Booking, type Doctor, type ReceptionEntry } from './model'
import { listPets, getPet, getClient, clientKey } from '../clients/api'
import type { PetDirectory } from '../clients/model'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'

const props = defineProps<{ doctors: Doctor[]; entry?: ReceptionEntry; initialPetId?: string }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const date = ref(props.entry ? clinicDate(new Date(props.entry.appointment.startAt)) : clinicDate())
const doctorId = ref(props.entry?.appointment.doctorId ?? '')
const pet = ref<PetDirectory>(), search = ref(''), searchText = ref(''), petPage = ref(0)
const selection = ref(''), busy = ref(false), uncertain = ref(false), message = ref('')
let snapshot: Booking | undefined
const key = crypto.randomUUID()
const range = bookingDateRange()
const searchQuery = useQuery({ queryKey: [...clientKey, 'booking-pets', searchText, petPage], queryFn: () => listPets(new URLSearchParams({ query: searchText.value, archived: 'false', page: String(petPage.value), size: '5' })), enabled: computed(() => !props.entry) })
const presetQuery = useQuery({ queryKey: [...clientKey, 'booking-preset', props.initialPetId], queryFn: async () => { const p = await getPet(props.initialPetId!); const c = await getClient(p.ownerId); return { pet: p, ownerName: c.name, ownerPhone: c.phone, ownerArchived: c.archived } }, enabled: !!props.initialPetId && !props.entry })
watch(presetQuery.data, value => { if (value && !pet.value) pet.value = value }, { immediate: true })
const slotQuery = useQuery({ queryKey: [...appointmentKey, 'availability', doctorId, date], queryFn: () => availability(doctorId.value, date.value), enabled: computed(() => !!doctorId.value && isBookingDate(date.value)) })
watch([doctorId, date], () => { selection.value = ''; message.value = '' })
watch(slotQuery.data, () => { if (!slotQuery.data.value?.some(s => s.startAt === selection.value)) selection.value = '' })
const slots = computed(() => slotQuery.data.value?.filter(s => Date.parse(s.startAt) > Date.now()) ?? [])
const selectedSlot = computed(() => slots.value.find(s => s.startAt === selection.value))
const activePet = computed(() => props.entry ? !!props.entry.patient && !props.entry.patient.petArchived && !props.entry.patient.ownerArchived : !!pet.value && !pet.value.pet.archived && !pet.value.ownerArchived)
const canSubmit = computed(() => isBookingDate(date.value) && !!selectedSlot.value && activePet.value && !slotQuery.isFetching.value && !slotQuery.isError.value)
function findPets() { const unchanged = searchText.value === search.value.trim() && petPage.value === 0; searchText.value = search.value.trim(); petPage.value = 0; if (unchanged) void searchQuery.refetch() }
async function submit() {
  if (busy.value || (uncertain.value && props.entry) || (!uncertain.value && !canSubmit.value)) return
  if (!uncertain.value) snapshot = { petId: props.entry?.appointment.petId ?? pet.value!.pet.id, doctorId: doctorId.value, ...selectedSlot.value! }
  busy.value = true; message.value = ''
  try {
    if (props.entry) await reschedule(props.entry.appointment.id, snapshot!)
    else await book(snapshot!, key)
    void queryClient.invalidateQueries({ queryKey: appointmentKey })
    emit('saved')
  } catch (error) {
    uncertain.value = isUncertain(error)
    message.value = uncertain.value ? props.entry ? '改期结果未确认。请关闭后刷新预约核实，勿直接重复改期；输入已保留。' : '预约结果未确认，输入已保留。可使用原请求安全核实并重试，或关闭后查询预约记录。' : errorMessage(error)
    if (!uncertain.value) void slotQuery.refetch()
  } finally { busy.value = false }
}
</script>

<template>
  <ElDialog :model-value="true" :title="entry ? '预约改期' : '新建预约'" top="5vh" width="min(700px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <form class="archive-form booking-form" @submit.prevent="submit">
      <p class="archive-note">{{ entry ? '仅更换同一医生的可预约时段；如需换医生，请取消后重新预约。' : '选择已有宠物档案与医生号源。未绑定主人账号也可正常预约。' }}</p>
      <fieldset :disabled="busy || uncertain">
        <template v-if="!entry">
          <div class="booking-search"><label>检索宠物或客户<input v-model="search" maxlength="100" placeholder="宠物名字、客户姓名或联系电话" @keydown.enter.prevent="findPets" /></label><ElButton @click="findPets">查找档案</ElButton></div>
          <p v-if="presetQuery.isError.value" class="archive-feedback" role="alert">预选宠物读取失败，可通过检索重新选择。<button type="button" class="text-button" @click="presetQuery.refetch()">重试</button></p>
          <AppState v-if="searchQuery.isPending.value" kind="loading" title="正在检索宠物" />
          <AppState v-else-if="searchQuery.isError.value" kind="error" title="宠物检索失败" :description="errorMessage(searchQuery.error.value)" action-label="重新读取" @action="searchQuery.refetch()" />
          <p v-else-if="!searchQuery.data.value?.items.length" class="archive-muted">没有找到正常档案，请调整检索条件，或先到客户与宠物模块建档。</p>
          <div v-else class="booking-pets" role="group" aria-label="选择预约宠物"><button v-for="item in searchQuery.data.value.items" :key="item.pet.id" type="button" :aria-pressed="pet?.pet.id === item.pet.id" :disabled="item.pet.archived || item.ownerArchived" @click="pet = item"><strong>{{ item.pet.name }} · {{ item.pet.species }}</strong><small>{{ item.ownerName }} · {{ item.ownerPhone }}</small></button></div>
          <div v-if="(searchQuery.data.value?.totalPages ?? 0) > 1" class="booking-pages"><ElButton :disabled="petPage === 0" @click="petPage--">前一页宠物</ElButton><span>{{ petPage + 1 }} / {{ searchQuery.data.value?.totalPages }}</span><ElButton :disabled="petPage + 1 >= (searchQuery.data.value?.totalPages ?? 1)" @click="petPage++">后一页宠物</ElButton></div>
          <p v-if="pet" class="booking-selected" role="status">已选：{{ pet.pet.name }} / {{ pet.ownerName }} · {{ pet.ownerPhone }}<span v-if="!activePet"> · 档案已归档，无法预约</span></p>
        </template>
        <p v-else class="booking-selected">{{ entry.patient?.petName || '宠物资料不可用' }} / {{ entry.patient?.ownerName }} · {{ entry.doctorName }}</p>
        <div class="archive-form__row"><label>接诊医生<select v-model="doctorId" aria-label="接诊医生" required :disabled="!!entry"><option value="" disabled>请选择医生</option><option v-if="entry && !doctors.some(d => d.id === doctorId)" :value="doctorId">{{ entry.doctorName }}</option><option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }}</option></select></label><label>就诊日期（上海时间）<input v-model="date" type="date" required :min="range.min" :max="range.max" /></label></div>
        <p class="archive-note">可预约 {{ range.min }} 至 {{ range.max }}（含当日），仅显示已排班且尚未开始的号源。</p>
        <div class="booking-slot-heading"><strong>选择可预约时段</strong><button type="button" class="text-button" :disabled="!doctorId || slotQuery.isFetching.value" @click="slotQuery.refetch()">刷新号源</button></div>
        <p v-if="!isBookingDate(date)" role="alert" class="archive-feedback">请选择一个月内的有效日期。</p>
        <p v-else-if="!doctorId" class="archive-muted">选择医生与日期后显示号源。</p>
        <AppState v-else-if="slotQuery.isPending.value" kind="loading" title="正在读取号源" />
        <AppState v-else-if="slotQuery.isError.value" kind="error" title="号源读取失败" :description="errorMessage(slotQuery.error.value)" action-label="重新读取" @action="slotQuery.refetch()" />
        <p v-else-if="!slots.length" class="archive-muted">当天暂无可预约时段，请更换日期或联系排班管理员。</p>
        <div v-else class="booking-slots" role="group" aria-label="可预约时段"><button v-for="slot in slots" :key="slot.startAt" type="button" :aria-pressed="selection === slot.startAt" @click="selection = slot.startAt">{{ slotLabel(slot) }}</button></div>
      </fieldset>
      <p v-if="message" class="archive-feedback" role="alert">{{ message }}</p>
      <div class="archive-form__actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并查询预约' : '返回' }}</ElButton><ElButton native-type="submit" type="primary" :loading="busy" :disabled="uncertain ? !!entry : !canSubmit">{{ uncertain && !entry ? '使用原请求安全重试' : entry ? '确认改期' : '确认预约' }}</ElButton></div>
    </form>
  </ElDialog>
</template>
