# Phase 7 发布检查清单

## 1. 发布前检查（Preflight）

- [ ] 六个服务均可访问 `/actuator/health`
- [ ] 六个服务均可访问 `/actuator/prometheus`
- [ ] Prometheus `targets` 页面全部为 `UP`
- [ ] Grafana `ShortLink Overview` 仪表盘有实时数据
- [ ] Alertmanager 可接收测试告警
- [ ] 核心链路冒烟通过：创建、跳转、统计、风控

## 2. 灰度发布顺序

1. 先发布监控栈（Prometheus/Grafana/Alertmanager）
2. 发布 `gateway-service`
3. 发布 `user-service` 与 `project-service`
4. 发布 `stats-service` 与 `risk-service`
5. 发布 `aggregation-service`

## 3. 每批发布后检查

- [ ] 服务健康检查通过
- [ ] HTTP 5xx 比例未异常升高
- [ ] 跳转链路 P95 在阈值内
- [ ] MQ 消费失败率无持续异常
- [ ] 缓存命中率无突降

## 4. 发布通过标准

- [ ] 连续观察 30-60 分钟无 P1/P2 告警
- [ ] 核心业务功能可用
- [ ] 验收报告已填写并评审
