# Textellent MCP Server — Build, Run, and Usage Guide

This document is a complete, platform-by-platform guide for compiling, configuring, running, and using the **Textellent MCP Server**. It assumes you may be new to Java, Maven, and Docker. If you already know those tools, use the [Quick Reference](#quick-reference) at the end.

---

## Table of Contents

1. [What This Project Is](#what-this-project-is)
2. [Architecture Overview](#architecture-overview)
3. [Prerequisites](#prerequisites)
4. [Installing Prerequisites — Linux](#installing-prerequisites--linux)
5. [Installing Prerequisites — macOS](#installing-prerequisites--macos)
6. [Installing Prerequisites — Windows](#installing-prerequisites--windows)
7. [Getting the Source Code](#getting-the-source-code)
8. [Maven Repository Access (Important)](#maven-repository-access-important)
9. [Configuration](#configuration)
10. [Compiling the Project](#compiling-the-project)
11. [Running Without Docker](#running-without-docker)
12. [Docker for Beginners](#docker-for-beginners)
13. [Installing Docker — Linux, macOS, and Windows](#installing-docker--linux-macos-and-windows)
14. [Building and Running With Docker](#building-and-running-with-docker)
15. [Using the MCP Server](#using-the-mcp-server)
16. [Connecting AI Agents](#connecting-ai-agents)
17. [Testing Your Setup](#testing-your-setup)
18. [Troubleshooting](#troubleshooting)
19. [Quick Reference](#quick-reference)

---

## What This Project Is

The **Textellent MCP Server** is a [Spring Boot](https://spring.io/projects/spring-boot) application that exposes Textellent REST APIs through the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/). AI platforms (Claude, ChatGPT, n8n, custom clients) send JSON-RPC requests to this server; the server validates them and forwards calls to your Textellent API backend.

| Property | Value |
|----------|-------|
| **Artifact name** | `textellent-mcp-server` |
| **Version** | `1.0.0` |
| **Default port** | `9090` |
| **MCP endpoint** | `POST /mcp` |
| **Protocol** | MCP STREAMABLE over HTTP (JSON-RPC 2.0) |
| **Spring Boot** | `4.0.5` |
| **Spring AI** | `2.0.0-M3` |
| **Recommended Java (runtime)** | **Java 22** (matches `Dockerfile`) |
| **Build tool** | Apache Maven 3.6+ |
| **Registered MCP tools** | 28 (one JSON schema per tool under `src/main/resources/schemas/`) |

The server does **not** replace your Textellent API. It sits in front of it as a protocol adapter:

```
AI Client  →  MCP Server (port 9090)  →  Textellent REST API (e.g. port 8080)
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  AI Platforms (Claude Desktop, ChatGPT, n8n, curl, etc.)    │
└──────────────────────────┬──────────────────────────────────┘
                           │  HTTPS / HTTP
                           │  JSON-RPC 2.0 → POST /mcp
                           │  Auth: OAuth2 JWT, API Key, or none (local)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Textellent MCP Server (this project)                       │
│  - Security (OAuth2 / API key / local dev mode)             │
│  - Rate limiting, circuit breaker, audit logging            │
│  - Spring AI MCP (STREAMABLE transport)                     │
│  - 28 tools: contacts, tags, messages, events, webhooks     │
└──────────────────────────┬──────────────────────────────────┘
                           │  REST (WebClient)
                           │  Headers: authCode, partnerClientCode
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Textellent API Backend                                     │
│  Default: http://localhost:8080                             │
│  Production: https://client.textellent.com                  │
└─────────────────────────────────────────────────────────────┘
```

**Request flow (simplified):**

1. Client sends a JSON-RPC request to `POST /mcp`.
2. The server authenticates the request (unless `SECURITY_MODE=local`).
3. For `tools/call`, arguments are validated against JSON schemas in `src/main/resources/schemas/`.
4. The appropriate service class calls the Textellent REST API.
5. The response is wrapped in MCP-compliant JSON-RPC format and returned.

---

## Prerequisites

Everything you need before you can compile or run this project.

### Required (all platforms)

| Prerequisite | Minimum version | Recommended | Purpose |
|--------------|-----------------|-------------|---------|
| **Java JDK** | 17 (Spring Boot 4 requirement) | **22** | Compile and run the server; Docker image uses Java 22 |
| **Apache Maven** | 3.6 | 3.9+ | Download dependencies and build the JAR |
| **Git** | 2.x | Latest | Clone the repository |
| **Network access** | — | — | Maven must download dependencies from remote repositories |
| **Textellent API backend** | — | — | The REST API this server proxies to (local or remote) |
| **Textellent credentials** | — | — | `authCode` and optionally `partnerClientCode` for API calls |

### Required for Docker deployment

| Prerequisite | Purpose |
|--------------|---------|
| **Docker Engine** | Build and run the containerized server |
| **Docker Compose** (optional) | Orchestrate multi-container setups (not shipped in this repo; see [Docker section](#building-and-running-with-docker)) |

### Required for Claude Desktop integration (optional)

| Prerequisite | Minimum version | Purpose |
|--------------|-----------------|---------|
| **Node.js** | 18 LTS | Run `mcp-bridge.js` (stdio ↔ HTTP bridge) |
| **Claude Desktop** | 0.10.0+ | Desktop AI client that speaks MCP over stdio |

### Required for automated testing script (optional)

| Prerequisite | Purpose |
|--------------|---------|
| **curl** | HTTP requests from the command line |
| **jq** | Pretty-print JSON in `test-mcp.sh` |

### Required for IDE development (optional)

| Prerequisite | Purpose |
|--------------|---------|
| **IntelliJ IDEA** or **VS Code** + Java extensions | Edit and debug Java code |
| **Lombok plugin** | Annotation processing (`@Slf4j`, etc.) |

### Internal Maven dependency (critical)

| Dependency | Coordinates | Notes |
|------------|-------------|-------|
| **Textellent Maestro** | `com.textellent:maestro-starter:1.2.1` | **Not published on Maven Central.** You need access to Textellent's private Maven repository or a locally installed copy. See [Maven Repository Access](#maven-repository-access-important). |

### Disk and memory

| Resource | Recommendation |
|----------|----------------|
| **Disk space** | ~500 MB for JDK, Maven cache, and build artifacts |
| **RAM** | 2 GB minimum for build; 512 MB–1 GB for running the server |
| **CPU** | Any modern 64-bit processor |

### Port requirements

| Port | Service | Configurable? |
|------|---------|---------------|
| **9090** | MCP Server (default) | Yes — set `PORT` or `server.port` |
| **8080** | Textellent API backend (default target) | Yes — set `TEXTELLENT_API_BASE_URL` |

Ensure these ports are free before starting, or change the MCP server port in configuration.

---

## Installing Prerequisites — Linux

These instructions cover common distributions (Ubuntu/Debian, Fedora/RHEL). Adjust package names for your distro.

### 1. Install Git

**Ubuntu / Debian:**

```bash
sudo apt update
sudo apt install -y git
git --version
```

**Fedora / RHEL:**

```bash
sudo dnf install -y git
git --version
```

### 2. Install Java 22 (JDK)

**Ubuntu / Debian (using Temurin):**

```bash
sudo apt install -y wget apt-transport-https
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/trusted.gpg.d/adoptium.asc
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-22-jdk
java -version
```

**Fedora:**

```bash
sudo dnf install -y java-22-openjdk-devel
java -version
```

Set `JAVA_HOME` (add to `~/.bashrc` or `~/.zshrc`):

```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export PATH="$JAVA_HOME/bin:$PATH"
```

### 3. Install Apache Maven

**Ubuntu / Debian:**

```bash
sudo apt install -y maven
mvn -version
```

**Fedora:**

```bash
sudo dnf install -y maven
mvn -version
```

Or install manually from [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) and add `bin/` to your `PATH`.

### 4. Install curl and jq (optional, for testing)

```bash
# Ubuntu / Debian
sudo apt install -y curl jq

# Fedora
sudo dnf install -y curl jq
```

### 5. Install Node.js (optional, for Claude Desktop bridge)

```bash
# Using NodeSource (Ubuntu example)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node --version
```

---

## Installing Prerequisites — macOS

### 1. Install Homebrew (if not already installed)

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Follow the on-screen instructions to add Homebrew to your `PATH`.

### 2. Install Git, Java, Maven, curl, jq

```bash
brew install git maven curl jq
brew install --cask temurin@22
```

Verify:

```bash
git --version
java -version    # Should show Java 22
mvn -version
```

Set `JAVA_HOME` in `~/.zshrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 22)
export PATH="$JAVA_HOME/bin:$PATH"
```

Reload: `source ~/.zshrc`

### 3. Install Node.js (optional)

```bash
brew install node@20
node --version
```

---

## Installing Prerequisites — Windows

### 1. Install Git

1. Download Git for Windows from [https://git-scm.com/download/win](https://git-scm.com/download/win).
2. Run the installer; accept defaults unless you have preferences.
3. Open **Git Bash** or **PowerShell** and verify:

```powershell
git --version
```

### 2. Install Java 22 (JDK)

1. Download **Eclipse Temurin 22** from [https://adoptium.net/](https://adoptium.net/).
2. Run the installer. Enable **"Set JAVA_HOME"** and **"Add to PATH"** if offered.
3. Open a **new** Command Prompt or PowerShell:

```powershell
java -version
```

If `java` is not found, set environment variables manually:

- `JAVA_HOME` → e.g. `C:\Program Files\Eclipse Adoptium\jdk-22.x.x-hotspot`
- Add `%JAVA_HOME%\bin` to the system `Path`

### 3. Install Apache Maven

1. Download Maven from [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) (Binary zip).
2. Extract to e.g. `C:\Program Files\Apache\maven`.
3. Set environment variables:
   - `MAVEN_HOME` → `C:\Program Files\Apache\maven`
   - Add `%MAVEN_HOME%\bin` to `Path`
4. Verify in a new terminal:

```powershell
mvn -version
```

### 4. Install curl (optional)

Windows 10/11 includes `curl` in PowerShell. For `jq`, use [https://jqlang.github.io/jq/download/](https://jqlang.github.io/jq/download/) or:

```powershell
winget install jqlang.jq
```

### 5. Install Node.js (optional)

1. Download the LTS installer from [https://nodejs.org/](https://nodejs.org/).
2. Run the installer and verify:

```powershell
node --version
```

---

## Getting the Source Code

Clone the repository and enter the project directory:

```bash
git clone <your-repository-url>
cd mcp-server
```

On Windows, you may use Git Bash or PowerShell. Avoid paths with spaces if possible; if your path contains spaces (e.g. `External Drive`), always quote paths:

```powershell
cd "C:\path with spaces\mcp-server"
```

---

## Maven Repository Access (Important)

The project depends on **`com.textellent:maestro-starter:1.2.1`**, which is **not** available on Maven Central. Before `mvn package` will succeed, you must have one of the following:

### Option 1: Configure Textellent's private Maven repository (recommended for Textellent developers)

Add repository credentials to your Maven `settings.xml` (typically `~/.m2/settings.xml` on Linux/macOS or `%USERPROFILE%\.m2\settings.xml` on Windows). Your team should provide the repository URL, username, and password or token.

Example structure (replace placeholders with values from your team):

```xml
<settings>
  <servers>
    <server>
      <id>textellent-repo</id>
      <username>YOUR_USERNAME</username>
      <password>YOUR_TOKEN_OR_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>textellent</id>
      <repositories>
        <repository>
          <id>textellent-repo</id>
          <url>https://your-private-repo.example.com/repository/maven-releases/</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>textellent</activeProfile>
  </activeProfiles>
</settings>
```

### Option 2: Install Maestro JAR locally

If you have the `maestro-starter-1.2.1.jar` file:

```bash
mvn install:install-file \
  -DgroupId=com.textellent \
  -DartifactId=maestro-starter \
  -Dversion=1.2.1 \
  -Dpackaging=jar \
  -Dfile=/path/to/maestro-starter-1.2.1.jar
```

**Windows (PowerShell):** use backticks for line continuation or put the command on one line.

### Build note: Jackson version property

If Maven reports errors like `'${jackson.version}' must be a valid version`, the explicit Jackson dependencies in `pom.xml` are missing a property that Spring Boot's parent POM normally provides. Either:

- Remove the three explicit `jackson-*` dependencies (Spring Boot manages Jackson versions), **or**
- Add `<jackson.version>` to the `<properties>` section of `pom.xml`.

Contact your maintainers if the build fails at this step.

---

## Configuration

Configuration is loaded from `src/main/resources/application.yml` and can be overridden with **environment variables** (recommended for production and Docker).

### Minimum configuration for local development

| Variable | Value | Description |
|----------|-------|-------------|
| `SECURITY_MODE` | `local` | Disables OAuth2/API key checks (**development only**) |
| `SPRING_PROFILES_ACTIVE` | `local` | Activates local Spring profile |
| `TEXTELLENT_API_BASE_URL` | `http://localhost:8080` | URL of your Textellent REST API |

### Setting environment variables

**Linux / macOS (bash/zsh):**

```bash
export SECURITY_MODE=local
export SPRING_PROFILES_ACTIVE=local
export TEXTELLENT_API_BASE_URL=http://localhost:8080
```

**Windows (PowerShell):**

```powershell
$env:SECURITY_MODE = "local"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:TEXTELLENT_API_BASE_URL = "http://localhost:8080"
```

**Windows (Command Prompt):**

```cmd
set SECURITY_MODE=local
set SPRING_PROFILES_ACTIVE=local
set TEXTELLENT_API_BASE_URL=http://localhost:8080
```

### Security modes

| Mode | `SECURITY_MODE` | Use case |
|------|-----------------|----------|
| **Local** | `local` | Local development only. **No authentication.** Never use in production. |
| **API Key** | `apikey` | Simple deployments. Clients send `X-API-Key` header. |
| **OAuth2 JWT** | `oauth2` | Production. Clients send `Authorization: Bearer <JWT>`. |

#### API Key mode variables

| Variable | Example | Description |
|----------|---------|-------------|
| `API_KEY` | `your-secret-key` | Shared secret |
| `API_KEY_SCOPES` | `read,write` | Allowed scopes |

#### OAuth2 mode variables

| Variable | Description |
|----------|-------------|
| `OAUTH2_ISSUER_URI` | Issuer URI (auto-discovers JWKS) |
| `OAUTH2_JWK_SET_URI` | Direct JWKS endpoint (alternative to issuer) |

### Complete environment variable reference

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `9090` | HTTP port for the MCP server |
| `SECURITY_MODE` | `oauth2` | `oauth2`, `apikey`, or `local` |
| `TEXTELLENT_API_BASE_URL` | `http://localhost:8080` | Textellent REST API base URL |
| `TEXTELLENT_API_TIMEOUT` | `30000` | Backend request timeout (ms) |
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile |
| `SPRING_AI_MCP_ENABLED` | `true` | Enable Spring AI MCP server |
| `SPRING_AI_MCP_PROTOCOL` | `STREAMABLE` | MCP transport protocol |
| `ALLOWED_ORIGINS` | (see `application.yml`) | CORS allowed origins |
| `RATELIMIT_READ_CAPACITY` | `100` | Read operations per minute (per tenant) |
| `RATELIMIT_WRITE_CAPACITY` | `20` | Write operations per minute (per tenant) |
| `LOG_LEVEL` | `INFO` | Root log level |
| `LOG_LEVEL_SECURITY` | `DEBUG` | Security log level |
| `API_KEY` | (empty) | API key when `SECURITY_MODE=apikey` |
| `API_KEY_SCOPES` | `read,write` | API key scopes |
| `OAUTH2_ISSUER_URI` | (empty) | OAuth2 issuer |
| `OAUTH2_JWK_SET_URI` | (empty) | OAuth2 JWKS URL |

### Textellent API credentials (passed per request)

These are **not** server environment variables. MCP clients must send them as HTTP headers on each request (the server forwards them to the Textellent backend):

| Header | Required | Description |
|--------|----------|-------------|
| `authCode` | Yes | Textellent authentication code |
| `partnerClientCode` | Optional | Partner client identifier |

---

## Compiling the Project

Compilation produces an executable JAR at:

```
target/textellent-mcp-server-1.0.0.jar
```

### Standard build (skip tests for faster compile)

From the project root:

```bash
mvn clean package -DskipTests
```

### Full build with tests

```bash
mvn clean package
```

### Build only (no tests, no clean)

```bash
mvn package -DskipTests
```

### Expected successful output

Look for:

```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

And the JAR file:

```bash
ls -la target/textellent-mcp-server-1.0.0.jar
```

**Windows:**

```powershell
dir target\textellent-mcp-server-1.0.0.jar
```

### Verify tool registration at startup

When you run the server, check logs for registered MCP tools (count may vary by version; schemas define **28** tools).

---

## Running Without Docker

You need the Textellent API backend reachable at `TEXTELLENT_API_BASE_URL` (default `http://localhost:8080`).

### Method 1: Maven Spring Boot plugin (development)

```bash
export SECURITY_MODE=local
export SPRING_PROFILES_ACTIVE=local
export TEXTELLENT_API_BASE_URL=http://localhost:8080

mvn spring-boot:run
```

**Windows PowerShell:**

```powershell
$env:SECURITY_MODE = "local"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:TEXTELLENT_API_BASE_URL = "http://localhost:8080"
mvn spring-boot:run
```

### Method 2: Run the compiled JAR (production-like)

```bash
export SECURITY_MODE=local
export SPRING_PROFILES_ACTIVE=local
export TEXTELLENT_API_BASE_URL=http://localhost:8080

java -jar target/textellent-mcp-server-1.0.0.jar
```

**Windows:**

```powershell
java -jar target\textellent-mcp-server-1.0.0.jar
```

(Set environment variables in the same shell session first.)

### Method 3: JAR with inline property overrides

```bash
java -jar target/textellent-mcp-server-1.0.0.jar \
  --server.port=9090 \
  --textellent.api.base-url=http://localhost:8080 \
  --security.mode=local
```

### Confirm the server is running

You should see console output similar to:

```
========================================
Textellent MCP Server Started!
MCP Endpoint: http://localhost:9090/mcp
Health Check: http://localhost:9090/health
========================================
```

**Health check:**

```bash
curl http://localhost:9090/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "textellent-mcp-server",
  "version": "1.0.0"
}
```

**Version:**

```bash
curl http://localhost:9090/version
```

### Stop the server

- **Foreground terminal:** press `Ctrl+C`
- **Background process:** find the PID and terminate it (`kill <pid>` on Linux/macOS, Task Manager on Windows)

---

## Docker for Beginners

If you have never used Docker, read this section first.

### What is Docker?

**Docker** packages an application and everything it needs to run (Java runtime, JAR file, default settings) into an **image**. You run that image as a **container** — an isolated process on your machine that behaves the same on Linux, macOS, and Windows.

Think of it this way:

| Concept | Analogy |
|---------|---------|
| **Dockerfile** | Recipe for building the image |
| **Image** | A snapshot / template (like a VM template) |
| **Container** | A running instance of the image |
| **Docker Engine** | The program that builds images and runs containers |

### Why use Docker for this project?

- You do not need to install Java 22 on the host (only Docker).
- The same image runs consistently in dev, CI, and production.
- Easy to pass configuration via environment variables at `docker run` time.

### Basic Docker commands you will use

| Command | What it does |
|---------|--------------|
| `docker --version` | Check Docker is installed |
| `docker build -t name:tag .` | Build an image from a Dockerfile |
| `docker images` | List local images |
| `docker run -p 9090:9090 name:tag` | Start a container, map port 9090 |
| `docker ps` | List running containers |
| `docker logs <container>` | View container logs |
| `docker stop <container>` | Stop a container |
| `docker rm <container>` | Remove a stopped container |

### Port mapping explained

`-p 9090:9090` means:

```
host port 9090  →  container port 9090
```

So `http://localhost:9090` on your machine reaches the server inside the container.

---

## Installing Docker — Linux, macOS, and Windows

### Linux (Ubuntu example)

```bash
# Official convenience script (review before running in production environments)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Allow your user to run docker without sudo (log out/in after)
sudo usermod -aG docker $USER

docker --version
docker run hello-world
```

### macOS

1. Download **Docker Desktop** from [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/).
2. Install and start Docker Desktop from Applications.
3. Wait until the whale icon shows "Docker Desktop is running".
4. Verify in Terminal:

```bash
docker --version
docker run hello-world
```

### Windows

1. Install **Docker Desktop for Windows** from [https://www.docker.com/products/docker-desktop/](https://www.docker.com/products/docker-desktop/).
2. Enable **WSL 2** when prompted (recommended).
3. Restart if required; start Docker Desktop.
4. Verify in PowerShell:

```powershell
docker --version
docker run hello-world
```

**Note:** Docker Desktop requires virtualization enabled in BIOS/UEFI. On Windows Home, use WSL 2 backend.

---

## Building and Running With Docker

This project's `Dockerfile` uses **Eclipse Temurin Java 22 JRE** and expects a **pre-built JAR**. Docker does not run Maven for you in the current Dockerfile — you build the JAR first, then build the image.

### Step 1: Build the JAR on the host

```bash
mvn clean package -DskipTests
```

### Step 2: Build the Docker image

From the project root, pass the JAR path as a build argument:

```bash
docker build \
  --build-arg JAR_FILE=target/textellent-mcp-server-1.0.0.jar \
  -t textellent-mcp-server:latest \
  .
```

**Windows (PowerShell, one line):**

```powershell
docker build --build-arg JAR_FILE=target/textellent-mcp-server-1.0.0.jar -t textellent-mcp-server:latest .
```

Verify the image:

```bash
docker images textellent-mcp-server
```

### Step 3: Run the container

#### Local development (no auth)

```bash
docker run -d \
  --name textellent-mcp \
  -p 9090:9090 \
  -e SECURITY_MODE=local \
  -e SPRING_PROFILES_ACTIVE=local \
  -e TEXTELLENT_API_BASE_URL=http://host.docker.internal:8080 \
  textellent-mcp-server:latest
```

**Why `host.docker.internal`?** Inside the container, `localhost` refers to the container itself, not your machine. `host.docker.internal` (Docker Desktop on Mac/Windows; add `--add-host=host.docker.internal:host-gateway` on Linux) points to the host where your Textellent API may be running.

**Linux** — if `host.docker.internal` is not available:

```bash
docker run -d \
  --name textellent-mcp \
  --add-host=host.docker.internal:host-gateway \
  -p 9090:9090 \
  -e SECURITY_MODE=local \
  -e SPRING_PROFILES_ACTIVE=local \
  -e TEXTELLENT_API_BASE_URL=http://host.docker.internal:8080 \
  textellent-mcp-server:latest
```

#### Production-style (API key)

```bash
docker run -d \
  --name textellent-mcp \
  -p 9090:9090 \
  -e SECURITY_MODE=apikey \
  -e API_KEY=your-secret-key \
  -e API_KEY_SCOPES=read,write \
  -e TEXTELLENT_API_BASE_URL=https://client.textellent.com \
  textellent-mcp-server:latest
```

#### View logs

```bash
docker logs -f textellent-mcp
```

#### Stop and remove

```bash
docker stop textellent-mcp
docker rm textellent-mcp
```

### Docker Compose (optional, not included in repo)

The README references `docker-compose up`, but this repository does not currently ship a `docker-compose.yml`. You can create one yourself:

```yaml
# docker-compose.yml (example — create this file in the project root if desired)
services:
  mcp-server:
    image: textellent-mcp-server:latest
    build:
      context: .
      args:
        JAR_FILE: target/textellent-mcp-server-1.0.0.jar
    ports:
      - "9090:9090"
    environment:
      SECURITY_MODE: local
      SPRING_PROFILES_ACTIVE: local
      TEXTELLENT_API_BASE_URL: http://host.docker.internal:8080
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

Then:

```bash
mvn clean package -DskipTests
docker compose up -d
docker compose logs -f
```

---

## Using the MCP Server

### HTTP endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/mcp` | Required (except `local` mode) | MCP JSON-RPC endpoint |
| `GET` | `/health` | Public | Basic health check |
| `GET` | `/version` | Public | Server name and version |
| `GET` | `/actuator/health` | Auth required | Detailed health (Spring Actuator) |
| `GET` | `/actuator/metrics` | Auth required | Application metrics |
| `GET` | `/actuator/prometheus` | Auth required | Prometheus scrape endpoint |

Base URL: `http://localhost:9090` (or your deployed host).

### MCP JSON-RPC methods

| Method | Description |
|--------|-------------|
| `initialize` | Protocol handshake |
| `tools/list` | List all available tools and schemas |
| `tools/call` | Execute a tool by name |

### Available tools (28)

| Category | Tool names |
|----------|------------|
| **Messages** | `messages_send` |
| **Contacts** | `contacts_add`, `contacts_update`, `contacts_get`, `contacts_get_all`, `contacts_get_summary`, `contacts_delete`, `contacts_find`, `contacts_find_multiple_phones` |
| **Tags** | `tags_create`, `tags_update`, `tags_get`, `tags_get_all`, `tags_get_summary`, `tags_assign_contacts`, `tags_remove_contacts`, `tags_delete` |
| **Events** | `events_incoming_message`, `events_outgoing_delivery_status`, `events_new_contact_details`, `events_associate_contact_tag`, `events_disassociate_contact_tag`, `events_phone_added_dnt`, `events_phone_removed_dnt`, `events_phone_added_wrong_number` |
| **Webhooks** | `webhook_subscribe`, `webhook_unsubscribe`, `webhook_list_subscriptions` |

Input schemas live in `src/main/resources/schemas/<tool_name>.json`.

### Example: List tools (local mode)

```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -H "partnerClientCode: YOUR_PARTNER_CLIENT_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }'
```

### Example: Call a tool

```bash
curl -X POST http://localhost:9090/mcp \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
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

### Example: API key mode

```bash
curl -X POST http://localhost:9090/mcp \
  -H "X-API-Key: your-secret-key" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {}
  }'
```

### Example: OAuth2 JWT mode

```bash
curl -X POST http://localhost:9090/mcp \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "authCode: YOUR_AUTH_CODE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "messages_send",
      "arguments": {
        "text": "Hello from MCP",
        "from": "+1234567890",
        "to": "+0987654321",
        "mediaFileIds": [],
        "mediaFileURLs": []
      }
    }
  }'
```

---

## Connecting AI Agents

### Claude Desktop (via `mcp-bridge.js`)

Claude Desktop expects MCP over **stdio**. This project serves MCP over **HTTP**, so the included `mcp-bridge.js` script translates between them.

**Prerequisites:** Node.js, MCP server running on port 9090.

**Config file locations:**

| OS | Path |
|----|------|
| **macOS** | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| **Windows** | `%APPDATA%\Claude\claude_desktop_config.json` |
| **Linux** | `~/.config/Claude/claude_desktop_config.json` |

**Example configuration:**

```json
{
  "mcpServers": {
    "textellent": {
      "command": "node",
      "args": ["/absolute/path/to/mcp-server/mcp-bridge.js"],
      "env": {
        "TEXTELLENT_AUTH_CODE": "your_auth_code_here",
        "TEXTELLENT_PARTNER_CLIENT_CODE": "your_client_code_here",
        "MCP_SERVER_HOST": "localhost",
        "MCP_SERVER_PORT": "9090"
      }
    }
  }
}
```

Use **absolute paths** in `args`. On Windows, use forward slashes or escaped backslashes.

Restart Claude Desktop after saving. The bridge logs to stderr; check Claude's MCP logs if connection fails.

### MCP Desktop extension (`.mcpb`)

The repo includes `manifest.json` for packaging as a Claude Desktop extension. See `TESTING_WITH_AI_AGENTS.md` for packaging with the `mcpb` CLI.

### Remote / HTTP-native clients

Clients that support MCP over HTTP can connect directly to:

```
https://your-host:9090/mcp
```

Configure OAuth2 or API key authentication as required by your `SECURITY_MODE`.

---

## Testing Your Setup

### 1. Automated test script

```bash
chmod +x test-mcp.sh
./test-mcp.sh YOUR_AUTH_CODE YOUR_PARTNER_CLIENT_CODE
```

**Windows (Git Bash):** same commands. **PowerShell:** run curl commands manually or use WSL.

The script checks health, lists tools, and attempts `contacts_get_all`.

### 2. Manual health and version checks

```bash
curl http://localhost:9090/health
curl http://localhost:9090/version
```

### 3. Run unit tests

```bash
mvn test
```

---

## Troubleshooting

### Build failures

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Could not find `maestro-starter` | Private dependency not configured | See [Maven Repository Access](#maven-repository-access-important) |
| `${jackson.version}` invalid | Missing property in `pom.xml` | Remove explicit Jackson deps or add `jackson.version` property |
| `java: invalid target release` | JDK too old | Install Java 17+ (22 recommended) |
| `mvn: command not found` | Maven not on PATH | Install Maven and restart terminal |

### Runtime failures

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Connection refused` to port 8080 | Textellent API not running | Start backend or set `TEXTELLENT_API_BASE_URL` |
| Port 9090 already in use | Another process on 9090 | Change `PORT` or stop the other process |
| `401 Unauthorized` on `/mcp` | Wrong security mode or missing auth | Set `SECURITY_MODE=local` for dev, or send JWT/API key |
| `403 Forbidden` | Missing OAuth scope | JWT needs `read` or `write` scope as required by the tool |
| Docker container cannot reach API on host | Wrong URL inside container | Use `host.docker.internal:8080` (see Docker section) |

### MCP / client failures

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Claude Desktop shows connection error | MCP server not running or bridge path wrong | Start server; verify absolute path to `mcp-bridge.js` |
| Tool call returns backend error | Invalid `authCode` | Verify credentials with Textellent |
| Empty tool list | Auth failure or wrong endpoint | Check headers and `POST /mcp` URL |

### Useful diagnostic commands

```bash
# Is anything listening on 9090?
# Linux/macOS:
lsof -i :9090

# Windows PowerShell:
netstat -ano | findstr :9090

# Docker container health
docker logs textellent-mcp --tail 100

# Maven dependency tree
mvn dependency:tree
```

---

## Quick Reference

### One-time setup

```bash
git clone <repo-url> && cd mcp-server
# Configure Maven for maestro-starter (see Maven Repository Access)
export SECURITY_MODE=local SPRING_PROFILES_ACTIVE=local
mvn clean package -DskipTests
```

### Run locally

```bash
java -jar target/textellent-mcp-server-1.0.0.jar
# MCP: http://localhost:9090/mcp
# Health: http://localhost:9090/health
```

### Docker

```bash
mvn clean package -DskipTests
docker build --build-arg JAR_FILE=target/textellent-mcp-server-1.0.0.jar -t textellent-mcp-server:latest .
docker run -d --name textellent-mcp -p 9090:9090 \
  -e SECURITY_MODE=local -e TEXTELLENT_API_BASE_URL=http://host.docker.internal:8080 \
  --add-host=host.docker.internal:host-gateway \
  textellent-mcp-server:latest
```

### Test

```bash
curl http://localhost:9090/health
./test-mcp.sh YOUR_AUTH_CODE YOUR_CLIENT_CODE
```

---

## Related documentation

| Document | Contents |
|----------|----------|
| [README.md](README.md) | Feature overview, security modes, monitoring |
| [CONFIGURATION.md](CONFIGURATION.md) | Port layout, API mapping, auth header flow |
| [TESTING_WITH_AI_AGENTS.md](TESTING_WITH_AI_AGENTS.md) | MCP Inspector, Claude Desktop, Postman examples |

---

**Document version:** 1.0  
**Project version:** 1.0.0  
**Last updated:** July 2026
