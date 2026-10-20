<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type Address } from '@/api/modules/user'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// ============ 状态 ============
const addresses = ref<Address[]>([])
const loading = ref(false)
const deletingId = ref<number | null>(null)

// ============ 标签显示 ============
function tagLabel(tag?: Address['tag']) {
  if (!tag) return ''
  if (tag === 'home') return '家'
  if (tag === 'company') return '公司'
  if (tag === 'school') return '学校'
  return ''
}

// ============ 加载地址列表 ============
async function loadAddresses() {
  loading.value = true
  try {
    const list = await userApi.addresses()
    addresses.value = list || []
  } catch (e: any) {
    ElMessage.error(e?.message || '地址加载失败')
    addresses.value = []
  } finally {
    loading.value = false
  }
}
onMounted(loadAddresses)

// ============ 设为默认 ============
async function setDefault(id: number) {
  try {
    await userApi.setDefaultAddress(id)
    ElMessage.success('已设为默认地址')
    loadAddresses()
  } catch (e: any) {
    ElMessage.error(e?.message || '设置失败')
  }
}

// ============ 删除地址 ============
async function deleteAddress(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该收货地址？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    deletingId.value = id
    await userApi.deleteAddress(id)
    ElMessage.success('已删除')
    loadAddresses()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    deletingId.value = null
  }
}

// ============ 跳转编辑/新增 ============
function goEdit(id: number) {
  router.push(`/user/address/edit/${id}`)
}
function goAdd() {
  router.push('/user/address/edit')
}
</script>

<template>
  <div class="address-view">
    <div class="address-container">
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">收货地址</span>
      </nav>

      <div class="page-head">
        <div>
          <h1 class="page-title">收货地址</h1>
          <p class="page-subtitle">管理你的收货信息，下单更省心</p>
        </div>
        <button class="action-btn primary" @click="goAdd">
          <span class="material-symbols-outlined">add</span>
          新增地址
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 空状态 -->
      <PetEmptyState
        v-else-if="addresses.length === 0"
        icon="location_off"
        title="暂无收货地址"
        desc="添加第一个地址，让包裹准确送达"
      >
        <template #action>
          <button class="retry-btn" @click="goAdd">
            <span class="material-symbols-outlined">add</span>
            新增收货地址
          </button>
        </template>
      </PetEmptyState>

      <!-- 地址列表（响应式双栏网格） -->
      <div v-else class="address-grid">
        <div
          v-for="addr in addresses"
          :key="addr.id"
          class="address-card pet-card"
          :class="{ default: addr.isDefault, deleting: deletingId === addr.id }"
        >
          <div class="card-main">
            <div class="addr-header">
              <span class="addr-name">{{ addr.receiver }}</span>
              <span class="addr-phone">{{ addr.phone }}</span>
              <span v-if="addr.tag" class="addr-tag">{{ tagLabel(addr.tag) }}</span>
              <span v-if="addr.isDefault" class="default-badge">默认</span>
            </div>
            <p class="addr-detail">
              {{ [addr.province, addr.city, addr.district].filter(Boolean).join(' ') }} {{ addr.detail }}
            </p>
          </div>
          <div class="card-actions">
            <button
              v-if="!addr.isDefault"
              class="action-btn primary"
              @click.stop="setDefault(addr.id)"
            >
              <span class="material-symbols-outlined">check_circle</span>
              设为默认
            </button>
            <button class="action-btn outline" @click.stop="goEdit(addr.id)">
              <span class="material-symbols-outlined">edit</span>
              编辑
            </button>
            <button
              class="action-btn danger"
              @click.stop="deleteAddress(addr.id)"
              :disabled="deletingId === addr.id"
            >
              <span v-if="deletingId !== addr.id" class="material-symbols-outlined">delete</span>
              <span v-else class="material-symbols-outlined spinning">refresh</span>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.address-view {
  min-height: 100vh;
  background: $color-background;
}

.address-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

// ============ 面包屑 + 标题（与我的订单一致） ============
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-brand;
    font-weight: 600;
  }
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
  margin-bottom: $spacing-lg;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  line-height: 1.2;
}

.page-subtitle {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

// ============ 操作按钮（与我的订单一致：圆角 pill） ============
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;
  border: 1px solid transparent;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &.primary {
    background: $color-brand;
    border-color: $color-brand;
    color: #ffffff;

    &:hover:not(:disabled) {
      background: #ff9461;
      border-color: #ff9461;
    }
  }

  &.outline {
    background: transparent;
    border-color: $color-outline-variant;
    color: $color-on-surface;

    &:hover:not(:disabled) {
      border-color: $color-brand;
      color: $color-brand;
    }
  }

  &.danger {
    background: transparent;
    border-color: rgba($color-danger, 0.4);
    color: $color-danger;

    &:hover:not(:disabled) {
      background: rgba($color-danger, 0.1);
    }
  }
}

// ============ 空状态（与我的订单一致） ============
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  text-align: center;
}

.state-icon-wrap {
  width: 84px;
  height: 84px;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand-container;
  color: $color-brand;
  margin-bottom: $spacing-xs;

  .material-symbols-outlined {
    font-size: 56px;
    opacity: 0.6;
  }
}

.state-title {
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.state-sub {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  max-width: 280px;
  line-height: 1.5;
}

.retry-btn {
  margin-top: $spacing-sm;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-xl;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  }

  &:active {
    transform: scale(0.96);
  }
}

// ============ 地址网格 ============
.address-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(2, 1fr);
  }
}

.address-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.3);
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  overflow: hidden;
  transition: box-shadow $transition-base, border-color $transition-base, transform $transition-base;

  &.default {
    border-color: $color-brand;
    box-shadow: 0 0 0 2px rgba(255, 122, 50, 0.12), $shadow-sm;
  }

  &.deleting {
    opacity: 0.6;
    pointer-events: none;
  }

  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-2px);
  }
}

.card-main {
  flex: 1;
  padding: $spacing-lg;
}

.addr-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;
}

.addr-name {
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.addr-phone {
  font-size: $font-size-body-md;
  color: $color-on-surface;
}

.addr-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px $spacing-xs;
  background: $color-brand-container;
  color: $color-on-brand-container;
  font-size: $font-size-label-sm;
  font-weight: 600;
  border-radius: $radius-sm;
}

.default-badge {
  margin-left: auto;
  padding: 1px $spacing-xs;
  background: $color-brand;
  color: $color-on-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;
  border-radius: $radius-sm;
}

.addr-detail {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: $line-height-normal;
}

// ============ 卡片操作栏 ============
.card-actions {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid rgba(223, 192, 179, 0.15);
  background: $color-surface-container-low;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// ============ 加载状态 ============
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-3xl;
  min-height: 40vh;

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid $color-outline-variant;
    border-top-color: $color-brand;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: $spacing-md;
  }

  p {
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
  }
}
@include mobile {
  .address-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }
  .breadcrumb {
    margin-bottom: 0.75rem;
  }
  .page-head {
    margin-bottom: 1rem;
  }
  .page-title {
    font-size: 1.5rem;
  }
  .address-grid {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }
  .card-main {
    padding: 0.75rem;
  }
  .card-actions {
    padding: 0.75rem;
    gap: 0.5rem;
  }
  .action-btn {
    flex: 1;
  }
  .state-block {
    padding: 1.5rem 1rem;
  }
  .state-icon-wrap {
    width: 5rem;
    height: 5rem;
  }
  .state-sub {
    max-width: 100%;
  }
  .loading-state {
    padding: 1.5rem;
  }
}

</style>
