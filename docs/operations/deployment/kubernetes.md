# Kubernetes Deployment

> Deploy BUTTERFLY on Kubernetes for production workloads

**Last Updated**: 2025-12-03  
**Target Audience**: Platform engineers, SREs

---

## Prerequisites

- Kubernetes 1.27+
- kubectl configured
- Helm 3.12+
- 32+ GB cluster memory
- Storage class with dynamic provisioning

---

## Quick Start

### Using Helm

```bash
# Add Helm repository
helm repo add butterfly https://charts.butterfly.example.com
helm repo update

# Install with default values
helm install butterfly butterfly/butterfly-stack \
  --namespace butterfly \
  --create-namespace

# Install with custom values
helm install butterfly butterfly/butterfly-stack \
  --namespace butterfly \
  --create-namespace \
  -f values-production.yaml
```

---

## Namespace Setup

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: butterfly
  labels:
    name: butterfly
    env: production
```

```bash
kubectl apply -f namespace.yaml
```

---

## Secrets Configuration

### Create Secrets

```bash
# Create secrets from literals
kubectl create secret generic butterfly-secrets \
  --namespace butterfly \
  --from-literal=postgres-password='your-strong-password' \
  --from-literal=jwt-secret='your-jwt-secret-256-bits' \
  --from-literal=api-key-salt='your-api-key-salt'

# Or from file
kubectl create secret generic butterfly-secrets \
  --namespace butterfly \
  --from-file=./secrets/
```

### External Secrets (Production)

```yaml
# external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: butterfly-secrets
  namespace: butterfly
spec:
  refreshInterval: 1h
  secretStoreRef:
    kind: ClusterSecretStore
    name: vault-backend
  target:
    name: butterfly-secrets
  data:
    - secretKey: postgres-password
      remoteRef:
        key: butterfly/production
        property: postgres-password
    - secretKey: jwt-secret
      remoteRef:
        key: butterfly/production
        property: jwt-secret
```

---

## Service Deployments

### CAPSULE

```yaml
# capsule-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: capsule
  namespace: butterfly
  labels:
    app: capsule
    component: service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: capsule
  template:
    metadata:
      labels:
        app: capsule
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: butterfly-service
      containers:
        - name: capsule
          image: butterfly/capsule:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes,production"
            - name: JAVA_OPTS
              value: "-Xms512m -Xmx2g -XX:+UseG1GC"
            - name: CASSANDRA_CONTACT_POINTS
              value: "cassandra-0.cassandra.butterfly.svc.cluster.local"
            - name: REDIS_HOST
              value: "redis-master.butterfly.svc.cluster.local"
            - name: KAFKA_BOOTSTRAP_SERVERS
              value: "kafka-0.kafka.butterfly.svc.cluster.local:9092"
          envFrom:
            - secretRef:
                name: butterfly-secrets
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2"
              memory: "4Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: capsule
                topologyKey: kubernetes.io/hostname
---
apiVersion: v1
kind: Service
metadata:
  name: capsule
  namespace: butterfly
spec:
  selector:
    app: capsule
  ports:
    - port: 8080
      targetPort: 8080
      name: http
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: capsule-pdb
  namespace: butterfly
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: capsule
```

### NEXUS Gateway

```yaml
# nexus-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nexus
  namespace: butterfly
  labels:
    app: nexus
    component: gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nexus
  template:
    metadata:
      labels:
        app: nexus
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8083"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: butterfly-service
      containers:
        - name: nexus
          image: butterfly/nexus:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8083
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "kubernetes,production"
            - name: JAVA_OPTS
              value: "-Xms512m -Xmx2g -XX:+UseG1GC"
            - name: CAPSULE_URL
              value: "http://capsule.butterfly.svc.cluster.local:8080"
            - name: ODYSSEY_URL
              value: "http://odyssey.butterfly.svc.cluster.local:8081"
            - name: PERCEPTION_URL
              value: "http://perception.butterfly.svc.cluster.local:8082"
            - name: PLATO_URL
              value: "http://plato.butterfly.svc.cluster.local:8086"
          envFrom:
            - secretRef:
                name: butterfly-secrets
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2"
              memory: "4Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8083
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8083
            initialDelaySeconds: 40
            periodSeconds: 30
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: nexus
              topologyKey: kubernetes.io/hostname
---
apiVersion: v1
kind: Service
metadata:
  name: nexus
  namespace: butterfly
spec:
  selector:
    app: nexus
  ports:
    - port: 8083
      targetPort: 8083
      name: http
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: nexus-pdb
  namespace: butterfly
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: nexus
```

---

## Ingress Configuration

### NGINX Ingress

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: butterfly-ingress
  namespace: butterfly
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.butterfly.example.com
      secretName: butterfly-tls
  rules:
    - host: api.butterfly.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: nexus
                port:
                  number: 8083
```

---

## Horizontal Pod Autoscaler

```yaml
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: nexus-hpa
  namespace: butterfly
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nexus
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15
        - type: Pods
          value: 4
          periodSeconds: 15
      selectPolicy: Max
```

---

## ConfigMaps

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: butterfly-config
  namespace: butterfly
data:
  application.yaml: |
    server:
      shutdown: graceful
    
    spring:
      lifecycle:
        timeout-per-shutdown-phase: 30s
    
    management:
      endpoints:
        web:
          exposure:
            include: health,prometheus,info
      health:
        probes:
          enabled: true
```

---

## Network Policies

```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: butterfly-network-policy
  namespace: butterfly
spec:
  podSelector:
    matchLabels:
      component: service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: nexus
      ports:
        - protocol: TCP
          port: 8080
        - protocol: TCP
          port: 8081
        - protocol: TCP
          port: 8082
        - protocol: TCP
          port: 8086
  egress:
    - to:
        - podSelector:
            matchLabels:
              component: database
      ports:
        - protocol: TCP
          port: 9042
        - protocol: TCP
          port: 5432
        - protocol: TCP
          port: 6379
    - to:
        - podSelector:
            matchLabels:
              app: kafka
      ports:
        - protocol: TCP
          port: 9092
```

---

## Deployment Commands

```bash
# Apply all manifests
kubectl apply -k ./kubernetes/

# Check deployment status
kubectl get deployments -n butterfly
kubectl get pods -n butterfly

# View logs
kubectl logs -f deployment/capsule -n butterfly

# Port forward for local access
kubectl port-forward svc/nexus 8083:8083 -n butterfly

# Rolling update
kubectl rollout restart deployment/capsule -n butterfly

# Check rollout status
kubectl rollout status deployment/capsule -n butterfly

# Rollback
kubectl rollout undo deployment/capsule -n butterfly
```

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Docker Deployment](docker.md) | Development setup |
| [Production Checklist](production-checklist.md) | Go-live checklist |
| [Monitoring](../monitoring/README.md) | Observability |

