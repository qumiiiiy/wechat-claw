const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const sourcePath = process.argv[2];
if (!sourcePath) {
    throw new Error('userscript path is required');
}
const source = fs.readFileSync(sourcePath, 'utf8');

function declaration(name, nextName) {
    const start = source.indexOf(`function ${name}(`);
    assert.notEqual(start, -1, `missing function ${name}`);
    const end = source.indexOf(`function ${nextName}(`, start);
    assert.notEqual(end, -1, `missing function ${nextName}`);
    return source.slice(start, end).trim();
}

const anchors = [
    {
        getAttribute: () => 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu'
            + '?courseid=260875267&clazzid=140208372&cpi=405389055'
            + '&enc=a45c62790d497598ab07d7f526dbefef&pageHeader=1'
    },
    {
        getAttribute: () => 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
            + '?courseid=999999999&clazzid=888888888&cpi=777777777'
    },
    {
        getAttribute: () => 'javascript:void(0)'
    }
];
const context = {
    URL,
    window: {
        location: {
            href: 'https://mooc1.chaoxing.com/mycourse',
            hostname: 'mooc1.chaoxing.com'
        }
    },
    document: {
        querySelectorAll: (selector) => selector === 'iframe' ? [] : anchors,
        querySelector: () => null
    },
    safeUrl: (value) => {
        try {
            return new URL(String(value || ''), 'https://mooc1.chaoxing.com/mycourse');
        } catch (error) {
            return null;
        }
    },
    queryValue: (url, firstName, secondName) => url.searchParams.get(firstName)
        || url.searchParams.get(secondName)
        || '',
    looksLikeChaoxingCourseUrl: (url) => /mycourse|studentcourse|studentstudy|courseid|clazzid|mooc-ans|mooc2-ans/i
        .test(url.pathname + '?' + url.searchParams.toString()),
    isChaoxingHost: (hostname) => hostname === 'chaoxing.com'
        || hostname.endsWith('.chaoxing.com')
};
vm.createContext(context);
vm.runInContext([
    declaration('findCourseEntryLink', 'enterCourseCard'),
    'this.findEntry = findCourseEntryLink;'
].join('\n'), context);

const session = {
    courseUrl: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
        + '?courseid=260875267&clazzid=140208372&cpi=405389055&ut=s'
};
const entry = context.findEntry(session);
assert.ok(entry, 'course entry link must be found by course id');
assert.equal(entry.url.searchParams.get('courseid'), '260875267');
assert.equal(entry.url.searchParams.get('enc'), 'a45c62790d497598ab07d7f526dbefef');

assert.equal(
    context.findEntry({ courseUrl: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
        + '?courseid=111111111&clazzid=222222222&cpi=405389055&ut=s' }),
    null,
    'unrelated course id must not match');

console.log('OCS course entry link tests passed');
