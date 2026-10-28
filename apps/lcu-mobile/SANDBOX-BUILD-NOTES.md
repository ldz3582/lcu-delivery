# lcu-mobile → mp-weixin build notes (DSH Windows sandbox)

Status: **build succeeds**. Output: `apps/lcu-mobile/dist/build/mp-weixin` (123 files, 27 pages).

## The environment constraint that shapes everything

This DSH session runs in the Windows `workspace-write` sandbox, which **denies piped
child stdio** (libuv implements stdio pipes with named pipes; creating/connecting a
named pipe is denied). Concretely:

* `spawn/spawnSync/exec/execFile/execFileSync` with `stdio: 'pipe'` (the default) →
  `Error: spawn EPERM` (errno `-4048`)
* `stdio: 'inherit'`, `stdio: 'ignore'` and **integer file descriptors still work**
* `net.createServer('\\\\.\\pipe\\…')` / `net.connect()` → `EPERM`

Two consequences hit this app:

1. **esbuild** (used by Vite for bundling `vite.config.ts` and for the `vite:esbuild`
   TS transform) starts a native service process and talks to it over stdin/stdout →
   every `esbuild.transform()/build()` died with `spawn EPERM`.
2. `npm install` dies in `esbuild/install.js` (`execFileSync`) and in npm's own
   lifecycle-script pipes (lifecycle scripts need `--foreground-scripts`); a plain
   `npm run build:mp-weixin` is fine because npm runs the main script with inherited
   stdio.

## Exact commands that work

```powershell
$root='C:\Users\HP\Desktop\外卖项目'
$env:npm_config_cache="$root\.local\npm-cache-mobile"
$env:TMP="$root\.local\tmp"; $env:TEMP="$root\.local\tmp"
$env:PATH='D:\develop\waimai-env\node22;'+$env:PATH
Set-Location "$root\lcu-delivery\apps\lcu-mobile"

# 1) install WITHOUT lifecycle scripts (esbuild's postinstall cannot spawn here)
& 'D:\develop\waimai-env\node22\npm.cmd' install --ignore-scripts --no-audit --no-fund

# 2) install sandbox shims (idempotent; both keep a pristine backup)
node scripts/apply-esbuild-cli-shim.cjs
node scripts/apply-vite-sandbox-patches.cjs

# 3) build
& 'D:\develop\waimai-env\node22\npm.cmd' run build:mp-weixin
```

## Changes made to this app (all reversible, nothing else touched)

| Change | Why | Revert |
| --- | --- | --- |
| `node_modules/esbuild/lib/main.js` replaced by `scripts/esbuild-cli-shim.cjs` | routes `esbuild.transform/build/formatMessages` to the **real native esbuild binary** (`@esbuild/win32-x64/esbuild.exe`) in one-shot CLI mode with stdin/stdout/stderr redirected to temp **files** — no pipes. `build()` emulates Vite's config-loader plugins (`externalize-deps` → `--packages=external`, `inject-file-scope-variables` → literal `--define` for `__dirname/__filename/import.meta.url`). | `node scripts/apply-esbuild-cli-shim.cjs --revert` |
| `node_modules/vite/dist/node/chunks/dep-whKeNLxG.js`: wrapped `exec('net use')` in `optimizeSafeRealPathSync()` in try/catch | `child_process.exec` throws `EPERM` **synchronously** there; Vite's `resolvePackageData()` swallows it and reports every package as missing → `[vite:terser] terser not found`. Network-drive mapping is optional. | `node scripts/apply-vite-sandbox-patches.cjs --revert` |
| `package.json` devDependency `"terser": "^5.31.0"` | Uni-app's production mp build uses `build.minify: 'terser'`; Vite reports `terser not found` and terser is a pure-JS dep (no install scripts). | remove the line |
| `package-lock.json` created | side effect of `npm install --save-dev terser` (npm's `package-lock=true`). Pins the tree that built successfully, including `esbuild@0.20.2`. | delete it |

Not changed: `src/**`, `vite.config.ts`, `tsconfig.json`, `index.html`, `.env.example`,
backend, admin-web, any `@dcloudio` package version, vite version (5.2.8), Vue version.

## Verification of the output

```
dist/build/mp-weixin
  123 files, 323.8 KB
  .js 36   .json 30   .wxss 29   .wxml 28
  app.js / app.json / app.wxss / project.config.json
  common/vendor.js, components/AppTabBar.{js,json,wxml,wxss}
  api/index.js, stores/cart.js, utils/{auth,nav,request,subscribe}.js
  pages/**  -> 27/27 pages have .js + .wxml + .wxss + .json
```

`app.json` → 27 pages:
`pages/launch/index`, `pages/login/index`, `pages/index/index`, `pages/shop/detail`,
`pages/cart/cart`, `pages/coupon/index`, `pages/favorite/index`, `pages/order/list`,
`pages/order/detail`, `pages/address/list`, `pages/address/edit`, `pages/address/pick`,
`pages/review/edit`, `pages/after-sale/apply`, `pages/after-sale/list`,
`pages/merchant/apply`, `pages/merchant/finance`, `pages/merchant/coupons`,
`pages/merchant/orders`, `pages/merchant/products`, `pages/merchant/shop`,
`pages/rider/apply`, `pages/rider/finance`, `pages/rider/pool`, `pages/rider/orders`,
`pages/rider/profile`, `pages/mine/index`

`project.config.json` → `appid: wx06f28a1a6a6b5c73`, `compileType: miniprogram`,
`setting.minified: true`, `urlCheck: false`. UTF-8 Chinese content is intact in the
emitted `.wxml`/`.json`.

## Known non-fatal messages

* `The current application is not configured with Appid, and uni statistics cannot be used.`
  — `src/manifest.json` has an empty root `appid` (the mp-weixin appid is set); uni
  statistics only. Harmless for the build.
* `npm warn cleanup Failed to remove some directories … EPERM` appears only when an
  install fails part-way; a successful run has no such warnings.
