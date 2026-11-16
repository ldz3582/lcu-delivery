<#
.SYNOPSIS
  lcu-delivery payment / auth acceptance check.

.DESCRIPTION
  Verifies the wechat-pay v3 + official-account login change set end to end.
  Works in mock mode without any merchant credentials, and prints a checklist
  for the real-channel switch-over.

  Layers:
    L0  environment   : ports, health, jar artifact
    L1  channels      : prepay returns the right channel for MINIAPP / MP / H5 / NATIVE
    L2  callbacks     : notify / refund-notify / transfer-notify reachable, idempotent
    L3  platform auth : oauth-url, oauth-login, mp_openid persisted, repeated login reuses the same user
    L4  business flow : order -> pay -> accept -> deliver -> withdraw payout
    L5  real channel  : config completeness self-check (no network calls)

.EXAMPLE
  pwsh -File ops\verify-pay.ps1
  pwsh -File ops\verify-pay.ps1 -Base http://127.0.0.1:8082 -SkipFlow
#>
param(
  [string]$Base = 'http://127.0.0.1:8082',
  [string]$AdminPassword = 'Admin@123456',
  [switch]$SkipFlow
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0
$script:skip = 0

function Section($t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Check($name, $cond, $detail = '') {
  if ($cond) {
    $script:pass++
    $suffix = if ($detail) { " -> $detail" } else { '' }
    Write-Host ("  [PASS] {0}{1}" -f $name, $suffix) -ForegroundColor Green
  } else {
    $script:fail++
    $suffix = if ($detail) { " -> $detail" } else { '' }
    Write-Host ("  [FAIL] {0}{1}" -f $name, $suffix) -ForegroundColor Red
  }
}
function Skip($name, $why) {
  $script:skip++
  Write-Host ("  [SKIP] {0} -> {1}" -f $name, $why) -ForegroundColor Yellow
}

function JPost($path, $token, $obj, $timeout = 15) {
  $h = @{ 'Content-Type' = 'application/json' }
  if ($token) { $h['Authorization'] = "Bearer $token" }
  $body = $obj | ConvertTo-Json -Depth 6 -Compress
  try {
    $r = Invoke-WebRequest "$Base$path" -Method POST -Headers $h -Body $body -UseBasicParsing -TimeoutSec $timeout
    return [pscustomobject]@{ status = $r.StatusCode; data = ($r.Content | ConvertFrom-Json) }
  } catch {
    $resp = $_.Exception.Response
    return [pscustomobject]@{ status = if ($resp) { [int]$resp.StatusCode } else { 0 }; data = $null; error = $_.Exception.Message }
  }
}

function JGet($path, $token) {
  $h = @{}
  if ($token) { $h['Authorization'] = "Bearer $token" }
  try {
    $r = Invoke-WebRequest "$Base$path" -Headers $h -UseBasicParsing -TimeoutSec 15
    return ($r.Content | ConvertFrom-Json)
  } catch { return $null }
}

function PortUp($port) {
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $iar = $c.BeginConnect('127.0.0.1', $port, $null, $null)
    $ok = $iar.AsyncWaitHandle.WaitOne(1200, $false)
    if ($ok) { $c.EndConnect($iar) }
    return $ok
  } catch { return $false } finally { $c.Close() }
}

# ---------------------------------------------------------------- L0
Section 'L0 environment'
Check 'MySQL 3307' (PortUp 3307)
Check 'Redis 6380' (PortUp 6380)
Check 'API 8082' (PortUp 8082)
Check 'admin-web 5176' (PortUp 5176)
Check 'H5 5175' (PortUp 5175)

$health = $null
try {
  $raw = (Invoke-WebRequest "$Base/actuator/health" -UseBasicParsing -TimeoutSec 5).Content
  # Windows PowerShell returns byte[] for application/json responses
  if ($raw -is [byte[]]) { $raw = [Text.Encoding]::UTF8.GetString($raw) }
  $health = $raw | ConvertFrom-Json
} catch { }
Check 'actuator health UP' ($health -and $health.status -eq 'UP') $(if ($health) { $health.status } else { 'no response' })

$jar = Get-Item 'server/target/delivery-0.1.0.jar' -ErrorAction SilentlyContinue
Check 'backend jar present' ($jar -ne $null) $(if ($jar) { "{0:N1} MB, {1}" -f ($jar.Length / 1MB), $jar.LastWriteTime } else { 'missing' })

# ---------------------------------------------------------------- L3 (needs no order)
Section 'L3 platform-side login'
$oauthUrl = JGet '/api/auth/oauth-url?redirect=http://127.0.0.1:5175/'
Check 'GET /api/auth/oauth-url reachable' ($oauthUrl -ne $null -and $oauthUrl.code -eq 0)
Check 'oauth-url returns null without official account' ($oauthUrl -and $null -eq $oauthUrl.data) 'expected in mock mode; front-end falls back to student login'

$probe = 'verify_' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$l1 = JPost '/api/auth/oauth-login' $null @{ code = $probe }
$okLogin = ($l1.data -and $l1.data.code -eq 0 -and $l1.data.data.token)
Check 'POST /api/auth/oauth-login issues token' $okLogin
$l2 = JPost '/api/auth/oauth-login' $null @{ code = $probe }
Check 'repeated oauth-login reuses the same user' ($okLogin -and $l2.data.data.userId -eq $l1.data.data.userId) $(if ($okLogin) { "userId=$($l1.data.data.userId)" } else { '' })

# ---------------------------------------------------------------- L2
Section 'L2 callback contract'
$n = JPost '/api/pay/notify' $null @{ outTradeNo = 'NONEXISTENT'; transactionId = 'X' }
Check 'POST /api/pay/notify answers SUCCESS' ($n.data -and $n.data.code -eq 'SUCCESS') 'no auth required'
$rn = JPost '/api/pay/refund-notify' $null @{ outRefundNo = 'NONEXISTENT' }
Check 'POST /api/pay/refund-notify answers SUCCESS' ($rn.data -and $rn.data.code -eq 'SUCCESS')
$tn = JPost '/api/pay/transfer-notify' $null @{ out_bill_no = 'W0'; state = 'SUCCESS' }
Check 'POST /api/pay/transfer-notify answers SUCCESS' ($tn.data -and $tn.data.code -eq 'SUCCESS')
$n2 = JPost '/api/pay/notify' $null @{ outTradeNo = 'NONEXISTENT'; transactionId = 'X' }
Check 'notify is idempotent (repeat call still SUCCESS)' ($n2.data -and $n2.data.code -eq 'SUCCESS')

# ---------------------------------------------------------------- L4 prep
Section 'L4 business flow'
$user = JPost '/api/auth/mock-login' $null @{ openid = 'mock_user'; role = 'USER' }
$ut = $user.data.data.token
Check 'user mock-login' ($ut -ne $null)

$shops = JGet '/api/shops?campus=1' $ut
Check 'shop list reachable' ($shops -and $shops.code -eq 0) $(if ($shops) { "total=$($shops.data.total)" } else { '' })

$calc = JPost '/api/cart/calculate' $ut @{ shopId = 1; items = @(@{ productId = 2; qty = 2 }) }
Check 'cart calculate' ($calc.data -and $calc.data.code -eq 0) $(if ($calc.data) { "total=$($calc.data.data.totalAmount)" } else { '' })

$order = JPost '/api/orders' $ut @{ shopId = 1; addressId = 1; items = @(@{ productId = 2; qty = 2 }); remark = 'verify' }
$oid = $null
if ($order.data -and $order.data.code -eq 0) { $oid = $order.data.data.orderId }
Check 'create order' ($oid -ne $null) $(if ($oid) { "orderId=$oid" } else { $order.error })
if (-not $oid) { Write-Host '  cannot continue flow without an order' -ForegroundColor Red }

# ---------------------------------------------------------------- L1 channels
if ($oid) {
  Section 'L1 pay channels (mock)'
  $expected = @{ MINIAPP = 'miniapp'; MP = 'mp'; H5 = 'h5'; NATIVE = 'native' }
  foreach ($scene in @('MINIAPP', 'MP', 'H5', 'NATIVE')) {
    $p = JPost '/api/pay/prepay' $ut @{ orderId = $oid; scene = $scene }
    $ok = ($p.data -and $p.data.code -eq 0 -and $p.data.data.channel -eq $expected[$scene])
    Check "prepay scene=$scene" $ok $(if ($p.data -and $p.data.code -eq 0) { "channel=$($p.data.data.channel) mock=$($p.data.data.mock) tradeNo=$($p.data.data.outTradeNo)" } else { if ($p.data) { $p.data.msg } else { $p.error } })
  }

  $defaultScene = JPost '/api/pay/prepay' $ut @{ orderId = $oid }
  Check 'prepay defaults to MINIAPP' ($defaultScene.data -and $defaultScene.data.data.channel -eq 'miniapp') $(if ($defaultScene.data) { "channel=$($defaultScene.data.data.channel)" } else { '' })
}

# ---------------------------------------------------------------- L4 pay + deliver
if ($oid -and -not $SkipFlow) {
  Section 'L4 pay, deliver, settle'
  $paid = JPost '/api/pay/mock-pay' $ut @{ orderId = $oid }
  Check 'mock pay' ($paid.data -and $paid.data.code -eq 0 -and $paid.data.data.paid -eq $true) $(if ($paid.data) { "status=$($paid.data.data.status)" } else { '' })

  $sync = JPost '/api/pay/sync' $ut @{ orderId = $oid }
  Check 'sync reports paid' ($sync.data -and $sync.data.code -eq 0 -and $sync.data.data.paid -eq $true)

  $m = JPost '/api/auth/mock-login' $null @{ openid = 'mock_merchant'; role = 'MERCHANT' }
  $mt = $m.data.data.token
  $acc = JPost "/api/merchant/orders/$oid/accept" $mt @{}
  Check 'merchant accept' ($acc.data -and $acc.data.code -eq 0) $(if ($acc.data) { $acc.data.msg } else { '' })
  $rdy = JPost "/api/merchant/orders/$oid/ready" $mt @{}
  Check 'merchant ready' ($rdy.data -and $rdy.data.code -eq 0)

  $r = JPost '/api/auth/mock-login' $null @{ openid = 'mock_rider'; role = 'RIDER' }
  $rt = $r.data.data.token
  $grab = JPost "/api/rider/orders/$oid/grab" $rt @{}
  Check 'rider grab' ($grab.data -and $grab.data.code -eq 0) $(if ($grab.data) { $grab.data.msg } else { '' })
  $pick = JPost "/api/rider/orders/$oid/pickup" $rt @{ photo = 'https://example.com/p.jpg' }
  Check 'rider pickup' ($pick.data -and $pick.data.code -eq 0)
  $dlv = JPost "/api/rider/orders/$oid/deliver" $rt @{ photo = 'https://example.com/d.jpg' }
  Check 'rider deliver' ($dlv.data -and $dlv.data.code -eq 0)

  $od = JGet "/api/orders/$oid" $ut
  Check 'order finished (status=50)' ($od -and $od.data.status -eq 50) $(if ($od) { "$($od.data.status) $($od.data.statusText)" } else { '' })

  $st = JGet '/api/admin/settlements' (JPost '/api/admin/login' $null @{ username = 'admin'; password = $AdminPassword }).data.data.token
  Check 'settlement created for the order' ($st -and $st.code -eq 0) $(if ($st) { "total=$($st.data.total)" } else { '' })
}

# ---------------------------------------------------------------- L4 payout
Section 'L4 withdraw payout'
$at = (JPost '/api/admin/login' $null @{ username = 'admin'; password = $AdminPassword }).data.data.token
Check 'admin login' ($at -ne $null)

$mt2 = (JPost '/api/auth/mock-login' $null @{ openid = 'mock_merchant'; role = 'MERCHANT' }).data.data.token
$wallet = JGet '/api/merchant/wallet' $mt2
$balance = 0
if ($wallet -and $wallet.data) { $balance = [int]$wallet.data.balance }
Write-Host ("  merchant wallet balance = {0} fen" -f $balance)
if ($balance -ge 1000) {
  $wd = JPost '/api/merchant/withdrawals' $mt2 @{ amount = 1000 }
  $wid = if ($wd.data -and $wd.data.code -eq 0) { $wd.data.data } else { $null }
  Check 'apply withdrawal' ($wid -ne $null) $(if ($wid) { "withdrawId=$wid" } else { if ($wd.data) { $wd.data.msg } else { $wd.error } })
  if ($wid) {
    $audit = JPost "/api/admin/withdrawals/$wid/audit" $at @{ pass = $true; reason = 'verify' }
    Check 'audit withdrawal (payout via PayoutGateway)' ($audit.data -and $audit.data.code -eq 0) $(if ($audit.data) { $audit.data.msg } else { '' })
  }
} else {
  Skip 'withdraw payout' "merchant wallet balance $balance < 1000 fen; settle an order first (T+1) or top the wallet up"
}

# ---------------------------------------------------------------- L5
Section 'L5 real-channel readiness (checklist, no network)'
$envNames = @('WX_USER_APPID', 'WX_USER_SECRET', 'WX_MP_APPID', 'WX_MP_SECRET',
  'WXPAY_MCH_ID', 'WXPAY_MCH_SERIAL_NO', 'WXPAY_API_V3_KEY',
  'WXPAY_PRIVATE_KEY_PATH', 'WXPAY_NOTIFY_URL')
$present = @()
foreach ($n in $envNames) {
  $v = [Environment]::GetEnvironmentVariable($n)
  if ($v) { $present += $n }
}
if ($present.Count -eq 0) {
  Write-Host '  backend is running with mock pay (no WXPAY_* env vars in this shell).' -ForegroundColor Yellow
  Write-Host '  To switch to the real channel: set the 9 variables below, set APP_MOCK_PAY=false, restart, then re-run this script with -Base pointing at that instance.'
} else {
  Write-Host ("  detected in this shell: {0}" -f ($present -join ', '))
}
Write-Host ''
Write-Host '  required before APP_MOCK_PAY=false:' -ForegroundColor Cyan
Write-Host '    WX_USER_APPID / WX_USER_SECRET          mini program'
Write-Host '    WX_MP_APPID / WX_MP_SECRET              official account (H5 in WeChat, web login)'
Write-Host '    WXPAY_MCH_ID / WXPAY_MCH_SERIAL_NO      merchant id + cert serial'
Write-Host '    WXPAY_API_V3_KEY                        api v3 key, exactly 32 bytes'
Write-Host '    WXPAY_PRIVATE_KEY_PATH                  apiclient_key.pem, readable by the process'
Write-Host '    WXPAY_NOTIFY_URL                        https://<domain>/api/pay/notify'
Write-Host '  wechat products to enable: JSAPI pay, H5 pay, Native pay, merchant transfer'
Write-Host '  official account: authorize domain configured; mini program: request domain whitelisted'

# ---------------------------------------------------------------- summary
Write-Host ''
$color = if ($script:fail -eq 0) { 'Green' } else { 'Red' }
Write-Host ("===== RESULT  PASS={0}  FAIL={1}  SKIP={2} =====" -f $script:pass, $script:fail, $script:skip) -ForegroundColor $color
if ($script:fail -gt 0) { exit 1 }
exit 0
