-- ============================================================
-- 本地/生产运行数据库初始化（MySQL 8）
-- 运行应用只使用 ai_platform。
-- 集成测试库请单独执行 docs/sql/init-test.sql。
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'ai_platform'@'localhost'
    IDENTIFIED BY 'AiPlatform@2026';
CREATE USER IF NOT EXISTS 'ai_platform'@'%'
    IDENTIFIED BY 'AiPlatform@2026';

GRANT ALL PRIVILEGES ON ai_platform.* TO 'ai_platform'@'localhost';
GRANT ALL PRIVILEGES ON ai_platform.* TO 'ai_platform'@'%';

FLUSH PRIVILEGES;

SELECT 'ai_platform runtime database initialized' AS result;
