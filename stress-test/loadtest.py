#!/usr/bin/env python3
"""
JMeter-equivalent load test for AgentDemo.

通过 httpx + asyncio 模拟 JMeter 的 Thread Group + HTTP Sampler：
- 多个并发档位（light/medium/heavy）
- 每个档位跑固定时长
- 收集：响应时间分布（min/avg/p50/p90/p95/p99/max）、吞吐量、错误率

输出 CSV + 汇总 JSON 给后续报告生成。
"""
import argparse
import asyncio
import csv
import json
import statistics
import sys
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path

import httpx


@dataclass
class SampleResult:
    timestamp: float
    endpoint: str
    method: str
    status: int
    elapsed_ms: float
    success: bool
    error: str = ""


@dataclass
class EndpointConfig:
    name: str
    method: str
    path: str
    weight: int = 1
    requires_auth: bool = True
    body: dict = field(default_factory=dict)


# 业务端点采样权重：高频读 60%、列表 25%、写 5%、管理 10%
ENDPOINTS = [
    EndpointConfig("hasUsers", "GET", "/api/auth/has-users", weight=2, requires_auth=False),
    EndpointConfig("loginCheck", "GET", "/api/auth/me", weight=2),
    EndpointConfig("modelsList", "GET", "/api/model/list", weight=3),
    EndpointConfig("knowledgeList", "GET", "/api/knowledge/list", weight=3),
    EndpointConfig("notesList", "GET", "/api/note/list", weight=2),
    EndpointConfig("snippetsList", "GET", "/api/snippet/list", weight=2),
    EndpointConfig("skillsList", "GET", "/api/skill/list", weight=2),
    EndpointConfig("tasksList", "GET", "/api/task/list", weight=2),
    EndpointConfig("searchHistory", "GET", "/api/search/history", weight=1),
    EndpointConfig("emailConfigs", "GET", "/api/email/config/list", weight=1),
    EndpointConfig("emailListenerStatus", "GET", "/api/email/listener/status", weight=1),
    EndpointConfig("inboxSummary", "GET", "/api/inbox/summary", weight=2),
    EndpointConfig("backupList", "GET", "/api/backup/list", weight=1),
    EndpointConfig("personalInsights", "GET", "/api/personal/insights", weight=1),
    EndpointConfig("chatSessions", "GET", "/api/chat/history/sessions", weight=1),
    EndpointConfig("settingsList", "GET", "/api/settings", weight=1),
    EndpointConfig("createNote", "POST", "/api/note", weight=1, body={
        "title": "stress-test-note", "content": "压力测试笔记内容"
    }),
    EndpointConfig("createSnippet", "POST", "/api/snippet", weight=1, body={
        "title": "stress-snippet", "language": "java", "code": "System.out.println(1);"
    }),
    EndpointConfig("getSchedule", "GET", "/api/schedule/date/2026-07-15", weight=1),
]


def build_endpoints():
    """按权重展开成数组，Sampler 用 random.choice 抽样"""
    pool = []
    for ep in ENDPOINTS:
        for _ in range(ep.weight):
            pool.append(ep)
    return pool


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


@dataclass
class ScenarioResult:
    name: str
    concurrency: int
    duration_s: int
    total_requests: int = 0
    success_count: int = 0
    error_count: int = 0
    elapsed_ms: list = field(default_factory=list)
    by_endpoint: dict = field(default_factory=dict)
    by_status: dict = field(default_factory=dict)
    errors: dict = field(default_factory=dict)

    def aggregate(self, wall_time_s: float):
        return {
            "scenario": self.name,
            "concurrency": self.concurrency,
            "duration_s": self.duration_s,
            "wall_time_s": round(wall_time_s, 2),
            "total_requests": self.total_requests,
            "success": self.success_count,
            "errors": self.error_count,
            "error_rate_pct": round(100 * self.error_count / max(self.total_requests, 1), 3),
            "throughput_rps": round(self.total_requests / max(wall_time_s, 0.001), 2),
            "latency_ms": {
                "min": round(min(self.elapsed_ms), 2) if self.elapsed_ms else 0,
                "avg": round(statistics.mean(self.elapsed_ms), 2) if self.elapsed_ms else 0,
                "p50": round(percentile(self.elapsed_ms, 50), 2),
                "p90": round(percentile(self.elapsed_ms, 90), 2),
                "p95": round(percentile(self.elapsed_ms, 95), 2),
                "p99": round(percentile(self.elapsed_ms, 99), 2),
                "max": round(max(self.elapsed_ms), 2) if self.elapsed_ms else 0,
            },
            "by_endpoint": {
                k: {
                    "count": v["count"],
                    "avg_ms": round(v["total_ms"] / max(v["count"], 1), 2),
                    "errors": v["errors"],
                }
                for k, v in self.by_endpoint.items()
            },
            "by_status": dict(self.by_status),
            "top_errors": dict(sorted(self.errors.items(), key=lambda x: -x[1])[:10]),
        }


async def worker(worker_id: int, token: str, endpoint_pool, base_url: str,
                 stop_at: float, scenario_result: ScenarioResult, samples: list):
    """单个 worker 协程：循环采样端点并发起请求，直到 stop_at 时间点。"""
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    timeout = httpx.Timeout(connect=5.0, read=30.0, write=10.0, pool=5.0)
    async with httpx.AsyncClient(base_url=base_url, headers=headers, timeout=timeout) as client:
        loop = asyncio.get_event_loop()
        while loop.time() < stop_at:
            import random
            ep = random.choice(endpoint_pool)
            url = ep.path
            t0 = loop.time()
            try:
                if ep.method == "GET":
                    r = await client.get(url)
                elif ep.method == "POST":
                    r = await client.post(url, json=ep.body)
                else:
                    r = await client.request(ep.method, url)
                elapsed_ms = (loop.time() - t0) * 1000
                success = 200 <= r.status_code < 400
                sample = SampleResult(loop.time(), ep.name, ep.method, r.status_code, elapsed_ms, success)
                samples.append(sample)
                scenario_result.total_requests += 1
                if success:
                    scenario_result.success_count += 1
                else:
                    scenario_result.error_count += 1
                    scenario_result.errors[f"{ep.method} {ep.path} -> {r.status_code}"] = \
                        scenario_result.errors.get(f"{ep.method} {ep.path} -> {r.status_code}", 0) + 1
                scenario_result.elapsed_ms.append(elapsed_ms)
                scenario_result.by_status[r.status_code] = scenario_result.by_status.get(r.status_code, 0) + 1
                bucket = scenario_result.by_endpoint.setdefault(
                    ep.name, {"count": 0, "total_ms": 0.0, "errors": 0})
                bucket["count"] += 1
                bucket["total_ms"] += elapsed_ms
                if not success:
                    bucket["errors"] += 1
            except Exception as e:
                elapsed_ms = (loop.time() - t0) * 1000
                sample = SampleResult(loop.time(), ep.name, ep.method, 0, elapsed_ms, False, str(e)[:100])
                samples.append(sample)
                scenario_result.total_requests += 1
                scenario_result.error_count += 1
                key = f"{type(e).__name__}: {str(e)[:60]}"
                scenario_result.errors[key] = scenario_result.errors.get(key, 0) + 1


async def run_scenario(name: str, concurrency: int, duration_s: int,
                        base_url: str, token: str) -> tuple:
    print(f"[{name}] starting: concurrency={concurrency}, duration={duration_s}s")
    endpoint_pool = build_endpoints()
    scenario_result = ScenarioResult(name=name, concurrency=concurrency, duration_s=duration_s)
    samples: list = []
    stop_at = asyncio.get_event_loop().time() + duration_s
    tasks = [
        asyncio.create_task(worker(i, token, endpoint_pool, base_url, stop_at, scenario_result, samples))
        for i in range(concurrency)
    ]
    wall_start = time.time()
    # 进度条
    for tick in range(duration_s):
        await asyncio.sleep(1)
        running = sum(1 for t in tasks if not t.done())
        print(f"  [{name}] t={tick + 1}/{duration_s}s concurrency_running={running} total_req={scenario_result.total_requests}")
    await asyncio.gather(*tasks, return_exceptions=True)
    wall_time = time.time() - wall_start
    print(f"[{name}] done: {scenario_result.total_requests} reqs in {wall_time:.1f}s "
          f"({scenario_result.total_requests / wall_time:.1f} rps)")
    return scenario_result, samples, wall_time


async def warmup(base_url: str, token: str):
    print("[warmup] 1 user for 5s")
    await run_scenario("warmup", 1, 5, base_url, token)


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8000")
    parser.add_argument("--username", default="loadtest")
    parser.add_argument("--password", default="LoadTest123!")
    parser.add_argument("--scenarios", default="10:30,30:30,50:30",
                        help="comma-separated concurrency:duration pairs")
    parser.add_argument("--results-dir", default="stress-test/results")
    args = parser.parse_args()

    out = Path(args.results_dir)
    out.mkdir(parents=True, exist_ok=True)

    # 登录获取 token
    async with httpx.AsyncClient(base_url=args.base_url, timeout=10.0) as c:
        r = await c.post("/api/auth/login/password",
                          json={"username": args.username, "password": args.password})
        r.raise_for_status()
        token = r.json()["data"]["accessToken"]
        print(f"[auth] got token, length={len(token)}")

    # Warmup
    await warmup(args.base_url, token)

    scenarios = []
    for s in args.scenarios.split(","):
        concurrency, duration = s.split(":")
        scenarios.append((int(concurrency), int(duration)))

    all_summaries = []
    all_samples_csv = []
    for conc, dur in scenarios:
        result, samples, wall_time = await run_scenario(
            f"concurrency-{conc}", conc, dur, args.base_url, token)
        summary = result.aggregate(wall_time)
        all_summaries.append(summary)
        for s in samples:
            all_samples_csv.append(asdict(s))
        # 每个 scenario 之间休息 5s 让系统回收连接
        await asyncio.sleep(5)

    # 写文件
    summary_path = out / "summary.json"
    summary_path.write_text(json.dumps(all_summaries, indent=2, ensure_ascii=False))
    print(f"[done] summary: {summary_path}")

    csv_path = out / "samples.csv"
    with csv_path.open("w", newline="") as f:
        if all_samples_csv:
            writer = csv.DictWriter(f, fieldnames=list(all_samples_csv[0].keys()))
            writer.writeheader()
            writer.writerows(all_samples_csv)
    print(f"[done] samples: {csv_path} ({len(all_samples_csv)} rows)")


if __name__ == "__main__":
    asyncio.run(main())