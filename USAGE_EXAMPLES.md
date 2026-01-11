# Usage Examples - Java Spring Boot MCP Server

This guide shows how to use the NTG Apps Broker MCP server (Java version) from Cursor chat.

## Prerequisites

1. **Build the JAR**: `mvn clean package`
2. **Configure Cursor**: Add entry to `~/.cursor/mcp.json` (see README)
3. **Restart Cursor**: After configuration
4. **Backend Running**: Ensure `http://localhost:7070/Smart2Go` is accessible

## Example 1: Health Check (ping)

**In Cursor chat:**

```
Use the ping tool to check if the MCP server is working.
```

**Expected result:**
- Tool returns: `{"message": "pong"}`

---

## Example 2: Login Flow

**Step 1: Login**

In Cursor chat:

```
Login to NTG Apps Broker with username "admin", password "ntg", company "NTG", and clientId "my-client-1"
```

**What happens:**
- MCP calls `login` tool
- Authenticates against `http://localhost:7070/Smart2Go/rest/MainFunciton/login`
- Extracts `UserSessionToken` from response
- Stores token server-side keyed by `clientId: "my-client-1"`

**Expected result:**
- Returns `sessionToken` and `clientId` in the response

---

## Example 3: Create App (after login)

**Step 2: Create App**

In Cursor chat (use the **same clientId** from login):

```
Create an app with:
- clientId: "my-client-1"
- AppearOnMobile: true
- appName: "My Test App"
- appIdentifier: "com.example.testapp"
- shortNotes: "A test app created via MCP"
- icon: "fa fa-martini-glass"
```

**What happens:**
- MCP looks up session token by `clientId: "my-client-1"`
- Validates app parameters
- Calls `http://localhost:7070/Smart2Go/rest/Apps/saveApp` with:
  - Body: app spec
  - Headers: `SessionToken: <extracted-token>`

**Expected result:**
- Returns app spec and upstream API response

---

## Example 4: Complete Workflow (Natural Language)

**In Cursor chat, you can say:**

```
I need to create a new app in NTG Apps Broker. First, log me in with username "admin", password "ntg", company "NTG", and use clientId "cursor-session-1". Then create an app with:
- AppearOnMobile: true
- appName: "Cursor Generated App"
- appIdentifier: "com.ntg.cursorapp"
- shortNotes: "Created via Cursor MCP integration"
- icon: "fa fa-code"
```

**Cursor will:**
1. Call `login` tool first
2. Then call `create_app` tool with the same `clientId`

---

## Important Notes

### Session Management
- **Always use the same `clientId`** for `login` and subsequent `create_app` calls.
- Sessions are stored **server-side in memory** (restarting the MCP server clears them).
- If you get **"you must log in first"**, call `login` again with the same `clientId`.

### Error Handling
- If login fails, check that `MCP_AUTH_INTEGRATION_ENABLED=true` and `MCP_AUTH_BASE_URL` is set.
- If `create_app` fails, ensure `MCP_APPS_INTEGRATION_ENABLED=true` and `MCP_APPS_BASE_URL` is set.
- Check `logs/mcp.jsonl` for detailed request/response logs.

### Logs
All API calls are logged to:
- **`logs/mcp.jsonl`** (JSON lines format)
- Passwords and tokens are automatically redacted in logs

---

## Troubleshooting

### MCP server not appearing in Cursor
1. Check `~/.cursor/mcp.json` exists and is valid JSON.
2. Verify JAR path is **absolute** and correct.
3. Ensure `MCP_STDIO_MODE=true` in environment variables.
4. Restart Cursor completely.
5. Check Java version: `java -version` (must be 17+)

### "you must log in first" error
- Ensure you called `login` **before** `create_app`.
- Use the **exact same `clientId`** in both calls.
- Sessions are in-memory, so restarting the server clears them.

### No logs appearing
- Check `logs/mcp.jsonl` exists (auto-created on first log).
- Verify `MCP_LOG_TO_FILE=true` in your environment.

### JAR not found
- Ensure you've run `mvn clean package` to build the JAR.
- Verify the path in `mcp.json` points to `target/apps-broker-mcp-1.0.0.jar`.

