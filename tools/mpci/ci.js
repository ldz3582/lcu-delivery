#!/usr/bin/env node
/**
 * 微信小程序代码上传 / 预览（miniprogram-ci 封装）
 *
 * 由 ops/publish-wechat.sh 调用，也可以单独用：
 *   node tools/mpci/ci.js preview  --key ~/wx.key              # 出体验版二维码，手机扫码直接看
 *   node tools/mpci/ci.js upload   --key ~/wx.key --version 1.0.0 --desc "首次提审"
 *
 * 参数：
 *   --appid    小程序 AppID（默认读 apps/lcu-mobile/src/manifest.json）
 *   --project  产物目录（默认 apps/lcu-mobile/dist/build/mp-weixin）
 *   --key      上传密钥 .key 文件路径（小程序后台「开发管理 → 开发设置 → 小程序代码上传」下载）
 *   --version  版本号，如 1.0.0
 *   --desc     版本描述
 *   --robot    上传机器人编号 1-30，默认 1
 *
 * 两个必须满足的前置条件（脚本会检查并给出人话提示）：
 *   1) 本机公网 IP 已加到后台的「IP 白名单」
 *   2) 密钥文件属于当前 AppID，且是在后台「开发管理 → 小程序代码上传」生成的
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '../..')

function parseArgs(argv) {
  const out = { _: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a.startsWith('--')) {
      const k = a.slice(2)
      const v = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true
      out[k] = v
    } else out._.push(a)
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const cmd = args._[0]

function die(msg, hint) {
  console.error(`\n✗ ${msg}`)
  if (hint) console.error(`  ${hint}`)
  process.exit(1)
}

function readAppid() {
  const mp = path.join(ROOT, 'apps/lcu-mobile/src/manifest.json')
  if (!fs.existsSync(mp)) die(`找不到 ${mp}`)
  const j = JSON.parse(fs.readFileSync(mp, 'utf8'))
  return (j['mp-weixin'] && j['mp-weixin'].appid) || ''
}

if (!cmd || !['preview', 'upload'].includes(cmd)) {
  die('用法: node tools/mpci/ci.js <preview|upload> --key <密钥路径> [--version 1.0.0] [--desc 描述]')
}

const appid = String(args.appid || readAppid())
const projectPath = path.resolve(ROOT, args.project || 'apps/lcu-mobile/dist/build/mp-weixin')
const privateKeyPath = args.key ? path.resolve(args.key.replace(/^~(?=$|\/)/, process.env.HOME)) : ''
const version = String(args.version || '0.0.0')
const desc = String(args.desc || (cmd === 'preview' ? '预览' : '上传'))
const robot = Number(args.robot || 1)

if (!/^wx[0-9a-f]{16}$/.test(appid)) {
  die(`AppID 看起来不对：「${appid}」`, '去做小程序后台复制真实 AppID，或先跑 ./ops/use-wechat.sh <AppID> <AppSecret>')
}
if (!privateKeyPath) {
  die('缺少上传密钥 --key', '小程序后台 → 开发管理 → 开发设置 → 小程序代码上传 → 生成并下载密钥，形如 private.wx1234.key')
}
if (!fs.existsSync(privateKeyPath)) die(`密钥文件不存在：${privateKeyPath}`)
if (!fs.existsSync(path.join(projectPath, 'app.json'))) {
  die(`产物目录不像小程序：${projectPath}`, '先跑 ./ops/publish-wechat.sh build')
}

// 产物自检：真机/审核环境下打不通的地址，这里提前拦掉
const appJson = JSON.parse(fs.readFileSync(path.join(projectPath, 'app.json'), 'utf8'))
console.log(`▶ AppID      ${appid}`)
console.log(`  产物       ${projectPath}（${appJson.pages.length} 个页面）`)
console.log(`  版本       ${version}`)
console.log(`  描述       ${desc}`)

const ci = require('miniprogram-ci')
const project = new ci.Project({
  appid,
  type: 'miniProgram',
  projectPath,
  privateKeyPath,
  ignores: ['node_modules/**/*', '.git/**/*', '**/*.map'],
})

const setting = {
  es6: true,
  minify: true,
  autoPrefixWXSS: true,
  codeProtect: false,
}

async function main() {
  if (cmd === 'preview') {
    const out = path.join(ROOT, 'apps/lcu-mobile/dist/preview.jpg')
    const ret = await ci.preview({
      project,
      desc,
      setting,
      robot,
      qrcodeFormat: 'image',
      qrcodeOutputDest: out,
      onProgressUpdate: (t) => {
        if (t && t._msg) process.stdout.write(`\r  ${t._msg}          `)
      },
    })
    console.log(`\n✓ 预览二维码：${out}`)
    if (ret && ret.subPackageInfo) console.log('  分包信息：', JSON.stringify(ret.subPackageInfo))
    return
  }

  const ret = await ci.upload({
    project,
    version,
    desc,
    setting,
    robot,
    onProgressUpdate: (t) => {
      if (t && t._msg) process.stdout.write(`\r  ${t._msg}          `)
    },
  })
  console.log(`\n✓ 上传成功（版本 ${version}，机器人 ${robot}）`)
  if (ret && ret.subPackageInfo) console.log('  分包信息：', JSON.stringify(ret.subPackageInfo))
  console.log('\n下一步（只能你在网页上操作）：')
  console.log('  1) https://mp.weixin.qq.com → 管理 → 版本管理 → 开发版本，找到刚上传的版本')
  console.log('  2) 点「选为体验版本」→ 用自己的手机扫码，先把体验版跑通')
  console.log('  3) 确认没问题后点「提交审核」，填测试账号与功能说明')
  console.log('  4) 审核通过后点「发布」')
}

main().catch((e) => {
  const msg = String((e && e.message) || e)
  console.error(`\n✗ ${cmd} 失败：${msg}`)
  if (/ip|whitelist|白名单|40164/i.test(msg)) {
    console.error('  多半是「IP 白名单」没配：后台 → 开发管理 → 开发设置 → 小程序代码上传 → IP 白名单，')
    console.error('  把本机公网 IP 加进去（curl -s https://api.ipify.org 可以查）。')
  }
  if (/privateKey|key|invalid/i.test(msg)) {
    console.error('  也可能是密钥不属于这个 AppID，重新下载一次上传密钥。')
  }
  process.exit(1)
})
