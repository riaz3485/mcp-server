# Textellent MCP Server

A Spring Boot server that exposes Textellent APIs through **Spring AI MCP** using **STREAMABLE HTTP** transport.

This codebase now uses a pure Spring AI MCP implementation (no legacy custom MCP controller/registry stack).

## Features

### Core Capabilities
- **26 MCP Tools** exposing Textellent's current API surface
- **Spring AI MCP (STREAMABLE)** endpoint at `/mcp`
- **Domain-modular tool classes** under `com.textellent.mcp.tools`
- **Shared execution core** under `com.textellent.mcp.core`
- **JSON schema-based validation** using `src/main/resources/schemas/*.json` as the source of truth

### Security & Authorization
- **OAuth2 Resource Server** with JWT validation
- **Scope-based Access Control**:
  - `read` - Read-only operations
  - `write` - Write/mutating operations
- **API Key Authentication** (alternative mode)
- **Multi-tenant Isolation** via JWT claims
- **CORS Configuration** for web clients

### Operational Excellence
- **Rate Limiting** (separate limits for read/write operations)
- **Circuit Breaker & Retries** for resilience
- **Structured Logging** with correlation IDs
- **Audit Logging** for all tool calls
- **Health Checks** and metrics (Spring Actuator)
- **Prometheus Metrics** for monitoring

## Quick Start

### Local Development

```bash
# Clone and configure
git clone <repo-url>
cd mcp-server
cp .env.example .env

# Edit .env for local mode
echo "SECURITY_MODE=local" >> .env
echo "SPRING_PROFILES_ACTIVE=local" >> .env

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run

# Test
curl http://localhost:9090/health
curl http://localhost:9090/version
```

### Docker Deployment

```bash
# Build image
docker build -t textellent-mcp-server:latest .

# Run with docker-compose
cp .env.example .env
# Edit .env with production settings
docker-compose up -d

# View logs
docker-compose logs -f
```

### Production Deployment

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for comprehensive guides on:
- Kubernetes deployment
- Cloud platforms (AWS, GCP, Azure)
- OAuth2 configuration
- Connector setup for Claude/ChatGPT/n8n
- Monitoring and troubleshooting

## Architecture

### Runtime Architecture

```
┌─────────────────────────────────────────┐
│        AI Platforms                     │
│   - Claude Connectors                   │
│   - ChatGPT Apps                        │
│   - n8n MCP Client                      │
│   - Custom MCP Clients                  │
└──────────────┬──────────────────────────┘
               │
               │ HTTPS (MCP over HTTP)
               │ OAuth2 JWT / API Key
               │
               ▼
┌──────────────────────────────────────────┐
│     Load Balancer / API Gateway          │
│   - TLS Termination                      │
│   - DDoS Protection                      │
│   - Rate Limiting (L7)                   │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│   Textellent MCP Server (Instances)      │
│                                          │
│   ┌────────────────────────────────┐    │
│   │  Security Layer                │    │
│   │  - OAuth2 / API key / local    │    │
│   │  - Scope Enforcement           │    │
│   │  - Tenant Isolation            │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  Spring AI MCP Server          │    │
│   │  - Streamable endpoint (/mcp)  │    │
│   │  - @McpTool annotations        │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  Tool Layer                    │    │
│   │  - com.textellent.mcp.tools    │    │
│   │  - messages/contacts/tags/...  │    │
│   └────────────┬───────────────────┘    │
│                │                         │
│   ┌────────────▼───────────────────┐    │
│   │  Core Layer                    │    │
│   │  - dispatch / auth / rate-limit│    │
│   │  - schema validation           │    │
│   └────────────┬───────────────────┘    │
└────────────────┼────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────┐
│     Textellent API Backend               │
│   (Your existing REST API)               │
└──────────────────────────────────────────┘
```

### Request Flow

```
1. Client Request
   ├─→ MCP-Protocol-Version header check
   ├─→ OAuth2 JWT validation
   └─→ Extract tenant context from JWT

2. Authorization
   ├─→ Check required scope for tool
   ├─→ Enforce read vs write permissions
   └─→ Rate limit check (tenant-specific)

3. Tool Execution
   ├─→ Validate arguments against canonical JSON schema
   ├─→ Execute tool via modular core dispatcher
   ├─→ Apply circuit breaker & retries
   └─→ Call Textellent backend API

4. Response
   ├─→ Format as MCP-compliant content
   ├─→ Log audit event
   └─→ Return JSON-RPC response
```

## API Endpoints

### MCP Protocol Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/mcp` | Required | Spring AI MCP streamable endpoint |
| GET | `/health` | Public | Health check |
| GET | `/version` | Public | Version information |
| GET | `/actuator/health` | Auth | Detailed health |
| GET | `/actuator/metrics` | Auth | Metrics |
| GET | `/actuator/prometheus` | Auth | Prometheus metrics |

### Tool Package Layout

- `com.textellent.mcp.tools.messages`
- `com.textellent.mcp.tools.contacts`
- `com.textellent.mcp.tools.tags`
- `com.textellent.mcp.tools.events`
- `com.textellent.mcp.tools.webhooks`
- `com.textellent.mcp.core` (shared execution, credentials, and schema validation)

## Available Tools

Contacts, tags, messages, events, and configuration tools are all directly callable through `tools/call`.

## Security Configuration

### OAuth2 JWT Mode (Production)

```yaml
SECURITY_MODE=oauth2
OAUTH2_ISSUER_URI=https://your-auth-provider.com/
# OR
OAUTH2_JWK_SET_URI=https://your-auth-provider.com/.well-known/jwks.json
```

**JWT Requirements**:
- Standard claims: `iss`, `sub`, `exp`, `iat`
- Tenant claim: `tenant_id`, `tenantId`, or `organization_id`
- Scope claim: `scope` (space-separated) or `scp` (array)

**Example JWT**:
```json
{
  "iss": "https://auth.yourcompany.com",
  "sub": "user|123456",
  "exp": 1735689600,
  "iat": 1735686000,
  "tenant_id": "acme-corp",
  "scope": "read write"
}
```

### API Key Mode (Simple)

```yaml
SECURITY_MODE=apikey
API_KEY=your-secret-key-here
API_KEY_SCOPES=read,write
```

**Client Request**:
```bash
curl -X POST https://your-server.com/mcp \
  -H "X-API-Key: your-secret-key-here" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### Local Mode (Development Only)

```yaml
SECURITY_MODE=local
SPRING_PROFILES_ACTIVE=local
```

**⚠️ Warning**: Disables all security. Never use in production!

## Usage Examples

### Call a Tool

```bash
curl -X POST https://mcp.yourcompany.com/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_TEXTELLENT_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "contacts_get_all",
      "arguments": {
        "searchKey": "",
        "pageSize": 10,
        "pageNum": 1
      }
    }
  }'
```

## Environment Variables

### Required

| Variable | Description | Example |
|----------|-------------|---------|
| `SECURITY_MODE` | Auth mode: oauth2, apikey, local | `oauth2` |
| `TEXTELLENT_API_BASE_URL` | Backend API URL | `https://client.textellent.com` |

### OAuth2 Configuration

| Variable | Description |
|----------|-------------|
| `OAUTH2_ISSUER_URI` | OAuth2 issuer URI (auto-discovers JWKS) |
| `OAUTH2_JWK_SET_URI` | Direct JWKS endpoint URL |

### API Key Configuration

| Variable | Description |
|----------|-------------|
| `API_KEY` | Secret API key |
| `API_KEY_SCOPES` | Comma-separated scopes |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `9090` | Server port |
| `ALLOWED_ORIGINS` | `*` | CORS allowed origins |
| `RATELIMIT_READ_CAPACITY` | `100` | Read ops/minute |
| `RATELIMIT_WRITE_CAPACITY` | `20` | Write ops/minute |
| `LOG_LEVEL` | `INFO` | Root log level |
| `LOG_LEVEL_SECURITY` | `INFO` | Security log level |

See `.env.example` for complete list.

## Multi-Tenancy

The server automatically extracts tenant context from JWT claims:

1. **Tenant Identification**:
   - `tenant_id` claim (preferred)
   - `tenantId` claim (alternative)
   - `organization_id` claim (alternative)

2. **Tenant Isolation**:
   - Separate rate limit buckets per tenant
   - Tenant ID included in all audit logs
   - Tenant context in structured logging (MDC)

3. **Header Override** (API key mode):
   ```bash
   -H "X-Tenant-ID: custom-tenant-id"
   ```

## Rate Limiting

### Configuration

```yaml
# Read operations (GET, LIST)
RATELIMIT_READ_CAPACITY=100         # Max tokens
RATELIMIT_READ_REFILL=100           # Tokens per refill
RATELIMIT_READ_DURATION=1           # Refill interval (minutes)

# Write operations (CREATE, UPDATE, DELETE)
RATELIMIT_WRITE_CAPACITY=200
RATELIMIT_WRITE_REFILL=200
RATELIMIT_WRITE_DURATION=1
```

### Behavior

- **Per-Tenant**: Each tenant has separate buckets
- **Tool-Based**: Read tools check read limit, write tools check write limit
- **Response**: HTTP 200 with JSON-RPC error `-32000` when exceeded

## Audit Logging

All tool calls are logged with:
- Timestamp
- Tenant ID
- User ID
- Trace ID (correlation)
- Tool name
- Status (SUCCESS/FAILURE)
- Redacted arguments (sensitive fields masked)

Example audit log:
```json
{
  "timestamp": "2025-01-15T10:30:45.123Z",
  "event": "TOOL_CALL",
  "tenantId": "acme-corp",
  "userId": "user|123456",
  "traceId": "abc-def-123",
  "toolName": "messages_send",
  "status": "SUCCESS",
  "arguments": {
    "text": "Hello",
    "from": "+1234567890",
    "authCode": "***REDACTED***"
  }
}
```

## Monitoring

### Health Checks

```bash
# Basic health
curl https://mcp.yourcompany.com/health

# Detailed health (requires auth)
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/health
```

### Metrics

```bash
# All metrics
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/metrics

# Prometheus format
curl -H "Authorization: Bearer TOKEN" \
  https://mcp.yourcompany.com/actuator/prometheus
```

### Key Metrics

- `http.server.requests` - Request counts and latencies
- `resilience4j.circuitbreaker.calls` - Circuit breaker stats
- `resilience4j.retry.calls` - Retry attempts
- `jvm.memory.used` - Memory usage
- `system.cpu.usage` - CPU usage

## Development

### Prerequisites

- Java 8+
- Maven 3.6+
- Docker (optional)

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

### Run Locally

```bash
# With Maven
mvn spring-boot:run

# With JAR
java -jar target/textellent-mcp-server-1.0.0.jar
```

### IDE Setup

1. Import as Maven project
2. Enable Lombok annotation processing
3. Set Java SDK to 8 or higher

## Deployment

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for detailed guides on:

- **Docker** - Container deployment
- **Kubernetes** - Scalable cluster deployment
- **AWS ECS/Fargate** - Serverless containers
- **Google Cloud Run** - Managed containers
- **Azure Container Instances** - Quick deployment

## Connector Integration

### Claude Connectors

Submit to Claude Directory with:
- Endpoint: `https://your-server.com/mcp`
- Protocol: MCP over HTTPS
- Auth: OAuth2 with PKCE
- Scopes: `read`, `write`

### ChatGPT Apps (MCP)

Configure GPT Action with OpenAPI spec pointing to `/mcp` endpoint.

### n8n MCP Client

Use HTTP Request node with:
- POST to `/mcp`
- OAuth2 authentication
- JSON-RPC 2.0 request body

See [DEPLOYMENT.md](DEPLOYMENT.md#connector-setup) for detailed setup instructions.

## Troubleshooting

### Common Issues

**401 Unauthorized**
- Check JWT token validity and expiration
- Verify issuer URI matches token issuer

**403 Forbidden**
- User lacks required scope
- Check `requiredScope` in tool definition

**429 Rate Limit Exceeded**
- Adjust rate limits or implement backoff
- Check tenant-specific limits

**Connection Refused**
- Verify `TEXTELLENT_API_BASE_URL`
- Ensure backend API is accessible

See [DEPLOYMENT.md](DEPLOYMENT.md#troubleshooting) for more details.

## License

Copyright © 2025 Textellent. All rights reserved.

## Support

- **Documentation**: [DEPLOYMENT.md](DEPLOYMENT.md), [CONFIGURATION.md](CONFIGURATION.md)
- **Logs**: Check structured logs with trace IDs
- **Health**: Monitor `/actuator/health` endpoint
- **Metrics**: View `/actuator/prometheus` for insights

---

**Version**: 1.0.0
**MCP Protocol**: 2025-06-18 (Spring AI STREAMABLE)
**Spring Boot**: 3.3.5
**Java**: 22
