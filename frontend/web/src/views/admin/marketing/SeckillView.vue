<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const list = ref<any[]>([])
const products = ref<Array<{ id: number; name: string }>>([])
const loading = ref(false)
const dialog = ref(false)
const form = ref({
  id: 0,
  productId: null as number | null,
  title: '',
  seckillPrice: '',
  stock: '',
  startTime: '',
  endTime: '',
  status: 1,
})

async function load() {
  loading.value = true
  try {
    list.value = (await adminApi.seckillList()) as any[]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  try {
    // 改用管理端商品列表接口（真实数据，覆盖全量在售商品），不再用 search 兜底
    const res = await adminApi.productList({ page: 1, size: 200 })
    products.value = (res.list || []).map((p: any) => ({ id: p.id, name: p.name }))
  } catch {
    products.value = []
  }
}

function openCreate() {
  form.value = {
    id: 0, productId: null, title: '', seckillPrice: '',
    stock: '', startTime: '', endTime: '', status: 1,
  }
  dialog.value = true
}

function openEdit(row: any) {
  form.value = {
    id: row.id,
    productId: row.product_id,
    title: row.title,
    seckillPrice: String(row.seckill_price),
    stock: String(row.stock),
    startTime: (row.start_time || '').replace('T', ' ').slice(0, 16),
    endTime: (row.end_time || '').replace('T', ' ').slice(0, 16),
    status: Number(row.status),
  }
  dialog.value = true
}

async function save() {
  if (!form.value.productId) return ElMessage.warning('请选择商品')
  if (!form.value.title.trim()) return ElMessage.warning('请填写活动标题')
  if (!form.value.seckillPrice || Number(form.value.seckillPrice) <= 0) return ElMessage.warning('请填写有效秒杀价')
  if (!form.value.stock || Number(form.value.stock) <= 0) return ElMessage.warning('请填写有效库存')
  if (!form.value.startTime || !form.value.endTime) return ElMessage.warning('请填写活动时间')
  try {
    if (form.value.id) {
      await adminApi.updateSeckill(form.value.id, {
        title: form.value.title,
        seckillPrice: Number(form.value.seckillPrice),
        stock: Number(form.value.stock),
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        status: form.value.status,
      })
      ElMessage.success('已更新')
    } else {
      await adminApi.createSeckill({
        productId: form.value.productId,
        title: form.value.title,
        seckillPrice: Number(form.value.seckillPrice),
        stock: Number(form.value.stock),
        startTime: form.value.startTime,
        endTime: form.value.endTime,
      })
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function toggle(row: any) {
  try {
    await adminApi.toggleSeckillStatus(row.id)
    ElMessage.success('已切换')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除活动「${row.title}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await adminApi.deleteSeckill(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function statusLabel(s: number) {
  return s === 1 ? '启用' : '下架'
}

onMounted(() => { load(); loadProducts() })
</script>

<template>
  <div class="seckill-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">秒杀配置</h1>
        <p class="page-sub">限时秒杀活动管理</p>
      </div>
      <button class="btn-primary" @click="openCreate">
        <span class="material-symbols-outlined">add</span>
        新建秒杀
      </button>
    </div>

    <section class="table-card" v-loading="loading">
      <div v-if="!list.length" class="empty-tip">暂无秒杀活动</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>活动</th>
            <th>商品</th>
            <th>秒杀价</th>
            <th>库存/已售</th>
            <th>时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td class="name">{{ r.title }}</td>
            <td>{{ r.product_name }}</td>
            <td class="amount">¥{{ Number(r.seckill_price).toFixed(2) }}</td>
            <td>{{ r.stock }} / {{ r.sold }}</td>
            <td class="mono">
              {{ (r.start_time || '').replace('T', ' ').slice(0, 16) }}<br />
              <span class="sub">~ {{ (r.end_time || '').replace('T', ' ').slice(0, 16) }}</span>
            </td>
            <td>
              <span class="status-pill" :class="r.status === 1 ? 'on' : 'off'">
                {{ statusLabel(r.status) }}
              </span>
            </td>
            <td>
              <div class="actions">
                <button class="link" @click="openEdit(r)">编辑</button>
                <button class="link" @click="toggle(r)">
                  {{ r.status === 1 ? '下架' : '上架' }}
                </button>
                <button class="link danger" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <el-dialog v-model="dialog" :title="form.id ? '编辑秒杀活动' : '新建秒杀活动'" width="560px">
      <div class="form">
        <div class="field">
          <label class="field-label">商品 <span class="required">*</span></label>
          <select v-model="form.productId" class="input" :disabled="!!form.id">
            <option :value="null">请选择商品</option>
            <option v-for="p in products" :key="p.id" :value="p.id">{{ p.name }} (#{{ p.id }})</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">活动标题 <span class="required">*</span></label>
          <input v-model="form.title" class="input" placeholder="限时秒杀：xxx 5 折抢" maxlength="60" />
        </div>
        <div class="field-row">
          <div class="field">
            <label class="field-label">秒杀价 (¥) <span class="required">*</span></label>
            <input v-model="form.seckillPrice" type="number" min="0" step="0.01" class="input" />
          </div>
          <div class="field">
            <label class="field-label">库存 <span class="required">*</span></label>
            <input v-model="form.stock" type="number" min="1" class="input" />
          </div>
        </div>
        <div class="field-row">
          <div class="field">
            <label class="field-label">开始时间 <span class="required">*</span></label>
            <input v-model="form.startTime" type="datetime-local" class="input" />
          </div>
          <div class="field">
            <label class="field-label">结束时间 <span class="required">*</span></label>
            <input v-model="form.endTime" type="datetime-local" class="input" />
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
.seckill-view { display: flex; flex-direction: column; gap: $spacing-md; }
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
.amount { font-weight: 700; color: $color-brand; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; line-height: 1.5; }
.sub { color: $color-on-surface-variant; font-size: 11px; }

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
  &:disabled { background: $color-surface-container; }
}
.dialog-actions { display: flex; justify-content: flex-end; gap: $spacing-sm; }
.dialog-actions .link { padding: $spacing-sm $spacing-lg; color: $color-on-surface-variant; }
</style>
