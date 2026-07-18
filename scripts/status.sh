#!/usr/bin/env bash
# Tum servislerin /actuator/health durumunu tablo halinde gosterir.
declare -a S=(
  "config-server:8888" "eureka-server:8761" "gateway:8080"
  "customer:9002" "product-catalog:9003" "order:9004" "subscription:9005"
  "usage:9006" "billing:9007" "payment:9008" "notification:9009" "ticket:9010"
)
printf "%-20s %-8s %s\n" "SERVİS" "PORT" "DURUM"
printf "%-20s %-8s %s\n" "──────" "────" "─────"
up=0; total=0
for e in "${S[@]}"; do
  name="${e%%:*}"; port="${e##*:}"; total=$((total+1))
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$port/actuator/health" 2>/dev/null)
  # 200 = acik health; 401/403 = servis AYAKTA ama /actuator/health JWT korumali
  # (customer & billing). Her ikisi de "calisiyor" demek; sadece 000/yok = kapali.
  if [ "$code" = "200" ]; then printf "%-20s %-8s ✅ UP\n" "$name" "$port"; up=$((up+1))
  elif [ "$code" = "401" ] || [ "$code" = "403" ]; then printf "%-20s %-8s ✅ UP (health kilitli)\n" "$name" "$port"; up=$((up+1))
  else printf "%-20s %-8s ❌ (%s)\n" "$name" "$port" "${code:-yok}"; fi
done
echo "──────"
echo "Ayakta: $up/$total"
echo
echo "Keycloak:"
kc=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:8085/realms/telco-crm" 2>/dev/null)
[ "$kc" = "200" ] && echo "  ✅ Keycloak realm hazır" || echo "  ❌ Keycloak/realm yok (kod: ${kc:-yok}) → docker compose up -d --force-recreate keycloak"
