# 新增 API 接口文档

本文档记录了日程文件管理和邮件发送相关的新增接口。

---

## 1. 日程文件管理接口

### 1.1 获取所有日程文件列表

获取存储目录下所有的日程文件列表。

- **URL**: `/api/schedule/files`
- **Method**: `GET`
- **响应示例**:
```json
[
  "schedule-2026-03-29.md",
  "schedule-2026-03-30.md",
  "schedule-2026-03-31.md"
]
```

---

### 1.2 按日期查询日程文件内容

获取指定日期的日程文件内容和数据库中的日程列表。

- **URL**: `/api/schedule/file/date/{date}`
- **Method**: `GET`
- **路径参数**:
  - `date`: 日期，格式 `yyyy-MM-dd`，如 `2026-03-29`
- **响应示例**:
```json
{
  "date": "2026-03-29",
  "fileName": "schedule-2026-03-29.md",
  "content": "# 日程安排 - 2026-03-29\n\n## 会议\n\n- **时间**: 2026-03-29 14:00\n- **地点**: 会议室A\n- **描述**: 项目进度汇报\n\n---\n",
  "events": [
    {
      "id": 1,
      "title": "会议",
      "eventTime": "2026-03-29T14:00:00",
      "eventDate": "2026-03-29",
      "location": "会议室A",
      "filePath": "./data/schedules/schedule-2026-03-29.md"
    }
  ]
}
```

---

### 1.3 按文件名读取日程文件内容

根据文件名读取日程文件内容。

- **URL**: `/api/schedule/file/{fileName}`
- **Method**: `GET`
- **路径参数**:
  - `fileName`: 文件名，如 `schedule-2026-03-29.md`
- **响应示例**:
```json
{
  "fileName": "schedule-2026-03-29.md",
  "date": "2026-03-29",
  "content": "# 日程安排 - 2026-03-29\n\n..."
}
```

---

### 1.4 按日程ID读取对应的日程文件内容

根据日程事件的ID获取对应的日程文件内容。

- **URL**: `/api/schedule/{id}/file`
- **Method**: `GET`
- **路径参数**:
  - `id`: 日程事件ID
- **响应示例**:
```json
{
  "event": {
    "id": 1,
    "title": "会议",
    "eventTime": "2026-03-29T14:00:00",
    "eventDate": "2026-03-29",
    "location": "会议室A",
    "filePath": "./data/schedules/schedule-2026-03-29.md"
  },
  "fileName": "schedule-2026-03-29.md",
  "content": "# 日程安排 - 2026-03-29\n\n..."
}
```

---

## 2. 日程管理接口（完整）

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 日程列表 | GET | `/api/schedule/list` | 获取所有日程 |
| 最近日程 | GET | `/api/schedule/latest` | 获取最近的日程 |
| 今日日程 | GET | `/api/schedule/today` | 获取今天的日程 |
| 明日日程 | GET | `/api/schedule/tomorrow` | 获取明天的日程 |
| 日期查询 | GET | `/api/schedule/date/{date}` | 获取指定日期日程 |
| 日期范围查询 | GET | `/api/schedule/range` | 获取日期范围内的日程 |
| **日程文件列表** | GET | `/api/schedule/files` | **获取所有日程文件列表** |
| **按日期查文件** | GET | `/api/schedule/file/date/{date}` | **按日期查询日程文件内容** |
| **按文件名读取** | GET | `/api/schedule/file/{fileName}` | **按文件名读取日程文件内容** |
| **按ID读文件** | GET | `/api/schedule/{id}/file` | **按日程ID读取文件内容** |
| 添加日程 | POST | `/api/schedule` | 添加日程 |
| 更新日程 | PUT | `/api/schedule/{id}` | 更新日程 |
| 删除日程 | DELETE | `/api/schedule/{id}` | 删除日程 |
| 完成日程 | PUT | `/api/schedule/{id}/complete` | 标记完成 |
| 取消日程 | PUT | `/api/schedule/{id}/cancel` | 取消日程 |
| 解析邮件 | POST | `/api/schedule/parse-email` | 从邮件提取日程 |
| 解析并保存 | POST | `/api/schedule/parse-and-save` | 解析并保存日程 |

---

## 3. 日程文件存储说明

### 文件命名格式

```
./data/schedules/
├── schedule-2026-03-29.md    # 2026年3月29日的日程
├── schedule-2026-03-30.md    # 2026年3月30日的日程
├── summary-2026-03-29.md     # 2026年3月29日的汇总
└── ...
```

### 文件内容格式 (Markdown)

```markdown
# 日程安排 - 2026-03-29

**更新时间**: 2026-03-29 10:30

---

## 1. 项目会议

- **时间**: 2026-03-29 14:00
- **地点**: 会议室A
- **描述**: 项目进度汇报
- **来源邮件**: sender@example.com

---

## 2. 客户电话

- **时间**: 2026-03-29 16:00
- **描述**: 确认合作细节

---
```

### 数据库字段说明

`schedule_event` 表新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `file_path` | VARCHAR(500) | 日程文件存储路径 |

---

## 4. 邮件发送配置

### 配置项 (application.yaml)

```yaml
app:
  # 邮件发送配置 (SMTP)
  mail:
    smtp-host: smtp.163.com          # SMTP服务器地址
    smtp-port: 465                   # SMTP端口
    smtp-username: your@163.com      # 发件邮箱
    smtp-password: "授权码"           # SMTP授权码（非邮箱密码）
    smtp-ssl-enabled: true           # 启用SSL
    from-name: AI Agent              # 发件人名称

  # 日程管理配置
  schedule:
    enabled: true                        # 是否启用日程功能
    storage-path: ./data/schedules       # 日程文件存储路径
    user-email: user@example.com         # 接收汇总/提醒的邮箱
    daily-summary-cron: "0 0 20 * * ?"   # 每天20:00发送汇总
    morning-reminder-cron: "0 0 8 * * ?" # 每天08:00发送提醒
```

### 获取SMTP授权码

以163邮箱为例：
1. 登录163邮箱 → 设置 → POP3/SMTP/IMAP
2. 开启"IMAP/SMTP服务"
3. 获取**授权码**（不是邮箱密码）
4. 将授权码填入 `smtp-password`

---

## 5. 数据流程

```
新邮件到达
    ↓
EmailListenerService 监听 (IMAP)
    ↓
EmailProcessingService 解析日程 (AI提取)
    ↓
ScheduleEvent 保存到数据库
    ↓
ScheduleFileService 写入文件 (schedule-yyyy-MM-dd.md)
    ↓
(每日20:00) ScheduleSummaryService 定时汇总
    ↓
EmailSenderService 发送邮件给用户 (SMTP)
```

---

## 6. 测试示例

### 测试日程文件接口

```bash
# 获取文件列表
curl http://localhost:8000/api/schedule/files

# 按日期查询
curl http://localhost:8000/api/schedule/file/date/2026-03-29

# 按文件名读取
curl http://localhost:8000/api/schedule/file/schedule-2026-03-29.md

# 按日程ID读取文件
curl http://localhost:8000/api/schedule/1/file
```

### 测试日程查询接口

```bash
# 今日日程
curl http://localhost:8000/api/schedule/today

# 指定日期
curl http://localhost:8000/api/schedule/date/2026-03-29

# 日期范围
curl "http://localhost:8000/api/schedule/range?startDate=2026-03-01&endDate=2026-03-31"
```