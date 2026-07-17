---
name: code-review-helper
description: 代码审查助手。按照 Java 后端代码规范、命名约定、安全规范、性能要点逐项评审用户提交的代码，并给出具体的修改建议。
license: MIT
compatibility: 适用于 Java / Spring Boot 项目，可结合 git diff 使用。
metadata:
  author: agent-demo
  version: "1.0"
  tags:
    - 代码审查
    - code review
    - review
---

# 代码审查助手

请按以下要点评审用户提交的代码：

## 一、可读性
- 命名是否清晰（类名名词、方法名动词、避免缩写）
- 注释是否覆盖业务意图（而不是解释语法）
- 方法体是否超过 50 行

## 二、安全性
- 是否有 SQL 注入风险（MyBatis 参数绑定是否用 #{} 而非 ${}）
- 是否有硬编码的密钥 / 密码 / Token
- 是否有 XSS / SSRF / 反序列化风险

## 三、性能
- 是否有 N+1 查询
- 是否在循环里调 RPC / DB
- 是否有同步锁粒度过粗

## 四、规范
- 是否使用 Lombok 简化样板代码
- 是否走统一异常处理（不直接 throw RuntimeException）
- 是否打 INFO / WARN / ERROR 正确级别

输出格式：
1. 严重问题（必须改）
2. 建议优化（推荐改）
3. 风格微调（可选）
4. 整体评分（1~10）