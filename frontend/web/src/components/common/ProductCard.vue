<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useCartStore } from '@/stores/cart'

const props = withDefaults(
  defineProps<{
    id: number
    cover: string
    name: string
    price: number
    saleCount?: number
    skuId?: number
    /** 左上角标，如 HOT / NEW；为空则不展示 */
    cornerTag?: string
    cornerTagType?: 'red' | 'blue'
    /** 是否显示右下角加购按钮 */
    showCart?: boolean
  }>(),
  {
    saleCount: 0,
    skuId: undefined,
    cornerTag: '',
    cornerTagType: 'red',
    showCart: true,
  },
)

const router = useRouter()
const cartStore = useCartStore()
const addingIds = ref<Set<number>>(new Set())

// 从商品名称尾部解析规格（如「猫粮 5kg」→ 5kg）
const spec = computed(() => {
  const m = props.name.match(/(\d+\s*(?:ml|mL|ML|g|G|kg|Kg|KG|L|l|斤|只|支|瓶|盒|袋|罐|包|片|粒))$/i)
  return m ? m[1] : ''
})
const adding = computed(() => addingIds.value.has(props.id))

function goDetail() {
  router.push(`/product/${props.id}`)
}

async function onAdd(evt: Event) {
  evt.stopPropagation()
  const skuId = props.skuId
  if (!skuId) {
    ElMessage.warning('该商品暂无可选规格，请到详情页选择')
    return
  }
  if (addingIds.value.has(props.id)) return
  addingIds.value.add(props.id)
  try {
    await cartStore.add(skuId, 1, { productId: props.id })
    await cartStore.fetch()
    ElMessage.success('已加入购物车')
  } catch {
    ElMessage.error('加购失败，请稍后重试')
  } finally {
    addingIds.value.delete(props.id)
  }
}
</script>

<template>
  <article class="product-card pet-card" @click="goDetail">
    <div class="prod-cover">
      <img :src="cover" :alt="name" />
      <span v-if="cornerTag" class="prod-tag" :class="cornerTagType">{{ cornerTag }}</span>
      <button
        v-if="showCart"
        class="prod-cart"
        :class="{ loading: adding }"
        :disabled="adding || !skuId"
        :title="skuId ? '加入购物车' : '请到详情页选择规格'"
        @click="onAdd"
      >
        <span class="material-symbols-outlined" v-if="!adding">shopping_cart</span>
        <span class="material-symbols-outlined spinning" v-else>progress_activity</span>
      </button>
    </div>
    <div class="prod-body">
      <h4 class="prod-name">{{ name }}</h4>
      <p v-if="spec" class="prod-spec">{{ spec }}</p>
      <div class="prod-services">
        <span class="service-tag">正品保障</span>
        <span class="service-tag">闪电发货</span>
      </div>
      <div class="prod-price-row">
        <div class="prod-price">
          <span class="yen">¥</span>
          <span class="amount">{{ Number(price).toFixed(2) }}</span>
        </div>
        <span class="prod-sold">已售 {{ saleCount ?? 0 }}</span>
      </div>
    </div>
  </article>
</template>

<style lang="scss" scoped>
.product-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $color-surface;
  border-radius: $radius-xl;
  padding: $spacing-sm;
  box-shadow: $shadow-sm;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08);

    .prod-cover img {
      transform: scale(1.04);
    }
  }
}

.prod-cover {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-surface-container;
  margin-bottom: $spacing-sm;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.4s ease;
  }
}

.prod-tag {
  position: absolute;
  top: $spacing-sm;
  left: $spacing-sm;
  padding: 3px 6px;
  border-radius: $radius-sm;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;

  &.red {
    background: $color-emergency;
  }

  &.blue {
    background: $color-secondary;
  }
}

.prod-cart {
  position: absolute;
  bottom: $spacing-sm;
  right: $spacing-sm;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: #ffffff;
  border: none;
  border-radius: $radius-full;
  box-shadow: $shadow-md;
  cursor: pointer;
  transition:
    transform $transition-fast,
    background $transition-fast,
    opacity $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    transform: scale(1.1);
    background: $color-brand-dark;
  }

  &:disabled {
    background: $color-outline-variant;
    cursor: not-allowed;
    transform: none;
    opacity: 0.7;
  }

  &.loading {
    background: $color-brand-dark;
    pointer-events: none;
  }

  .spinning {
    animation: spin 0.8s linear infinite;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.prod-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.prod-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  line-height: 1.45;
  @include text-ellipsis(2);
  // 固定 2 行高度，保证卡片底部对齐
  min-height: calc(#{$font-size-body-md} * 1.45 * 2);
}

.prod-spec {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.2;
  min-height: calc(#{$font-size-label-md} * 1.2);
}

.prod-services {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 18px;
}

.service-tag {
  padding: 2px 6px;
  border-radius: $radius-sm;
  font-size: 10px;
  color: $color-on-surface-variant;
  background: $color-surface-container-high;
  line-height: 1;
}

.prod-price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 4px;
}

.prod-price {
  display: flex;
  align-items: flex-start;
  font-family: $font-family-headline;
  color: $color-brand;

  .yen {
    font-size: $font-size-label-md;
    font-weight: 700;
    margin-top: 2px;
    margin-right: 1px;
  }

  .amount {
    font-size: $font-size-headline-md;
    font-weight: 700;
    line-height: 1;
  }
}

.prod-sold {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1;
}

@include mobile {
  .product-card {
    padding: 0.6rem;
    border-radius: 1rem;
  }
  .prod-cover {
    margin-bottom: 0.5rem;
    border-radius: 0.75rem;
  }
}
</style>
