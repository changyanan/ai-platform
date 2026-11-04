-- ============================================================
-- 自动化集成测试库（MySQL 8）
-- 仅由 Maven test / application-test.yml 使用，应用运行时不连接。
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_platform_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'ai_platform'@'localhost'
    IDENTIFIED BY 'AiPlatform@2026';
CREATE USER IF NOT EXISTS 'ai_platform'@'%'
    IDENTIFIED BY 'AiPlatform@2026';

GRANT ALL PRIVILEGES ON ai_platform_test.* TO 'ai_platform'@'localhost';
GRANT ALL PRIVILEGES ON ai_platform_test.* TO 'ai_platform'@'%';

FLUSH PRIVILEGES;

SELECT 'ai_platform_test database initialized' AS result;
