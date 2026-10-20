const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const sourcePath = process.argv[2];
if (!sourcePath) {
    throw new Error('userscript path is required');
}
const source = fs.readFileSync(sourcePath, 'utf8');
const start = source.indexOf('function isChaoxingLoginPage(');
const end = source.indexOf('function isChaoxingHost(', start);
assert.notEqual(start, -1, 'missing isChaoxingLoginPage');
assert.notEqual(end, -1, 'missing isChaoxingHost boundary');

let selectorMatch = false;
const context = {
    window: {
        location: {
            hostname: 'passport2.chaoxing.com',
            pathname: '/login'
        }
    },
    document: {
        querySelector: () => selectorMatch ? {} : null
    }
};
vm.createContext(context);
vm.runInContext(source.slice(start, end) + '\nthis.detectLogin = isChaoxingLoginPage;', context);

assert.equal(context.detectLogin(), true, 'passport host must wait for login');

context.window.location.hostname = 'mooc1.chaoxing.com';
context.window.location.pathname = '/mycourse';
selectorMatch = true;
assert.equal(context.detectLogin(), true, 'login form on course host must wait for login');

selectorMatch = false;
assert.equal(context.detectLogin(), false, 'authenticated course page must continue discovery');

console.log('OCS login-page adapter tests passed');
