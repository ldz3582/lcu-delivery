'use strict'
/**
 * DSH Windows sandbox shim for esbuild  (native binary over file-backed stdio)
 * ===========================================================================
 *
 * Why this exists
 * ---------------
 * This DSH session runs inside the Windows file/process sandbox, which denies
 * stdio pipes for child processes. libuv implements piped stdio with named
 * pipes, and both creating and connecting a named pipe are denied, so every
 *     child_process.spawn/spawnSync(..., { stdio: 'pipe' })   // also 'exec'
 * fails with `spawn EPERM` (errno -4048). `stdio: 'inherit' | 'ignore'` and
 * integer file descriptors still work.
 *
 * The real `esbuild` package starts its compiled service with
 *     spawn(binary, [], { stdio: ['pipe', 'pipe', 'inherit'] })
 * and speaks a request/response protocol over those pipes, so on this host
 * every esbuild JS API call dies with `Error: spawn EPERM at
 * ensureServiceIsRunning`. Vite needs esbuild for (a) bundling vite.config.ts
 * and (b) the `vite:esbuild` TS/JSX transform, therefore `uni build` failed in
 * config loading.
 *
 * Fix
 * ---
 * Keep using the real, version-matched esbuild binary, but run it in one-shot
 * CLI mode with stdin/stdout/stderr redirected to temp FILES (integer fds), so
 * no pipes are involved:
 *
 *     esbuild.exe --loader=ts --sourcefile=... --tsconfig=<tmp> --sourcemap=inline
 *                 < input.ts > output.js 2> errors.txt
 *
 * This reproduces the observable behaviour of `esbuild.transform()` /
 * `esbuild.build()` / `esbuild.formatMessages()` that vite, rollup and uni-app
 * rely on:
 *   - transform: code + sourcemap (inline map is extracted and returned as the
 *     API's `map` string), tsconfigRaw -> temp tsconfig, define/target/loader/
 *     minify/charset/jsx/... mapped to CLI flags, CLI errors parsed into the
 *     API's { text, location } shape.
 *   - build: entryPoints/bundle/format/platform/external/define/metafile/
 *     sourcemap/stdin/write/outdir mapped to CLI flags. Vite's config-loader
 *     plugins are emulated: `externalize-deps` -> --packages=external,
 *     `inject-file-scope-variables` -> literal --define for __dirname /
 *     __filename / import.meta.url of the config file. Any other plugin makes
 *     this throw loudly instead of producing wrong output.
 *   - context: thin wrapper over build (used by vite's dev-time dep optimizer).
 *
 * Requirements: the platform binary that `esbuild` already depends on
 * (@esbuild/win32-x64/esbuild.exe here) must be installed - it is, npm install
 * skips only *scripts*, not optional binary packages.
 *
 * Revert: node scripts/apply-esbuild-cli-shim.cjs --revert
 */
const fs = require('fs')
const os = require('os')
const path = require('path')
const { spawnSync } = require('child_process')

/** Locate the `esbuild` package directory (works from lib/main.js and from scripts/). */
function resolvePkgDir() {
  let dir = __dirname
  for (let i = 0; i < 6; i++) {
    const pkgJson = path.join(dir, 'package.json')
    if (fs.existsSync(pkgJson)) {
      try {
        if (JSON.parse(fs.readFileSync(pkgJson, 'utf8')).name === 'esbuild') return dir
      } catch {
        /* keep walking */
      }
    }
    const parent = path.dirname(dir)
    if (parent === dir) break
    dir = parent
  }
  return path.join(__dirname, '..')
}

const pkgDir = resolvePkgDir()
let PKG_VERSION = '0.0.0'
try {
  PKG_VERSION = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8')).version
} catch {
  /* ignore */
}
// Keep temp files inside the project (the sandbox only guarantees writes there).
const TMP_ROOT =
  process.env.DSH_ESBUILD_TMP || path.join(pkgDir, '..', '.cache', 'dsh-esbuild-shim')
const DEBUG = !!process.env.DSH_ESBUILD_SHIM_DEBUG

let seq = 0
function nextId() {
  seq += 1
  return `${process.pid}-${Date.now()}-${seq}`
}

function log(...args) {
  if (DEBUG) console.error('[esbuild-cli-shim]', ...args)
}

// ---------------------------------------------------------------- binary ---
let cachedBinary = null
function findBinary() {
  if (cachedBinary) return cachedBinary
  const candidates = []
  if (process.env.ESBUILD_BINARY_PATH) candidates.push(process.env.ESBUILD_BINARY_PATH)
  const platformKey = `${process.platform}-${process.arch}`
  const binName = process.platform === 'win32' ? 'esbuild.exe' : 'esbuild'
  const platformPkg = `@esbuild/${platformKey}`
  candidates.push(
    path.join(pkgDir, '..', platformPkg, binName),
    path.join(pkgDir, '..', '..', platformPkg, binName),
    path.join(pkgDir, 'bin', binName)
  )
  for (const candidate of candidates) {
    if (candidate && fs.existsSync(candidate)) {
      cachedBinary = candidate
      log('binary:', candidate)
      return candidate
    }
  }
  throw new Error(
    `[esbuild-cli-shim] cannot find the native esbuild binary for ${platformKey} ` +
      `(looked for ${platformPkg}/${binName}); reinstall with --ignore-scripts so the ` +
      `optional @esbuild/* package is present`
  )
}

// ------------------------------------------------------------- runner -----
function cleanup(paths) {
  for (const p of paths) {
    try {
      fs.unlinkSync(p)
    } catch {
      /* best effort */
    }
  }
}

/**
 * Run the esbuild CLI with stdio backed by temp FILES (never pipes).
 * Returns { status, stdout, stderr }.
 */
function runCli(args, input, options) {
  const opts = options || {}
  fs.mkdirSync(TMP_ROOT, { recursive: true })
  const id = nextId()
  const base = path.join(TMP_ROOT, `esb-${id}`)
  const inPath = `${base}.in`
  const outPath = `${base}.out`
  const errPath = `${base}.err`
  const tmpFiles = [inPath, outPath, errPath]

  fs.writeFileSync(inPath, input == null ? '' : input)
  let inFd = fs.openSync(inPath, 'r')
  let outFd = fs.openSync(outPath, 'w')
  let errFd = fs.openSync(errPath, 'w')
  let result
  try {
    const env = { ...process.env }
    delete env.ESBUILD_BINARY_PATH
    result = spawnSync(findBinary(), args, {
      cwd: opts.cwd || process.cwd(),
      stdio: [inFd, outFd, errFd],
      // keep the sandbox happy: no new console window, no shell
      windowsHide: true,
      shell: false,
      env,
      maxBuffer: 64 * 1024 * 1024
    })
  } finally {
    for (const fd of [inFd, outFd, errFd]) {
      if (fd !== undefined && fd !== null) {
        try {
          fs.closeSync(fd)
        } catch {
          /* ignore */
        }
      }
      inFd = outFd = errFd = null
    }
  }
  const stdout = fs.readFileSync(outPath, 'utf8')
  const stderr = fs.readFileSync(errPath, 'utf8')
  const metaPath = opts.metafilePath
  let metafile = null
  if (metaPath && fs.existsSync(metaPath)) {
    try {
      metafile = JSON.parse(fs.readFileSync(metaPath, 'utf8'))
    } catch (e) {
      log('failed to read metafile', e.message)
    }
    tmpFiles.push(metaPath)
  }
  cleanup(tmpFiles)
  if (result.error) throw result.error
  return { status: result.status, stdout, stderr, metafile }
}

// --------------------------------------------------- diagnostics parsing ---
const ERROR_RE = /^\s*(✘|✖|×|X)?\s*\[(ERROR|WARNING)\]\s*(.*)$/
const LOCATION_RE = /^\s*(\S.*?):(\d+):(\d+):\s*$/

function parseDiagnostics(stderr) {
  const errors = []
  const warnings = []
  const lines = String(stderr || '').split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const m = ERROR_RE.exec(line)
    if (!m) continue
    const kind = m[2]
    const diag = { text: m[3].trim(), location: null }
    // A location line follows the message (possibly after a blank line).
    for (let j = i + 1; j < Math.min(i + 4, lines.length); j++) {
      const lm = LOCATION_RE.exec(lines[j])
      if (lm) {
        diag.location = { file: lm[1], line: Number(lm[2]), column: Number(lm[3]), lineText: '', suggestion: '' }
        // esbuild prints "   12 │ source line" after the location
        for (let k = j + 1; k < Math.min(j + 3, lines.length); k++) {
          const src = /^\s*\d+\s*│\s?(.*)$/.exec(lines[k])
          if (src) {
            diag.location.lineText = src[1]
            break
          }
        }
        break
      }
    }
    ;(kind === 'ERROR' ? errors : warnings).push(diag)
  }
  return { errors, warnings }
}

function diagnosticsMessage(errors, warnings) {
  const all = [...errors, ...warnings]
  if (!all.length) return 'esbuild failed'
  return all
    .map((d) => {
      const loc = d.location ? `\n    ${d.location.file}:${d.location.line}:${d.location.column}` : ''
      return `${d.text}${loc}`
    })
    .join('\n')
}

function throwIfFailed(res, label) {
  const { errors, warnings } = parseDiagnostics(res.stderr)
  if (res.status !== 0 || errors.length > 0) {
    if (!errors.length && res.stderr.trim()) {
      errors.push({ text: res.stderr.trim(), location: null })
    }
    const message = label ? `${label}: ${diagnosticsMessage(errors, [])}` : diagnosticsMessage(errors, [])
    const error = new Error(message)
    error.errors = errors
    error.warnings = warnings
    throw error
  }
  return warnings
}

// --------------------------------------------------------- arg mapping ----
function pushTarget(args, target) {
  if (!target) return
  const value = Array.isArray(target) ? target.join(',') : String(target)
  if (value) args.push(`--target=${value}`)
}

function pushDefine(args, define) {
  if (!define) return
  for (const [key, value] of Object.entries(define)) {
    args.push(`--define:${key}=${typeof value === 'string' ? value : JSON.stringify(value)}`)
  }
}

function pushBannerFooter(args, flag, value) {
  if (value == null) return
  if (typeof value === 'string') {
    args.push(`--${flag}:js=${value}`)
    return
  }
  for (const key of Object.keys(value)) {
    if (key === 'css' && flag !== 'banner' && flag !== 'footer') continue
    args.push(`--${flag}:${key}=${value[key]}`)
  }
}

function pushSourcemap(args, sourcemap) {
  if (!sourcemap) return
  const value = sourcemap === true ? 'inline' : String(sourcemap)
  args.push(`--sourcemap=${value}`)
}

function pushMinify(args, minify) {
  if (!minify) return
  if (minify === true) args.push('--minify')
  else if (minify === 'whitespace') args.push('--minify-whitespace')
  else if (minify === 'syntax') args.push('--minify-syntax')
  else if (minify === 'identifiers') args.push('--minify-identifiers')
  else args.push('--minify')
}

function pushJsx(args, options) {
  if (options.jsx) args.push(`--jsx=${options.jsx}`)
  if (options.jsxFactory) args.push(`--jsx-factory=${options.jsxFactory}`)
  if (options.jsxFragment) args.push(`--jsx-fragment=${options.jsxFragment}`)
  if (options.jsxImportSource) args.push(`--jsx-import-source=${options.jsxImportSource}`)
  if (options.jsxDev) args.push('--jsx-dev')
}

function pushCommon(args, options) {
  if (options.platform) args.push(`--platform=${options.platform}`)
  if (options.format) args.push(`--format=${options.format}`)
  pushTarget(args, options.target)
  pushDefine(args, options.define)
  if (options.charset) args.push(`--charset=${options.charset}`)
  if (options.keepNames) args.push('--keep-names')
  if (options.legalComments) args.push(`--legal-comments=${options.legalComments}`)
  if (options.treeShaking) args.push(`--tree-shaking=${options.treeShaking}`)
  if (options.globalName) args.push(`--global-name=${options.globalName}`)
  if (options.lineLimit) args.push(`--line-limit=${options.lineLimit}`)
  if (options.supported) {
    for (const [key, value] of Object.entries(options.supported)) args.push(`--supported:${key}=${value}`)
  }
  if (options.drop) for (const item of options.drop) args.push(`--drop:${item}`)
  if (options.pure) for (const item of options.pure) args.push(`--pure:${item}`)
  pushBannerFooter(args, 'banner', options.banner)
  pushBannerFooter(args, 'footer', options.footer)
  pushMinify(args, options.minify)
  pushJsx(args, options)
}

/**
 * tsconfigRaw is a JSON string/object in the API but a file path on the CLI.
 * Writing it to a temp file keeps long/awkward JSON off the Windows command line.
 */
function writeTempTsconfig(tsconfigRaw) {
  if (tsconfigRaw == null) return null
  const json = typeof tsconfigRaw === 'string' ? tsconfigRaw : JSON.stringify(tsconfigRaw)
  if (!json.trim()) return null
  fs.mkdirSync(TMP_ROOT, { recursive: true })
  const file = path.join(TMP_ROOT, `tsconfig-${nextId()}.json`)
  fs.writeFileSync(file, json)
  return file
}

function extractInlineSourcemap(code) {
  let map = ''
  let out = code
  const re = /(?:\/\/|\/\*)[#@] sourceMappingURL=data:application\/json;base64,([A-Za-z0-9+/=]+)(?:\s*\*\/)?\s*$/m
  const match = re.exec(code)
  if (match) {
    try {
      map = Buffer.from(match[1], 'base64').toString('utf8')
    } catch {
      map = ''
    }
    out = code.slice(0, match.index).replace(/\s*$/, '')
    if (code.slice(match.index).trim().endsWith('*/')) out = out.replace(/\s*$/, '')
  }
  return { code: out, map }
}

// ------------------------------------------------------------ transform ---
function transformImpl(input, options) {
  const opts = options || {}
  const args = []
  const loader = opts.loader || 'js'
  args.push(`--loader=${loader}`)
  if (opts.sourcefile) args.push(`--sourcefile=${opts.sourcefile}`)
  // transform() takes `tsconfigRaw`; the CLI flag for it is --tsconfig-raw (JSON inline).
  if (opts.tsconfigRaw != null) {
    const json = typeof opts.tsconfigRaw === 'string' ? opts.tsconfigRaw : JSON.stringify(opts.tsconfigRaw)
    if (json.trim()) args.push(`--tsconfig-raw=${json}`)
  }
  pushCommon(args, opts)
  pushSourcemap(args, opts.sourcemap)
  log('transform', loader, opts.sourcefile || '')
  const res = runCli(args, input, { cwd: opts.absWorkingDir || process.cwd() })
  const warnings = throwIfFailed(res, 'Transform failed')
  const { code, map } = extractInlineSourcemap(res.stdout)
  return { code, map, warnings, errors: [] }
}

// ---------------------------------------------------------------- build ---
const EMULATED_PLUGINS = new Set(['externalize-deps', 'inject-file-scope-variables'])

function normalizeEntryPoints(entryPoints) {
  if (!entryPoints) return []
  if (Array.isArray(entryPoints)) {
    return entryPoints.map((entry) => (Array.isArray(entry) ? `${entry[0]}=${entry[1]}` : String(entry)))
  }
  return Object.entries(entryPoints).map(([name, file]) => `${name}=${file}`)
}

/**
 * Vite's config loader injects `var __vite_injected_original_{dirname,filename,import_meta_url}`
 * through a plugin and maps __dirname/__filename/import.meta.url onto those names
 * via `define`. With the CLI we cannot run plugins, so substitute the literal
 * values for the config file itself (the only module that normally uses them).
 */
function resolveInjectedDefines(define, entryFile) {
  if (!define || !entryFile) return define
  const dirname = path.dirname(entryFile)
  const fileUrl = require('url').pathToFileURL(entryFile).href
  const values = {
    __vite_injected_original_dirname: JSON.stringify(dirname),
    __vite_injected_original_filename: JSON.stringify(entryFile),
    __vite_injected_original_import_meta_url: JSON.stringify(fileUrl)
  }
  const out = {}
  for (const [key, value] of Object.entries(define)) {
    if (typeof value === 'string' && values[value]) out[key] = values[value]
    else out[key] = value
  }
  return out
}

function buildImpl(options) {
  const opts = options || {}
  const args = []
  const entryPoints = normalizeEntryPoints(opts.entryPoints)
  const pluginNames = (opts.plugins || []).map((plugin) => plugin && plugin.name).filter(Boolean)
  const unsupported = pluginNames.filter((name) => !EMULATED_PLUGINS.has(name))
  if (unsupported.length) {
    throw new Error(
      `[esbuild-cli-shim] build() was called with plugins that cannot be emulated over the CLI: ${unsupported.join(', ')}. ` +
        'Only vite config-loading (externalize-deps / inject-file-scope-variables) is supported.'
    )
  }
  const emulatesExternalize = pluginNames.includes('externalize-deps')

  if (entryPoints.length) args.push(...entryPoints)
  if (opts.bundle) args.push('--bundle')
  if (emulatesExternalize) args.push('--packages=external')
  // The CLI cannot write a metafile for stdout output, and the API must not touch
  // the disk when `write: false`, so capture through a temp output path instead.
  const capture = opts.write === false
  let tempOutFile = null
  let tempOutDir = null
  if (capture) {
    fs.mkdirSync(TMP_ROOT, { recursive: true })
    if (opts.outdir) {
      tempOutDir = path.join(TMP_ROOT, `out-${nextId()}`)
      args.push(`--outdir=${tempOutDir}`)
    } else {
      tempOutFile = path.join(TMP_ROOT, `out-${nextId()}.js`)
      args.push(`--outfile=${tempOutFile}`)
    }
  } else {
    if (opts.outdir) args.push(`--outdir=${opts.outdir}`)
    if (opts.outfile) args.push(`--outfile=${opts.outfile}`)
  }
  if (opts.mainFields) for (const field of opts.mainFields) args.push(`--main-fields=${field}`)
  if (opts.conditions) for (const condition of opts.conditions) args.push(`--conditions=${condition}`)
  if (opts.external) for (const item of opts.external) args.push(`--external:${item}`)
  if (opts.resolveExtensions) args.push(`--resolve-extensions=${opts.resolveExtensions.join(',')}`)
  let tempTsconfig = null
  if (opts.tsconfigRaw != null) {
    tempTsconfig = writeTempTsconfig(opts.tsconfigRaw)
    if (tempTsconfig) args.push(`--tsconfig=${tempTsconfig}`)
  } else if (opts.tsconfig) {
    args.push(`--tsconfig=${opts.tsconfig}`)
  }
  if (opts.assetNames) args.push(`--asset-names=${opts.assetNames}`)
  if (opts.chunkNames) args.push(`--chunk-names=${opts.chunkNames}`)
  if (opts.entryNames) args.push(`--entry-names=${opts.entryNames}`)
  if (opts.publicPath) args.push(`--public-path=${opts.publicPath}`)
  if (opts.keepNames) args.push('--keep-names')
  if (opts.mangleProps) args.push(`--mangle-props=${opts.mangleProps}`)
  if (opts.treeShaking) args.push(`--tree-shaking=${opts.treeShaking}`)
  if (opts.metafile) {
    fs.mkdirSync(TMP_ROOT, { recursive: true })
    opts.__metafilePath = path.join(TMP_ROOT, `metafile-${nextId()}.json`)
    args.push(`--metafile=${opts.__metafilePath}`)
  }
  if (opts.sourcemap) pushSourcemap(args, opts.sourcemap)
  const injectPlugin = pluginNames.includes('inject-file-scope-variables')
  const entryFile = entryPoints.length ? entryPoints[0].split('=').pop() : undefined
  const define = injectPlugin ? resolveInjectedDefines(opts.define, entryFile && path.resolve(opts.absWorkingDir || process.cwd(), entryFile)) : opts.define
  const buildOptions = { ...opts, define, __metafilePath: opts.__metafilePath }
  pushCommon(args, buildOptions)

  let stdinContents = null
  if (opts.stdin) {
    if (opts.stdin.contents != null) stdinContents = opts.stdin.contents
    if (opts.stdin.loader) args.push(`--loader=${opts.stdin.loader}`)
    if (opts.stdin.sourcefile) args.push(`--sourcefile=${opts.stdin.sourcefile}`)
  }

  log('build', entryPoints.join(','), pluginNames.join(','))
  const runCwd = opts.absWorkingDir || (opts.stdin && opts.stdin.resolveDir) || process.cwd()
  let res
  try {
    res = runCli(args, stdinContents, {
      cwd: runCwd,
      metafilePath: opts.__metafilePath
    })
  } finally {
    if (tempTsconfig) cleanup([tempTsconfig])
  }
  const warnings = throwIfFailed(res, 'Build failed')
  const outputFiles = []
  if (capture) {
    if (tempOutFile) {
      const text = fs.readFileSync(tempOutFile, 'utf8')
      outputFiles.push({ path: path.join(runCwd, 'output.js'), text, contents: Buffer.from(text) })
      cleanup([tempOutFile])
    } else if (tempOutDir) {
      const walk = (dir) => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
          const full = path.join(dir, entry.name)
          if (entry.isDirectory()) walk(full)
          else {
            const text = fs.readFileSync(full, 'utf8')
            outputFiles.push({ path: full.replace(tempOutDir, runCwd), text, contents: Buffer.from(text) })
          }
        }
      }
      walk(tempOutDir)
      fs.rmSync(tempOutDir, { recursive: true, force: true })
    }
  }
  return {
    outputFiles,
    metafile: res.metafile || undefined,
    warnings,
    errors: []
  }
}

// ------------------------------------------------------- public API ------
function build(options) {
  return Promise.resolve().then(() => buildImpl(options))
}
function buildSync(options) {
  return buildImpl(options)
}
function transform(input, options) {
  return Promise.resolve().then(() => transformImpl(input, options))
}
function transformSync(input, options) {
  return transformImpl(input, options)
}
function context(options) {
  return Promise.resolve().then(() => {
    let last = null
    return {
      rebuild: () => Promise.resolve().then(() => (last = buildImpl(options))),
      cancel: () => Promise.resolve(),
      dispose: () => Promise.resolve(),
      watch: () => Promise.resolve(),
      onEnd: (cb) => {
        last && cb && cb(last)
      }
    }
  })
}
function formatMessages(messages, options) {
  return Promise.resolve().then(() => formatMessagesSync(messages, options))
}
function formatMessagesSync(messages, options) {
  const kind = (options && options.kind) || 'error'
  const prefix = kind === 'error' ? '✘ [ERROR] ' : '▲ [WARNING] '
  return (messages || []).map((message) => {
    let text = `${prefix}${message.text}`
    if (message.location) {
      const { file, line, column, lineText } = message.location
      text += `\n\n    ${file}:${line}:${column}:`
      if (lineText) {
        text += `\n      ${line}: │ ${lineText}`
        if (column != null) text += `\n        ╵ ${' '.repeat(Math.max(0, column - 1))}^`
      }
    }
    return text
  })
}
function analyzeMetafile(metafile) {
  return Promise.resolve('')
}
function analyzeMetafileSync() {
  return ''
}
function stop() {
  return Promise.resolve()
}

exports.build = build
exports.buildSync = buildSync
exports.transform = transform
exports.transformSync = transformSync
exports.context = context
exports.formatMessages = formatMessages
exports.formatMessagesSync = formatMessagesSync
exports.analyzeMetafile = analyzeMetafile
exports.analyzeMetafileSync = analyzeMetafileSync
exports.stop = stop
exports.version = PKG_VERSION
