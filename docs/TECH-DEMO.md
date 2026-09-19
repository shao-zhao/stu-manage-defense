# 技术演示与答辩操作说明

本页只描述已实现代码与可观察效果。天气是唯一明确依赖外网的扩展；SSE 是基于 HTTP 长连接的服务端推送。

| 技术点 | 代码位置 | 页面入口与操作 | 预期效果 | 原理与答辩问答 |
|---|---|---|---|---|
| Redis 缓存 | `stu-backend/src/main/java/com/example/stubackend/service/SchoolService.java` 的 dashboard 读取与失效；Redis 配置在 `application.properties` | 管理端“技术演示”页点击缓存读取 | 页面显示真实 `cache.hit`、`ttlSeconds` 和 `generatedAt`。已有缓存时两次都可能命中；等待 TTL 到期或修改课程触发失效后，再读取可观察未命中到命中，命中时生成时间不变而 TTL 下降 | 缓存数据在 Redis 写入后设置 TTL；课程/选课/成绩变更清除相关键，避免展示旧统计。问：为什么不是内存 Map？答：Redis 可被多实例共享并有真实过期策略。 |
| ECharts | `stu-frontend/src/components/AppChart.vue`、`DashboardView.vue` | 管理员首页切换统计维度、悬停图表 | 图表随接口返回的课程/成绩统计更新，有 tooltip 和筛选交互 | ECharts 只负责将真实 API 数据可视化，统计仍由后端计算。问：图表是否造数？答：不造数，页面读取 dashboard 接口。 |
| SSE 通知 | `EventHub.java`、`ApiController` 的通知流；`NotificationsView.vue` | 任一已登录管理端保持连接，在技术演示页观察状态；教师提交成绩 | 页面收到 `event:notification`，顶部未读数同步；普通选课只刷新统计，不作为通知触发 | 浏览器用带 Bearer 的 HTTP 流连接；提交事务完成后才推送，断开 emitter 会清理。问：为何不用轮询？答：推送减少空请求并能及时展示状态。 |
| JWT 与路由鉴权 | `JwtAuthFilter.java`、`stu-frontend/src/stores/auth.ts`、`router/index.ts` | 三角色登录、切换页面；冻结账号后继续使用旧 token | 页面按角色导航；后端拒绝越权和冻结 token | 前端用于体验层的路由守卫，后端 filter 才是安全边界；JWT 每次请求解析并检查账号状态。 |
| 事务选课与成绩 | `SchoolService.java` 的选退课、`saveGrades`、成绩状态流 | 教师建课发布；学生选退重选；教师提交；管理员退回、审核、发布 | 容量不超卖；成绩从草稿到发布；重复发布不重复加学分 | 数据库事务把状态和关联记录作为整体提交，选课行锁防并发；成绩按权重算总分，已发布标记保障幂等。 |
| 媒体与 Excel | `ApiController`/`OperationsController`，`MediaView.vue`/`StaffView.vue` | 教师上传 PNG 或视频；管理员下载模板并导入 | 图片可预览，上传者可删除；Excel 错行整体回滚 | 后端验证上传类型和所有者；Excel 在一个事务内校验，任一重复行失败就没有半条新数据。 |
| 天气 API | 前端天气服务及 dashboard 组件 | 有网时打开首页天气卡片；断网后重试 | 有网显示接口返回数据；断网提示不可用 | 外部 HTTP 调用有超时/失败提示，不将天气失败伪装成本地业务故障。 |

答辩演示顺序建议：先用管理员首页连续读 dashboard 展示 Redis 命中与 ECharts；管理员打开通知页保持 SSE；教师发布课程、学生选课、教师提交成绩，回到管理员页展示通知；管理员审核发布，再让学生查看学分和 GPA。最后用 Excel 两行“先新增后重复”文件说明事务回滚，再说明离线包可在 `18090/13306/16379` 的独立数据目录启动。
