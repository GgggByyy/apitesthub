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
| M1 | 项目/接口定义管理（CRUD + 试调 + Vue3 前端） | ✅ |
| M2 | 执行引擎 v1：同步执行套件 + 三类断言 + 结果落库 | ✅ |
| M3 | 执行引擎 v2：线程池并发 + 参数化变量 + 链式调用 | ✅ |
| M4 | WebSocket 实时日志 + Redis 状态缓存/分布式锁 | ✅ |
| M5 | Quartz 定时回归 + 失败重跑 + 测试报告 | ✅ |
| M6 | Docker Compose 一键部署 + 单元测试 | ⬜ |

## M5 已实现

- **Quartz 定时回归**：`schedule_job` 表配置 cron（Quartz 格式：秒 分 时 日 月 周）动态注册/删除 Job；应用重启自动重注册全部启用计划；`@DisallowConcurrentExecution` + 分布式锁双层幂等（定时与手动触发撞车时后者 409）
- **失败自动重跑**：非 RETRY 任务存在失败用例 → 只重跑失败用例一次，`trigger_type=RETRY` 单独记录；只重跑一层防止无限循环掩盖真失败；重跑在主锁释放后触发（实测 1ms 内撞锁的竞态修复）
- **测试报告**（ECharts）：通过率环形图 + 用例耗时 Top 柱状图（慢接口橙色标记）+ 失败归因表；**响应时间 > 800ms 自动标记慢接口**；支持 JSON / 自包含 HTML 导出
- **验收数据**：每分钟计划整点自动触发（SCHEDULED）；停掉被测系统后回归 0/8 全红并自动生成 RETRY 0/8 重跑记录；恢复后回归转绿

## M4 已实现

- **任务异步化**：执行触发立即返回（12ms，原同步需 ~850ms）；套件调度池与用例执行池分离（不同工作粒度，避免互相饥饿）
- **WebSocket 实时日志**：前端订阅任务（`/ws/task?taskId=`，JWT 握手校验——浏览器 WS API 不能自定义 header，token 走 query）；引擎每完成一条用例推送事件，实测 500 条任务浏览器收到全部 501 条事件；`ConcurrentWebSocketSessionDecorator` 解决 session 并发发送（串行化 + 慢消费者缓冲上限自动断开）
- **Redis 三件套**：
  - `INCR task:progress:{id}` 实时进度 + `SET task:live:{id}` 实时通过/失败数（列表页 RUNNING 行合并展示）
  - `SET task:status:{id}` 终态缓存 TTL 1h
  - `SETNX lock:suite:{id} EX 300` 分布式锁防重复触发：并发双触发一进一拒（409）；value 存 UUID + Lua 比对删除防误删
- **降级容错**：Redis 宕机时无锁执行 + 直查库（实测通过）；降级 catch 面用 `DataAccessException` 父类——Lettuce 命令超时抛 `QueryTimeoutException` 而非 `RedisConnectionFailureException`（实测踩坑）

## M3 已实现（执行引擎 v2，项目技术核心）

- **变量体系**：任务级 `VariableContext`（ConcurrentHashMap，方法参数传递而非 ThreadLocal——线程池线程复用会串数据）；接口定义中 `{{var}}` 占位符自动渲染，未定义变量显式失败
- **链式调用**：用例 `extract_rules` 按 JSONPath 从响应提取变量（如 `$.data.token → token`），后续用例的 headers/url/body 引用 `{{token}}` 自动注入——登录态自动续接，无需手动填 token
- **批次并行执行模型**（StagePlanner 依赖分析）：按变量消费/生产关系把套件用例切批——依赖已解锁的用例同批**并行**（自建 ThreadPoolExecutor），依赖前序变量的用例开新批**串行等待**；批次内生产的变量批次完成后解锁（并行执行中彼此不可见）
- **自建线程池**：`ThreadPoolExecutor(8, 16, 60s, LinkedBlockingQueue(200), 命名工厂, CallerRunsPolicy)`——有界队列防 OOM，CallerRuns 保证用例不丢（提交线程亲自执行的天然背压）
- **验收数据**：链式 token 自动注入（请求快照可证）；500 条独立用例 647ms 全过（16 线程并行）；并发双任务变量严格隔离（无登录链的任务消费 `{{token}}` 报 undefined variable，不会捡到其他任务的 token）

## M2 已实现

- **测试用例**：`test_case` = 接口选择 + 断言集合 + 执行顺序；断言结构 `[{"type":"status","op":"eq","expect":200}]` 保存前逐条校验
- **测试套件**：`test_suite` = 用例有序集合（`case_ids` 顺序即执行顺序），只能引用同项目用例
- **执行引擎 v1**：`POST /api/suites/{id}/execute` 同步遍历执行 → 每条用例：HttpExecutor 发请求 → AssertEvaluator 断言 → `exec_task` + `exec_result` 落库（含请求快照/响应/耗时/失败原因）；单用例失败不中断套件（回归语义：一份报告看到全部结果）
- **三类断言**：状态码 eq / 响应体 JSONPath（eq/ne/gt/lt/contains，如 `$.code eq 200`）/ 响应时间上限 lt
- **任务查询**：`GET /api/tasks`（列表）、`GET /api/tasks/{id}/results`（明细）
- **前端**：项目页三标签（接口/用例/套件）+ 断言可视化编辑器 + 任务列表/报告页（可展开请求快照与响应体）

### 执行单元复用（设计要点）

M1 的"发送试一下"重构为 `engine.HttpExecutor` 组件——试调与执行引擎共用同一执行单元，保证"试调看到的"和"回归跑到的"行为完全一致；`AssertEvaluator` 为纯静态逻辑（无 Spring 依赖），是 M6 单元测试的核心靶子。

## M1 已实现

- **项目管理**：`GET/POST /api/projects`、`PUT/DELETE /api/projects/{id}`（删除级联清理接口定义，事务保证）
- **接口定义管理**：`GET/POST /api/projects/{pid}/apis`、`PUT/DELETE /api/apis/{id}`，支持 method/url/headers/params/body（JSON 字段保存前校验合法性）
- **发送试一下**：`POST /api/apis/{id}/try` —— 后端用 OkHttp 代理调用被测接口，返回 状态码/耗时/响应头/响应体（执行引擎的第一块砖）
- **数据隔离**：所有操作校验项目归属（owner 不符统一 404）
- **CORS**：放行 Vite 开发端口（localhost 与 127.0.0.1 两个 origin），预检 OPTIONS 在拦截器放行
- **Vue3 前端**：登录页 / 项目列表 / 接口管理页（表格 + 表单弹窗 + 试调结果弹窗），axios 统一封装（自动带 token、401 踢回登录、统一错误提示）

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

# 5. 启动前端开发服务器（Vue3 + Element Plus）
cd frontend && npm install && npm run dev
# 浏览器打开 http://localhost:5173，注册/登录后新建项目、录入接口、试调
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
