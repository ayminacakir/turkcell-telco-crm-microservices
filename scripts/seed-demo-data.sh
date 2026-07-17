#!/usr/bin/env bash
# TelcoX demo verisi — tum servis DB'lerine tutarli ornek veri basar.
# On kosul: docker compose ayakta (db'ler + keycloak) ve servisler calisiyor.
# Idempotent: tekrar calistirmak guvenlidir (ON CONFLICT DO NOTHING).
#
# Sabit kimlikler (frontend telcox-shared.js ile ayni olmak ZORUNDA):
#   CUST1 (Elif Aydın)  : 11111111-0000-4000-8000-000000000001  msisdn 05324174712
#   CUST2 (Mert Kaya)   : 11111111-0000-4000-8000-000000000002  msisdn 05335558899
#   SUB1 / SUB2         : 22222222-...01 / ...02
set -e

CUST1=11111111-0000-4000-8000-000000000001
CUST2=11111111-0000-4000-8000-000000000002
SUB1=22222222-0000-4000-8000-000000000001
SUB2=22222222-0000-4000-8000-000000000002
INV1=33333333-0000-4000-8000-000000000001
INV2=33333333-0000-4000-8000-000000000002
INV3=33333333-0000-4000-8000-000000000003

psql_db() { docker exec -i "$1" psql -U postgres -d "$2" -v ON_ERROR_STOP=1; }

echo "── 1/7 Katalog (tarifeler + ek paketler)"
psql_db product-catalog-db product_catalog_db <<SQL
INSERT INTO product_catalog_service.tariffs (id,code,name,type,monthly_fee,minutes_included,sms_included,data_mb_included,status,effective_from,target_segment,version) VALUES
 ('44444444-0000-4000-8000-000000000001','GENC_HYBRID_10GB','Genç Hybrid 10GB','HYBRID',259.90,500,250,10240,'ACTIVE','2026-01-01','YOUTH',2),
 ('44444444-0000-4000-8000-000000000002','SUPER_POSTPAID_20GB','Süper Postpaid 20GB','POSTPAID',349.90,1000,500,20480,'ACTIVE','2026-01-01','GENERAL',1),
 ('44444444-0000-4000-8000-000000000003','EKO_PREPAID_5GB','Eko Prepaid 5GB','PREPAID',149.90,250,100,5120,'ACTIVE','2026-03-01','GENERAL',1)
ON CONFLICT (code) DO NOTHING;
INSERT INTO product_catalog_service.tariff_versions (id,tariff_id,version,code,name,type,monthly_fee,minutes_included,sms_included,data_mb_included,status,effective_from,target_segment,archived_at) VALUES
 ('44444444-0000-4000-8000-000000000011','44444444-0000-4000-8000-000000000001',1,'GENC_HYBRID_10GB','Genç Hybrid 10GB','HYBRID',249.90,500,250,10240,'ACTIVE','2026-01-01','YOUTH',now())
ON CONFLICT DO NOTHING;
INSERT INTO product_catalog_service.addons (id,code,name,price,type,validity_days) VALUES
 ('45444444-0000-4000-8000-000000000001','EK_DATA_5GB','Ek 5GB İnternet',89.90,'DATA',30),
 ('45444444-0000-4000-8000-000000000002','EK_SMS_250','Ek 250 SMS',29.90,'SMS',30),
 ('45444444-0000-4000-8000-000000000003','MUZIK_VAS','TelcoX Müzik','19.90','VAS',30)
ON CONFLICT (code) DO NOTHING;
SQL

echo "── 2/7 Abonelik (abonelikler + MSISDN havuzu + SIM)"
psql_db subscription-db subscription_db <<SQL
INSERT INTO subscriptions (id,customer_id,order_id,msisdn,tariff_code,status,mnp_status,activated_at) VALUES
 ('$SUB1','$CUST1','66666666-0000-4000-8000-000000000001','05324174712','GENC_HYBRID_10GB','ACTIVE','NONE',now()-interval '75 days'),
 ('$SUB2','$CUST2','66666666-0000-4000-8000-000000000002','05335558899','SUPER_POSTPAID_20GB','ACTIVE','NONE',now()-interval '40 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO msisdn_pool (msisdn,status) VALUES
 ('05324174712','ALLOCATED'),('05335558899','ALLOCATED'),
 ('05360001122','FREE'),('05360001133','FREE'),('05360001144','FREE')
ON CONFLICT (msisdn) DO NOTHING;
INSERT INTO sim_cards (iccid,imsi,msisdn,status) VALUES
 ('8990001234567890001','286010000000001','05324174712','ACTIVE'),
 ('8990001234567890002','286010000000002','05335558899','ACTIVE')
ON CONFLICT (iccid) DO NOTHING;
SQL

echo "── 3/7 Kullanım (kota %82 dolulukta + kullanım kayıtları)"
psql_db usage-db usage_db <<SQL
INSERT INTO usage_service.quotas (id,subscription_id,period_start,period_end,minutes_remaining,sms_remaining,mb_remaining) VALUES
 ('55555555-0000-4000-8000-000000000001','$SUB1',date_trunc('month',now())::date,(date_trunc('month',now())+interval '1 month - 1 day')::date,312,198,1843),
 ('55555555-0000-4000-8000-000000000002','$SUB2',date_trunc('month',now())::date,(date_trunc('month',now())+interval '1 month - 1 day')::date,780,430,15360)
ON CONFLICT (id) DO NOTHING;
INSERT INTO usage_service.usage_records (id,subscription_id,type,quantity,recorded_at,cdr_ref)
SELECT gen_random_uuid(),'$SUB1',(ARRAY['DATA','VOICE','SMS'])[1+(g%3)],(ARRAY[512,4,1])[1+(g%3)]*(1+g%5),now()-(g||' hours')::interval,'CDR-2026-'||lpad(g::text,6,'0')
FROM generate_series(1,24) g
ON CONFLICT DO NOTHING;
SQL

echo "── 4/7 Fatura (3 fatura + kalemler + kesim dönemi)"
psql_db billing-db billing_db <<SQL
INSERT INTO billing_service.invoices (id,customer_id,subscription_id,period_start,period_end,sub_total,tax,grand_total,status,due_date,issued_at) VALUES
 ('$INV1','$CUST1','$SUB1',date_trunc('month',now())::date,(date_trunc('month',now())+interval '1 month - 1 day')::date,259.90,52.00,311.90,'PENDING',(date_trunc('month',now())+interval '1 month')::date,now()),
 ('$INV2','$CUST1','$SUB1',(date_trunc('month',now())-interval '1 month')::date,(date_trunc('month',now())-interval '1 day')::date,249.90,50.00,299.90,'PAID',date_trunc('month',now())::date,now()-interval '1 month'),
 ('$INV3','$CUST1','$SUB1',(date_trunc('month',now())-interval '2 month')::date,(date_trunc('month',now())-interval '1 month - 1 day')::date,274.80,55.00,329.80,'PAID',(date_trunc('month',now())-interval '1 month')::date,now()-interval '2 month')
ON CONFLICT (id) DO NOTHING;
INSERT INTO billing_service.invoice_lines (id,invoice_id,description,quantity,unit_price,line_total) VALUES
 ('37333333-0000-4000-8000-000000000001','$INV1','Genç Hybrid 10GB aylık ücret',1,259.90,259.90),
 ('37333333-0000-4000-8000-000000000002','$INV2','Genç Hybrid 10GB aylık ücret',1,249.90,249.90),
 ('37333333-0000-4000-8000-000000000003','$INV3','Genç Hybrid 10GB aylık ücret',1,249.90,249.90),
 ('37333333-0000-4000-8000-000000000004','$INV3','Aşım: İnternet 1.2GB',1.2,20.75,24.90)
ON CONFLICT (id) DO NOTHING;
INSERT INTO billing_service.bill_cycles (id,customer_id,subscription_id,day_of_month,next_run_date) VALUES
 ('38333333-0000-4000-8000-000000000001','$CUST1','$SUB1',1,(date_trunc('month',now())+interval '1 month')::date)
ON CONFLICT (id) DO NOTHING;
SQL

echo "── 5/7 Ödeme (ödenmiş faturaların tahsilatları)"
psql_db payment-db payment_db <<SQL
INSERT INTO payment_service.payments (id,invoice_id,customer_id,amount,method,status,external_ref,paid_at) VALUES
 ('39333333-0000-4000-8000-000000000001','$INV2','$CUST1',299.90,'CREDIT_CARD','COMPLETED','PSP-REF-88421',now()-interval '25 days'),
 ('39333333-0000-4000-8000-000000000002','$INV3','$CUST1',329.80,'CREDIT_CARD','COMPLETED','PSP-REF-71203',now()-interval '55 days')
ON CONFLICT (id) DO NOTHING;
SQL

echo "── 6/7 Bildirim (geçmiş bildirimler)"
psql_db notification-db notification_db <<SQL
INSERT INTO notification_service.notifications (id,user_id,template_code,channel,payload_json,status,sent_at) VALUES
 ('50333333-0000-4000-8000-000000000001','$CUST1','QUOTA_WARNING_80','SMS','{"thresholdPercentage":"80"}','SENT',now()-interval '2 hours'),
 ('50333333-0000-4000-8000-000000000002','$CUST1','INVOICE_GENERATED','EMAIL','{"amount":"311.90","currency":"TRY"}','SENT',now()-interval '1 day'),
 ('50333333-0000-4000-8000-000000000003','$CUST1','PAYMENT_RECEIVED','SMS','{"amount":"299.90","currency":"TRY"}','SENT',now()-interval '25 days'),
 ('50333333-0000-4000-8000-000000000004','$CUST1','WELCOME_SMS','SMS','{"tariffCode":"GENC_HYBRID_10GB","msisdn":"05324174712"}','SENT',now()-interval '75 days')
ON CONFLICT (id) DO NOTHING;
SQL

echo "── 7/7 Destek (talepler + yorumlar)"
psql_db ticket-db ticket_db <<SQL
INSERT INTO ticket_service.tickets (id,customer_id,category,priority,status,sla_due_at,created_at,assigned_team,sla_breach_notified) VALUES
 ('60333333-0000-4000-8000-000000000001','$CUST1','BILLING','HIGH','RESOLVED',now()-interval '1 day'+interval '8 hours',now()-interval '1 day','Kıdemli Destek Ekibi',false),
 ('60333333-0000-4000-8000-000000000002','$CUST1','NETWORK','MEDIUM','IN_PROGRESS',now()+interval '20 hours',now()-interval '4 hours','Genel Destek Ekibi',false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO ticket_service.ticket_comments (id,ticket_id,author_id,body,created_at) VALUES
 ('61333333-0000-4000-8000-000000000001','60333333-0000-4000-8000-000000000001','$CUST1','Faturamda tanımadığım bir kalem var.',now()-interval '1 day'),
 ('61333333-0000-4000-8000-000000000002','60333333-0000-4000-8000-000000000002','$CUST1','Evde sinyal çok zayıf, görüşme yapamıyorum.',now()-interval '4 hours')
ON CONFLICT (id) DO NOTHING;
SQL

echo
echo "✔ Demo verisi hazır. Arayüz: http://localhost:8080/TelcoX.html"
echo "  Müşteri: elif.aydin / telcox123   ·   Yönetici: ops / telcox123"
