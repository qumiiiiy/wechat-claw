const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const sourcePath = process.argv[2];
if (!sourcePath) {
    throw new Error('launch page path is required');
}
const html = fs.readFileSync(sourcePath, 'utf8');
const scripts = Array.from(html.matchAll(/<script>([\s\S]*?)<\/script>/g));
assert.equal(scripts.length, 1, 'launch page must contain one inline script');

const elements = {
    'ocs-install-help': { hidden: true },
    'ocs-install-title': { textContent: '' },
    'ocs-launch-status': { textContent: '' }
};
let readyHandler;
let timeoutHandler;
const context = {
    document: {
        documentElement: { dataset: {} },
        getElementById: (id) => elements[id]
    },
    window: {
        addEventListener: (name, handler) => {
            if (name === 'wechat-claw-ocs-companion-ready') {
                readyHandler = handler;
            }
        },
        setTimeout: (handler) => {
            timeoutHandler = handler;
        }
    }
};
vm.createContext(context);
vm.runInContext(scripts[0][1], context);
assert.equal(typeof readyHandler, 'function');
assert.equal(typeof timeoutHandler, 'function');

readyHandler({ detail: { version: '0.6.0' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.1' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.2' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.3' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.4' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.5' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.6' } });
assert.equal(context.document.documentElement.dataset.companion, 'outdated');
assert.match(elements['ocs-launch-status'].textContent, /请更新到 0\.6\.7/);
assert.equal(elements['ocs-install-help'].hidden, false);

readyHandler({ detail: { version: '0.6.7' } });
assert.equal(context.document.documentElement.dataset.companion, 'ready');
assert.match(elements['ocs-launch-status'].textContent, /0\.6\.7 已连接/);
assert.equal(elements['ocs-install-help'].hidden, true);

console.log('OCS launch version handshake tests passed');
