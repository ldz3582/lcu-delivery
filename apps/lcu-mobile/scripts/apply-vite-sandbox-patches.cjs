'use strict'
/**
 * Sandbox patch for Vite on this Windows host (idempotent, revertible).
 *
 * Problem
 * -------
 * Vite's `optimizeSafeRealPathSync()` (Windows) runs
 *     exec('net use', (error, stdout) => { ... })
 * to map network drives. This DSH sandbox denies piped child stdio, and
 * `child_process.exec()` throws `EPERM` SYNCHRONOUSLY in that case (it does not
 * surface through the callback). The throw escapes `optimizeSafeRealPathSync()`
 * and is swallowed by the `try { } catch { }` inside Vite's
 * `resolvePackageData()`, which then reports every package as missing.
 *
 * Symptom seen in lcu-mobile:
 *     [vite:terser] terser not found ... You need to install it.
 * even though node_modules/terser exists (resolvePackageData('terser', root)
 * returns null because safeRealpathSync() threw).
 *
 * Fix
 * ---
 * Wrap the `exec('net use')` call in try/catch. The network-drive mapping is a
 * pure optimisation; without it Vite keeps using `fs.realpathSync`, which works.
 *
 * Usage (from apps/lcu-mobile):
 *   node scripts/apply-vite-sandbox-patches.cjs          # patch
 *   node scripts/apply-vite-sandbox-patches.cjs --revert # restore
 */
const fs = require('fs')
const path = require('path')

const appRoot = path.resolve(__dirname, '..')
const viteDist = path.join(appRoot, 'node_modules', 'vite', 'dist')
const MARKER = '[dsh-vite-sandbox-patch]'

function findChunk() {
  const stack = [viteDist]
  while (stack.length) {
    const dir = stack.pop()
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) stack.push(full)
      else if (entry.name.endsWith('.js')) {
        const text = fs.readFileSync(full, 'utf8')
        if (text.includes("exec('net use'")) return { file: full, text }
      }
    }
  }
  return null
}

function fail(message) {
  console.error('apply-vite-sandbox-patches: ' + message)
  process.exit(1)
}

const found = findChunk()
if (!found) fail('could not find the vite chunk containing exec(\'net use\')')
const { file, text } = found
const backup = file + '.dshorig'

if (process.argv.includes('--revert')) {
  if (!fs.existsSync(backup)) fail('no .dshorig backup found - nothing to revert')
  fs.copyFileSync(backup, file)
  console.log('reverted ' + path.relative(appRoot, file))
  process.exit(0)
}

if (text.includes(MARKER)) {
  console.log('vite sandbox patch already applied (' + path.relative(appRoot, file) + ')')
  process.exit(0)
}

const openPattern = `    exec('net use', (error, stdout) => {
        if (error)
            return;
        const lines = stdout.split('\\n');`

const closePattern = `        if (windowsNetworkMap.size === 0) {
            safeRealpathSync = fs$l.realpathSync.native;
        }
        else {
            safeRealpathSync = windowsMappedRealpathSync;
        }
    });`

if (!text.includes(openPattern) || !text.includes(closePattern)) {
  fail('vite internals changed - expected code not found, refusing to patch')
}

let patched = text.replace(
  openPattern,
  `    try {
    exec('net use', (error, stdout) => {
        if (error)
            return;
        const lines = stdout.split('\\n');`
)

patched = patched.replace(
  closePattern,
  `${closePattern}
    } catch {
        // ${MARKER} child_process.exec() throws EPERM synchronously when the sandbox
        // denies piped child stdio. Network-drive realpath mapping is optional: keep
        // the default fs.realpathSync-based implementation instead of aborting.
        safeRealpathSync = fs$l.realpathSync;
    }`
)

if (!fs.existsSync(backup)) fs.copyFileSync(file, backup)
fs.writeFileSync(file, patched)
console.log('patched ' + path.relative(appRoot, file) + ' (original kept as .dshorig)')
