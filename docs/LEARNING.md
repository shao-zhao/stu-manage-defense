# 从页面到业务：学习路线

这份笔记只对应当前项目的真实代码。建议按“浏览器请求如何到达数据库，再怎样把结果送回页面”的顺序阅读。

## 1. 一次解包 API

位置：[request.ts](../stu-frontend/src/util/request.ts)。后端 JSON 成功响应固定为 `{ code: 202, message, data }`。响应拦截器只接受 `202`，`505` 和 HTTP 错误转换为统一提示与 `Promise.reject`；页面调用 `api<T>` 时只拿到 `data`。

```ts
export async function api<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await request.request<ApiEnvelope<T>>(config)
  return response.data.data
}
```

这样 `CoursesView.vue` 可以直接写 `api<Course[]>({ url: '/api/courses' })`，不必在每个页面重复判断 `code`。下载 Excel 是例外：`responseType: 'blob'` 先原样返回，避免把二进制当 JSON 解包。

## 2. JWT 与当前用户上下文

位置：[auth.ts](../stu-frontend/src/stores/auth.ts)、[request.ts](../stu-frontend/src/util/request.ts)、`stu-backend/.../security/JwtAuthFilter.java`。

登录成功后，Pinia 保存用户信息，令牌存入 `localStorage`；请求拦截器集中添加 `Authorization: Bearer <token>`。路由守卫据此决定能否进入页面，但它只改善体验。后端过滤器会重新验签、检查令牌版本和账号状态，再把 `CurrentUser` 放入当前请求，因此不能靠手工输入地址绕过权限。

```ts
const token = localStorage.getItem('stu_manage_token')
if (token) config.headers.Authorization = `Bearer ${token}`
```

思考题：为什么冻结用户不能只让前端隐藏按钮？因为旧令牌和直接 HTTP 请求仍可能存在，安全检查必须在后端。

## 3. 表格行插槽

位置：[CoursesView.vue](../stu-frontend/src/views/CoursesView.vue)。Element Plus 表格不知道“状态”一列该怎样显示，`#default="{ row }"` 把当前行对象交给模板，再根据每一行的 `status` 显示不同标签和操作。

```vue
<el-table-column label="状态">
  <template #default="{ row }">
    <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
  </template>
</el-table-column>
```

这比依赖表格索引更稳：筛选、排序或分页后，按钮仍操作同一个 `row.id`。

## 4. 课程状态与选课并发

位置：[CoursesView.vue](../stu-frontend/src/views/CoursesView.vue)、`SchoolService.publishCourse`、`closeCourse`、`reopenCourse`、`enroll`。

课程有 `UNPUBLISHED → PUBLISHED → CLOSED` 三种状态。前端在已发布或停开时锁住编号、教师、学分、学时、学期、时间和容量，只允许修改地点、封面、简介；服务端仍会执行同一规则。学生只能看到已发布或停开课程，停开课程保留已有选课但不能新选。

选课的关键 SQL 使用 `for update` 锁定该学生的选课记录，随后在同一个事务里复查容量、重复选课和课程状态，再增减 `course.enrolled`。所以两个学生同时点击最后一个名额，也由数据库决定只有一个事务成功。

答辩可以这样解释：前端的禁用按钮是提示，事务和行锁才是防超卖的最后边界。

## 5. Redis 看板缓存

位置：`ApiController.dashboard`、`SchoolService.dashboard`、[TechnologyView.vue](../stu-frontend/src/views/TechnologyView.vue)。

管理员技术页顺序请求两次 `/api/dashboard`：第一次可能查询业务数据并写入 Redis，第二次读取同一用户缓存。接口实际返回 `cache.backend`、`cache.hit`、`cache.ttlSeconds`，`generatedAt` 在 dashboard 顶层；页面原样展示，不在浏览器里伪造命中或倒计时。

```ts
const first = await api<DashboardData>({ url: '/api/dashboard' })
const second = await api<DashboardData>({ url: '/api/dashboard' })
firstRead.value = first.cache
secondRead.value = second.cache
dashboard.value = second
```

课程、选课和成绩发生变更时，后端会删除相关 dashboard key。Redis 不可用时，接口明确返回 `backend: DOWN`，仍直接从数据库计算统计，页面不宣称缓存有效。

## 6. SSE：事务完成后再通知

位置：[AppPage.vue](../stu-frontend/src/views/AppPage.vue)、`EventHub.java`、`SchoolService.gradeTransition`。

登录后的主布局用原生 `fetch` 建立 `GET /api/events`，同样带 Bearer 请求头。前端按空行分割 SSE 数据，兼容 `event:notification` 与 `event: notification`、CRLF 换行；收到 `notification` 增加未读数，收到 `refresh` 发出页面刷新事件。网络中断只有限次数重连，卸载布局时中止请求。

```ts
if (type === 'notification') unread.value++
if (type === 'refresh') window.dispatchEvent(new CustomEvent('data-refresh'))
```

成绩提交完成后，服务端才发送通知，避免“页面已经提示成功、数据库却回滚”的假消息。演示时保留管理员页面打开，由教师提交成绩，即可观察管理员通知角标变化。

## 7. 建议的阅读顺序

1. 先登录并看 [AppPage.vue](../stu-frontend/src/views/AppPage.vue) 的菜单与 SSE 状态。
2. 再看 [CoursesView.vue](../stu-frontend/src/views/CoursesView.vue) 的课程状态和行插槽。
3. 打开技术演示页，对照 `ApiController.dashboard` 看 Redis 字段。
4. 最后读 `SchoolService.enroll` 与 `gradeTransition`，理解为什么规则在后端事务里，而不是只写在页面里。
