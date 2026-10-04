'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = vm.createContext({
  document: { getElementById: () => ({ addEventListener() {} }) },
  window: { addEventListener() {} }
});
vm.runInContext(fs.readFileSync('public/app.js', 'utf8') + '\nthis.api = { insertionPosition, removeImageReferences, imageMarker, imageMime };', context);
const { insertionPosition, removeImageReferences, imageMarker, imageMime } = context.api;
const role = 'Assistant:\n本文';
assert.equal(insertionPosition(role, 0), 'Assistant:'.length);
assert.equal(insertionPosition(role, 4), 'Assistant:'.length);
const fenced = '説明\n```java\ncode\n```\n末尾';
assert.throws(() => insertionPosition(fenced, fenced.indexOf('code')), /コードブロック/);
assert.equal(insertionPosition(fenced, fenced.indexOf('```')), fenced.indexOf('```'));
assert.equal(insertionPosition(fenced, fenced.lastIndexOf('```') + 3), fenced.lastIndexOf('```') + 3);
assert.throws(() => insertionPosition('```\ncode', 8), /コードブロック/);
const marker = '![名前変更済み](attachment:img-1)';
assert.equal(removeImageReferences('前\n' + marker + '\n後', 'img-1'), '前\n後');
const literal = '```\n' + marker + '\n```\n`' + marker + '`';
assert.equal(removeImageReferences(literal, 'img-1'), literal);
assert.equal(imageMarker({name:'図[1]\\\u2028.png',id:'img-1'}), '![図\\[1\\]\\\\ .png](attachment:img-1)');
assert.equal(imageMime(new Uint8Array([137,80,78,71,13,10,26,10])), 'image/png');
assert.throws(() => imageMime(new TextEncoder().encode('<svg onload="alert(1)">')), /PNG/);
console.log('Image frontend tests passed (insertion boundaries, removal, names, image signatures)');
