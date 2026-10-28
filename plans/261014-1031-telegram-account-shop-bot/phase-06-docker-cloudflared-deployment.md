# Phase 06: Deploy VPS (Ubuntu, 6GB RAM) — Docker Compose + Cloudflare Tunnel

## Mục tiêu
Bot + PostgreSQL + cloudflared chạy 24/7 bằng Docker Compose, webhook SePay đi qua Cloudflare Tunnel (không mở port), DB backup hằng ngày.

## Phụ thuộc
- Phase 05

## Điều kiện tiên quyết
- **Domain quản lý trên Cloudflare** (mua domain rẻ, đổi nameserver về Cloudflare) để tạo *named tunnel* với URL cố định, vd `https://pay.<domain>/sepay/webhook`
- Chưa có domain: dùng quick tunnel (`trycloudflare.com`) để test; URL đổi mỗi lần restart → phải cập nhật lại trên SePay; trong lúc đó dùng `/confirm` xác nhận tay

## Files sẽ tạo
- `Dockerfile` (multi-stage: maven build → eclipse-temurin:17-jre)
- `docker-compose.prod.yml`: `postgres` (volume), `bot` (port 8080 chỉ trong network nội bộ), `cloudflared` (`tunnel run --token $CF_TUNNEL_TOKEN`)
- `deploy/backup-db.sh` (pg_dump hằng ngày, giữ 7 bản) + hướng dẫn cron
- `docs/deployment-guide.md`

## Implementation steps
1. VPS: cài Docker Engine + compose plugin
2. Clone code, tạo `.env` (chmod 600)
3. Cloudflare Zero Trust → tạo tunnel → lấy token → public hostname `pay.<domain>` → `http://bot:8080`
4. `docker compose -f docker-compose.prod.yml up -d --build`
5. SePay: thêm TK MB, tạo webhook `https://pay.<domain>/sepay/webhook`, xác thực API Key
6. Cron backup
7. Test thật: chuyển 2.000₫ → kiểm tra tự cộng ví

## Todo list
- [x] Dockerfile + compose prod
- [x] Backup script
- [x] Deployment guide
- [ ] Test chuyển khoản thật (cần làm trên VPS)

## Definition of Done
- [ ] Reboot VPS → cả 3 container tự chạy lại (`restart: unless-stopped`)
- [ ] Chuyển khoản thật → bot xử lý < 30s (cần làm trên VPS)
- [ ] File backup xuất hiện sau cron

## Risks
- Lộ `.env` / token tunnel: không commit, chmod 600
- JVM ăn RAM: giới hạn `-Xmx512m` (VPS 6GB dư sức)
