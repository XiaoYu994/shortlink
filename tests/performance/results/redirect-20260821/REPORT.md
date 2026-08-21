# 短链接跳转压测报告

- 时间：2026-08-21 09:34:17–09:35:18 CST（60s，含 10s 爬坡）
- 环境：本机 Docker Desktop（Nginx :80 → aggregation :8003），单机、热缓存
- 脚本：`tests/performance/jmeter/redirect-localhost.jmx`
- 目标：`GET http://localhost/Zvldb`，不跟随跳转，断言 HTTP 302
- JMeter HTML：同目录 `html/index.html`

## 结论

跳转是这个系统最该压的接口：公网 QPS 几乎都打在这条链路上（Nginx → 本地缓存 → Redis → 302），创建短链是后台、还有 20 次/秒限流。

本轮是**热缓存最佳路径**，不是容量上限：

| 指标 | 结果 |
|---|---|
| 并发 | 20 线程，10s 爬坡，持续 60s |
| 样本 | 242,034 |
| 错误 | **0**（全部 302） |
| 吞吐 | **4,091 req/s** |
| 平均 | 4.2 ms |
| 中位数 | 5 ms |
| 90% / 95% / 99% | 9 / 10 / 12 ms |
| 最大 | 126 ms（爬坡初期） |

压完后 aggregation / gateway 仍是 UP，短链仍 302 到 `https://www.deepseek.com/`。

## 资源（压测中 Docker stats）

| 容器 | 平均 CPU | 峰值 CPU | 内存 |
|---|---|---|---|
| shortlink-frontend (Nginx) | 273% | 756% | 35 MiB |
| shortlink-aggregation | 255% | 687% | 1.1 GiB |
| shortlink-broker (RocketMQ) | 68% | 181% | 3.5 GiB |
| shortlink-redis | 18% | 42% | 301 MiB |
| shortlink-mysql | 25% | 37% | 523 MiB |
| shortlink-gateway | ~0% | 3% | 389 MiB |

跳转不经过 gateway（Nginx 把 `/` 直接转到 aggregation），所以 gateway CPU 几乎不动。瓶颈主要在 **Nginx 转发 + aggregation 发 MQ + 统计消费**。

## 跳转快、统计跟不上

302 很快，是因为只读 Caffeine/Redis，统计是异步的。本轮约 24 万次点击涌进 RocketMQ 后，统计消费者大量：

```text
IP解析失败或超时, IP: 172.29.0.1
SocketTimeoutException: Connect timed out
```

每个消息会同步调高德（3s 超时）。本机 Docker 网关 IP 本来也定位不了，高德再超时，统计会堆积。这不影响 302，但会抬高 aggregation / broker CPU 和内存。

公网压测若也同步打高德，统计会先于跳转被打满。

## 这轮不能当容量结论

1. 只有一条已预热短链，几乎不回源数据库。
2. 客户端和 Nginx 都在本机，没有公网 RTT。
3. Docker Desktop 端口转发，和 Linux 服务器 iptables 不是同一回事。
4. 20 线程已经把 Nginx、aggregation 打到数倍 CPU；再加线程多半是本机打满，不是服务容量。
5. RocketMQ broker 占了约 3.5 GiB，本机内存会被中间件先吃掉。

若要更接近生产：多条短链（含冷链）、Linux 服务器、关闭或异步化高德、阶梯加压（20 → 50 → 100），并单独看 RocketMQ 堆积。

## 复跑

```bash
export HEAP="-Xms512m -Xmx1024m"
bash /mnt/d/JAVA/apache-jmeter-5.6.3/bin/jmeter -n \
  -t tests/performance/jmeter/redirect-localhost.jmx \
  -Jhost=localhost -Jport=80 -Jpath=/Zvldb \
  -Jthreads=20 -Jramp=10 -Jduration=60 \
  -l /tmp/redirect.jtl -e -o /tmp/redirect-html
```
