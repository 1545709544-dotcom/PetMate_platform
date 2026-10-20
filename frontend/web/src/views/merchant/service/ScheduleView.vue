<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const DAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

const schedule = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await merchantApi.getSchedule()
    schedule.value = { ...r.schedule }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载排班失败')
    schedule.value = {}
  } finally {
    loading.value = false
  }
}

function toggleDay(key: string) {
  if (schedule.value[key]) {
    delete schedule.value[key]
  } else {
    schedule.value[key] = '09:00-18:00'
  }
  schedule.value = { ...schedule.value }
}

function updateRange(key: string, value: string) {
  schedule.value[key] = value
}

async function save() {
  saving.value = true
  try {
    await merchantApi.saveSchedule(schedule.value)
    ElMessage.success('已保存排班')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="schedule-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">排班管理</h1>
        <p class="page-sub">设置可预约的服务时段</p>
      </div>
      <button class="btn-primary" :disabled="saving" @click="save">
        <span class="material-symbols-outlined">save</span>
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </div>

    <section class="card">
      <div class="day-list">
        <div v-for="(d, i) in DAYS" :key="d" class="day-row" :class="{ on: !!schedule[DAY_KEYS[i]] }">
          <div class="day-head">
            <label class="day-check">
              <input type="checkbox" :checked="!!schedule[DAY_KEYS[i]]" @change="toggleDay(DAY_KEYS[i])" />
              <span class="day-name">{{ d }}</span>
            </label>
          </div>
          <div class="day-body" v-if="schedule[DAY_KEYS[i]]">
            <input
              class="time-input"
              :value="schedule[DAY_KEYS[i]]"
              @input="updateRange(DAY_KEYS[i], ($event.target as HTMLInputElement).value)"
              placeholder="09:00-18:00"
            />
            <span class="hint">格式: HH:MM-HH:MM，可写多段（逗号分隔）</span>
          </div>
          <div class="day-body off" v-else>休息</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.schedule-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }
.btn-primary {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  background: $color-brand; color: #fff; border: none; border-radius: $radius-md; font-weight: 700; cursor: pointer;
  &:disabled { opacity: 0.6; }
}

.card { padding: $spacing-lg; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }

.day-list { display: flex; flex-direction: column; gap: $spacing-sm; }
.day-row { display: grid; grid-template-columns: 120px 1fr; align-items: center; gap: $spacing-md; padding: $spacing-md; background: $color-surface-container-low; border-radius: $radius-default;
  &.on { background: rgba(255, 122, 50, 0.08); border-left: 4px solid $color-brand; }
}

.day-check { display: flex; align-items: center; gap: $spacing-xs; cursor: pointer; }
.day-name { font-weight: 700; color: $color-on-surface; }
.day-body { display: flex; align-items: center; gap: $spacing-sm; }
.day-body.off { color: $color-on-surface-variant; font-size: $font-size-label-md; }
.time-input { flex: 1; max-width: 240px; padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default; background: #fff; font-family: $font-family-mono; font-size: $font-size-body-md; }
.hint { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
</style>
