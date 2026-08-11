# Infrastructure

Milestone 1 Terraform placeholder.

Planned production components:
- WAF / load balancer
- 2+ API containers
- managed PostgreSQL
- Redis
- private GPU/vLLM workers
- object storage + CDN for signed APK releases
- secrets manager
- centralized metrics/logging

Do not expose the inference/vLLM endpoint publicly.
