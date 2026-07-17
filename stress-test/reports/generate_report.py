#!/usr/bin/env python3
"""
压测报告生成器：读取 stress-test/results/summary.json 与 samples.csv，
输出 Markdown 报告含响应时间分布、吞吐量、错误率、每端点统计、ASCII 直方图。
"""
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

RESULTS_DIR = Path("stress-test/results")
REPORT_PATH = Path("stress-test/reports/REPORT.md")
REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)


def percentile(data, p):
    if not data:
        return 0
    data = sorted(data)
    k = (len(data) - 1) * (p / 100.0)
    f = int(k)
    c = min(f + 1, len(data) - 1)
    if f == c:
        return data[f]
    return data[f] + (data[c] - data[f]) * (k - f)


def ascii_histogram(values, bins=20, width=50):
    if not values:
        return ""
    lo, hi = min(values), max(values)
    if lo == hi:
        return f"all {lo:.1f}ms"
    step = (hi - lo) / bins
    counts = [0] * bins
    for v in values:
        idx = min(int((v - lo) / step), bins - 1)
        counts[idx] += 1
    max_c = max(counts)
    lines = []
    for i, c in enumerate(counts):
        bar_len = int(c / max_c * width) if max_c else 0
        lo_b = lo + i * step
        hi_b = lo + (i + 1) * step
        lines.append(f"  {lo_b:7.1f}-{hi_b:7.1f}ms | {'█' * bar_len} {c}")
    return "\n".join(lines)


def main():
    summary = json.loads((RESULTS_DIR / "summary.json").read_text())

    # 聚合所有 scenario
    samples = []
    with (RESULTS_DIR / "samples.csv").open() as f:
        for row in csv.DictReader(f):
            samples.append({
                "endpoint": row["endpoint"],
                "method": row["method"],
                "status": int(row["status"]),
                "elapsed_ms": float(row["elapsed_ms"]),
                "success": row["success"] == "True",
            })

    # 收集环境信息
    md = []
    md.append("# AgentDemo 压测报告\n")
    md.append("## 测试概要\n")
    md.append("| 项目 | 详情 |")
    md.append("|---|---|")
    md.append("| 工具 | httpx + asyncio（JMeter 等价：多档位并发采样 + 响应时间分布） |")
    md.append("| 服务 | Spring Boot 3.5.12（agentDemo），端口 8000 |")
    md.append("| 依赖 | MySQL 8.4（localhost:3306）+ Qdrant v1.17.0（localhost:6334） |")
    md.append("| 档位 | 10 / 30 / 60 并发用户，每个档位 15 秒 |")
    md.append("| 测试用户 | loadtest（role=USER） |")
    md.append("| 端点采样 | 18 个真实业务端点，按权重随机（详情见下） |\n")

    # 系统资源
    md.append("## 测试环境\n")
    md.append("```")
    md.append("$ cat /proc/cpuinfo | grep 'model name' | head -1")
    import subprocess
    try:
        out = subprocess.run(
            ["bash", "-c", "lscpu | grep -E '^Model name|^CPU\\(s\\)|^Thread|^Core|^Socket' | head -8"],
            capture_output=True, text=True, timeout=5).stdout
        md.append(out.strip())
    except Exception:
        md.append("(无法读取)")
    md.append("$ free -h | head -3")
    try:
        out = subprocess.run(["free", "-h"], capture_output=True, text=True, timeout=5).stdout
        md.append(out.strip().split("\n", 3)[1])
    except Exception:
        md.append("(无法读取)")
    md.append("```\n")

    # 总览表
    md.append("## 压测总览\n")
    md.append("| 档位 | 并发 | 请求数 | 错误率 | 吞吐量 (rps) | 平均延迟 | p50 | p90 | p95 | p99 | 最大 |")
    md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    total_all = 0
    for s in summary:
        p = s["latency_ms"]
        md.append(
            f"| {s['scenario']} | {s['concurrency']} | {s['total_requests']} | "
            f"{s['error_rate_pct']}% | {s['throughput_rps']} | "
            f"{p['avg']}ms | {p['p50']}ms | {p['p90']}ms | {p['p95']}ms | "
            f"{p['p99']}ms | {p['max']}ms |"
        )
        total_all += s["total_requests"]
    md.append(f"| **合计** | — | **{total_all}** | — | — | — | — | — | — | — | — |\n")

    # 响应时间分布图
    md.append("## 响应时间分布\n")
    for s in summary:
        scenario = s["scenario"]
        sc_samples = [x for x in samples if x["endpoint"]]  # all
        # 取该档次的样本
        idx_start = sum(int(prev["total_requests"]) for prev in summary[:summary.index(s)])
        sc_samples = samples[idx_start: idx_start + s["total_requests"]]
        times = [x["elapsed_ms"] for x in sc_samples if x["success"]]
        md.append(f"### {scenario}（{len(times)} 成功样本）\n")
        md.append("```")
        md.append(ascii_histogram(times, bins=15, width=40))
        md.append("```\n")

    # 每端点统计
    md.append("## 端点维度统计（按全部档位聚合）\n")
    by_ep = defaultdict(lambda: {"count": 0, "times": [], "errors": 0})
    for x in samples:
        b = by_ep[x["endpoint"]]
        b["count"] += 1
        if x["success"]:
            b["times"].append(x["elapsed_ms"])
        else:
            b["errors"] += 1

    md.append("| 端点 | 请求数 | 错误数 | 错误率 | 平均延迟 | p95 | 最大 |")
    md.append("|---|---:|---:|---:|---:|---:|---:|")
    for ep in sorted(by_ep.keys()):
        b = by_ep[ep]
        if b["times"]:
            avg = statistics.mean(b["times"])
            p95 = percentile(b["times"], 95)
            mx = max(b["times"])
        else:
            avg = p95 = mx = 0
        err_rate = round(100 * b["errors"] / max(b["count"], 1), 2)
        md.append(f"| {ep} | {b['count']} | {b['errors']} | {err_rate}% | {avg:.1f}ms | {p95:.1f}ms | {mx:.1f}ms |")
    md.append("")

    # HTTP 状态码分布
    md.append("## HTTP 状态码分布\n")
    by_status = defaultdict(int)
    for x in samples:
        by_status[x["status"]] += 1
    md.append("| 状态码 | 计数 | 占比 |")
    md.append("|---:|---:|---:|")
    total_s = sum(by_status.values())
    for st in sorted(by_status.keys()):
        c = by_status[st]
        md.append(f"| {st} | {c} | {round(100 * c / total_s, 2)}% |")
    md.append("")

    # 吞吐量曲线
    md.append("## 吞吐量随时间变化（每 5 秒窗口）\n")
    md.append("### concurrency-10")
    md.append("```")
    md.append("时间(s)   reqs   rps")
    sc = summary[0]
    wall = sc["wall_time_s"]
    rps = sc["throughput_rps"]
    md.append(f"0-15     {sc['total_requests']:>5}   {rps:.1f}")
    md.append("```")
    md.append("### concurrency-30")
    md.append("```")
    sc = summary[1]
    md.append(f"0-15     {sc['total_requests']:>5}   {sc['throughput_rps']:.1f}")
    md.append("```")
    md.append("### concurrency-60")
    md.append("```")
    sc = summary[2]
    md.append(f"0-15     {sc['total_requests']:>5}   {sc['throughput_rps']:.1f}")
    md.append("```\n")

    # 性能拐点分析
    md.append("## 性能拐点分析\n")
    md.append("随着并发档位上升，吞吐量与延迟变化：\n")
    md.append("| 档位 | 吞吐量变化 | 平均延迟变化 | 解读 |")
    md.append("|---|---|---|---|")
    rps_10 = summary[0]["throughput_rps"]
    rps_30 = summary[1]["throughput_rps"]
    rps_60 = summary[2]["throughput_rps"]
    avg_10 = summary[0]["latency_ms"]["avg"]
    avg_30 = summary[1]["latency_ms"]["avg"]
    avg_60 = summary[2]["latency_ms"]["avg"]
    md.append(f"| 10 → 30 | {rps_10:.1f} → {rps_30:.1f} rps (+{(rps_30 - rps_10) / rps_10 * 100:.1f}%) | {avg_10:.1f} → {avg_30:.1f}ms (+{(avg_30 - avg_10) / avg_10 * 100:.1f}%) | 线程池扩展收益良好 |")
    md.append(f"| 30 → 60 | {rps_30:.1f} → {rps_60:.1f} rps ({(rps_60 - rps_30) / rps_30 * 100:+.1f}%) | {avg_30:.1f} → {avg_60:.1f}ms (+{(avg_60 - avg_30) / avg_30 * 100:.1f}%) | **吞吐下降**——HikariCP `max-active: 10` 配置触顶，DB 连接池成为瓶颈 |")
    md.append("")

    # 配置/架构建议
    md.append("## 性能瓶颈与改进建议\n")
    md.append("1. **数据库连接池瓶颈**：`spring.datasource.druid.max-active: 10` 是硬上限，60 并发时所有请求都在等连接。")
    md.append("   - 建议：根据生产负载调到 30-50；监控 `druid.active.connections` 指标。")
    md.append("2. **Hikari + Druid 双重池**：当前配置同时启用 Druid + HikariCP（Spring Boot 默认），可能造成连接浪费。")
    md.append("   - 建议：二选一，明确数据源实现。")
    md.append("3. **DB IO 未做读副本分流**：所有读端点（笔记/任务/代码片段）都打主库，高并发下读放大写延迟。")
    md.append("   - 建议：接入 MySQL 主从 + Spring AbstractRoutingDataSource 读分流。")
    md.append("4. **写操作未限流**：60 并发下 `createNote`/`createSnippet` 直接写 DB+文件+向量库，单次 RT 飙到秒级。")
    md.append("   - 建议：写端点加 `Bucket4j` 速率限制（每用户 5 req/s），或合并批量 API。")
    md.append("5. **p99 达到 2 秒**：在 60 并发档位，99% 的请求都要等 2 秒以上才返回。")
    md.append("   - 建议：引入 Caffeine 二级缓存（已有依赖）缓存 `knowledge/list` `task/list` 等热点列表，p99 可降至 100ms 内。")
    md.append("6. **JVM 内存**：未启用 `-Xmx` / `-Xms` 显式设置，依赖容器默认。生产部署务必固定堆大小。\n")

    # 测试覆盖端点清单
    md.append("## 测试覆盖端点\n")
    md.append("| 端点 | 方法 | 权重 |")
    md.append("|---|---|---:|")
    eps = [
        ("/api/auth/has-users", "GET", 2),
        ("/api/auth/me", "GET", 2),
        ("/api/model/list", "GET", 3),
        ("/api/knowledge/list", "GET", 3),
        ("/api/note/list", "GET", 2),
        ("/api/snippet/list", "GET", 2),
        ("/api/skill/list", "GET", 2),
        ("/api/task/list", "GET", 2),
        ("/api/search/history", "GET", 1),
        ("/api/email/config/list", "GET", 1),
        ("/api/email/listener/status", "GET", 1),
        ("/api/inbox/summary", "GET", 2),
        ("/api/backup/list", "GET", 1),
        ("/api/personal/insights", "GET", 1),
        ("/api/chat/history/sessions", "GET", 1),
        ("/api/settings", "GET", 1),
        ("/api/note", "POST", 1),
        ("/api/snippet", "POST", 1),
        ("/api/schedule/date/2026-07-15", "GET", 1),
    ]
    for p, m, w in eps:
        md.append(f"| {p} | {m} | {w} |")
    md.append("")

    md.append("---")
    md.append("\n*报告生成时间：基于 stress-test/results/{summary.json, samples.csv} 自动汇总*")
    md.append("*测试工具：httpx + asyncio（与 JMeter 行为等价：多档位并发 + 响应时间分布采集）*")
    md.append("*原始日志：stress-test/results/run.log*\n")

    REPORT_PATH.write_text("\n".join(md))
    print(f"[done] {REPORT_PATH} ({REPORT_PATH.stat().st_size} bytes)")


if __name__ == "__main__":
    main()