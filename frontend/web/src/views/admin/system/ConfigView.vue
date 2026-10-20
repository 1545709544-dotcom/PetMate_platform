<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

interface ConfigRow {
  config_key: string
  config_value: string
  updated_at: string
}

const loading = ref(false)
const saving = ref(false)
const items = ref<ConfigRow[]>([])

async function load() {
  loading.value = true
  try {
    items.value = await adminApi.systemConfigList()
  } catch (e: any) {
    ElMessage.error(e?.message || '系统参数加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const map: Record<string, string> = {}
    items.value.forEach((it) => {
      map[it.config_key] = it.config_value
    })
    await adminApi.updateSystemConfig(map)
    ElMessage.success('系统参数已保存并全局生效')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function isJson(v: string) {
  const t = (v || '').trim()
  return t.startsWith('{') || t.startsWith('[')
}

onMounted(load)
</script>

<template>
  <div class="config-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">系统参数</h1>
        <p class="page-sub">维护平台全局参数（站点名称、默认运费、短信模板、AI 问诊配额、风控开关等）。修改后即时全局生效，无需重启。</p>
      </div>
      <button class="btn-primary" :disabled="saving" @click="save">
        <span class="material-symbols-outlined">save</span>
        {{ saving ? '保存中...' : '保存全部' }}
      </button>
    </div>

    <section class="config-card">
      <div v-if="items.length === 0 && !loading" class="empty">暂无系统参数</div>
      <div v-for="item in items" :key="item.config_key" class="config-row">
        <div class="cfg-meta">
          <code class="cfg-key">{{ item.config_key }}</code>
          <span class="cfg-updated">更新于 {{ item.updated_at || '—' }}</span>
        </div>
        <textarea
          v-model="item.config_value"
          class="cfg-value"
          :class="{ 'is-json': isJson(item.config_value) }"
          rows="2"
        ></textarea>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.config-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: $spacing-md;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  max-width: 720px;
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-md;
  font-weight: 700;
  cursor: pointer;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  .material-symbols-outlined { font-size: 18px; }
}
.config-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.config-row {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: $spacing-md;
  align-items: start;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
  &:last-child { border-bottom: none; padding-bottom: 0; }
}
.cfg-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cfg-key {
  font-family: $font-family-mono;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  background: $color-surface-container-low;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  word-break: break-all;
}
.cfg-updated {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.cfg-value {
  width: 100%;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  font-size: $font-size-body-md;
  font-family: $font-family-mono;
  background: #ffffff;
  color: $color-on-surface;
  outline: none;
  resize: vertical;
  line-height: 1.5;
  &:focus { border-color: $color-brand; }
  &.is-json { background: #fbfdff; }
}
.empty {
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  text-align: center;
  padding: $spacing-lg 0;
}
</style>
