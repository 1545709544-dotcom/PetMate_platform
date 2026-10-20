<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const list = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = ref({
  id: 0,
  name: '',
  type: 'reduction' as 'reduction' | 'discount' | 'category',
  threshold: '',
  amount: '',
  discount: '',
  validStart: '',
  validEnd: '',
  status: 1,
})

async function load() {
  loading.value = true
  try {
    list.value = (await adminApi.couponList()) as any[]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = {
    id: 0, name: '', type: 'reduction', threshold: '',
    amount: '', discount: '', validStart: '', validEnd: '', status: 1,
  }
  dialog.value = true
}

function openEdit(row: any) {
  form.value = {
    id: row.id,
    name: row.name,
    type: row.type,
    threshold: String(row.threshold),
    amount: String(row.amount),
    discount: row.discount != null ? String(row.discount) : '',
    validStart: (row.valid_start || '').replace('T', ' ').slice(0, 16),
    validEnd: (row.valid_end || '').replace('T', ' ').slice(0, 16),
    status: Number(row.status),
  }
  dialog.value = true
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请填写名称')
  if (!form.value.validStart || !form.value.validEnd) return ElMessage.warning('请填写有效期')
  try {
    const data: any = {
      name: form.value.name,
      type: form.value.type,
      threshold: Number(form.value.threshold || 0),
      amount: Number(form.value.amount || 0),
      validStart: form.value.validStart,
      validEnd: form.value.validEnd,
      status: form.value.status,
    }
    if (form.value.type === 'discount' && form.value.discount) data.discount = Number(form.value.discount)
    if (form.value.id) {
      await adminApi.updateCoupon(form.value.id, data)
      ElMessage.success('已更新')
    } else {
      await adminApi.createCoupon(data)
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await adminApi.deleteCoupon(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function typeLabel(t: string) {
  if (t === 'discount') return '折扣'
  if (t === 'category') return '品类券'
  return '满减'
}

onMounted(load)
</script>

<template>
  <div class="coupon-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">优惠券配置</h1>
        <p class="page-sub">发放满减 / 折扣券</p>
      </div>
      <button class="btn-primary" @click="openCreate">
        <span class="material-symbols-outlined">add</span>
        新建优惠券
      </button>
    </div>

    <section class="table-card" v-loading="loading">
      <div v-if="!list.length" class="empty-tip">暂无优惠券</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>门槛 / 优惠</th>
            <th>有效期</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td class="name">{{ r.name }}</td>
            <td>{{ typeLabel(r.type) }}</td>
            <td>
              <template v-if="r.type === 'discount'">
                {{ (Number(r.discount) * 10).toFixed(1) }} 折
              </template>
              <template v-else>
                满 ¥{{ Number(r.threshold).toFixed(0) }} 减 ¥{{ Number(r.amount).toFixed(0) }}
              </template>
            </td>
            <td class="mono">
              {{ (r.valid_start || '').replace('T', ' ').slice(0, 16) }}<br />
              <span class="sub">~ {{ (r.valid_end || '').replace('T', ' ').slice(0, 16) }}</span>
            </td>
            <td>
              <span class="status-pill" :class="r.status === 1 ? 'on' : 'off'">
                {{ r.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <div class="actions">
                <button class="link" @click="openEdit(r)">编辑</button>
                <button class="link danger" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <el-dialog v-model="dialog" :title="form.id ? '编辑优惠券' : '新建优惠券'" width="560px">
      <div class="form">
        <div class="field">
          <label class="field-label">券名称 <span class="required">*</span></label>
          <input v-model="form.name" class="input" maxlength="40" />
        </div>
        <div class="field">
          <label class="field-label">类型 <span class="required">*</span></label>
          <select v-model="form.type" class="input">
            <option value="reduction">满减券</option>
            <option value="discount">折扣券</option>
            <option value="category">品类券</option>
          </select>
        </div>
        <div class="field-row">
          <div class="field">
            <label class="field-label">门槛 (¥)</label>
            <input v-model="form.threshold" type="number" min="0" step="0.01" class="input" placeholder="0 表示无门槛" />
          </div>
          <div class="field" v-if="form.type === 'discount'">
            <label class="field-label">折扣 <span class="required">*</span></label>
            <input v-model="form.discount" type="number" min="0" max="1" step="0.01" class="input" placeholder="0.9 = 9 折" />
          </div>
          <div class="field" v-else>
            <label class="field-label">优惠金额 (¥) <span class="required">*</span></label>
            <input v-model="form.amount" type="number" min="0" step="0.01" class="input" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label class="field-label">生效时间 <span class="required">*</span></label>
            <input v-model="form.validStart" type="datetime-local" class="input" />
          </div>
          <div class="field">
            <label class="field-label">失效时间 <span class="required">*</span></label>
            <input v-model="form.validEnd" type="datetime-local" class="input" />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button class="link" @click="dialog = false">取消</button>
          <button class="btn-primary" @click="save">保存</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.coupon-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }
.btn-primary {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  background: $color-brand; color: #fff; border: none; border-radius: $radius-md; font-weight: 700; cursor: pointer;
}

.table-card { background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; overflow: hidden; }
.empty-tip { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left; padding: $spacing-md; font-size: $font-size-label-sm; color: $color-on-surface-variant; font-weight: 600;
  background: $color-surface-container-low; border-bottom: 1px solid $color-outline-variant;
}
.data-table td { padding: $spacing-md; border-bottom: 1px solid $color-outline-variant; font-size: $font-size-body-md; }
.name { font-weight: 700; color: $color-on-surface; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; line-height: 1.5; }
.sub { font-size: 11px; }

.status-pill { display: inline-block; padding: 2px $spacing-md; border-radius: $radius-full; font-size: $font-size-label-sm; font-weight: 700; }
.status-pill.on { background: rgba(82, 196, 26, 0.15); color: $color-success; }
.status-pill.off { background: $color-surface-container; color: $color-on-surface-variant; }

.actions { display: flex; gap: $spacing-sm; }
.link { background: none; border: none; color: $color-secondary; cursor: pointer; font-size: $font-size-label-sm; font-weight: 700; }
.link.danger { color: $color-emergency; }
.link:hover { text-decoration: underline; }

.form { display: flex; flex-direction: column; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-md; }
.field-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.required { color: $color-emergency; }
.input {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; font-family: inherit;
}
.dialog-actions { display: flex; justify-content: flex-end; gap: $spacing-sm; }
.dialog-actions .link { padding: $spacing-sm $spacing-lg; color: $color-on-surface-variant; }
</style>
