# -*- coding: utf-8 -*-
"""
从 yangchongbang.cn/adoption 抓取真实领养帖子（含实拍图），
下载图片到本地 static/uploads/adoption/，生成整批替换 t_adoption 的 SQL。

数据质量规则（验收要求：不重复、图片与内容相符、字段完整）：
1. 仅接受真实领养帖：详情链接必须是 /adopt/{id}（排除 /idle/ 闲置转让、无链接的“求领养”请求帖）。
2. 按图片 URL 去重（同一张图只保留首次出现的帖子）。
3. 按归一化标题去重（剔除站方重复铺帖）。
4. 剔除脏标题：纯数字 / 过短 / “有需要的联系”这类无品种信息的无效帖。
5. 优先从 mix-card__sub(品种 年龄 性别) 解析字段；sub 为空时从标题回补品种/性别/年龄。
"""
import os
import re
import json
import subprocess
from collections import Counter

BASE = "https://www.yangchongbang.cn/adoption"
UPLOAD_DIR = r"D:\PetMate\petmate-backend\static\uploads\adoption"
OUT_SQL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adoption_ycb_seed.sql")
TARGET = 42
MAX_PAGE = 8

os.makedirs(UPLOAD_DIR, exist_ok=True)

# 品种词表（长词优先匹配，避免“布偶猫”被“布偶”截断）
BREED_LIST = [
    "布偶猫", "布偶", "橘猫", "英短", "美短", "暹罗猫", "缅因猫", "波斯猫", "蓝猫", "银渐层",
    "金渐层", "虎斑猫", "虎斑", "奶牛猫", "狸花猫", "加菲猫", "折耳猫", "蓝白猫", "蓝白", "三花猫",
    "三花", "中华田园猫", "田园猫", "曼基康", "苏格兰折耳", "孟买猫", "俄罗斯蓝猫", "豹猫", "临清狮猫",
    "金毛", "拉布拉多", "哈士奇", "萨摩耶", "泰迪", "贵宾犬", "贵宾", "比熊", "柯基", "柴犬",
    "法国斗牛犬", "法斗", "巴哥", "博美", "边境牧羊犬", "边牧", "德国牧羊犬", "德牧", "雪纳瑞",
    "西高地", "约克夏", "比格犬", "松狮", "中华田园犬", "田园犬", "马尔泰", "秋田犬", "阿拉斯加",
    "吉娃娃", "藏獒", "京巴", "腊肠犬", "沙皮犬", "斗牛犬", "史宾格", "可卡", "比熊犬",
    "仓鼠", "豚鼠", "荷兰猪", "龙猫", "蜜袋鼯", "刺猬", "鹦鹉", "虎皮鹦鹉", "乌龟", "垂耳兔",
    "蜥蜴", "香猪", "迷你猪", "鸭子", "鸽子", "金丝熊", "兔子",
    # 补充：标题中出现的非标准写法
    "流浪猫", "兔兔", "侏儒兔", "狸白", "橘白", "黑渐层", "长毛蓝白", "蓝白长毛",
]
BREED_LIST_SORTED = sorted(BREED_LIST, key=len, reverse=True)
CAT_BREED_HINT = ["猫", "布偶", "英短", "美短", "暹罗", "缅因", "波斯", "银渐层", "金渐层",
                  "虎斑", "奶牛", "狸花", "加菲", "折耳", "蓝白", "三花", "田园猫", "曼基康",
                  "孟买", "俄罗斯蓝", "豹猫", "狮猫"]
DOG_BREED_HINT = ["狗", "犬", "金毛", "拉布拉多", "哈士奇", "萨摩", "泰迪", "贵宾", "比熊", "柯基",
                  "柴犬", "法斗", "巴哥", "博美", "边牧", "德牧", "雪纳瑞", "西高地", "约克夏",
                  "松狮", "田园犬", "马尔泰", "秋田", "阿拉斯加", "吉娃娃", "藏獒", "京巴", "腊肠",
                  "沙皮", "史宾格", "可卡"]


def curl_text(url):
    try:
        out = subprocess.run(["curl", "-s", "-m", "15", url], capture_output=True, text=True, timeout=20)
        return out.stdout
    except Exception:
        return ""


def curl_bin(url, path):
    try:
        r = subprocess.run(["curl", "-s", "-m", "25", "-o", path, url], capture_output=True, text=True, timeout=30)
        return r.returncode == 0 and os.path.exists(path) and os.path.getsize(path) > 1024
    except Exception:
        return False


def clean(s):
    if not s:
        return ""
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("**", "").replace("*", "")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def parse_cards(html):
    return re.findall(r"<article class=\"mix-card\".*?</article>", html, re.S)


def extract(card):
    # 仅接受真实领养帖
    m_link = re.search(r'href="/(adopt)/(\d+)"', card)
    if not m_link:
        return None
    detail_id = m_link.group(2)

    m_img = re.search(r'src=\"(https://oss-qn-st\.9worker\.com/[^\"]+)\"', card)
    if not m_img:
        return None
    img = m_img.group(1)

    m_title = re.search(r'mix-card__title-text[^>]*>(.*?)</span>', card)
    title = clean(m_title.group(1)) if m_title else ""
    if not title:
        m_alt = re.search(r'alt=\"([^\"]*)\"', card)
        title = clean(m_alt.group(1)) if m_alt else ""

    m_sub = re.search(r'mix-card__sub[^>]*>(.*?)</p>', card)
    sub = clean(m_sub.group(1)) if m_sub else ""
    m_price = re.search(r'mix-card__price[^>]*>(.*?)</p>', card)
    price = clean(m_price.group(1)) if m_price else "无偿"
    m_city = re.search(r'mix-card__footer-text[^>]*>(.*?)</span>', card)
    raw_city = clean(m_city.group(1)) if m_city else ""
    city = raw_city if re.search(r"[市州区县盟]$", raw_city) else "未知"

    return {
        "detail_id": detail_id, "img": img, "title": title, "sub": sub,
        "price": price, "city": city,
    }


def detect_breed(text):
    for b in BREED_LIST_SORTED:
        if b in text:
            return b
    return ""


def classify(breed, sub, title):
    hay = breed + " " + sub + " " + title
    if any(k in hay for k in CAT_BREED_HINT):
        return "cat"
    if any(k in hay for k in DOG_BREED_HINT):
        return "dog"
    return "other"


def parse_gender(text):
    if re.search(r"弟弟|哥哥|公|雄|男孩|小公|男宝", text):
        return 1
    if re.search(r"妹妹|姐姐|母|雌|女孩|小母|女宝", text):
        return 2
    return 0


CN_NUM = {'一': 1, '二': 2, '两': 2, '三': 3, '四': 4, '五': 5,
           '六': 6, '七': 7, '八': 8, '九': 9, '十': 10}


def parse_age(text):
    # 支持阿拉伯数字与中文数字（两/二/三…），允许“个多月/岁多”等写法
    # 未匹配返回 None，交由调用方回退到 sub 或置“未知”
    if not text:
        return None
    m = re.search(r"(\d+|[一二两三四五六七八九十])\s*((?:个)?\s*月多?|岁多?|天多?|周多?)", text)
    if not m:
        return None
    num = m.group(1)
    unit = re.sub(r"\s+", "", m.group(2))
    if num.isdigit():
        return f"{num}{unit}"
    return f"{CN_NUM.get(num, num)}{unit}"


def norm_title(t):
    return re.sub(r"[\s，。、！？·…]+", "", t).strip()


def is_junk_title(title):
    if not title:
        return True
    if re.fullmatch(r"\d+", title):
        return True
    if len(title) < 5:
        return True
    if title in ("有需要的联系", "有需要的联系 个人原因 不养了"):
        return True
    return False


def is_real_pet(title, sub, breed):
    if breed:
        return True
    hay = title + " " + sub
    if re.search(r"希望(再)?来|想要(一)?只|想养|求(一)?只|我家有|因为我们家", hay):
        return False
    pet_kw = ["猫", "狗", "犬", "兔", "仓鼠", "豚鼠", "龙猫", "蜜袋", "刺猬", "鹦鹉",
              "龟", "蜥蜴", "蛇", "猪", "鸭", "鸟"]
    return any(k in hay for k in pet_kw)


def esc(s):
    return "" if s is None else str(s).replace("\\", "\\\\").replace("'", "\\'")


def main():
    records = []
    seen_imgs = set()
    seen_titles = set()
    page = 1
    while len(records) < TARGET and page <= MAX_PAGE:
        html = curl_text(f"{BASE}?page={page}")
        if not html:
            print(f"  page {page}: 空响应，停止")
            break
        cards = parse_cards(html)
        print(f"  page {page}: {len(cards)} cards")
        dropped = {"idle/req": 0, "junk": 0, "no_pet": 0, "dup_img": 0, "dup_title": 0}
        for c in cards:
            d = extract(c)
            if not d:
                dropped["idle/req"] += 1
                continue
            if is_junk_title(d["title"]):
                dropped["junk"] += 1
                continue
            # 字段解析（sub 优先，标题回补）
            breed = detect_breed(d["sub"]) or detect_breed(d["title"])
            if not is_real_pet(d["title"], d["sub"], breed):
                dropped["no_pet"] += 1
                continue
            if d["img"] in seen_imgs:
                dropped["dup_img"] += 1
                continue
            nt = norm_title(d["title"])
            if nt in seen_titles:
                dropped["dup_title"] += 1
                continue
            seen_imgs.add(d["img"])
            seen_titles.add(nt)

            # 字段解析：标题优先（站方 sub 常有“1岁/公”默认值），sub 兜底
            breed = detect_breed(d["sub"]) or detect_breed(d["title"])
            if not breed:
                breed = {"cat": "家猫", "dog": "家犬", "other": "其它小动物"}[pet_type]

            pet_type = classify(breed, d["sub"], d["title"])
            gender = parse_gender(d["title"]) or parse_gender(d["sub"])
            age_title = parse_age(d["title"])
            age_sub = parse_age(d["sub"])
            if age_title and age_title != "1岁":
                age = age_title
            elif age_sub:
                age = age_sub
            elif age_title:
                age = age_title
            else:
                age = "未知"

            # 城市：市辖区为占位符，尝试从标题识别直辖市，否则归未知
            city = d["city"]
            if city == "市辖区":
                if "重庆" in d["title"]:
                    city = "重庆市"
                elif "北京" in d["title"]:
                    city = "北京市"
                elif "上海" in d["title"]:
                    city = "上海市"
                elif "天津" in d["title"]:
                    city = "天津市"
                else:
                    city = "未知"

            # 下载图片，按文件魔数归一化扩展名（避免 .x-www-form-urlencoded/.octet-stream
            # 这类非标准后缀导致浏览器无法按图片渲染）
            base = d["img"].split("/")[-1].rsplit(".", 1)[0]
            tmp_abs = os.path.join(UPLOAD_DIR, base + ".tmp")
            ok = curl_bin(d["img"], tmp_abs)
            local_rel = ""
            if ok:
                try:
                    with open(tmp_abs, "rb") as fh:
                        head = fh.read(12)
                    if head[:4] == b"\x89PNG":
                        ext = ".png"
                    elif head[:2] == b"\xff\xd8":
                        ext = ".jpeg"
                    elif head[:4] == b"RIFF" and head[8:12] == b"WEBP":
                        ext = ".webp"
                    else:
                        ext = ".jpg"
                    final = os.path.join(UPLOAD_DIR, base + ext)
                    if os.path.abspath(final) != os.path.abspath(tmp_abs):
                        if os.path.exists(final):
                            os.remove(final)
                        os.rename(tmp_abs, final)
                    else:
                        ext = os.path.splitext(tmp_abs)[1]
                        final = tmp_abs
                    local_rel = f"/static/uploads/adoption/{base}{ext}"
                except Exception:
                    ok = False
            images = f'["{local_rel}"]' if (ok and local_rel) else "[]"

            vaccinated = 1 if ("已接种" in d["title"] or "疫苗" in d["title"] or "接种" in d["sub"]) else 0
            neutered = 1 if ("已绝育" in d["title"] or "绝育" in d["title"] or "绝育" in d["sub"]) else 0

            price_note = "无偿领养" if d["price"] == "无偿" else f"小偿领养 {d['price']}"
            rescue_story = f"【{city}】{d['title']}"
            health_status = price_note + "；健康与疫苗情况请联系救助人确认。"

            records.append({
                "title": d["title"][:120],
                "pet_type": pet_type,
                "breed": breed[:32],
                "gender": gender,
                "age": age[:16],
                "rescue_story": rescue_story[:500],
                "health_status": health_status[:120],
                "vaccinated": vaccinated,
                "neutered": neutered,
                "city": city[:32],
                "contact": None,
                "images": images,
                "status": 1,
            })
        print(f"    dropped: {dropped}, kept so far: {len(records)}")
        page += 1

    # 生成 SQL
    cols = ["title", "pet_type", "breed", "gender", "age", "rescue_story",
            "health_status", "vaccinated", "neutered", "city", "contact", "images", "status"]
    lines = []
    lines.append("-- 整批替换为 yangchongbang.cn 真实领养帖子（含本地实拍图，已去重+字段回补）")
    lines.append("-- 执行：mysql -uroot -p petmate --default-character-set=utf8mb4 < adoption_ycb_seed.sql")
    lines.append("SET NAMES utf8mb4;")
    lines.append("DELETE FROM t_adoption_application;")
    lines.append("DELETE FROM t_adoption;")
    lines.append("ALTER TABLE t_adoption AUTO_INCREMENT = 1;")
    lines.append("")
    for r in records:
        vals = [
            f"'{esc(r['title'])}'", f"'{esc(r['pet_type'])}'", f"'{esc(r['breed'])}'",
            str(r['gender']), f"'{esc(r['age'])}'", f"'{esc(r['rescue_story'])}'",
            f"'{esc(r['health_status'])}'", str(r['vaccinated']), str(r['neutered']),
            f"'{esc(r['city'])}'", "NULL", f"'{esc(r['images'])}'", str(r['status']),
        ]
        lines.append("INSERT INTO t_adoption (" + ", ".join(cols) + ") VALUES (" + ", ".join(vals) + ");")

    with open(OUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    tc = Counter(r["pet_type"] for r in records)
    cc = Counter(r["city"] for r in records)
    with_img = sum(1 for r in records if r["images"] != "[]")
    unknown_breed = sum(1 for r in records if r["breed"] in ("其它", "其它小动物") or r["age"] == "未知")
    print(f"\n✅ 采集 {len(records)} 条真实领养帖，SQL 已写入: {OUT_SQL}")
    print(f"   类型分布: {dict(tc)}")
    print(f"   带图: {with_img}/{len(records)}")
    print(f"   城市({len(cc)}): {dict(cc)}")
    print(f"   字段待完善(其它品种或未知年龄): {unknown_breed}")
    cities = ["全部城市"] + sorted(cc.keys())
    print("   FRONTEND cityOptions = " + json.dumps(cities, ensure_ascii=False))


if __name__ == "__main__":
    main()
