import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const query = process.argv[2];
if (!query) throw new Error('Usage: node tools/branding-audit.mjs <brand>');
const pattern = new RegExp(query, 'i');
const files = execFileSync('rg', ['--files', '--hidden', '--no-ignore', '-g', '!.git/**',
  '-g', '!**/build/**', '-g', '!**/.gradle/**', '-g', '!**/.cxx/**', '-g', '!**/.kotlin/**',
  '-g', '!**/node_modules/**', '-g', '!.tmp_verify/**', '-g', '!**/bin/**'],
  { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }).trim().split(/\r?\n/).filter(Boolean);
const skip = /(?:^|[/\\])(?:local\.properties|keystore\.properties)$|\.(?:apk|idsig|jar|jks|keystore|log|so|dll|exe|msi|class|onnx|tflite|png|jpe?g|webp|ico|icns|mp4|gif|woff2?|ttf|otf|zip|gz|pdf|a|lib)$/i;
const groups = {};
const mentions = [];
const paths = [];
for (const file of files) {
  if (pattern.test(file)) paths.push(file);
  if (skip.test(file)) continue;
  const buffer = fs.readFileSync(path.join(root, file));
  if (buffer.includes(0)) continue;
  const source = buffer.toString('utf8');
  if (!pattern.test(source)) continue;
  const group = file.split(/[/\\]/)[0];
  groups[group] = (groups[group] || 0) + 1;
  source.split(/\r?\n/).forEach((line, index) => {
    if (pattern.test(line) && !/^\s*(?:package|import)\s/.test(line)) {
      mentions.push({ file, line: index + 1, text: line.trim().slice(0, 260) });
    }
  });
}
const report = { scannedFiles: files.length, groups, paths, mentions };
fs.mkdirSync(path.join(root, '.tmp_verify'), { recursive: true });
fs.writeFileSync(path.join(root, '.tmp_verify', 'branding-audit.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify({ scannedFiles: files.length, groups, matchingPaths: paths.length,
  otherMentions: mentions.length, report: '.tmp_verify/branding-audit.json' }, null, 2));
