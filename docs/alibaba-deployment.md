# Proof of Alibaba Cloud Deployment

MBOA-OPS is deployed and running on an Alibaba Cloud ECS instance.

## Deployment details

- **Cloud provider**: Alibaba Cloud
- **Service**: Elastic Compute Service (ECS)
- **Instance type**: ecs.t6-c1m2.large (2 vCPU / 4 GiB RAM)
- **Region**: Singapore
- **OS**: Ubuntu 22.04 64-bit
- **Public IP**: 47.236.147.165
- **Live application**: http://47.236.147.165:3000
- **Backend API**: http://47.236.147.165:8080

## AI service

The backend calls Qwen Cloud (Alibaba DashScope) directly via the 
international endpoint, configured in 
[`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml): QWEN_BASE_URL=https://dashscope-intl.aliyuncs.com/compatible-mode/v1

Four models are used: qwen3.6-flash (fast classification), qwen3.7-max 
(complex reasoning), qwen3-vl-flash (handwritten list extraction via 
Qwen-VL), and qwen3-asr-flash (voice transcription via Qwen-ASR).

## Running containers (docker compose ps)

root@iZt4n1209nufijlvgybarrZ:~/mboa-ops# docker compose ps
NAME                  IMAGE               COMMAND                  SERVICE    CREATED          STATUS                    PORTS
mboa-ops-backend-1    mboa-ops-backend    "java -jar app.jar"      backend    41 minutes ago   Up 41 minutes             0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
mboa-ops-frontend-1   mboa-ops-frontend   "/docker-entrypoint.…"   frontend   41 minutes ago   Up 41 minutes             0.0.0.0:3000->80/tcp, [::]:3000->80/tcp
mboa-ops-postgres-1   postgres:16         "docker-entrypoint.s…"   postgres   41 minutes ago   Up 41 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
root@iZt4n1209nufijlvgybarrZ:~/mboa-ops#

## Backend startup log (Qwen connection warm-up)

oot@iZt4n1209nufijlvgybarrZ:~/mboa-ops# docker compose logs backend | grep -i "warm\|Started"
backend-1  | 2026-07-20T21:58:03.445Z  INFO 1 --- [mboa-ops-backend] [           main] c.m.backend.MboaOpsBackendApplication    : Starting MboaOpsBackendApplication v0.0.1-SNAPSHOT using Java 21.0.11 with PID 1 (/app/app.jar started by root in /app)
backend-1  | 2026-07-20T21:58:15.029Z  INFO 1 --- [mboa-ops-backend] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
backend-1  | 2026-07-20T21:58:15.049Z  INFO 1 --- [mboa-ops-backend] [           main] c.m.backend.MboaOpsBackendApplication    : Started MboaOpsBackendApplication in 12.319 seconds (process running for 13.206)
backend-1  | 2026-07-20T21:58:16.629Z  INFO 1 --- [mboa-ops-backend] [       Thread-1] c.m.backend.agents.qwen.QwenWarmup       : Warm-up Qwen effectué en 1428 ms : connexion prête


## How it was deployed

1. Provisioned an ECS instance (Ubuntu 22.04, 2 vCPU / 4 GiB)
2. Opened inbound ports 3000 (frontend) and 8080 (backend) in the 
   instance's Security Group
3. Installed Docker and Docker Compose on the instance
4. Cloned this repository and configured `.env` with the Qwen Cloud API 
   key and Alibaba Cloud endpoint
5. Built and launched all services: `docker compose up -d --build`

The full multi-service stack (PostgreSQL, Spring Boot backend, React 
frontend served via nginx) runs entirely on this Alibaba Cloud instance.
