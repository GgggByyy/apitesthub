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
