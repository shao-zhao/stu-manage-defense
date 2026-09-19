# 第二轮完善与便携交付约定

目标：完善课程管理与可演示技术细节，增加有用的中文注释，交付 Windows x64 离线便携压缩包及全栈 Docker 运行方式。GPT-6 统筹验收，GPT-5.6 Terra 执行。保留原数据库与已有修改。

## 课程规则
- 状态 UNPUBLISHED（未发布）、PUBLISHED（开放选课）、CLOSED（停止选课）。教师管理本人课程，管理员管理全部。
- 未发布且无关联选课/成绩/资料：可完整编辑（包含编号、任课教师）或 DELETE /api/courses/{id}，关联记录存在则禁止删除并解释原因。
- 已发布/停开：可修改 location、description、coverUrl 等说明性信息；编号、名称、教师、学分、课时、学期、时间、容量锁定。后端只允许白名单变更，拒绝核心字段变更，不能静默忽略。
- POST /api/courses/{id}/close：停止新选课，保留已有选课和成绩流程；成绩草稿时在选学生仍可退课。
- POST /api/courses/{id}/reopen：仅 CLOSED 且 gradeStatus=DRAFT 可恢复选课。已进入成绩流程不能重新开放。
- 查询支持 status 筛选。学生课程中心可见已发布和停开课程但停开按钮解释不可新选；个人选课及成绩记录完整保留。
- 关键操作做确认、状态提示、加载及失败提示。锁课程行避免编辑/发布/删除/选课并发破坏约束。缓存变更后失效。

## 技术展示与讲解
- 前端新增“技术演示”页面，明确区分实际业务效果与解释。Redis 用真实 dashboard 读取展示命中、剩余 TTL、读数；ECharts 提供真实统计与交互；SSE 显示连接状态并指导双窗口业务触发；天气明确外网依赖；媒体与 Excel 提供入口。
- 后端 GET /api/system/status（ADMIN）返回 database:{status,product,version}、redis:{status}、app:{javaVersion,springBootVersion}，禁止输出密码、JWT、数据库URL、文件绝对路径。可用性失败用明确 DOWN。
- dashboard.cache 保持原字段，ttlSeconds 返回真实 Redis 剩余寿命，增加 generatedAt（缓存内容生成时间）；不能以伪造延迟或假数据冒充缓存效果。
- 为 request/api、路由鉴权、SSE 生命周期、Vue 行插槽、JWT、事务选课、成绩加权/幂等、缓存等关键代码补中文解释，说明为什么，不逐句翻译代码。
- 文档逐项写“代码位置 → 页面入口 → 操作步骤 → 预期效果 → 原理 → 常见答辩问题”。SSE 是 HTTP 流式推送，不声称其为非 HTTP 协议。

## 便携运行接口
- 前端默认同源 API（baseURL 空串），开发 Vite 代理 /api、/static 至9090，支持 VITE_API_BASE_URL 显式覆盖；SSE 与媒体共用地址解析，不能硬编码 localhost:9090。
- 成品：Spring Boot 可执行 JAR 内嵌前端 dist，同端口服务页面/API，直接刷新 Vue 路由可用，缺失 API/静态文件不得错误回退 HTML。
- Windows x64 便携包内含 Java17 运行时、MySQL、Redis、JAR、演示数据初始化、START.cmd、STOP.cmd、检查/备份脚本、运行教程及第三方许可。使用相对路径，兼容 Windows PowerShell5.1，无需 Node/Maven/Python/Git。默认独立端口18090/13306/16379；端口冲突明确报错，禁止结束外部进程。数据库和密钥首次生成，仅项目自有数据目录；重新启动保留业务数据。
- 不依赖开发机用户目录、已有3306数据库、JAVA_HOME或联网下载。离线时天气明确不可用，核心业务及其它扩展正常。必须在全新目录（包括空格/中文路径场景）以新数据目录实测初始化、重启、恢复和完整smoke。没有真实第二台电脑时如实说明验证范围。
- Docker 全栈方案包括应用、MySQL、Redis及持久卷/健康检查，说明首次联网拉镜像与离线便携的区别。不触碰当前有故障的 Docker 全局目录。
- 根 scripts/docs/package 由 delivery 独占，后端代码由 backend 独占，前端代码由 frontend 独占。打包时三方直接协商。最终验证通过后同步现有 GitHub 私有仓库，阶段提交；大型便携ZIP留本地交付，不入源码Git。
