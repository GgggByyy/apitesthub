# ApitestHub — 接口自动化测试平台

面向测试团队的 Web 端接口自动化测试平台：接口定义、用例编排、并发执行、实时日志、定时回归与测试报告。起因是作者在做后端项目时用 Postman 手工回归 27 个用例太低效，于是自研平台把用例管理、自动执行与回归报告做成工具链，并以自己的后端项目作为被测系统持续回归。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DD344C?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

## 总体架构（规划）

```
前端（Vue3 + Element Plus + axios）
        │ RESTful API (JSON)
后端 Spring Boot 3.2 (Java 17)
        ├── MyBatis-Plus ←→ MySQL 8      （用户/接口/用例/任务/报告）
        ├── Spring Data Redis            （执行状态缓存、分布式锁）
        ├── OkHttp                        （执行引擎：向被测系统发请求）
        ├── WebSocket                     （实时日志推送）
        ├── Quartz                        （定时回归）
        └── ThreadPoolExecutor（自建线程池）（用例并发执行）
被测系统（SUT）：任意 REST 服务
```

## 开发进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 工程骨架 + JWT 鉴权 + 统一响应/全局异常 + BCrypt | ✅ |
| M1 | 项目/接口定义管理（CRUD + 试调） | ⬜ |
| M2 | 执行引擎 v1：同步执行套件 + 断言 + 结果落库 | ⬜ |
| M3 | 执行引擎 v2：线程池并发 + 参数化变量 + 链式调用 | ⬜ |
| M4 | WebSocket 实时日志 + Redis 状态缓存/分布式锁 | ⬜ |
| M5 | Quartz 定时回归 + 失败重跑 + 测试报告 | ⬜ |
| M6 | Docker Compose 一键部署 + 单元测试 | ⬜ |

## M0 已实现

- **用户注册 / 登录**：`POST /api/user/register`、`POST /api/user/login`
- **JWT 无状态鉴权**：jjwt 0.12（HMAC-SHA），`Authorization: Bearer <token>`，全局拦截器统一校验
- **BCrypt 密码加密**：spring-security-crypto 慢哈希（自带随机盐）
- **统一响应** `ApiResponse<T>{code, message, data}` + `@RestControllerAdvice` 全局异常处理
- **本地依赖一键起**：`docker compose -f docker-compose.dev.yml up -d`（MySQL 8 + Redis 7）

### 验收（M0）

```bash
# 注册
curl -X POST localhost:8080/api/user/register -H "Content-Type: application/json" \
     -d '{"username":"tester","password":"pass123456"}'
# → {"code":0,"message":"success","data":null}

# 登录拿 token
curl -X POST localhost:8080/api/user/login -H "Content-Type: application/json" \
     -d '{"username":"tester","password":"pass123456"}'
# → {"code":0,...,"data":{"token":"eyJhbGciOi..."}}

# 带 token 访问受保护接口
curl localhost:8080/api/user/me -H "Authorization: Bearer <token>"
# → {"code":0,...,"data":{"id":1,"username":"tester","role":"USER",...}}

# 不带 token → 401
curl -i localhost:8080/api/user/me
# → HTTP/1.1 401 {"code":401,"message":"unauthorized: missing or invalid token",...}
```

## 快速开始

```bash
# 1. 起本地依赖（MySQL 8 + Redis 7）
docker compose -f docker-compose.dev.yml up -d

# 2. 初始化数据库（建库 + user 表）
docker exec -i apitesthub-mysql mysql -uroot -papitesthub_dev < backend/src/main/resources/sql/init.sql

# 3. 配置凭证：复制模板为 application-local.yml（root 密码默认 apitesthub_dev，见模板）
cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml

# 4. 启动后端（JDK 17+，本仓库 Java 17 编译目标）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 目录结构（M0）

```
apitesthub/
├── docker-compose.dev.yml    # 本地开发依赖（MySQL/Redis）
└── backend/
    └── src/main/java/com/apitesthub/
        ├── controller/       # UserController（注册/登录/me）
        ├── service/          # UserService（BCrypt 加密、登录校验）
        ├── mapper/           # UserMapper（MyBatis-Plus BaseMapper）
        ├── entity/ dto/      # User 实体 / 请求响应对象（含参数校验注解）
        ├── interceptor/      # JwtInterceptor（统一鉴权）
        ├── config/           # JwtConfig、WebConfig（拦截器注册）
        ├── exception/        # BizException + 全局异常处理
        └── common/           # ApiResponse 统一响应
```

## 设计约定

- 统一响应 `ApiResponse<T>{code, message, data}`，code：0 成功 / 400 业务错 / 401 未认证 / 404 不存在 / 500 系统错（与作者另一个项目 [family-health-manager](https://github.com/GgggByyy/family-health-manager) 保持同一套规范）
- 密码 BCrypt 慢哈希；登录失败统一提示，防账号枚举；任何接口不回传密码字段
- 数据库凭证、JWT 密钥全部外置（环境变量 / application-local.yml，不入库）
