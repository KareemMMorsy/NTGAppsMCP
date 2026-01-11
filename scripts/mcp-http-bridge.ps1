$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Remote MCP endpoint (HTTP JSON-RPC)
$url = $env:MCP_REMOTE_MCP_URL
if (-not $url -or $url.Trim().Length -eq 0) {
  throw "Missing MCP_REMOTE_MCP_URL. Set it in your Cursor mcp.json under the server's env."
}

# Normalize: allow users to provide just host, or host + path, etc.
$url = $url.Trim()
if (-not ($url -match '^https?://')) { $url = "https://$url" }
if (-not ($url -match '/mcp$')) {
  if ($url.EndsWith('/')) { $url = "$url" + "mcp" } else { $url = "$url/mcp" }
}

# Bearer token used ONLY to authenticate to the MCP HTTP endpoint
$authToken = $env:MCP_HTTP_AUTH_TOKEN

# Upstream Smart2Go SessionToken (UserSessionToken). This is what saveApp requires.
$defaultSessionToken = $env:MCP_DEFAULT_SESSION_TOKEN

while (($line = [Console]::In.ReadLine()) -ne $null) {
  if ([string]::IsNullOrWhiteSpace($line)) { continue }

  $obj = $null
  try { $obj = $line | ConvertFrom-Json } catch { $obj = $null }

  # Inject sessionToken into create_app calls if it's not already provided
  try {
    if ($obj -and $obj.method -eq 'tools/call' -and $obj.params -and $obj.params.name -eq 'create_app') {
      if ($defaultSessionToken -and $defaultSessionToken.Trim().Length -gt 0) {
        if (-not $obj.params.arguments) { $obj.params | Add-Member -NotePropertyName 'arguments' -NotePropertyValue @{} -Force }
        if (-not $obj.params.arguments.sessionToken -or ($obj.params.arguments.sessionToken.ToString().Trim().Length -eq 0)) {
          $obj.params.arguments.sessionToken = $defaultSessionToken
        }
      }
    }
  } catch {
    # Non-fatal; proceed without injection
  }

  $headers = @{}
  if ($authToken -and $authToken.Trim().Length -gt 0) {
    $headers['Authorization'] = "Bearer $authToken"
  }

  try {
    $payload = if ($obj) { $obj | ConvertTo-Json -Depth 30 -Compress } else { $line }
    $resp = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body $payload -TimeoutSec 60
    $out = $resp | ConvertTo-Json -Depth 30 -Compress
    [Console]::Out.WriteLine($out)
    [Console]::Out.Flush()
  } catch {
    $id = $null
    try { if ($obj) { $id = $obj.id } } catch {}
    $msg = $_.Exception.Message
    $errObj = @{
      jsonrpc = '2.0'
      id     = $id
      error  = @{
        code    = -32000
        message = 'Upstream MCP HTTP error'
        data    = @{ message = $msg }
      }
    }
    $out = $errObj | ConvertTo-Json -Depth 10 -Compress
    [Console]::Out.WriteLine($out)
    [Console]::Out.Flush()
  }
}





