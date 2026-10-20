mysqldump: [Warning] Using a password on the command line interface can be insecure.
-- MySQL dump 10.13  Distrib 8.0.26, for Win64 (x86_64)
--
-- Host: localhost    Database: petmate
-- ------------------------------------------------------
-- Server version	8.0.26

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `t_article`
--

DROP TABLE IF EXISTS `t_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `cover` varchar(255) DEFAULT NULL,
  `author` varchar(64) DEFAULT 'PetMate 兽医团队',
  `view_count` int NOT NULL DEFAULT '0',
  `seo_url` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识文章';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_article`
--

LOCK TABLES `t_article` WRITE;
/*!40000 ALTER TABLE `t_article` DISABLE KEYS */;
INSERT INTO `t_article` VALUES (1,1,'幼猫喂养完全指南：从奶糕到成猫的过渡','# 幼猫喂养\n\n幼猫(0-12个月)处于快速生长期,对营养需求高。\n\n## 0-2 月龄\n- 以母乳或专用猫咪代乳粉为主\n- 每天 4-6 次少量喂食\n\n## 2-6 月龄\n- 奶糕为主,每日 3-4 次\n- 用温水或羊奶粉泡软\n\n## 6-12 月龄\n- 逐渐过渡到幼猫粮\n- 每天 2-3 次\n\n> 关键提示:换粮遵循 7 天过渡法,避免肠胃应激。','/src/assets/images/knowledge/c2-cat-food.jpg','李医生 · 兽医',1240,'kitten-feeding-guide','2026-07-20 09:13:04',0),(2,3,'幼犬基础服从训练 5 步法','# 幼犬基础训练\n\n## 1. 名字识别\n每天 10 次,零食诱导,3 天见效。\n\n## 2. 坐下\n手压臀部+口令\"坐\"。\n\n## 3. 等待\n短时间等待→奖励。\n\n## 4. 召回\n不可惩罚召回,否则它再也不会回来。\n\n## 5. 定点排便\n固定时间+固定地点+奖励。','/src/assets/images/knowledge/c1-dog-train.jpg','王教练 · 训犬师',890,'puppy-obedience','2026-07-20 09:13:04',0),(3,2,'猫疫苗怎么打？猫三联/妙三多超全攻略','# 猫疫苗\n\n## 核心疫苗\n- 猫三联(妙三多):预防猫瘟/猫鼻支/猫杯状\n- 狂犬疫苗\n\n## 免疫程序\n- 8 周龄:第一针\n- 12 周龄:第二针\n- 16 周龄:第三针 + 狂犬\n- 以后每年加强一针','/src/assets/images/knowledge/c3-vet-kitten.jpg','李医生 · 兽医',2150,'cat-vaccine-guide','2026-07-20 09:13:04',0),(4,1,'哪些植物对猫有毒？家庭绿植黑名单','# 危险的家居植物\n\n百合、郁金香、水仙、绿萝、滴水观音、龟背竹…… 都会对猫咪造成不同程度的伤害。\n\n## 推荐替代\n- 猫草(小麦苗)\n- 猫薄荷\n- 安全的:波士顿蕨、空气凤梨','/src/assets/images/knowledge/c4-cat-plant.jpg','陈编辑 · 内容',1860,'toxic-plants-for-cat','2026-07-20 09:13:04',0),(5,2,'狗狗口腔健康：刷牙与洁齿零食','# 狗狗口腔\n\n80% 的 3 岁以上狗狗存在口腔问题。\n\n## 每日刷牙\n- 用宠物专用牙刷\n- 宠物专用牙膏(可食用)\n- 每周 3 次以上\n\n## 替代方案\n- 洁齿零食\n- 咬胶\n- 洁齿玩具','/src/assets/images/knowledge/c5-dog-dental.jpg','李医生 · 兽医',920,'dog-dental-care','2026-07-20 09:13:04',0),(6,3,'怎么消耗狗狗过剩的精力？','# 消耗精力\n\n## 嗅觉游戏\n把零食藏在家里,让狗找。\n\n## 拔河比赛\n- 限时 3 分钟\n- 让狗赢几次\n\n## 飞盘\n低强度,30 分钟即可。','/src/assets/images/knowledge/c6-dog-play.jpg','王教练 · 训犬师',660,'burn-dog-energy','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_article` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_article_category`
--

DROP TABLE IF EXISTS `t_article_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_article_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_article_category`
--

LOCK TABLES `t_article_category` WRITE;
/*!40000 ALTER TABLE `t_article_category` DISABLE KEYS */;
INSERT INTO `t_article_category` VALUES (1,'喂养指南','https://img.icons8.com/color/96/pet-food.png',1),(2,'健康护理','https://img.icons8.com/color/96/heart-with-pulse.png',2),(3,'行为训练','https://img.icons8.com/color/96/dog-training.png',3),(4,'品种科普','https://img.icons8.com/color/96/pet-supplies.png',4);
/*!40000 ALTER TABLE `t_article_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_cart`
--

DROP TABLE IF EXISTS `t_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `selected` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`,`sku_id`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_cart`
--

LOCK TABLES `t_cart` WRITE;
/*!40000 ALTER TABLE `t_cart` DISABLE KEYS */;
INSERT INTO `t_cart` VALUES (1,10001,1,1001,3,1,'2026-07-20 09:13:04','2026-07-20 21:20:36',0),(2,10001,3,1003,1,1,'2026-07-20 09:13:04','2026-07-20 09:13:04',0),(3,10001,6,1006,2,0,'2026-07-20 09:13:04','2026-07-20 09:13:04',0),(4,90003,1,1001,1,1,'2026-07-20 21:26:40','2026-07-20 21:26:40',0);
/*!40000 ALTER TABLE `t_cart` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_consultation`
--

DROP TABLE IF EXISTS `t_consultation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_consultation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `pet_id` bigint DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'active',
  `risk_level` varchar(16) NOT NULL DEFAULT 'normal',
  `message_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `closed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问诊会话';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_consultation`
--

LOCK TABLES `t_consultation` WRITE;
/*!40000 ALTER TABLE `t_consultation` DISABLE KEYS */;
INSERT INTO `t_consultation` VALUES (1,10001,1,'Buddy 呕吐咨询','active','normal',4,'2026-07-20 09:13:04',NULL,0),(2,10001,2,'Moka 拉稀怎么办','active','urgent',6,'2026-07-20 09:13:04',NULL,0),(3,90003,NULL,'新问诊','active','normal',0,'2026-07-20 20:58:45',NULL,0),(4,90003,NULL,'新问诊','active','normal',0,'2026-07-20 20:58:48',NULL,0),(5,90003,NULL,'新问诊','active','normal',0,'2026-07-20 20:58:53',NULL,0),(6,90003,NULL,'新问诊','active','normal',0,'2026-07-20 21:18:19',NULL,0),(7,90003,NULL,'新问诊','active','normal',0,'2026-07-20 21:18:24',NULL,0),(8,90003,NULL,'新问诊','active','normal',0,'2026-07-20 21:19:08',NULL,0),(9,90003,NULL,'新问诊','active','normal',0,'2026-07-21 01:07:39',NULL,0),(10,90003,NULL,'新问诊','active','normal',0,'2026-07-21 01:07:44',NULL,0);
/*!40000 ALTER TABLE `t_consultation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_consultation_message`
--

DROP TABLE IF EXISTS `t_consultation_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_consultation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consultation_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'user/assistant/system',
  `content` text NOT NULL,
  `risk_level` varchar(16) DEFAULT NULL,
  `products` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_consult` (`consultation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问诊消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_consultation_message`
--

LOCK TABLES `t_consultation_message` WRITE;
/*!40000 ALTER TABLE `t_consultation_message` DISABLE KEYS */;
INSERT INTO `t_consultation_message` VALUES (1,1,'user','我家 Buddy 昨晚吐了两次,精神还可以,要紧吗?',NULL,NULL,'2026-07-20 09:13:04'),(2,1,'assistant','您好,呕吐的原因很多,常见包括:吃得太快、食物变质、误食异物、肠胃炎等。请问狗狗的呕吐物是什么颜色?是否带血?最近有没有换粮?','normal',NULL,'2026-07-20 09:13:04'),(3,1,'user','黄色泡沫,没有血。昨天偷吃了一根袜子。',NULL,NULL,'2026-07-20 09:13:04'),(4,1,'assistant','⚠️ 这种情况需要警惕异物阻塞。建议:1) 立即禁食 8-12 小时;2) 观察是否还有呕吐/精神萎靡;3) 如有异常,立即送医做 X 光。建议同时使用 [卫仕营养膏](javascript:void(0)) 补充体力。','urgent',NULL,'2026-07-20 09:13:04'),(5,2,'user','Moka 拉稀 3 天,带血丝',NULL,NULL,'2026-07-20 09:13:04'),(6,2,'assistant','⚠️ 拉稀带血丝需要立即送医!考虑急性肠胃炎/寄生虫/猫瘟。建议:1) 立即送医做粪检+猫瘟试纸;2) 期间禁食不禁水;3) 记录排便次数和性状。可备 [谷登化毛膏](javascript:void(0)) 备用。','urgent',NULL,'2026-07-20 09:13:04');
/*!40000 ALTER TABLE `t_consultation_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_coupon`
--

DROP TABLE IF EXISTS `t_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `type` varchar(16) NOT NULL COMMENT 'reduction/discount/category',
  `threshold` decimal(10,2) NOT NULL DEFAULT '0.00',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `discount` decimal(3,2) DEFAULT NULL,
  `valid_start` datetime NOT NULL,
  `valid_end` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_coupon`
--

LOCK TABLES `t_coupon` WRITE;
/*!40000 ALTER TABLE `t_coupon` DISABLE KEYS */;
INSERT INTO `t_coupon` VALUES (1,'新用户满100减10','reduction',100.00,10.00,NULL,'2025-01-01 00:00:00','2026-12-31 00:00:00',1,'2026-07-20 09:13:04',0),(2,'会员9.5折','discount',0.00,0.00,0.95,'2025-01-01 00:00:00','2026-12-31 00:00:00',1,'2026-07-20 09:13:04',0),(3,'猫粮品类满200减30','category',200.00,30.00,NULL,'2025-01-01 00:00:00','2026-12-31 00:00:00',1,'2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_coupon` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_favorite`
--

DROP TABLE IF EXISTS `t_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(16) NOT NULL COMMENT 'product/post/article/service',
  `target_id` bigint NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`,`is_deleted`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_favorite`
--

LOCK TABLES `t_favorite` WRITE;
/*!40000 ALTER TABLE `t_favorite` DISABLE KEYS */;
INSERT INTO `t_favorite` VALUES (1,10001,'product',1001,'皇家猫粮 K36','/src/assets/images/cart/p1-dogfood.jpg',198.00,'2026-07-20 09:13:04',0),(2,10001,'product',1004,'Cat Magic 猫抓板','/src/assets/images/cart/r1-cat-scratcher.jpg',89.00,'2026-07-20 09:13:04',0),(3,10001,'post',1,'今天 Buddy 第一次游泳',NULL,NULL,'2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_follow`
--

DROP TABLE IF EXISTS `t_follow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follower_id` bigint NOT NULL,
  `followee_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`follower_id`,`followee_id`),
  KEY `idx_follower` (`follower_id`),
  KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍏虫敞鍏崇郴';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_follow`
--

LOCK TABLES `t_follow` WRITE;
/*!40000 ALTER TABLE `t_follow` DISABLE KEYS */;
INSERT INTO `t_follow` VALUES (1,10001,10002,'2026-07-10 09:00:00'),(2,10001,10003,'2026-07-11 14:30:00'),(3,10002,10001,'2026-07-12 08:15:00'),(4,10003,10001,'2026-07-13 20:00:00'),(5,10004,10002,'2026-07-14 10:20:00');
/*!40000 ALTER TABLE `t_follow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_operation_log`
--

DROP TABLE IF EXISTS `t_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user` varchar(64) DEFAULT 'system',
  `action` varchar(255) NOT NULL,
  `target` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_operation_log`
--

LOCK TABLES `t_operation_log` WRITE;
/*!40000 ALTER TABLE `t_operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `t_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_order`
--

DROP TABLE IF EXISTS `t_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL,
  `user_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `pay_amount` decimal(10,2) NOT NULL,
  `freight_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `coupon_id` bigint DEFAULT NULL,
  `coupon_amount` decimal(10,2) DEFAULT '0.00',
  `status` varchar(16) NOT NULL DEFAULT 'pending_pay' COMMENT 'pending_pay/paid/shipping/done/cancelled/refunding',
  `receiver_snapshot` json DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=70004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_order`
--

LOCK TABLES `t_order` WRITE;
/*!40000 ALTER TABLE `t_order` DISABLE KEYS */;
INSERT INTO `t_order` VALUES (70001,'2026070100010001',10001,5001,198.00,198.00,0.00,NULL,0.00,'done','{\"phone\": \"13800138000\", \"address\": \"上海市浦东新区世纪大道 100 号\", \"receiver\": \"萌萌\"}','2026-07-01 10:30:00',NULL,NULL,NULL,'2026-07-01 10:25:00','2026-07-20 09:13:04',0),(70002,'2026071500010002',10001,5001,158.00,158.00,0.00,NULL,0.00,'paid','{\"phone\": \"13800138000\", \"address\": \"上海市浦东新区世纪大道 100 号\", \"receiver\": \"萌萌\"}','2026-07-15 14:00:00',NULL,NULL,NULL,'2026-07-15 13:55:00','2026-07-20 09:13:04',0),(70003,'2026071600010003',10001,NULL,89.00,89.00,0.00,NULL,0.00,'pending_pay','{\"phone\": \"13800138000\", \"address\": \"上海市浦东新区世纪大道 100 号\", \"receiver\": \"萌萌\"}',NULL,NULL,NULL,NULL,'2026-07-16 09:10:00','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_order_item`
--

DROP TABLE IF EXISTS `t_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `product_image` varchar(255) DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL,
  `snapshot_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_order_item`
--

LOCK TABLES `t_order_item` WRITE;
/*!40000 ALTER TABLE `t_order_item` DISABLE KEYS */;
INSERT INTO `t_order_item` VALUES (1,70001,1001,1,'皇家猫粮 K36 成猫专用 4kg','/src/assets/images/cart/p1-dogfood.jpg',198.00,1,NULL),(2,70003,1004,4,'Cat Magic 猫抓板剑麻猫窝','/src/assets/images/cart/r1-cat-scratcher.jpg',89.00,1,NULL);
/*!40000 ALTER TABLE `t_order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_pet`
--

DROP TABLE IF EXISTS `t_pet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_pet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `breed_id` bigint NOT NULL,
  `gender` tinyint NOT NULL DEFAULT '2' COMMENT '0公/1母/2未知',
  `birthday` date DEFAULT NULL,
  `weight` decimal(5,2) DEFAULT NULL,
  `neutered` tinyint NOT NULL DEFAULT '0',
  `avatar` varchar(255) DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `description` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_pet`
--

LOCK TABLES `t_pet` WRITE;
/*!40000 ALTER TABLE `t_pet` DISABLE KEYS */;
INSERT INTO `t_pet` VALUES (1,10001,'Buddy',1,1,0,'2022-03-15',25.50,1,'https://api.dicebear.com/7.x/dog/svg?seed=buddy','[\"亲人\", \"活泼\"]','我的金毛宝宝','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(2,10001,'Moka',2,7,1,'2023-06-01',4.20,0,'https://api.dicebear.com/7.x/cat/svg?seed=moka','[\"高冷\", \"挑食\"]','布偶小公主','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(3,10002,'Pepper',2,6,0,'2023-09-20',3.80,1,'https://api.dicebear.com/7.x/cat/svg?seed=pepper','[\"黏人\"]','英短弟弟','2026-07-20 09:13:04','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_pet` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_pet_breed`
--

DROP TABLE IF EXISTS `t_pet_breed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_pet_breed` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物品种';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_pet_breed`
--

LOCK TABLES `t_pet_breed` WRITE;
/*!40000 ALTER TABLE `t_pet_breed` DISABLE KEYS */;
INSERT INTO `t_pet_breed` VALUES (1,1,'金毛'),(2,1,'柯基'),(3,1,'柴犬'),(4,1,'拉布拉多'),(5,1,'萨摩耶'),(6,2,'英短'),(7,2,'美短'),(8,2,'布偶猫'),(9,2,'橘猫'),(10,2,'暹罗猫'),(11,3,'荷兰垂耳兔'),(12,3,'安哥拉兔'),(13,4,'虎皮鹦鹉'),(14,4,'仓鼠');
/*!40000 ALTER TABLE `t_pet_breed` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_pet_category`
--

DROP TABLE IF EXISTS `t_pet_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_pet_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物种类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_pet_category`
--

LOCK TABLES `t_pet_category` WRITE;
/*!40000 ALTER TABLE `t_pet_category` DISABLE KEYS */;
INSERT INTO `t_pet_category` VALUES (1,'犬','https://img.icons8.com/color/96/dog.png',0),(2,'猫','https://img.icons8.com/color/96/cat.png',0),(3,'兔','https://img.icons8.com/color/96/rabbit.png',0),(4,'异宠','https://img.icons8.com/color/96/parrot.png',0);
/*!40000 ALTER TABLE `t_pet_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_pet_health_record`
--

DROP TABLE IF EXISTS `t_pet_health_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_pet_health_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pet_id` bigint NOT NULL,
  `record_type` varchar(16) NOT NULL COMMENT 'vaccine/deworming/checkup/weight/illness',
  `title` varchar(64) NOT NULL,
  `content` text,
  `record_date` date NOT NULL,
  `next_date` date DEFAULT NULL,
  `reminder` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_pet` (`pet_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物健康记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_pet_health_record`
--

LOCK TABLES `t_pet_health_record` WRITE;
/*!40000 ALTER TABLE `t_pet_health_record` DISABLE KEYS */;
INSERT INTO `t_pet_health_record` VALUES (1,1,'vaccine','狂犬疫苗','已注射进口狂犬疫苗','2025-04-12','2026-04-12',1,'2026-07-20 09:13:04',0),(2,1,'deworming','体内驱虫','拜耳拜宠清','2025-05-10','2025-08-10',0,'2026-07-20 09:13:04',0),(3,1,'checkup','年度体检','血常规、生化全项正常','2025-06-20','2026-06-20',1,'2026-07-20 09:13:04',0),(4,2,'vaccine','猫三联','首免完成','2025-03-15','2026-03-15',1,'2026-07-20 09:13:04',0),(5,2,'weight','体重记录','4.2kg','2025-07-01',NULL,0,'2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_pet_health_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_post`
--

DROP TABLE IF EXISTS `t_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(64) DEFAULT NULL,
  `user_avatar` varchar(255) DEFAULT NULL,
  `content` text NOT NULL,
  `images` json DEFAULT NULL,
  `video_url` varchar(255) DEFAULT NULL,
  `topic_id` bigint DEFAULT NULL,
  `topic_name` varchar(64) DEFAULT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `comment_count` int NOT NULL DEFAULT '0',
  `collect_count` int NOT NULL DEFAULT '0',
  `visible` varchar(16) NOT NULL DEFAULT 'public',
  `audit_status` varchar(16) NOT NULL DEFAULT 'approved' COMMENT 'pending/approved/rejected',
  `audit_reason` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区帖子';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_post`
--

LOCK TABLES `t_post` WRITE;
/*!40000 ALTER TABLE `t_post` DISABLE KEYS */;
INSERT INTO `t_post` VALUES (1,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy','今天 Buddy 第一次去泳池,兴奋得不行！','[\"https://api.dicebear.com/7.x/dog/svg?seed=buddy\"]',NULL,1,'晒萌宠',38,12,8,'public','approved',NULL,'2026-07-15 14:30:00',0),(2,10003,'奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka','Moka 挑食怎么办?试了三种猫粮都兴趣不大,求支招 🥺','[\"https://api.dicebear.com/7.x/cat/svg?seed=moka\"]',NULL,2,'日常护理',25,36,4,'public','approved',NULL,'2026-07-14 09:12:00',0),(3,10004,'豆豆','https://api.dicebear.com/7.x/avataaars/svg?seed=pepper','新手养猫记第 1 天,奶糕喂多少合适?','[\"https://api.dicebear.com/7.x/cat/svg?seed=pepper\"]',NULL,2,'日常护理',9,5,1,'public','approved',NULL,'2026-07-16 22:08:00',0),(4,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy','推荐一款性价比超高的猫抓板,主子爱得不得了 ✨','[\"https://api.dicebear.com/7.x/cat/svg?seed=buddy2\"]',NULL,4,'健康问答',17,6,3,'public','approved',NULL,'2026-07-13 16:45:00',0),(5,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate','刚入手的饮水机,App 控制真的香~','[\"/src/assets/images/cart/p3-fountain.jpg\"]',NULL,3,'美食分享',12,3,2,'public','approved',NULL,'2026-07-17 08:20:00',0),(6,10003,'奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka','含敏感词测试【垃圾广告】应被审核拦截',NULL,NULL,1,'晒萌宠',0,0,0,'public','pending',NULL,'2026-07-18 10:00:00',0);
/*!40000 ALTER TABLE `t_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_post_comment`
--

DROP TABLE IF EXISTS `t_post_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_post_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(64) DEFAULT NULL,
  `user_avatar` varchar(255) DEFAULT NULL,
  `content` text NOT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子评论';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_post_comment`
--

LOCK TABLES `t_post_comment` WRITE;
/*!40000 ALTER TABLE `t_post_comment` DISABLE KEYS */;
INSERT INTO `t_post_comment` VALUES (1,1,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate','太可爱了！',5,'2026-07-20 09:13:04',0),(2,1,10004,'豆豆','https://api.dicebear.com/7.x/avataaars/svg?seed=pepper','Buddy 棒棒的',2,'2026-07-20 09:13:04',0),(3,2,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate','可以试试罐头拌粮',8,'2026-07-20 09:13:04',0),(4,2,10004,'豆豆','https://api.dicebear.com/7.x/avataaars/svg?seed=pepper','换冻干试试',3,'2026-07-20 09:13:04',0),(5,3,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy','幼猫 30-50g/天',4,'2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_post_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_post_like`
--

DROP TABLE IF EXISTS `t_post_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子点赞';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_post_like`
--

LOCK TABLES `t_post_like` WRITE;
/*!40000 ALTER TABLE `t_post_like` DISABLE KEYS */;
/*!40000 ALTER TABLE `t_post_like` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_product`
--

DROP TABLE IF EXISTS `t_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `category_id` bigint NOT NULL,
  `brand_id` bigint DEFAULT NULL,
  `merchant_id` bigint DEFAULT NULL,
  `main_image` varchar(255) NOT NULL,
  `sub_images` json DEFAULT NULL,
  `detail` longtext,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0下架/1上架',
  `sale_count` int NOT NULL DEFAULT '0',
  `view_count` int NOT NULL DEFAULT '0',
  `rating` decimal(3,2) NOT NULL DEFAULT '5.00',
  `sale_price` decimal(10,2) NOT NULL,
  `original_price` decimal(10,2) DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SPU';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_product`
--

LOCK TABLES `t_product` WRITE;
/*!40000 ALTER TABLE `t_product` DISABLE KEYS */;
INSERT INTO `t_product` VALUES (1001,'皇家猫粮 K36 成猫专用 4kg',11,1,NULL,'/src/assets/images/cart/p1-dogfood.jpg','[\"/src/assets/images/cart/p1-dogfood.jpg\"]','专为成猫设计,均衡营养,亮泽毛发',1,1280,8413,4.80,198.00,268.00,'[\"猫粮\", \"成猫\", \"进口\"]','2026-07-20 09:13:04','2026-07-20 21:26:47',0),(1002,'渴望 Orijen 鸡肉味狗粮 2kg',12,2,NULL,'/src/assets/images/cart/p2-salmon.jpg','[\"/src/assets/images/cart/p2-salmon.jpg\"]','加拿大原装进口,鲜肉配方',1,856,5210,4.90,320.00,399.00,'[\"狗粮\", \"进口\", \"鲜肉\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1003,'小佩 SOLO 智能宠物饮水机',22,3,NULL,'/src/assets/images/cart/p3-fountain.jpg','[\"/src/assets/images/cart/p3-fountain.jpg\"]','活水循环,三重过滤,App 远程控制',1,532,3420,4.70,469.00,599.00,'[\"饮水机\", \"智能\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1004,'Cat Magic 猫抓板剑麻猫窝',23,4,NULL,'/src/assets/images/cart/r1-cat-scratcher.jpg','[\"/src/assets/images/cart/r1-cat-scratcher.jpg\"]','天然剑麻,耐磨耐抓,猫咪最爱',1,412,2890,4.60,89.00,128.00,'[\"猫窝\", \"剑麻\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1005,'PetSafe 智能自动喂食器',22,3,NULL,'/src/assets/images/cart/r2-feeder.jpg','[\"/src/assets/images/cart/r2-feeder.jpg\"]','可定时定量,App 远程投喂',1,268,1980,4.50,369.00,459.00,'[\"喂食器\", \"智能\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1006,'ZEZE 宠物互动玩具组合',41,5,NULL,'/src/assets/images/cart/r3-toys.jpg','[\"/src/assets/images/cart/r3-toys.jpg\"]','逗猫棒+激光+响纸组合,让爱宠活力满满',1,723,4120,4.70,59.00,89.00,'[\"玩具\", \"互动\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1007,'PETFIT 宠物窝床四季通用',23,6,NULL,'/src/assets/images/cart/r4-bed.jpg','[\"/src/assets/images/cart/r4-bed.jpg\"]','柔软舒适,可拆洗,适合中小型犬猫',1,198,1560,4.40,129.00,169.00,'[\"窝床\", \"四季\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1008,'卫仕 猫用营养膏 120g',32,7,NULL,'/src/assets/images/consult/prod1-probiotic.jpg','[\"/src/assets/images/consult/prod1-probiotic.jpg\"]','补充维生素,亮毛护肤',1,1450,6890,4.80,39.90,59.00,'[\"营养\", \"猫用\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1009,'谷登 化毛膏 营养补充',32,7,NULL,'/src/assets/images/consult/prod2-hairball.jpg','[\"/src/assets/images/consult/prod2-hairball.jpg\"]','温和化毛,改善肠胃',1,982,4520,4.60,49.00,69.00,'[\"化毛\", \"营养\"]','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(1010,'PetMate 户外便携宠物水瓶 550ml',22,3,NULL,'/src/assets/images/products/p11-shiba-bottle.jpg','[\"/src/assets/images/products/p11-shiba-bottle.jpg\"]','<section><h2>核心卖点</h2><ul><li>户外便携:一体式收纳,可挂背包侧袋</li><li>550ml 大容量,一按即出水</li><li>食品级 ABS 材质,不含 BPA,安全无异味</li><li>防漏锁扣设计,倒置不洒</li><li>适用中小型犬 / 猫 / 通用宠物</li></ul><h2>产品参数</h2><table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;max-width:520px;\"><tr><th align=\"left\" style=\"background:#f7f7f7;\">参数</th><th align=\"left\" style=\"background:#f7f7f7;\">规格</th></tr><tr><td>容量</td><td>550ml</td></tr><tr><td>材质</td><td>食品级 ABS + 硅胶密封圈</td></tr><tr><td>颜色</td><td>蓝色 / 灰色</td></tr><tr><td>尺寸</td><td>直径 7cm × 高 23cm</td></tr><tr><td>净重</td><td>约 180g</td></tr><tr><td>适用</td><td>中小型犬 / 猫 / 通用宠物</td></tr></table><h2>使用建议</h2><p>徒步、露营、长途遛狗时为宠物随时补水。按压式出水口让陌生环境下的爱宠也能轻松喝到干净的水,告别一次性水瓶的浪费与不便。出行前请装满清水,按压顶部按钮即可出流;锁扣关闭后倒置、摇晃均不渗漏。</p><h2>清洁保养</h2><p>瓶身可拆解清洗,建议每周用温水加少量中性洗涤剂彻底清洁一次,晾干后再组装。硅胶圈可单独取出清洁,避免长期积水产生异味。</p><h2>包装清单</h2><p>水瓶主体 × 1、防漏硅胶圈(备用)× 1、说明书 × 1</p></section>',1,268,1985,4.80,119.00,159.00,'[\"户外\", \"便携\", \"饮水\", \"PetMate\"]','2026-07-20 10:06:40','2026-07-20 10:33:50',0);
/*!40000 ALTER TABLE `t_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_product_category`
--

DROP TABLE IF EXISTS `t_product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT '0',
  `name` varchar(32) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `level` tinyint NOT NULL DEFAULT '1',
  `sort` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_product_category`
--

LOCK TABLES `t_product_category` WRITE;
/*!40000 ALTER TABLE `t_product_category` DISABLE KEYS */;
INSERT INTO `t_product_category` VALUES (1,0,'宠物食品','https://img.icons8.com/color/96/pet-food.png',1,1),(2,0,'宠物用品','https://img.icons8.com/color/96/pet-supplies.png',1,2),(3,0,'医疗保健','https://img.icons8.com/color/96/medicine.png',1,3),(4,0,'玩具服饰','https://img.icons8.com/color/96/pet-toys.png',1,4),(11,1,'猫粮',NULL,2,1),(12,1,'狗粮',NULL,2,2),(13,1,'零食罐头',NULL,2,3),(21,2,'猫砂用品',NULL,2,1),(22,2,'食盆水具',NULL,2,2),(23,2,'窝床家具',NULL,2,3),(31,3,'驱虫保健',NULL,2,1),(32,3,'营养补剂',NULL,2,2),(41,4,'宠物玩具',NULL,2,1),(42,4,'美容洗护',NULL,2,2);
/*!40000 ALTER TABLE `t_product_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_product_review`
--

DROP TABLE IF EXISTS `t_product_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_product_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(64) DEFAULT NULL,
  `user_avatar` varchar(255) DEFAULT NULL,
  `rating` tinyint NOT NULL DEFAULT '5' COMMENT '1-5 鏄',
  `content` text,
  `images` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鍟嗗搧璇勪环';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_product_review`
--

LOCK TABLES `t_product_review` WRITE;
/*!40000 ALTER TABLE `t_product_review` DISABLE KEYS */;
INSERT INTO `t_product_review` VALUES (1,1001,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',5,'我家猫主子挑食,这款一吃就爱上,毛发都变亮了,回购第三次了!','[\"/src/assets/images/cart/p1-dogfood.jpg\"]','2026-07-12 10:25:00',0),(2,1001,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',4,'性价比不错,8kg 装更划算,快递略慢。',NULL,'2026-07-10 18:08:00',0),(3,1001,10003,'奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka',5,'正品,大包装吃得放心,推荐！',NULL,'2026-07-08 09:30:00',0),(4,1003,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',5,'App 控制真香,出差也能远程给猫加水,三重过滤水喝着放心。','[\"/src/assets/images/cart/p3-fountain.jpg\"]','2026-07-15 11:20:00',0),(5,1004,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',4,'猫主子终于不挠沙发了,剑麻材质很耐磨。',NULL,'2026-07-14 20:45:00',0),(6,1010,10003,'奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka',5,'出门带水太方便了,一键出水,硅胶圈不漏。','[\"/src/assets/images/products/p11-shiba-bottle.jpg\"]','2026-07-16 08:10:00',0);
/*!40000 ALTER TABLE `t_product_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_product_sku`
--

DROP TABLE IF EXISTS `t_product_sku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `sku_code` varchar(64) NOT NULL,
  `spec_values` json DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `stock` int NOT NULL DEFAULT '0',
  `image` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_product_sku`
--

LOCK TABLES `t_product_sku` WRITE;
/*!40000 ALTER TABLE `t_product_sku` DISABLE KEYS */;
INSERT INTO `t_product_sku` VALUES (1,1001,'SKU-1001-001','{\"口味\": \"鸡肉\", \"规格\": \"4kg\"}',198.00,320,NULL),(2,1001,'SKU-1001-002','{\"口味\": \"鸡肉\", \"规格\": \"8kg\"}',358.00,180,NULL),(3,1002,'SKU-1002-001','{\"口味\": \"鸡肉\", \"规格\": \"2kg\"}',320.00,220,NULL),(4,1002,'SKU-1002-002','{\"口味\": \"鸡肉\", \"规格\": \"6kg\"}',720.00,120,NULL),(5,1003,'SKU-1003-001','{\"颜色\": \"白色\"}',469.00,80,NULL),(6,1003,'SKU-1003-002','{\"颜色\": \"灰色\"}',469.00,60,NULL),(7,1004,'SKU-1004-001','{\"颜色\": \"米白\"}',89.00,200,NULL),(8,1005,'SKU-1005-001','{\"容量\": \"3L\"}',369.00,150,NULL),(9,1006,'SKU-1006-001','{\"套装\": \"标准\"}',59.00,500,NULL),(10,1007,'SKU-1007-001','{\"尺寸\": \"M\"}',129.00,220,NULL),(11,1007,'SKU-1007-002','{\"尺寸\": \"L\"}',169.00,160,NULL),(12,1008,'SKU-1008-001','{\"口味\": \"鸡肉\"}',39.90,600,NULL),(13,1009,'SKU-1009-001','{\"规格\": \"120g\"}',49.00,400,NULL),(14,1010,'SKU-1010-001','{\"容量\": \"550ml\", \"颜色\": \"蓝色\"}',119.00,200,NULL),(15,1010,'SKU-1010-002','{\"容量\": \"550ml\", \"颜色\": \"灰色\"}',119.00,200,NULL);
/*!40000 ALTER TABLE `t_product_sku` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_report`
--

DROP TABLE IF EXISTS `t_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint NOT NULL,
  `target_type` varchar(16) NOT NULL COMMENT 'post/comment/user',
  `target_id` bigint NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/resolved/rejected',
  `handle_note` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='涓炬姤';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_report`
--

LOCK TABLES `t_report` WRITE;
/*!40000 ALTER TABLE `t_report` DISABLE KEYS */;
INSERT INTO `t_report` VALUES (1,10001,'post',6,'明显是垃圾广告,内容空泛且刷屏','pending',NULL,'2026-07-18 11:00:00',NULL),(2,10002,'comment',4,'评论含人身攻击,请求管理员处理','pending',NULL,'2026-07-18 15:30:00',NULL),(3,10004,'user',10003,'多次发广告,私信骚扰','pending',NULL,'2026-07-19 09:45:00',NULL);
/*!40000 ALTER TABLE `t_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_review`
--

DROP TABLE IF EXISTS `t_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(64) DEFAULT NULL,
  `user_avatar` varchar(255) DEFAULT NULL,
  `rating` decimal(2,1) NOT NULL DEFAULT '5.0',
  `content` text,
  `images` json DEFAULT NULL,
  `reply` text,
  `replied_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_review`
--

LOCK TABLES `t_review` WRITE;
/*!40000 ALTER TABLE `t_review` DISABLE KEYS */;
INSERT INTO `t_review` VALUES (1,5001,90001,10001,'萌宠家长','https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',5.0,'环境很棒,Buddy 剪完毛精神多了,推荐李老师！',NULL,'谢谢您的支持,期待再次光临~','2026-07-16 18:30:00','2026-07-20 09:13:04',0),(2,5001,90002,10002,'小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',4.5,'服务态度好,价格略贵',NULL,NULL,NULL,'2026-07-20 09:13:04',0),(3,5001,90003,10003,'奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka',5.0,'寄养很放心,猫咪回来一点都不认生',NULL,'感谢信任~','2026-07-15 10:20:00','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_sensitive_word`
--

DROP TABLE IF EXISTS `t_sensitive_word`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_sensitive_word`
--

LOCK TABLES `t_sensitive_word` WRITE;
/*!40000 ALTER TABLE `t_sensitive_word` DISABLE KEYS */;
INSERT INTO `t_sensitive_word` VALUES (1,'垃圾广告','2026-07-20 09:13:04'),(2,'违法','2026-07-20 09:13:04'),(3,'敏感','2026-07-20 09:13:04'),(4,'暴力','2026-07-20 09:13:04');
/*!40000 ALTER TABLE `t_sensitive_word` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_service_item`
--

DROP TABLE IF EXISTS `t_service_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_service_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `category` varchar(32) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `duration` int NOT NULL DEFAULT '60',
  `cover` varchar(255) DEFAULT NULL,
  `description` text,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_service_item`
--

LOCK TABLES `t_service_item` WRITE;
/*!40000 ALTER TABLE `t_service_item` DISABLE KEYS */;
INSERT INTO `t_service_item` VALUES (1,5001,'精致洗护 SPA','美容',158.00,60,'/src/assets/images/home/hero-grooming.jpg','专业宠物美容师,含洗澡+修剪+吹干',1,'2026-07-20 09:13:04',0),(2,5001,'幼犬基础训练课','训练',1200.00,90,'/src/assets/images/knowledge/c1-dog-train.jpg','5 次课,训犬师一对一教学',1,'2026-07-20 09:13:04',0),(3,5001,'宠物寄养 24h','寄养',120.00,1440,'/src/assets/images/service/p1-board.jpg','24 小时专人照看,含两餐',1,'2026-07-20 09:13:04',0),(4,5001,'上门遛狗 1h','遛狗',60.00,60,'/src/assets/images/service/p2-home.jpg','专业遛狗师上门服务',1,'2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_service_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_store`
--

DROP TABLE IF EXISTS `t_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_store` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `type` varchar(16) NOT NULL DEFAULT 'comprehensive',
  `address` varchar(255) NOT NULL,
  `lng` decimal(10,6) DEFAULT '0.000000',
  `lat` decimal(10,6) DEFAULT '0.000000',
  `business_hours` json DEFAULT NULL,
  `photos` json DEFAULT NULL,
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核/1通过/2驳回',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_store`
--

LOCK TABLES `t_store` WRITE;
/*!40000 ALTER TABLE `t_store` DISABLE KEYS */;
INSERT INTO `t_store` VALUES (5001,20001,'PetMate 宠物家体验店','comprehensive','上海市浦东新区世纪大道 100 号',121.504500,31.238900,'{\"周末\": \"09:00-22:00\", \"周一-周五\": \"10:00-21:00\"}','[\"https://api.dicebear.com/7.x/dog/svg?seed=store\"]',1,'2026-07-20 09:13:04','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_store` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_system_config`
--

DROP TABLE IF EXISTS `t_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_system_config` (
  `config_key` varchar(64) NOT NULL,
  `config_value` text NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_system_config`
--

LOCK TABLES `t_system_config` WRITE;
/*!40000 ALTER TABLE `t_system_config` DISABLE KEYS */;
INSERT INTO `t_system_config` VALUES ('consult_quota_day','50','2026-07-20 09:13:04'),('default_freight','0','2026-07-20 09:13:04'),('site_name','PetMate 智能宠物管家','2026-07-20 09:13:04'),('sms_demo_code','123456','2026-07-20 09:13:04');
/*!40000 ALTER TABLE `t_system_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_topic`
--

DROP TABLE IF EXISTS `t_topic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_topic` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='话题';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_topic`
--

LOCK TABLES `t_topic` WRITE;
/*!40000 ALTER TABLE `t_topic` DISABLE KEYS */;
INSERT INTO `t_topic` VALUES (1,'晒萌宠','https://img.icons8.com/color/96/pets.png'),(2,'日常护理','https://img.icons8.com/color/96/dog.png'),(3,'美食分享','https://img.icons8.com/color/96/pet-food.png'),(4,'健康问答','https://img.icons8.com/color/96/heart-with-pulse.png');
/*!40000 ALTER TABLE `t_topic` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_user`
--

DROP TABLE IF EXISTS `t_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `open_id` varchar(64) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `nickname` varchar(64) NOT NULL DEFAULT '萌宠家长',
  `avatar` varchar(512) DEFAULT NULL,
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '0未知/1男/2女',
  `birthday` date DEFAULT NULL,
  `vip_level` tinyint NOT NULL DEFAULT '0',
  `bio` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0正常/1封禁/2注销',
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `password` varchar(255) DEFAULT NULL COMMENT 'BCrypt hash, NULL means use demo pwd 123456',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  KEY `idx_vip_level` (`vip_level`)
) ENGINE=InnoDB AUTO_INCREMENT=90004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_user`
--

LOCK TABLES `t_user` WRITE;
/*!40000 ALTER TABLE `t_user` DISABLE KEYS */;
INSERT INTO `t_user` VALUES (10001,NULL,'13800138000','李丽','http://localhost:8080/api/v1/static/uploads/avatar/u10001_1784531796450.jpg',1,NULL,1,'',0,'2026-07-20 21:20:36','2026-07-20 09:13:04','2026-07-20 21:20:36',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(10002,NULL,'13800138001','小糯团','https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',1,NULL,0,NULL,0,NULL,'2026-07-20 09:13:04','2026-07-20 18:21:26',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(10003,NULL,'13800138002','奶昔主人','https://api.dicebear.com/7.x/avataaars/svg?seed=moka',2,NULL,2,NULL,0,NULL,'2026-07-20 09:13:04','2026-07-20 18:21:26',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(10004,NULL,'13800138003','豆豆','https://api.dicebear.com/7.x/avataaars/svg?seed=pepper',0,NULL,0,NULL,0,NULL,'2026-07-20 09:13:04','2026-07-20 18:21:26',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(20001,NULL,'13900000001','宠物家旗舰店','https://api.dicebear.com/7.x/avataaars/svg?seed=store1',0,NULL,5,NULL,0,'2026-07-20 21:15:07','2026-07-20 09:13:04','2026-07-20 21:15:07',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(90001,NULL,'13700000000','超管 Admin','https://api.dicebear.com/7.x/avataaars/svg?seed=admin',1,NULL,5,NULL,0,'2026-07-20 21:11:52','2026-07-20 09:13:04','2026-07-20 21:11:51',0,'$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),(90002,NULL,'13912345678','Tester','https://api.dicebear.com/7.x/avataaars/svg?seed=13912345678',0,NULL,0,NULL,0,'2026-07-20 15:41:26','2026-07-20 15:40:55','2026-07-20 15:41:25',0,'$2a$10$wZU/Xtur/3p691VJ4OwdPOz9TKWTZKW.q7Sh37pUQ3T4PUUpMgnMG'),(90003,NULL,'13762522533','罗汀','/api/v1/static/uploads/avatar/u90003_1784567202575.png',0,NULL,0,'',0,'2026-07-20 23:54:06','2026-07-20 18:24:33','2026-07-21 01:06:42',0,'$2a$10$TqqerT/jcuFOxuAsDcraTeg1HX7TUhckybi8azSS6CSP4pSTuwE5O');
/*!40000 ALTER TABLE `t_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_user_address`
--

DROP TABLE IF EXISTS `t_user_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `receiver` varchar(64) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `province` varchar(32) NOT NULL,
  `city` varchar(32) NOT NULL,
  `district` varchar(32) NOT NULL,
  `detail` varchar(255) NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `tag` varchar(32) DEFAULT 'home',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货地址';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_user_address`
--

LOCK TABLES `t_user_address` WRITE;
/*!40000 ALTER TABLE `t_user_address` DISABLE KEYS */;
INSERT INTO `t_user_address` VALUES (1,10001,'萌萌','13800138000','上海市','上海市','浦东新区','世纪大道 100 号',1,'home','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(2,10001,'萌萌','13800138000','上海市','上海市','徐汇区','漕溪北路 88 号',0,'company','2026-07-20 09:13:04','2026-07-20 09:13:04',0),(3,10002,'小糯','13800138001','北京市','北京市','朝阳区','建国路 1 号',1,'home','2026-07-20 09:13:04','2026-07-20 09:13:04',0);
/*!40000 ALTER TABLE `t_user_address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `t_user_coupon`
--

DROP TABLE IF EXISTS `t_user_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'available' COMMENT 'available/used/expired',
  `claim_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `t_user_coupon`
--

LOCK TABLES `t_user_coupon` WRITE;
/*!40000 ALTER TABLE `t_user_coupon` DISABLE KEYS */;
/*!40000 ALTER TABLE `t_user_coupon` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-21  9:40:44
