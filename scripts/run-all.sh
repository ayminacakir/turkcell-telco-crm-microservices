#!/usr/bin/env bash
# Tum servisleri arka planda baslatir; loglar logs/ altina yazilir.
# Once altyapinin (docker) ayakta oldugundan emin ol.
set -e
mkdir -p logs
start(){ echo "  başlatılıyor: $1"; (cd "$1" && nohup mvn spring-boot:run > "../logs/$1.log" 2>&1 &); }
wait_up(){ # $1=port $2=isim
  for i in $(seq 1 60); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://localhost:$1/actuator/health)" = "200" ] && { echo "  ✅ $2 hazır"; return; }
    sleep 2
  done; echo "  ⚠ $2 ($1) 120sn'de kalkmadı — logs/$2.log'a bak"; }

echo "1) config-server"; start config-server; wait_up 8888 config-server
echo "2) eureka-server"; start eureka_server; wait_up 8761 eureka-server
echo "3) gateway";       start gateway_server; wait_up 8080 gateway
echo "4) iş servisleri (paralel)…"
start customer-service; start product-catalog-service; start order-service
start subscription-service; start usage-service; start billing-service
start payment-service; start notification-service; start ticket-service
echo "İş servisleri açılıyor, ~1-2 dk sürebilir. Durum için: ./scripts/status.sh"
