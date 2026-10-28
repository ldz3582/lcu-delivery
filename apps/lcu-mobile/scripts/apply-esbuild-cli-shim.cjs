'use strict'
/**
 * Installs the file-backed-stdio esbuild shim into node_modules/esbuild.
 *
 * The DSH Windows sandbox blocks piped child stdio (named pipes are denied), so
 * esbuild's service cannot start ("spawn EPERM"). See
 * scripts/esbuild-cli-shim.cjs for the full explanation. This script is
 * idempotent and keeps a pristine copy of the original entry point at
 * node_modules/esbuild/lib/main.native.js.
 *
 * Usage (from apps/lcu-mobile):
 *   node scripts/apply-esbuild-cli-shim.cjs          # install shim
 *   node scripts/apply-esbuild-cli-shim.cjs --revert # restore native esbuild
 */
const fs = require('fs')
const path = require('path')

const appRoot = path.resolve(__dirname, '..')
const esbuildLib = path.join(appRoot, 'node_modules', 'esbuild', 'lib')
const mainPath = path.join(esbuildLib, 'main.js')
const backupPath = path.join(esbuildLib, 'main.native.js')
const shimPath = path.join(__dirname, 'esbuild-cli-shim.cjs')
const MARKER = '[esbuild-cli-shim]'

function fail(message) {
  console.error('apply-esbuild-cli-shim: ' + message)
  process.exit(1)
}

if (!fs.existsSync(mainPath)) fail('node_modules/esbuild is not installed - run npm install first')

const revert = process.argv.includes('--revert')
const current = fs.readFileSync(mainPath, 'utf8')

if (revert) {
  if (!fs.existsSync(backupPath)) fail('no main.native.js backup found - nothing to revert')
  fs.copyFileSync(backupPath, mainPath)
  console.log('reverted node_modules/esbuild/lib/main.js to the native implementation')
  process.exit(0)
}

const platformKey = `${process.platform}-${process.arch}`
const binName = process.platform === 'win32' ? 'esbuild.exe' : 'esbuild'
const binPath = path.join(appRoot, 'node_modules', `@esbuild/${platformKey}`, binName)
if (!fs.existsSync(binPath)) {
  fail(`native binary @esbuild/${platformKey}/${binName} is missing - run: npm install --ignore-scripts --no-audit --no-fund`)
}

if (current.includes(MARKER)) {
  console.log('esbuild shim already installed')
  process.exit(0)
}

if (!fs.existsSync(backupPath)) fs.copyFileSync(mainPath, backupPath)
fs.copyFileSync(shimPath, mainPath)
console.log('installed file-backed-stdio esbuild shim (native entry kept at lib/main.native.js)')
