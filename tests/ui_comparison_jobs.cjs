const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

async function scenario(kind) {
  const nodes = {};
  const node = id => nodes[id] ||= { disabled: false, hidden: false, value: '', files: [], textContent: '',
    insertAdjacentElement(where, el) { nodes.cancel = el; } };
  ['analyse', 'candidate', 'reference', 'model', 'refSelector', 'status'].forEach(node);
  node('candidate').files = [{ name: 'watch.png' }];
  node('model').value = '124060';
  if (kind === 'reference-error') node('refSelector').value = 'cached.png';
  const calls = [];
  let displayed = false;
  let polls = 0;
  const sandbox = { console, Date, AbortSignal, FormData: class { append() {} }, File: class {},
    state: { task: 'gen' }, $: node, show() { displayed = true; },
    document: { createElement() { return { disabled: false }; }, querySelectorAll() { return Object.values(nodes); } },
    setTimeout(fn) { fn(); },
    async fetch(url, options = {}) {
      calls.push([url, options.method]);
      if (url.includes('reference-image')) throw new Error('Reference unavailable');
      if (options.method === 'POST') return { ok: true, async json() { return { job_id: 'job' }; } };
      if (options.method === 'DELETE') return { ok: true, async json() { return { state: 'cancelled' }; } };
      polls++;
      if (kind === 'poll-error') throw new Error('Connection lost');
      if (kind === 'cancel' && polls === 1) await nodes.cancel.onclick();
      return { ok: true, async json() { return { state: kind === 'cancel' ? 'cancelled' : 'complete',
        message: kind === 'cancel' ? 'Comparison cancelled.' : 'Done', elapsed_seconds: 1, result: {} }; } };
    }
  };
  vm.runInNewContext(fs.readFileSync('static/comparison-jobs.js', 'utf8'), sandbox);
  await Promise.all([nodes.analyse.onclick(), nodes.analyse.onclick()]);
  assert.equal(nodes.analyse.disabled, false);
  assert.equal(nodes.candidate.disabled, false);
  assert.equal(nodes.cancel.hidden, true);
  assert.ok(calls.filter(x => x[1] === 'POST').length <= 1, 'double click must not submit twice');
  if (kind === 'success') assert.equal(displayed, true);
  else assert.equal(displayed, false);
  if (kind === 'cancel' || kind === 'poll-error') assert.ok(calls.some(x => x[1] === 'DELETE'));
  if (kind === 'reference-error') assert.equal(calls.some(x => x[1] === 'POST'), false);
}

(async () => {
  for (const kind of ['success', 'cancel', 'poll-error', 'reference-error']) await scenario(kind);
  console.log('4 comparison UI scenarios passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
