# GGeolmuse Helm Chart

A Helm chart for deploying the GGeolmuse Trading Platform on Kubernetes.

## Prerequisites

- Kubernetes 1.24+
- Helm 3.8+
- Nginx Ingress Controller installed
- kubectl configured to communicate with your cluster

## Architecture

This chart deploys a complete microservices trading platform:

### Infrastructure Layer
- **Redis**: In-memory data store for caching
- **Keycloak**: Identity and access management

### Application Layer
- **Config Server**: Centralized configuration management
- **Gateway Server**: API gateway with service discovery
- **User Service**: User management and authentication
- **Trade Service**: Trading operations and portfolio management
- **Market Data Service**: Stock prices and market data
- **Backtest Service**: Investment strategy backtesting
- **Frontend Web**: React-based web application

## Installation

### 1. Create Kubernetes Secret

Before installing, create a secret with required credentials:

```bash
kubectl create secret generic ggeolmuse-secrets \
  --from-literal=KEYCLOAK_SECRET=your-keycloak-secret \
  --from-literal=KEYCLOAK_ADMIN_USERNAME=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=admin \
  --from-literal=KEYCLOAK_CLIENT_SECRET=your-client-secret \
  --from-literal=KEYCLOAK_TEST_USER_PASSWORD=test-password \
  --from-literal=MAIL_USERNAME=your-email@example.com \
  --from-literal=MAIL_PASSWORD=your-email-password \
  --from-literal=MAIL_FROM=noreply@ggeolmuse.com \
  --from-literal=ALPHAVANTAGE_API_KEY=your-alphavantage-key \
  --from-literal=KOREAEXIM_API_KEY=your-koreaexim-key
```

### 2. Install the Chart

#### Development Environment

```bash
helm install ggeolmuse ./helm/ggeolmuse -f ./helm/ggeolmuse/values-dev.yaml
```

#### Production Environment

```bash
helm install ggeolmuse ./helm/ggeolmuse -f ./helm/ggeolmuse/values-prod.yaml
```

#### Custom Configuration

```bash
helm install ggeolmuse ./helm/ggeolmuse \
  --set global.domain=my-domain.com \
  --set services.userService.replicaCount=3
```

## Configuration

### Global Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `global.domain` | Main application domain | `app.localhost` |
| `global.authDomain` | Keycloak authentication domain | `auth.localhost` |
| `global.imageRegistry` | Docker image registry | `muscathan` |
| `global.imagePullPolicy` | Image pull policy | `Always` |
| `global.springProfiles` | Spring active profiles | `dev,no-file-logging` |

### Service Configuration

Each service can be configured with:

- `enabled`: Enable/disable the service
- `replicaCount`: Number of pod replicas
- `image.repository`: Docker image repository
- `image.tag`: Docker image tag
- `service.type`: Kubernetes service type
- `service.port`: Service port

Example:

```yaml
services:
  userService:
    enabled: true
    replicaCount: 3
    image:
      repository: user-service
      tag: "1.2.3"
```

### Environment-Specific Values

#### Development (`values-dev.yaml`)
- Single replica for all services
- localhost domains
- Minimal resource allocation
- CORS allowing local development ports

#### Production (`values-prod.yaml`)
- Multiple replicas for HA
- Production domains with TLS
- Scaled resources
- Stricter CORS policies

## Upgrading

```bash
# Upgrade to new version
helm upgrade ggeolmuse ./helm/ggeolmuse -f ./helm/ggeolmuse/values-dev.yaml

# Upgrade with new image tag
helm upgrade ggeolmuse ./helm/ggeolmuse \
  --set services.userService.image.tag=1.2.4 \
  --reuse-values
```

## Rollback

```bash
# List releases
helm history ggeolmuse

# Rollback to previous version
helm rollback ggeolmuse

# Rollback to specific revision
helm rollback ggeolmuse 3
```

## Uninstallation

```bash
helm uninstall ggeolmuse
```

This will remove all Kubernetes resources associated with the chart, except for:
- Secrets (must be deleted manually)
- PersistentVolumeClaims (if any)

## Monitoring

### Check Pod Status

```bash
kubectl get pods -l app.kubernetes.io/instance=ggeolmuse
```

### View Logs

```bash
# User Service
kubectl logs -f deployment/user-service

# All services
kubectl logs -f -l app.kubernetes.io/instance=ggeolmuse
```

### Access Services

After installation with Ingress enabled:

- **Frontend**: http://app.localhost
- **Keycloak**: http://auth.localhost
- **API**: http://app.localhost/api/*

## Troubleshooting

### Pods not starting

```bash
# Describe pod to see events
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>
```

### Secret not found

Make sure you created the `ggeolmuse-secrets` before installation:

```bash
kubectl get secret ggeolmuse-secrets
```

### Network Policy issues

If services can't communicate:

```bash
# Check network policy
kubectl get networkpolicy
kubectl describe networkpolicy allow-from-gateway
```

### Ingress not working

```bash
# Check ingress controller
kubectl get pods -n ingress-nginx

# Check ingress resource
kubectl get ingress
kubectl describe ingress ggeolmuse-ingress
```

## Advanced Configuration

### Custom Values File

Create your own `values-custom.yaml`:

```yaml
global:
  domain: my-app.example.com

services:
  userService:
    replicaCount: 5
    resources:
      limits:
        memory: "2Gi"
        cpu: "1000m"
```

Install with custom values:

```bash
helm install ggeolmuse ./helm/ggeolmuse -f values-custom.yaml
```

### Disable Specific Services

```yaml
services:
  backtestService:
    enabled: false
```

### External Dependencies

To use external Redis or Keycloak instead of bundled ones:

```yaml
infrastructure:
  redis:
    enabled: false
  keycloak:
    enabled: false

global:
  redis:
    host: external-redis.example.com
    port: 6379
  keycloak:
    authServerUrl: https://external-keycloak.example.com
```

## Development

### Template Testing

```bash
# Render templates without installation
helm template ggeolmuse ./helm/ggeolmuse -f ./helm/ggeolmuse/values-dev.yaml

# Debug installation
helm install ggeolmuse ./helm/ggeolmuse -f ./helm/ggeolmuse/values-dev.yaml --dry-run --debug
```

### Linting

```bash
helm lint ./helm/ggeolmuse
```

## Support

For issues and questions:
- GitHub: https://github.com/musqat/ggeolmuse/issues
- Documentation: https://github.com/musqat/ggeolmuse

## License

Copyright © 2025 GGeolmuse Team
