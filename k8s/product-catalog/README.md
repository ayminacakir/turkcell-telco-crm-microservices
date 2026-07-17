# Kubernetes Demo — product-catalog-service

Dokümanın K8s hedefinin (Bölüm 11: "Minikube / Kind ile lokal") tek servislik örnek uygulaması.

**Neden bu servis?** Stateless, read-heavy ve Redis cache'li — HPA ile yatay ölçeklemenin
(NFR: "K8s HPA ile auto-scale") en anlamlı olduğu servis. Aynı kalıp diğer servislere kopyalanabilir.

## Ön koşullar

- Docker Desktop, [kind](https://kind.sigs.k8s.io) (`brew install kind`), kubectl

## Adımlar

```bash
# 1) Cluster olustur
kind create cluster --name telco

# 2) Jar + imaj uret ve cluster'a yukle (repo kokunden)
mvn -pl product-catalog-service package -DskipTests
docker build -t telco/product-catalog-service:local product-catalog-service
kind load docker-image telco/product-catalog-service:local --name telco

# 3) Manifest'leri uygula
kubectl apply -f k8s/product-catalog/

# 4) Pod'larin hazir olmasini izle
kubectl -n telco-crm get pods -w
```

## Test

```bash
kubectl -n telco-crm port-forward svc/product-catalog 9003:9003
```

Başka terminalde:

```bash
curl -s http://localhost:9003/actuator/health     # {"status":"UP"}
curl -s http://localhost:9003/hello
curl -s "http://localhost:9003/api/v1/tariffs?page=0&size=5"   # GET'ler token istemez
```

## HPA (otomatik ölçekleme)

HPA, metrics-server ister (kind'da varsayılan yok):

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n telco-crm get hpa -w
```

Yük üretince (`hey` veya döngüde curl) replika sayısının 4'e kadar arttığını görürsünüz.

## Bilinçli sadeleştirmeler

- **Kafka/Keycloak/config-server cluster'da yok:** outbox event'leri publish edilemez ve
  PENDING/FAILED bekler (retry mekanizması sayesinde kayıp olmaz); admin POST'ları token
  gerektirdiğinden bu demoda yalnızca GET'ler test edilir. Tam entegrasyon docker-compose'da.
- **Postgres kalıcı disksiz** — demo amaçlı; production'da PVC/StatefulSet veya yönetilen DB.

## Temizlik

```bash
kind delete cluster --name telco
```
