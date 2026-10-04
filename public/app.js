'use strict';
const $ = (id) => document.getElementById(id);
const limit = 2 * 1024 * 1024;
const imageLimit = 10 * 1024 * 1024;
const imageTotalLimit = 20 * 1024 * 1024;
const images = new Map();
let outputUrl = null;
let version = 0;
let busy = false;
let importingImages = false;
let sourceGeneration = 0;
let imageCursor = null;
const sample = 'User:\nこんにちは！このアプリで何ができますか？\n\nAssistant:\n# 会話をHTMLにまとめられます\nログを貼り付けて変換すると、**読みやすい吹き出し**で表示します。\n\n`chat-emoji.html` として保存すれば、オフラインでも開けます。\n\nUser:\nコードも残せますか？\n\nAssistant:\nもちろんです。コードブロックにも対応しています。\n\n```java\nSystem.out.println("こんにちは！");\n```';

function status(text, error = false) {
  $('status').textContent = text;
  $('status').classList.toggle('error', error);
}
function updateControls() {
  $('convert').disabled = busy || importingImages;
  $('add-image').disabled = importingImages;
}
function imageStatus(text, error = false) {
  $('image-status').textContent = text;
  $('image-status').classList.toggle('error', error);
}
function imageMarker(image) {
  const alt = image.name.replace(/[\u0000-\u001f\u007f\u0085\u2028\u2029]/g, ' ').replace(/[\\[\]]/g, '\\$&');
  return `![${alt}](attachment:${image.id})`;
}
function cursorSnapshot() {
  return { text: $('source').value, position: $('source').selectionStart, generation: sourceGeneration };
}
function insertionPosition(text, position) {
  let fenced = false;
  let offset = 0;
  let firstRoleEnd = null;
  let insideCode = false;
  for (const line of text.split(/\r\n|[\n\r\v\f\u0085\u2028\u2029]/)) {
    const end = offset + line.length;
    const fence = line.trim().startsWith('```');
    if (!fenced && /^(User|Assistant|あなた|自分|ChatGPT|AI):$/i.test(line.trim())) {
      if (firstRoleEnd === null) firstRoleEnd = end;
      if (position >= offset && position <= end) position = end;
    }
    if (position >= offset && position <= end) {
      insideCode = fence ? (fenced ? position < end : position > offset) : fenced;
    }
    if (fence) fenced = !fenced;
    offset = end + (text.slice(end, end + 2) === '\r\n' ? 2 : 1);
  }
  if (firstRoleEnd !== null && position < firstRoleEnd) return firstRoleEnd;
  if (insideCode) throw new Error('コードブロックの外にカーソルを移動して、画像を挿入してください。');
  return position;
}
function removeImageReferences(text, id) {
  let fenced = false;
  return text.split('\n').filter(line => {
    const trimmed = line.trim();
    if (trimmed.startsWith('```')) { fenced = !fenced; return true; }
    if (fenced) return true;
    const match = /^!\[(?:\\.|[^\\\]])*\]\(attachment:([A-Za-z0-9-]{1,80})\)$/.exec(trimmed);
    return !match || match[1] !== id;
  }).join('\n');
}
function insertImages(items, cursor = cursorSnapshot()) {
  const source = $('source');
  const position = insertionPosition(source.value, cursor.text === source.value ? cursor.position : source.selectionStart);
  const before = source.value.slice(0, position);
  const after = source.value.slice(position);
  const prefix = before ? (before.endsWith('\n\n') ? '' : before.endsWith('\n') ? '\n' : '\n\n') : '';
  const suffix = after ? (after.startsWith('\n\n') ? '' : after.startsWith('\n') ? '\n' : '\n\n') : '\n\n';
  source.setRangeText(prefix + items.map(imageMarker).join('\n\n') + suffix, position, position, 'end');
  invalidate();
  source.focus();
}
function renderImages() {
  const list = $('image-list');
  list.replaceChildren();
  for (const image of images.values()) {
    const item = document.createElement('li');
    const thumbnail = document.createElement('img');
    thumbnail.src = image.dataUrl;
    thumbnail.alt = '';
    const name = document.createElement('span');
    name.className = 'image-name';
    name.textContent = image.name;
    name.title = image.name;
    const actions = document.createElement('div');
    actions.className = 'image-actions';
    const insert = document.createElement('button');
    insert.type = 'button';
    insert.textContent = '再挿入';
    insert.setAttribute('aria-label', `${image.name} をカーソル位置に再挿入`);
    insert.addEventListener('click', () => {
      try {
        insertImages([image]);
        imageStatus(`${image.name} を挿入しました。`);
      } catch (error) { imageStatus(error.message, true); }
    });
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = '削除';
    remove.setAttribute('aria-label', `${image.name} を削除`);
    remove.addEventListener('click', () => {
      images.delete(image.id);
      $('source').value = removeImageReferences($('source').value, image.id);
      renderImages();
      invalidate();
      imageStatus(`${image.name} を削除しました。`);
    });
    actions.append(insert, remove);
    item.append(thumbnail, name, actions);
    list.append(item);
  }
  list.hidden = images.size === 0;
  const bytes = Array.from(images.values()).reduce((total, image) => total + image.size, 0);
  $('image-count').textContent = images.size ? `${images.size} 枚・合計 ${(bytes / 1024 / 1024).toFixed(1)} MiB` : '';
}
function resetImages() {
  sourceGeneration++;
  images.clear();
  renderImages();
  imageStatus('画像はこのブラウザ内で処理し、保存するHTMLに埋め込みます。');
}
function imageMime(bytes) {
  const has = (offset, values) => values.every((value, index) => bytes[offset + index] === value);
  if (has(0, [137, 80, 78, 71, 13, 10, 26, 10])) return 'image/png';
  if (has(0, [255, 216, 255])) return 'image/jpeg';
  if (has(0, [71, 73, 70, 56]) && (bytes[4] === 55 || bytes[4] === 57) && bytes[5] === 97) return 'image/gif';
  if (has(0, [82, 73, 70, 70]) && has(8, [87, 69, 66, 80])) return 'image/webp';
  throw new Error('PNG / JPEG / GIF / WebP の画像を選んでください。');
}
function readDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('画像ファイルを読み込めませんでした。'));
    reader.readAsDataURL(blob);
  });
}
async function readImage(file) {
  if (file.size > imageLimit) throw new Error('画像1枚は10 MiB以下にしてください。');
  const mime = imageMime(new Uint8Array(await file.slice(0, 12).arrayBuffer()));
  const dataUrl = await readDataUrl(new Blob([file], { type: mime }));
  await new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => image.naturalWidth && image.naturalHeight ? resolve() : reject(new Error('画像を表示できません。'));
    image.onerror = () => reject(new Error('画像が破損しているか、表示できない形式です。'));
    image.src = dataUrl;
  });
  return { id: `img-${crypto.randomUUID()}`, name: file.name || '貼り付け画像', dataUrl, size: file.size };
}
async function importImages(files, cursor = cursorSnapshot()) {
  if (!files.length) return;
  if (importingImages) return imageStatus('読み込み中です。完了してから画像を追加してください。', true);
  if (cursor.generation !== sourceGeneration) return;
  importingImages = true;
  updateControls();
  imageStatus('画像を読み込んでいます…');
  const accepted = [];
  const errors = [];
  let total = Array.from(images.values()).reduce((sum, image) => sum + image.size, 0);
  try {
    for (const file of files) {
      if (cursor.generation !== sourceGeneration) return;
      try {
        if (images.size + accepted.length >= 20) throw new Error('画像は20枚まで追加できます。');
        if (file.size > imageLimit) throw new Error('画像1枚は10 MiB以下にしてください。');
        if (total + file.size > imageTotalLimit) throw new Error('画像の合計は20 MiB以下にしてください。');
        const image = await readImage(file);
        if (cursor.generation !== sourceGeneration) return;
        accepted.push(image);
        total += image.size;
      } catch (error) {
        errors.push(`${file.name || '画像'}: ${error.message}`);
      }
    }
    if (accepted.length) {
      insertImages(accepted, cursor);
      for (const image of accepted) images.set(image.id, image);
      renderImages();
    }
    imageStatus([accepted.length ? `${accepted.length} 枚の画像を挿入しました。` : '', ...errors].filter(Boolean).join(' '), errors.length > 0);
  } catch (error) {
    imageStatus(error.message, true);
  } finally {
    importingImages = false;
    updateControls();
  }
}
function embedImages(html) {
  const document = new DOMParser().parseFromString(html, 'text/html');
  const missing = [];
  for (const placeholder of document.querySelectorAll('span.image-placeholder[data-attachment-id]')) {
    const image = images.get(placeholder.dataset.attachmentId);
    if (!image) {
      missing.push(placeholder.textContent);
      continue;
    }
    const element = document.createElement('img');
    element.className = 'chat-image';
    element.src = image.dataUrl;
    element.alt = image.name;
    placeholder.replaceWith(element);
  }
  if (missing.length) throw new Error('読み込まれていない画像があります。該当する画像の行を削除し、「画像を挿入」から追加し直してください。');
  return '<!doctype html>\n' + document.documentElement.outerHTML;
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
$('add-image').addEventListener('click', () => {
  imageCursor = cursorSnapshot();
  $('image-file').click();
});
$('image-file').addEventListener('change', () => {
  const files = Array.from($('image-file').files);
  $('image-file').value = '';
  importImages(files, imageCursor || cursorSnapshot());
  imageCursor = null;
});
$('source').addEventListener('paste', event => {
  const items = Array.from(event.clipboardData?.items || []);
  if (items.some(item => item.kind === 'string' && item.type === 'text/plain')) return;
  const files = items.filter(item => item.kind === 'file' && item.type.startsWith('image/')).map(item => item.getAsFile()).filter(Boolean);
  if (!files.length) return;
  event.preventDefault();
  importImages(files);
});
$('sample').addEventListener('click', () => {
  resetImages();
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
    resetImages();
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
  if (busy || importingImages) return;
  const source = $('source').value;
  invalidate();
  if (!source.trim()) return status('変換するログを入力してください。', true);
  if (new TextEncoder().encode(source).length > limit) return status('ログは2 MiB以下にしてください。', true);
  const requestedVersion = version;
  busy = true;
  updateControls();
  status('変換しています。初回の起動には少し時間がかかることがあります。');
  try {
    const response = await fetch('/api/convert', {
      method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: source, signal: AbortSignal.timeout(120000)
    });
    const responseHtml = await response.text();
    if (requestedVersion !== version) return;
    if (!response.ok) throw new Error(response.status >= 500 ? 'サーバーが応答できません。少し待ってから再度お試しください。' : responseHtml);
    const html = embedImages(responseHtml);
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
    updateControls();
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
