# -*- coding: utf-8 -*-
"""
生成 PetMate 领养模块种子数据 (60 条)。
数据来源：petopic.com 真实抓取的常见品种（狗/猫/其他各 20 条）。
输出可直接 `mysql ... < adoption_seed_60.sql` 执行的 INSERT 语句。
"""
import json
import os

# ---------- 真实抓取的常见品种（来自 petopic.com）----------
DOG_BREEDS = [
    "边境牧羊犬", "拉布拉多", "贵宾犬", "博美犬", "沙皮犬",
    "金毛寻回犬", "吉娃娃", "松狮犬", "柴犬", "萨摩耶",
    "罗威纳", "哈瓦那犬", "巴辛吉犬", "四国犬", "意大利灵缇",
    "苏格兰梗", "Jeju犬", "Akbash", "博佐伊犬", "萨路基犬",
]

CAT_BREEDS = [
    "英国短毛猫", "美国短毛猫", "布偶猫", "暹罗猫", "缅因猫",
    "孟加拉猫", "苏格兰折耳猫", "波斯猫", "俄罗斯蓝猫", "德文卷毛猫",
    "异国短毛猫", "挪威森林猫", "西伯利亚猫", "阿比西尼亚猫", "伯曼猫",
    "土耳其安哥拉猫", "曼基康猫", "缅甸猫", "斯芬克斯猫", "中国狸花猫",
]

OTHER_BREEDS = [
    # 兔 (6)
    "荷兰侏儒兔", "迷你垂耳兔", "巨型安哥拉兔", "英国垂耳兔", "美国垂耳兔", "荷兰垂耳兔",
    # 鹦鹉 (7)
    "非洲灰鹦鹉", "玄凤鹦鹉", "金刚鹦鹉", "亚马逊鹦鹉", "情侣鹦鹉", "折衷鹦鹉", "塞内加尔鹦鹉",
    # 仓鼠 (5)
    "坎贝尔仓鼠", "中国仓鼠", "土耳其仓鼠", "叙利亚仓鼠", "侏儒仓鼠",
    # 鸽子 (2)
    "归巢鸽", "观赏鸽",
]

AGE_BUCKETS = ["0-6个月", "青年（6个月-2岁）", "成年（2-7岁）", "老年（7岁及以上）"]
CITIES = ["深圳", "北京", "上海", "广州"]

CITY_CONTACT = {
    "深圳": "深圳市伴侣动物救助中心 0755-8888-6677",
    "北京": "北京流浪猫狗救助基地 010-6225-3344",
    "上海": "上海宠物领养日 021-5456-7788",
    "广州": "广州爱笑天使动物关爱中心 020-3766-9900",
}

# 生成 60 个不重复的可爱昵称：前缀 × 后缀 = 15 × 4 = 60
NICK_FIRST = ["小", "阿", "大", "老", "胖", "甜", "暖", "萌", "皮", "豆", "花", "球", "奶", "毛", "布"]
NICK_SECOND = ["豆", "球", "糖", "宝"]
NICKS = [NICK_FIRST[i // 4] + NICK_SECOND[i % 4] for i in range(60)]

GENDER_TEXT = {1: "男生", 2: "女生", 0: "未知"}


def esc(s):
    """MySQL 单引号转义。"""
    return "" if s is None else str(s).replace("\\", "\\\\").replace("'", "\\'")


def build_records():
    records = []
    seen = set()  # (pet_type, breed, gender, age) 去重校验
    global_idx = 0

    groups = [
        ("dog", DOG_BREEDS),
        ("cat", CAT_BREEDS),
        ("other", OTHER_BREEDS),
    ]

    for pet_type, breeds in groups:
        for j, breed in enumerate(breeds):
            age = AGE_BUCKETS[j % 4]
            gender = 1 if j % 2 == 0 else 2
            city = CITIES[j % 4]
            nick = NICKS[global_idx]

            # 唯一性校验
            key = (pet_type, breed, gender, age)
            assert key not in seen, f"重复组合: {key}"
            seen.add(key)

            # 疫苗 / 绝育逻辑（符合常识）
            vaccinated = 1 if (age != "0-6个月" or j % 3 == 0) else 0
            if age in ("成年（2-7岁）", "老年（7岁及以上）"):
                neutered = 1
            else:
                neutered = 1 if j % 2 == 0 else 0

            g_txt = GENDER_TEXT[gender]
            # 救助故事（按类型与属性生成，真实风格）
            if pet_type == "dog":
                story = (
                    f"这只{g_txt}{age}的{breed}是在{city}街头被救助志愿者发现的，"
                    f"当时瘦弱但亲人、不怕生。经过一段时间科学喂养与陪伴，现已恢复活力，"
                    f"期待一个能陪它奔跑、给它稳定陪伴的家。"
                )
            elif pet_type == "cat":
                story = (
                    f"这只{g_txt}{age}的{breed}因原主人变动而来到救助站，"
                    f"性格{g_txt == '女生' and '温婉粘人' or '独立又好奇'}，"
                    f"已适应室内生活。希望找到一个安静、有耐心的铲屎官家庭。"
                )
            else:
                story = (
                    f"这只{g_txt}{age}的{breed}由{city}爱心救助人从小养大，"
                    f"亲人且健康。因救助人精力有限现开放领养，"
                    f"需要有相关饲养经验、能提供合适环境的爱心家庭。"
                )

            vac_txt = "已接种疫苗" if vaccinated else "暂未接种疫苗（幼龄建议补种）"
            neu_txt = "已绝育" if neutered else "未绝育"
            health = f"已做基础体检，{vac_txt}；{neu_txt}。体内外驱虫已完成，食欲精神良好，适合家庭饲养。"

            title = f"{breed}「{nick}」待领养"

            records.append({
                "title": title,
                "pet_type": pet_type,
                "breed": breed,
                "gender": gender,
                "age": age,
                "rescue_story": story,
                "health_status": health,
                "vaccinated": vaccinated,
                "neutered": neutered,
                "city": city,
                "contact": CITY_CONTACT[city],
                "images": "[]",  # 空图，前端展示 paw 占位
                "status": 1,
            })
            global_idx += 1

    return records


def to_sql(records):
    cols = ["title", "pet_type", "breed", "gender", "age", "rescue_story",
            "health_status", "vaccinated", "neutered", "city", "contact", "images", "status"]
    lines = []
    lines.append("-- PetMate 领养模块种子数据（60 条，基于 petopic.com 真实品种）")
    lines.append("-- 执行：mysql -uroot -p petmate --default-character-set=utf8mb4 < adoption_seed_60.sql")
    lines.append("SET NAMES utf8mb4;")
    lines.append("")
    for r in records:
        vals = [
            f"'{esc(r['title'])}'",
            f"'{esc(r['pet_type'])}'",
            f"'{esc(r['breed'])}'",
            str(r['gender']),
            f"'{esc(r['age'])}'",
            f"'{esc(r['rescue_story'])}'",
            f"'{esc(r['health_status'])}'",
            str(r['vaccinated']),
            str(r['neutered']),
            f"'{esc(r['city'])}'",
            f"'{esc(r['contact'])}'",
            f"'{esc(r['images'])}'",
            str(r['status']),
        ]
        lines.append(
            "INSERT INTO t_adoption (" + ", ".join(cols) + ") VALUES (" + ", ".join(vals) + ");"
        )
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    recs = build_records()
    assert len(recs) == 60, f"期望 60 条，实际 {len(recs)}"
    # 标题唯一性
    titles = [r["title"] for r in recs]
    assert len(set(titles)) == 60, "标题存在重复"
    # 类型计数
    from collections import Counter
    c = Counter(r["pet_type"] for r in recs)
    assert c == {"dog": 20, "cat": 20, "other": 20}, f"类型计数异常: {dict(c)}"
    # city 分布
    cc = Counter(r["city"] for r in recs)

    sql = to_sql(recs)
    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adoption_seed_60.sql")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"✅ 生成 {len(recs)} 条记录，SQL 已写入: {out_path}")
    print(f"   类型分布: {dict(c)}")
    print(f"   城市分布: {dict(cc)}")
    print(f"   标题唯一: {len(set(titles)) == 60}  组合唯一: True")
