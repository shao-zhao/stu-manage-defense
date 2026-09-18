# 第一节 修改学生信息

本节在原有项目上接通学生表格的“修改”按钮。你可以修改姓名、班级和手机号；点击取消不会影响列表，保存成功后重新从数据库查询。学分由后续成绩业务维护，不允许通过这个接口直接修改。

## 本节阅读顺序

1. `src/views/StudentView.vue` 中的 `@click="openEdit(row)"`。
2. 同一文件中的 `openEdit` 和 `editStudent`。
3. 修改弹窗中的 `v-model`、`:model`、`:rules` 和 `prop`。
4. 同一文件中的 `editSave`。
5. 后端 `StudentController.java` 中的 `update` 方法。
6. 回到前端的 `await query()`。

先理解一条请求经过哪些地方，再看校验和错误处理。

## 从点击按钮到回填表单

```vue
<template #default="{ row }">
  <el-button @click="openEdit(row)">修改</el-button>
</template>
```

`row` 是表格交给插槽的当前行对象。点击某一行的按钮，就把那一行的数据作为参数传给 `openEdit`。这里的 `@click` 是点击事件，`openEdit(row)` 是事件发生时执行的函数调用。

```ts
const openEdit = (row: StudentEditForm) => {
  editStudent.value = {
    id: row.id,
    name: row.name ?? '',
    grade: row.grade ?? '',
    phone: row.phone ?? '',
  }
  editVisible.value = true
}
```

`StudentEditForm` 是 TypeScript 类型，描述这份表单有哪些字段，不会创建数据库表，也不会自动验证网络输入。

`ref` 保存响应式数据。脚本里通过 `.value` 取值、改值；模板会自动解开这一层，不需要写 `.value`。当 `editVisible.value` 变成 `true`，绑定它的弹窗就打开。

`?? ''` 表示：如果原值是 `null` 或 `undefined`，用空字符串代替，以便输入框显示。

这里必须创建一个新对象。若写成 `editStudent.value = row`，表单和表格会引用同一个对象；你刚在输入框里打字，表格数据就已经变了，即使没点保存。当前写法只复制四个基础字段，取消后丢弃副本即可。

## 输入框为什么能修改表单数据

```vue
<el-input v-model="editStudent.name" />
```

`v-model` 是双向绑定：表单里的姓名会显示在输入框中，你在输入框打字又会更新表单的姓名。它改变的是浏览器中的副本，此时还没有请求后端。

修改弹窗本身也用了 `v-model="editVisible"`：这里双向绑定的是“打开还是关闭”，不是学生数据。同一种语法绑定到不同组件，可以表达不同的状态。

## 表单校验如何配合

```vue
<el-form ref="editFormRef" :model="editStudent" :rules="editRules">
  <el-form-item label="姓名" prop="name">
    <el-input v-model="editStudent.name" />
  </el-form-item>
</el-form>
```

- `:model` 指定要检查哪份数据。
- `:rules` 指定每个字段的校验规则。
- `prop="name"` 将这一项对应到数据和规则中的 `name`。
- `ref="editFormRef"` 获取表单组件实例，脚本才能调用它的 `validate()`。

姓名和班级必填且不能全是空格。三个字段长度不能超过当前数据库的 255 字符。手机号本节允许为空，并兼容原来的短号码测试数据；真实号码规范可在后续账号管理时统一。

```ts
const valid = await editFormRef.value.validate().catch(() => false)
if (!valid) return
```

校验通过时继续；校验失败时让组件显示提示，并提前返回，不向后端发请求。

## 保存时前端发送什么

```ts
await request({
  method: 'PUT',
  url: '/api/student/update',
  data: editStudent.value,
})
```

`PUT` 在本项目中表示修改。`request.ts` 的 `baseURL` 与路径组合成 `http://localhost:9090/api/student/update`。这里的 `data` 是发给后端的请求体，与响应中的 `.data` 要分开理解。

发送的 JSON 类似：

```json
{"id":1,"name":"张三","grade":"计科1班","phone":"10086"}
```

`id` 决定修改哪一条记录。姓名可能重复，不能仅凭姓名定位。请求拦截器会沿用现有逻辑携带 token，但现阶段项目还没有真实的身份和角色校验，本节是本地教学功能。

`editSaving` 防止重复点击；保存期间按钮显示加载状态，输入和关闭操作暂时禁用。

## 后端如何接收并更新

```java
@PutMapping("/update")
public Result<Void> update(@RequestBody Student student)
```

类上的 `@RequestMapping("/api/student")` 和方法上的 `/update` 组成完整路径。`@RequestBody` 把 JSON 转为 Java 对象。`Result<Void>` 表示仍返回业务码与提示，但成功时不需要附带业务数据。

后端再次检查编号、必填项和长度。前端校验帮助使用者及时发现问题，后端校验还需要防止其他客户端绕过页面直接提交错误数据。

接着创建 `changes` 对象，只放入允许修改的字段：

```java
Student changes = new Student();
changes.setId(student.getId());
changes.setName(student.getName().trim());
changes.setGrade(student.getGrade().trim());
changes.setPhone(phone);
int updated = studentMapper.updateById(changes);
```

`.trim()` 去除首尾空格。MyBatis-Plus 根据 `id` 更新这一行，默认不更新值为 `null` 的实体字段，因此没有设置的 `earnCredits` 不进入更新语句。这个行为已经用真实数据库测试验证；将来若修改全局字段更新策略，需要重新检查。

实际执行的 SQL 形状是：

```sql
UPDATE student SET name=?, grade=?, phone=? WHERE id=?;
```

`?` 是由数据库驱动绑定的参数，不是手工拼接输入。更新行数为 0 时返回业务失败，提示刷新后重试。

## 成功和失败怎样结束

成功后返回业务码 `202`，响应拦截器放行，前端关闭弹窗、提示成功，然后 `await query()` 重新加载列表。查询条件保持原样，因此如果改后的姓名不再符合筛选条件，这条记录会从筛选结果中消失，这是正常表现。

业务失败时后端返回 `505`，已有拦截器负责提示并拒绝 Promise。`editSave` 的 `catch` 接住它，保留弹窗和输入，方便修正。网络或 HTTP 错误由本次保存函数补充提示，避免页面只在控制台报错。

如果保存已经成功但随后列表刷新失败，会明确提示“信息已保存，但列表刷新失败”，避免把刷新失败误认为保存失败。

`finally` 无论成功、失败或校验提前返回都会执行，用来恢复 `editSaving`。

## 如何验证

前端目录运行 `npm run build` 检查类型与构建。后端目录运行 `./mvnw.cmd test`；测试使用本地已配置的 MySQL，在事务里创建独立测试数据并自动回滚。MySQL 自增编号可能留有空号，这是回滚后的正常现象。

本节测试覆盖字段更新、清空手机号、重复保存、拒绝空白和超长输入、缺失编号、学生不存在，以及请求夹带学分时学分保持原值。

页面练习：选一行点击修改，改名字后取消，观察原行是否保持不变；再打开，将姓名清空并保存，观察必填提示。理解后再用你自己的测试记录尝试真正保存。

## 后续开发范围

后续继续按小功能推进：学生信息校验与分页、字段及状态迁移、真实登录和三种角色权限、课程管理与发布、选课退课、成绩提交审核发布、学分与 GPA、Excel 导入导出。

当前仍沿用原表的 `name`、`grade`、`earnCredits` 等字段；需求文档中的学号、账号状态和其他字段需要后续备份及迁移。旧“删除”按钮目前没有功能，文档要求账号只冻结，不在本节接通删除。

文档仍需后续确定的规则：退课记录保留还是删除；已有成绩是否允许退课；GPA 计算方式；按学期查询所需字段；缓考等状态和分项成绩如何存储。讨论到相应模块时再明确，不直接照抄矛盾内容或示例密码哈希。

## 本机启动备注

本次 Java 26 启动 Tomcat 时，默认临时目录曾导致 `Unable to establish loopback connection`。临时指定 socket 目录后启动成功，没有修改项目配置。若相同环境再次出现同一错误，可在后端目录运行：

```powershell
New-Item -ItemType Directory -Force -Path 'target/socket-tmp'
./mvnw.cmd -B -ntp spring-boot:run '-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=D:/codex/practice3/stu-manage/stu-backend/target/socket-tmp'
```

前端目录运行 `npm run dev -- --host 127.0.0.1`，访问 `http://127.0.0.1:5173/student`。如果前后端已经由 IDE 启动，不需要重复运行。
