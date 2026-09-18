# 从旧练习到答辩版：代码学习路线

| 主题 | 实际代码位置 | 关键点 |
| --- | --- | --- |
| 一次解包 API | stu-frontend/src/util/request.ts 的 api<T> | Axios 响应在这一层统一检查 {code:202} 并返回 data；页面只接收业务 T。 |
| Bearer、JWT 与用户上下文 | stu-frontend/src/stores/auth.ts；stu-backend/.../security/JwtAuthFilter.java | 前端统一加入 Authorization: Bearer；过滤器验签、验 token 版本和冻结状态，将 CurrentUser 放入请求。 |
| 表格行插槽 | stu-frontend/src/views/StudentView.vue 的 #default | Element Plus 的列插槽取得当前 row，用于状态标签和这一行的操作按钮。 |
| 成绩状态、事务、GPA | stu-backend/.../service/SchoolService.java 的 saveGrades、gradeTransition | 状态按 DRAFT→SUBMITTED→APPROVED→PUBLISHED 变化；退回回到草稿。发布在事务中加锁并按学分加权计算 GPA，published_applied 使重复发布不重复累计。 |
