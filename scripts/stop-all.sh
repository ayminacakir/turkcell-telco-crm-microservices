#!/usr/bin/env bash
# Tum Spring Boot servislerini durdurur (docker'a dokunmaz).
echo "Spring Boot süreçleri durduruluyor…"
pkill -f 'spring-boot:run' 2>/dev/null
pkill -f 'GatewayServerApplication\|EurekaServerApplication\|ConfigServerApplication' 2>/dev/null
# Porta göre kalan varsa temizle
for p in 8080 8761 8888 9002 9003 9004 9005 9006 9007 9008 9009 9010; do
  pid=$(lsof -ti tcp:$p 2>/dev/null)
  [ -n "$pid" ] && kill -9 $pid 2>/dev/null && echo "  port $p (pid $pid) durduruldu"
done
echo "Bitti. (Docker/Keycloak/DB'ler çalışmaya devam ediyor.)"
