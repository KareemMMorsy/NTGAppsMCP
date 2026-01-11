$ErrorActionPreference = 'Stop'

param(
  [string]$McpJsonPath = "$env:USERPROFILE\.cursor\mcp.json",
  [string]$ServerName = "NTGApps"
)

function Read-McpEnv {
  param([string]$Path, [string]$Name)

  if (-not (Test-Path $Path)) {
    throw "mcp.json not found: $Path"
  }

  $mcp = Get-Content -Raw $Path | ConvertFrom-Json
  if (-not $mcp.mcpServers -or -not $mcp.mcpServers.$Name) {
    throw "Server '$Name' not found under mcpServers in $Path"
  }

  $envObj = $mcp.mcpServers.$Name.env
  if (-not $envObj) { return @{} }

  $out = @{}
  foreach ($p in $envObj.PSObject.Properties) {
    $out[$p.Name] = [string]$p.Value
  }
  return $out
}

function Is-Blank([string]$s) { return (-not $s) -or ($s.Trim().Length -eq 0) }

# Only sync vars that the DEPLOYED SERVER actually uses.
# (Client-only vars like MCP_REMOTE_MCP_URL should NOT be pushed to the server.)
$allowedKeys = @(
  'PORT',
  'MCP_HTTP_SSE_MODE',
  'MCP_STDIO_MODE',
  'MCP_HTTP_AUTH_TOKEN',
  'MCP_DEFAULT_CLIENT_ID',
  'MCP_DEFAULT_SESSION_TOKEN',
  'MCP_AUTH_INTEGRATION_ENABLED',
  'MCP_AUTH_BASE_URL',
  'MCP_APPS_INTEGRATION_ENABLED',
  'MCP_APPS_BASE_URL',
  'MCP_IMPORT_APPS_DIR',
  'MCP_FILE_ALLOWED_ROOTS',
  'MCP_LOG_TO_FILE',
  'MCP_LOG_FILE_PATH'
)

$envs = Read-McpEnv -Path $McpJsonPath -Name $ServerName

$pairs = @()
foreach ($k in $allowedKeys) {
  if ($envs.ContainsKey($k) -and -not (Is-Blank $envs[$k])) {
    # Railway CLI expects: railway variables set KEY=VALUE KEY2=VALUE2 ...
    $pairs += ("{0}={1}" -f $k, $envs[$k])
  }
}

if ($pairs.Count -eq 0) {
  throw "No eligible env vars found to sync from $McpJsonPath ($ServerName)."
}

Write-Host "Syncing $($pairs.Count) vars to Railway for server '$ServerName' from: $McpJsonPath"
Write-Host "Note: this requires the Railway CLI and a linked project (run: railway login; railway link)."
Write-Host ""

$cmd = "railway variables set " + ($pairs -join ' ')
Write-Host $cmd
Write-Host ""

# Execute if railway exists, otherwise just print the command.
$railway = Get-Command railway -ErrorAction SilentlyContinue
if (-not $railway) {
  Write-Warning "Railway CLI not found in PATH. Install it, then run the command printed above."
  exit 0
}

& railway variables set @pairs
Write-Host "Done."


