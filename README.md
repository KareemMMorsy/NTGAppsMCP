# NTG Apps Broker MCP - Java Spring Boot Implementation

This is a **Java Spring Boot** implementation of the NTG Apps Broker MCP server, providing the same functionality as the Python version but built with Spring Boot and following Java best practices.

## Architecture

This project follows **Clean Architecture** principles:

- **Domain Layer**: Core business entities and value objects (`domain/`)
- **Ports**: Interfaces defining contracts (`ports/`)
- **Use Cases**: Business logic orchestration (`usecases/`)
- **Adapters**: Cross-cutting concerns (`adapters/`)
- **Infrastructure**: External integrations (HTTP, MCP stdio, etc.) (`infrastructure/`)

## Features

- ✅ **MCP Protocol Support**: JSON-RPC 2.0 over stdio for Cursor integration
- ✅ **Session Management**: Server-side session token storage
- ✅ **External API Integration**: HTTP clients for Auth and Apps services
- ✅ **Clean Architecture**: Separation of concerns with dependency inversion
- ✅ **Structured Logging**: JSON logging with Logstash encoder
- ✅ **Configuration**: Environment-based configuration via `application.yml`

## Prerequisites

- **Java 17+** (required)
- **Maven 3.6+** (for building)
- **Backend API** running at `http://localhost:7070/Smart2Go` (or configured URL)

## Building

```bash
# Build the project
mvn clean package

# This creates: target/apps-broker-mcp-1.0.0.jar
```

## Running

### As MCP Stdio Server (for Cursor)

The application runs as a stdio server when `MCP_STDIO_MODE=true`:

```bash
java -jar target/apps-broker-mcp-1.0.0.jar
```

Or with environment variables:

```bash
export MCP_STDIO_MODE=true
export MCP_AUTH_INTEGRATION_ENABLED=true
export MCP_AUTH_BASE_URL=http://localhost:7070/Smart2Go
export MCP_APPS_INTEGRATION_ENABLED=true
export MCP_APPS_BASE_URL=http://localhost:7070/Smart2Go

java -jar target/apps-broker-mcp-1.0.0.jar
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_STDIO_MODE` | Enable stdio mode | `false` |
| `MCP_AUTH_INTEGRATION_ENABLED` | Enable auth API integration | `false` |
| `MCP_AUTH_BASE_URL` | Auth API base URL | `http://localhost:7070/Smart2Go` |
| `MCP_APPS_INTEGRATION_ENABLED` | Enable apps API integration | `false` |
| `MCP_APPS_BASE_URL` | Apps API base URL | `http://localhost:7070/Smart2Go` |
| `MCP_LOG_TO_FILE` | Enable file logging | `true` |
| `MCP_LOG_FILE_PATH` | Log file path | `logs/mcp.jsonl` |

### application.yml

Configuration can also be set in `src/main/resources/application.yml`:

```yaml
mcp:
  stdio:
    enabled: ${MCP_STDIO_MODE:false}
  auth:
    base-url: ${MCP_AUTH_BASE_URL:http://localhost:7070/Smart2Go}
    integration-enabled: ${MCP_AUTH_INTEGRATION_ENABLED:false}
  apps:
    base-url: ${MCP_APPS_BASE_URL:http://localhost:7070/Smart2Go}
    integration-enabled: ${MCP_APPS_INTEGRATION_ENABLED:false}
```

## Cursor Integration

### 1. Build the JAR

```bash
mvn clean package
```

### 2. Configure Cursor

Add to `~/.cursor/mcp.json` (or `C:\Users\YourUsername\.cursor\mcp.json` on Windows):

```json
{
  "mcpServers": {
    "ntg-apps-broker": {
      "command": "java",
      "args": [
        "-jar",
        "F:/NTG/NTGApps Broker Java/target/apps-broker-mcp-1.0.0.jar"
      ],
      "env": {
        "MCP_STDIO_MODE": "true",
        "MCP_AUTH_INTEGRATION_ENABLED": "true",
        "MCP_AUTH_BASE_URL": "http://localhost:7070/Smart2Go",
        "MCP_APPS_INTEGRATION_ENABLED": "true",
        "MCP_APPS_BASE_URL": "http://localhost:7070/Smart2Go",
        "MCP_LOG_TO_FILE": "true",
        "MCP_LOG_FILE_PATH": "logs/mcp.jsonl"
      }
    }
  }
}
```

**Note**: Update the `-jar` path to your actual JAR location.

### 3. Restart Cursor

After configuring, restart Cursor completely. The MCP server will be automatically launched.

## Available Tools

The MCP server exposes these tools:

### `ping`

Health check: returns `{"message": "pong"}`.

### `login`

Login and store session token server-side keyed by `clientId`.

**Parameters:**
- `username` (string, required)
- `password` (string, required)
- `companyname` (string, required)
- `clientId` (string, required)

**Returns:**
```json
{
  "sessionToken": "...",
  "clientId": "..."
}
```

### `create_app`

Create app via `saveApp` API using session token.

**Parameters:**
- `clientId` (string, required) - Must match the `clientId` used in `login`
- `AppearOnMobile` (boolean, required)
- `appName` (string, required)
- `appIdentifier` (string, required)
- `shortNotes` (string, required)
- `icon` (string, required)

**Returns:**
```json
{
  "app": { ... },
  "appsService": {
    "status_code": 200,
    "body": [ ... ]
  }
}
```

## Usage Examples

### In Cursor Chat

**Login:**
```
Login to NTG Apps Broker with username "admin", password "ntg", company "NTG", and clientId "my-client-1"
```

**Create App:**
```
Create an app with:
- clientId: "my-client-1"
- AppearOnMobile: true
- appName: "My Test App"
- appIdentifier: "com.example.testapp"
- shortNotes: "A test app"
- icon: "fa fa-martini-glass"
```

## Project Structure

```
src/main/java/com/ntg/appsbroker/
├── AppsBrokerApplication.java      # Main Spring Boot application
├── domain/                          # Domain entities
│   ├── McpRequestData.java
│   ├── McpOutcome.java
│   ├── McpSuccess.java
│   ├── McpFailure.java
│   ├── AppError.java
│   └── mcp/
│       └── McpVersion.java
├── ports/                           # Interfaces (ports)
│   ├── AuthService.java
│   ├── AppsService.java
│   ├── SessionStore.java
│   ├── ContextProvider.java
│   └── AIGateway.java
├── usecases/                        # Business logic
│   └── HandleMcpRequestUseCase.java
├── adapters/                        # Adapters
│   ├── PromptBuilder.java
│   └── AIOutputParser.java
├── infrastructure/                  # Infrastructure implementations
│   ├── auth/
│   │   ├── HttpAuthService.java
│   │   └── LoginResult.java
│   ├── apps/
│   │   └── HttpAppsService.java
│   ├── sessions/
│   │   └── InMemorySessionStore.java
│   ├── context/
│   │   ├── UserContextProvider.java
│   │   ├── SystemContextProvider.java
│   │   └── FileContextProvider.java
│   ├── mcp/
│   │   └── McpStdioServer.java
│   └── config/
│       ├── AppConfig.java
│       └── DependencyConfig.java
└── schemas/                         # DTOs
    ├── McpRequest.java
    ├── McpResponse.java
    └── McpError.java
```

## Logging

Logs are written to:
- **Console**: Human-readable format
- **File**: JSON format (Logstash encoder) at `logs/mcp.jsonl`

Log rotation:
- Max file size: 10MB
- Max history: 10 files
- Total size cap: 100MB

## Development

### Running Tests

```bash
mvn test
```

### IDE Setup

1. Import as Maven project
2. Ensure Java 17+ is configured
3. Run `AppsBrokerApplication` main class

## Differences from Python Version

1. **Language**: Java 17+ instead of Python 3.11+
2. **Framework**: Spring Boot instead of FastAPI
3. **HTTP Client**: WebClient (reactive) instead of httpx
4. **JSON**: Jackson instead of Pydantic
5. **Logging**: SLF4J/Logback instead of structlog
6. **Configuration**: Spring `@Value` and `application.yml` instead of pydantic-settings

## Troubleshooting

### MCP server not appearing in Cursor

1. Check `mcp.json` syntax is valid JSON
2. Verify JAR path is correct and absolute
3. Ensure `MCP_STDIO_MODE=true` is set
4. Restart Cursor completely
5. Check logs in `logs/mcp.jsonl`

### "you must log in first" error

- Ensure you called `login` before `create_app`
- Use the **exact same `clientId`** in both calls
- Check that session store is working (in-memory, so restart clears sessions)

### API calls failing

- Verify `MCP_AUTH_INTEGRATION_ENABLED=true` and `MCP_APPS_INTEGRATION_ENABLED=true`
- Check backend API is running at configured URL
- Review logs for HTTP errors

## License

MIT

## Author

NTG Apps Broker Team

