/*
* 有来商城数据库
* MySQL8.x版本
*/

-- ----------------------------
-- 系统管理数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_system DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;

-- ----------------------------
-- OAuth2数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_oauth2_server DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;

-- ----------------------------
-- 商城会员数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_mall_ums DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;
-- ----------------------------
-- 商城商品数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_mall_pms DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;
-- ----------------------------
-- 商城订单数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_mall_oms DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;
-- ----------------------------
-- 商城营销数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_mall_sms DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;

-- ----------------------------
-- 代码生成数据库
-- ----------------------------
CREATE DATABASE IF NOT EXISTS youlai_codegen DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;