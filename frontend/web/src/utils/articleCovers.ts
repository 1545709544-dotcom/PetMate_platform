/**
 * 文章封面映射工具
 *
 * 数据库 t_article.cover 存的是开发期源码路径（如 /src/assets/images/knowledge/c2-cat-food.jpg），
 * 在生产构建中并不会被当作可访问 URL。这里把已知的封面文件名映射到 Vite 打包后的真实资源，
 * 解析失败时统一回退到知识页 hero 图，避免破图。
 */
import c1 from '@/assets/images/knowledge/c1-dog-train.jpg'
import c2 from '@/assets/images/knowledge/c2-cat-food.jpg'
import c3 from '@/assets/images/knowledge/c3-vet-kitten.jpg'
import c4 from '@/assets/images/knowledge/c4-cat-plant.jpg'
import c5 from '@/assets/images/knowledge/c5-dog-dental.jpg'
import c6 from '@/assets/images/knowledge/c6-dog-play.jpg'
import heroImg from '@/assets/images/knowledge/hero.jpg'

const COVER_MAP: Record<string, string> = {
  'c1-dog-train.jpg': c1,
  'c2-cat-food.jpg': c2,
  'c3-vet-kitten.jpg': c3,
  'c4-cat-plant.jpg': c4,
  'c5-dog-dental.jpg': c5,
  'c6-dog-play.jpg': c6,
}

/** 将数据库中的 cover 路径解析为可访问的图片 URL；无效/缺失时回退到 hero 图。 */
export function resolveCover(cover?: string | null): string {
  if (!cover) return heroImg
  const base = cover.split('/').pop() || ''
  return COVER_MAP[base] || heroImg
}

export { heroImg }
