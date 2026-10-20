<script setup lang="ts">
/**
 * 退货 / 换货物流回填弹窗。
 *
 * 这是原先整条售后链路里断掉的一环：商家同意退货后置为 returning，
 * 但用户端没有任何入口回填物流，returned 状态永远没人赋值，
 * 导致商家「确认收货」按钮形同虚设。这里把这一环补上。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { aftersaleApi, LOGISTICS_COMPANIES, type Aftersale } from '@/api/modules/aftersale'

const props = defineProps<{
  modelValue: boolean
  refund: Aftersale | null
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const form = ref({ logisticsCompany: '', logisticsNo: '' })
const submitting = ref(false)

watch(visible, (v) => {
  if (v) form.value = { logisticsCompany: '', logisticsNo: '' }
})

const isExchange = computed(() => props.refund?.refund_type === 'EXCHANGE')

async function submit() {
  if (!props.refund) return
  if (!form.value.logisticsCompany) {
    ElMessage.warning('请选择物流公司')
    return
  }
  const no = form.value.logisticsNo.trim()
  if (!no) {
    ElMessage.warning('请填写物流单号')
    return
  }
  submitting.value = true
  try {
    await aftersaleApi.fillLogistics(props.refund.id, {
      logisticsCompany: form.value.logisticsCompany,
      logisticsNo: no,
    })
    ElMessage.success('物流信息已提交，等待商家收货')
    visible.value = false
    emit('success')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isExchange ? '填写换货寄回物流' : '填写退货物流'"
    width="480px"
    :close-on-click-modal="false"
  >
    <div v-if="refund" class="fill-body">
      <!-- 退货地址：商家同意时填写的，用户照着寄 -->
      <div class="address-box">
        <div class="box-title">
          <span class="material-symbols-outlined">location_on</span>
          退货收件地址
        </div>
        <p v-if="refund.return_address" class="address-text">{{ refund.return_address }}</p>
        <p v-else class="address-text muted">
          商家未填写退货地址，请先通过订单联系商家确认后再寄出
        </p>
      </div>

      <div class="tip-row">
        <span class="material-symbols-outlined">priority_high</span>
        <span>请务必使用可查询的快递方式寄出，并保留寄件凭证。到付件商家可能拒收。</span>
      </div>

      <div class="field">
        <label class="field-label">物流公司 <i>*</i></label>
        <el-select v-model="form.logisticsCompany" placeholder="请选择物流公司" style="width: 100%">
          <el-option v-for="c in LOGISTICS_COMPANIES" :key="c" :label="c" :value="c" />
        </el-select>
      </div>

      <div class="field">
        <label class="field-label">物流单号 <i>*</i></label>
        <el-input
          v-model="form.logisticsNo"
          placeholder="请输入快递单号"
          maxlength="40"
          clearable
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.fill-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.address-box {
  padding: 12px 14px;
  border-radius: 12px;
  background: $color-surface-variant;
}

.box-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: 6px;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }
}

.address-text {
  margin: 0;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: 1.6;

  &.muted {
    color: $color-danger;
    font-size: $font-size-label-md;
  }
}

.tip-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 218, 214, 0.4);
  font-size: $font-size-label-md;
  color: $color-danger;
  line-height: 1.5;

  .material-symbols-outlined {
    font-size: 18px;
    flex-shrink: 0;
  }
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;

  i {
    color: $color-emergency;
    font-style: normal;
  }
}
</style>
