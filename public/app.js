'use strict';
const $ = (id) => document.getElementById(id);
const limit = 2 * 1024 * 1024;
let outputUrl = null;
let version = 0;
let busy = false;
const sample = 'User:\nこんにちは！このアプリで何ができますか？\n\nAssistant:\n# 会話をHTMLにまとめられます\nログを貼り付けて変換すると、**読みやすい吹き出し**で表示します。\n\n`chat-emoji.html` として保存すれば、オフラインでも開けます。\n\nUser:\nコードも残せますか？\n\nAssistant:\nもちろんです。コードブロックにも対応しています。\n\n```java\nSystem.out.println("こんにちは！");\n```';

function status(text, error = false) {
  $('status').textContent = text;
  $('status').classList.toggle('error', error);
}
function invalidate() {
  version++;
  $('count').textContent = `${$('source').value.length.toLocaleString('ja-JP')} 文字`;
  $('download').disabled = true;
  $('preview').hidden = true;
  $('preview').removeAttribute('srcdoc');
  $('empty').hidden = false;
  $('preview-state').textContent = '未変換';
  if (outputUrl) URL.revokeObjectURL(outputUrl);
  outputUrl = null;
  status('ログを入力して、変換ボタンを押してください。');
}
$('source').addEventListener('input', invalidate);
$('sample').addEventListener('click', () => {
  $('source').value = sample;
  invalidate();
  status('サンプルを入力しました。「HTMLに変換」を押してください。');
});
$('file').addEventListener('change', async () => {
  const file = $('file').files[0];
  if (!file) return;
  const readVersion = version;
  try {
    if (file.size > limit) throw new Error('ファイルは2 MiB以下にしてください。');
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
    if (readVersion !== version) return;
    $('source').value = text;
    invalidate();
    status(`${file.name} を読み込みました。`);
  } catch (error) {
    status(error instanceof TypeError ? 'UTF-8形式のテキストファイルを選んでください。' : error.message, true);
  } finally {
    $('file').value = '';
  }
});
$('convert').addEventListener('click', async () => {
  if (busy) return;
  const source = $('source').value;
  invalidate();
  if (!source.trim()) return status('変換するログを入力してください。', true);
  if (new TextEncoder().encode(source).length > limit) return status('ログは2 MiB以下にしてください。', true);
  const requestedVersion = version;
  busy = true;
  $('convert').disabled = true;
  status('変換しています。初回の起動には少し時間がかかることがあります。');
  try {
    const response = await fetch('/api/convert', {
      method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: source, signal: AbortSignal.timeout(120000)
    });
    const html = await response.text();
    if (requestedVersion !== version) return;
    if (!response.ok) throw new Error(response.status >= 500 ? 'サーバーが応答できません。少し待ってから再度お試しください。' : html);
    outputUrl = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }));
    $('preview').srcdoc = html;
    $('preview').hidden = false;
    $('empty').hidden = true;
    $('download').disabled = false;
    $('preview-state').textContent = '変換済み';
    status('変換できました。HTMLをダウンロードして保存できます。');
  } catch (error) {
    if (requestedVersion === version) status(error.name === 'TimeoutError' ? '時間がかかっています。しばらく待って、もう一度お試しください。' : error instanceof TypeError ? 'サーバーに接続できません。通信状態を確認してください。' : error.message, true);
  } finally {
    busy = false;
    $('convert').disabled = false;
  }
});
$('download').addEventListener('click', () => {
  if (!outputUrl) return;
  const link = document.createElement('a');
  link.href = outputUrl;
  link.download = 'chat-emoji.html';
  document.body.append(link);
  link.click();
  link.remove();
});
window.addEventListener('pagehide', () => { if (outputUrl) URL.revokeObjectURL(outputUrl); });
