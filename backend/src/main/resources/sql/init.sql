-- ApitestHub M0：用户与鉴权
CREATE DATABASE IF NOT EXISTS apitesthub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE apitesthub;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,          -- BCrypt 加密
    role VARCHAR(20) DEFAULT 'USER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- M1：项目（多项目隔离，owner_id 数据隔离）
CREATE TABLE IF NOT EXISTS `project` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_owner (owner_id)
);

-- M1：接口定义（被测接口档案）
CREATE TABLE IF NOT EXISTS `api_info` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    method VARCHAR(10) NOT NULL,             -- GET/POST/PUT/DELETE
    url VARCHAR(500) NOT NULL,
    headers TEXT,                            -- JSON: {"Content-Type":"application/json"}
    params TEXT,                             -- JSON: 查询参数
    body TEXT,                               -- JSON: 请求体
    updated_at DATETIME,
    KEY idx_project (project_id)
);

-- M2：测试用例（一次调用 + 断言集合）
CREATE TABLE IF NOT EXISTS `test_case` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,                -- 套件内执行顺序
    extract_rules TEXT,                      -- M3 预留：JSON [{"source":"$.data.token","var":"token"}]
    assertions TEXT,                         -- JSON [{"type":"status","op":"eq","expect":200}]
    enabled TINYINT DEFAULT 1,
    KEY idx_project (project_id)
);

-- M2：测试套件（用例有序集合，回归的基本单位）
CREATE TABLE IF NOT EXISTS `test_suite` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    case_ids TEXT,                           -- JSON数组 [1,2,3]，顺序即执行顺序
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
);

-- M2：执行任务（一次触发一条记录）
CREATE TABLE IF NOT EXISTS `exec_task` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,       -- MANUAL / SCHEDULED / RETRY
    status VARCHAR(20) NOT NULL,             -- PENDING/RUNNING/SUCCESS/FAILED
    total_count INT DEFAULT 0,
    pass_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    duration_ms BIGINT,
    started_at DATETIME,
    finished_at DATETIME,
    KEY idx_suite (suite_id),
    KEY idx_status_time (status, started_at)
);

-- M2：用例执行结果明细
CREATE TABLE IF NOT EXISTS `exec_result` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    case_name VARCHAR(100),
    request_snapshot TEXT,                   -- 实际发出的请求（变量已替换）
    response_status INT,
    response_body TEXT,
    response_time_ms INT,
    passed TINYINT DEFAULT 1,
    fail_reason VARCHAR(500),
    KEY idx_task (task_id)
);

-- M5：定时回归计划
CREATE TABLE IF NOT EXISTS `schedule_job` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    cron_expr VARCHAR(50) NOT NULL,          -- 如 0 * * * * ? 每分钟
    enabled TINYINT DEFAULT 1,
    last_fire_time DATETIME,
    KEY idx_suite (suite_id)
);
