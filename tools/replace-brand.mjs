import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const [from, to, mode] = process.argv.slice(2);
if (!from || !to) throw new Error('Usage: node tools/replace-brand.mjs <from> <to> [--apply]');
const escaped = from.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const pattern = new RegExp(escaped, 'gi');
function replace(value) {
  return value.replace(pattern, match => match === match.toUpperCase() ? to.toUpperCase()
    : match === match.toLowerCase() ? to.toLowerCase() : to);
}
const files = execFileSync('rg', ['--files', '--hidden', '--no-ignore', '-g', '!.git/**',
  '-g', '!**/build/**', '-g', '!**/.gradle/**', '-g', '!**/.cxx/**', '-g', '!**/.kotlin/**',
  '-g', '!**/node_modules/**', '-g', '!.tmp_verify/**', '-g', '!**/bin/**'],
  { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }).trim().split(/\r?\n/).filter(Boolean);
const skip = /(?:^|[/\\])(?:local\.properties|keystore\.properties)$|\.(?:apk|idsig|jar|jks|keystore|log|so|dll|exe|msi|class|onnx|tflite|png|jpe?g|webp|ico|icns|mp4|gif|woff2?|ttf|otf|zip|gz|pdf|a|lib)$/i;
const changes = [];
const moves = [];
const directories = new Set();
for (const file of files) {
  const absolute = path.resolve(root, file);
  if (!absolute.startsWith(root + path.sep)) throw new Error('Path outside workspace');
  if (!skip.test(file)) {
    const buffer = fs.readFileSync(absolute);
    if (!buffer.includes(0)) {
      const original = buffer.toString('utf8');
      let updated = original;
      if (/^(?:codebase-map|\.vscode)[/\\]/.test(file)) {
        for (const prefix of [JSON.stringify(root).slice(1, -1), root, root.replaceAll('\\', '/')]) {
          updated = updated.replaceAll(prefix, '.');
        }
      }
      updated = updated
        .replaceAll('github.com/kushagrasinghx/' + from, 'github.com/Lokal-Konnekt/' + to)
        .replaceAll('github.com/repos/kushagrasinghx/' + from, 'github.com/repos/Lokal-Konnekt/' + to)
        .replaceAll('github.com/KabirSinghBhatia/' + from, 'github.com/Lokal-Konnekt/' + to)
        .replaceAll('https://api.' + from.toLowerCase() + '.kushagrasingh.in', 'https://konnekt-listen-together.onrender.com')
        .replaceAll('https://' + from.toLowerCase() + '-67v8.onrender.com', 'https://konnekt-listen-together.onrender.com')
        .replaceAll(from + ' contributors', 'project contributors')
        .replaceAll(from + ' adaptation', 'shared adaptation');
      updated = replace(updated);
      if (updated !== original) changes.push({ file, absolute, updated });
    }
  }
  const basename = path.basename(file);
  if (replace(basename) !== basename && !skip.test(file)) {
    const target = path.join(path.dirname(absolute), replace(basename));
    if (fs.existsSync(target)) throw new Error('Rename conflict: ' + target);
    moves.push({ from: absolute, to: target });
  }
  let directory = path.dirname(file);
  while (directory !== '.') {
    if (replace(path.basename(directory)) !== path.basename(directory)) directories.add(directory);
    directory = path.dirname(directory);
  }
}
const dirMoves = [...directories].sort((a, b) => b.split(path.sep).length - a.split(path.sep).length)
  .map(directory => ({ from: path.resolve(root, directory),
    to: path.resolve(root, path.dirname(directory), replace(path.basename(directory))) }));
for (const move of dirMoves) if (fs.existsSync(move.to)) throw new Error('Directory conflict: ' + move.to);
console.log(JSON.stringify({ changedTextFiles: changes.length, renamedFiles: moves.length,
  renamedDirectories: dirMoves.length, apply: mode === '--apply' }, null, 2));
if (mode === '--apply') {
  for (const change of changes) fs.writeFileSync(change.absolute, change.updated);
  for (const move of moves) fs.renameSync(move.from, move.to);
  for (const move of dirMoves) fs.renameSync(move.from, move.to);
}
