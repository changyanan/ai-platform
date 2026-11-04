-- PostgreSQL + PGVector 初始化。
-- 该脚本仅在数据卷首次创建时由官方镜像执行。
-- 应用侧的 Flyway 也会以幂等方式再次确认扩展存在。
CREATE EXTENSION IF NOT EXISTS vector;
