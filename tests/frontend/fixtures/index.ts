/**
 * 测试固定数据（fixtures）。
 *
 * 把「一个正常的用户 / 购物车项 / 订单」集中定义在这里，用例通过展开覆盖
 * 自己关心的字段：
 *
 * ```ts
 * const invalidItem = { ...cartItem, valid: false }
 * ```
 *
 * 好处：用例只显式声明「与默认值的差异」，读者一眼看出这条用例的关键变量是什么。
 */

/** 正常登录用户。 */
export const userFixture = {
  id: 1001,
  nickname: '测试用户',
  phone: '13800008888',
  avatar: '',
  role: 'user' as const,
  vipLevel: 0,
  verified: false,
  status: 1,
}

/** 管理员。 */
export const adminFixture = { ...userFixture, id: 9001, role: 'admin' as const }

/** 商家（店主）。 */
export const merchantFixture = {
  ...userFixture,
  id: 2001,
  role: 'merchant' as const,
  merchantId: 30001,
  subRole: 'OWNER',
}

/**
 * 购物车项 —— 有效、已选中。
 *
 * 金额相关字段刻意用「元」为单位的字符串/数字，与后端 DECIMAL(10,2) 对齐。
 * 前端测试计划 F-M1 关注的浮点累加问题，用例可基于此构造边界值
 * （如 19.99 × 3、0.1 + 0.2）。
 */
export const cartItemFixture = {
  id: 1,
  skuId: 101,
  productId: 201,
  title: '测试商品',
  specName: '默认规格',
  cover: '',
  price: 19.99,
  quantity: 1,
  selected: true,
  valid: true,
  stock: 100,
}

/** 失效商品（已下架/售罄）—— 必须被排除出结算（F-M4）。 */
export const invalidCartItemFixture = {
  ...cartItemFixture,
  id: 2,
  skuId: 102,
  title: '已下架商品',
  valid: false,
}

/** 订单。 */
export const orderFixture = {
  id: 5001,
  orderNo: 'P1234567890123',
  status: 'pending_pay',
  totalAmount: 19.99,
  payAmount: 19.99,
  createdAt: '2026-08-07 10:00:00',
  items: [cartItemFixture],
}
