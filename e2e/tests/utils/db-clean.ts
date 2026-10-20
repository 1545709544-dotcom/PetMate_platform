/**
 * 测试数据隔离 —— 直接用 mysql 命令清掉 petmate_test_ 前缀的数据。
 *
 * 策略：
 *  - 测试创建的数据（用户昵称 / 商品名 / 帖子内容 / 商家名）都以 petmate_test_ 开头
 *  - 测试用 phone 用 139xxxxxxxx 随机段（不与演示账号 13800138000~003 撞）
 *  - 跑前 + 跑后清一遍：按 nickname LIKE 'petmate_test_%' 找用户，再级联删
 *
 * 为什么不走 API 删除：
 *  - 删除接口不一定存在（计划 §3.1 提到「数据隔离差」是项目现状）
 *  - 后端订单 / 售后有状态机约束，API 删不动
 *  - 测试只造数据、不依赖被测对象的行为去清数据 —— 否则清数据这一步本身就是测点
 */

import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { MYSQL, TEST_TAG } from './constants.js'

const execFileP = promisify(execFile)

const MYSQL_BIN = 'D:/mysql-8.0.26-winx64/bin/mysql.exe'

async function sqlExec(sql: string): Promise<string> {
  try {
    const { stdout } = await execFileP(
      MYSQL_BIN,
      [
        '-h', MYSQL.host,
        '-P', String(MYSQL.port),
        '-u', MYSQL.user,
        `-p${MYSQL.password}`,
        MYSQL.database,
        '-N', '-B',
        '-e', sql,
      ],
      { windowsHide: true, maxBuffer: 50 * 1024 * 1024 },
    )
    return stdout
  } catch (e: any) {
    if (e.stdout) return e.stdout
    throw e
  }
}

async function findIds(sql: string): Promise<number[]> {
  const out = await sqlExec(sql)
  return out
    .trim()
    .split('\n')
    .filter(Boolean)
    .map((s) => parseInt(s, 10))
    .filter((n) => !Number.isNaN(n))
}

export async function findTestUserIds(): Promise<number[]> {
  return findIds(
    `SELECT id FROM t_user WHERE is_deleted=0 AND nickname LIKE '${TEST_TAG}%'`,
  )
}

export async function findTestMerchantIds(): Promise<number[]> {
  return findIds(
    `SELECT id FROM t_user WHERE is_deleted=0 AND role='merchant' AND nickname LIKE '${TEST_TAG}%'`,
  )
}

export async function findTestProductIds(): Promise<number[]> {
  return findIds(`SELECT id FROM t_product WHERE name LIKE '${TEST_TAG}%'`)
}

export async function findTestPostIds(): Promise<number[]> {
  return findIds(
    `SELECT id FROM t_post WHERE user_nickname LIKE '${TEST_TAG}%' OR content LIKE '${TEST_TAG}%'`,
  )
}

/**
 * 跑前清理 —— 保证测试环境干净
 * 用事务批量删除，失败回滚
 */
export async function cleanTestData(): Promise<{ cleaned: Record<string, number> }> {
  const cleaned: Record<string, number> = {}

  const userIds = await findTestUserIds()
  const merchantIds = await findTestMerchantIds()
  const productIds = await findTestProductIds()
  const postIds = await findTestPostIds()

  cleaned.userIds = userIds.length
  cleaned.merchantIds = merchantIds.length
  cleaned.productIds = productIds.length
  cleaned.postIds = postIds.length

  if (
    userIds.length === 0 &&
    merchantIds.length === 0 &&
    productIds.length === 0 &&
    postIds.length === 0
  ) {
    return { cleaned }
  }

  const sqls: string[] = []

  // ===== 用户关联清理（按 FK 依赖顺序） =====
  if (userIds.length) {
    const ulist = userIds.join(',')
    // 取消外键检查避免顺序问题
    sqls.push(`SET FOREIGN_KEY_CHECKS=0`)

    // 订单项先于订单（t_order_item 没有 user_id，靠 order_id 关联）
    sqls.push(`DELETE FROM t_order_item WHERE order_id IN (SELECT id FROM t_order WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_order WHERE user_id IN (${ulist})`)

    // 售后相关
    sqls.push(`DELETE FROM t_refund_log WHERE order_no IN (SELECT order_no FROM t_order WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_refund WHERE user_id IN (${ulist})`)

    // 购物车 / 地址 / 收藏 / 关注
    sqls.push(`DELETE FROM t_cart WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_user_address WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_favorite WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_follow WHERE follower_id IN (${ulist}) OR followee_id IN (${ulist})`)

    // 宠物相关
    sqls.push(`DELETE FROM t_pet_photo WHERE pet_id IN (SELECT id FROM t_pet WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_pet_health_record WHERE pet_id IN (SELECT id FROM t_pet WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_pet_reminder WHERE pet_id IN (SELECT id FROM t_pet WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_pet WHERE user_id IN (${ulist})`)

    // 积分 / 优惠券 / 通知
    sqls.push(`DELETE FROM t_points_log WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_points_account WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_user_coupon WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_user_notification WHERE user_id IN (${ulist})`)

    // 问诊会话 / 消息
    sqls.push(`DELETE FROM t_consultation_message WHERE consultation_id IN (SELECT id FROM t_consultation WHERE user_id IN (${ulist}))`)
    sqls.push(`DELETE FROM t_consultation WHERE user_id IN (${ulist})`)

    // 帖子（按 user_id；之后还会按 user_nickname 兜底）
    sqls.push(`DELETE FROM t_post_collect WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_post_like WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_post_comment WHERE user_id IN (${ulist})`)
    sqls.push(`DELETE FROM t_post WHERE user_id IN (${ulist})`)

    // 商品浏览足迹
    sqls.push(`DELETE FROM t_product_footprint WHERE user_id IN (${ulist})`)

    // 评价
    sqls.push(`DELETE FROM t_product_review WHERE user_id IN (${ulist})`)

    // 软删用户（避免硬删触发 FK）
    sqls.push(`UPDATE t_user SET is_deleted=1 WHERE id IN (${ulist})`)

    sqls.push(`SET FOREIGN_KEY_CHECKS=1`)
  }

  // ===== 商家清理 =====
  if (merchantIds.length) {
    const mlist = merchantIds.join(',')
    sqls.push(`SET FOREIGN_KEY_CHECKS=0`)
    sqls.push(`DELETE FROM t_product_sku WHERE product_id IN (SELECT id FROM t_product WHERE merchant_id IN (${mlist}))`)
    sqls.push(`DELETE FROM t_product WHERE merchant_id IN (${mlist})`)
    sqls.push(`DELETE FROM t_merchant_staff WHERE merchant_id IN (${mlist})`)
    sqls.push(`UPDATE t_store SET is_deleted=1 WHERE user_id IN (${mlist})`)
    sqls.push(`SET FOREIGN_KEY_CHECKS=1`)
  }

  // ===== 商品按名字兜底 =====
  if (productIds.length) {
    const plist = productIds.join(',')
    sqls.push(`SET FOREIGN_KEY_CHECKS=0`)
    sqls.push(`DELETE FROM t_product_sku WHERE product_id IN (${plist})`)
    sqls.push(`DELETE FROM t_product WHERE id IN (${plist})`)
    sqls.push(`SET FOREIGN_KEY_CHECKS=1`)
  }

  // ===== 帖子兜底 =====
  if (postIds.length) {
    const polist = postIds.join(',')
    sqls.push(`SET FOREIGN_KEY_CHECKS=0`)
    sqls.push(`DELETE FROM t_post_collect WHERE post_id IN (${polist})`)
    sqls.push(`DELETE FROM t_post_like WHERE post_id IN (${polist})`)
    sqls.push(`DELETE FROM t_post_comment WHERE post_id IN (${polist})`)
    sqls.push(`DELETE FROM t_post WHERE id IN (${polist})`)
    sqls.push(`SET FOREIGN_KEY_CHECKS=1`)
  }

  for (const sql of sqls) {
    try {
      await sqlExec(sql)
    } catch (e: any) {
      // eslint-disable-next-line no-console
      console.warn(
        `[cleanTestData] skip: ${sql.slice(0, 80)}... (${e.message?.slice(0, 80)})`,
      )
    }
  }

  return { cleaned }
}