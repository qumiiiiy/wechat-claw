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
    anchor('/mooc2-ans/mycourse/studentstudy?courseid=1001&clazzid=2002&chapterId=3003', '第一章'),
    anchor('/mooc2-ans/mycourse/studentstudy?courseid=9999&clazzid=2002&chapterId=3004', '其他课程'),
    anchor('/mooc2-ans/mycourse/studentcourse?courseid=1001&clazzid=2002', '课程目录'),
    anchor('javascript:void(0)', '无效链接')
];
const context = {
    URL,
    window: {
        location: {
            href: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
                + '?courseid=1001&clazzid=2002&cpi=4004',
            pathname: '/mooc2-ans/mycourse/studentcourse',
            search: '?courseid=1001&clazzid=2002&cpi=4004'
        }
    },
    document: {
        querySelectorAll: () => anchors
    },
    boundedText: (value, maxCharacters) => String(value || '').trim().slice(0, maxCharacters),
    isChaoxingHost: (hostname) => hostname === 'chaoxing.com'
        || hostname.endsWith('.chaoxing.com')
};
vm.createContext(context);
vm.runInContext([
    declaration('findChaoxingLessonLinks', 'safeUrl'),
    declaration('safeUrl', 'matchesCourseIdentity'),
    declaration('matchesCourseIdentity', 'queryValue'),
    declaration('queryValue', 'looksLikeChaoxingLessonUrl'),
    declaration('looksLikeChaoxingLessonUrl', 'findChaoxingCourseLinks'),
    declaration('isChaoxingCourseDirectoryPage', 'isEmbeddedLearningPage'),
    'this.findLessons = findChaoxingLessonLinks;',
    'this.isDirectory = isChaoxingCourseDirectoryPage;'
].join('\n'), context);

const matches = context.findLessons({
    targetCourseUrl: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
        + '?courseid=1001&clazzid=2002&cpi=4004'
});
assert.equal(matches.length, 1);
assert.equal(matches[0].title, '第一章');
assert.equal(matches[0].url.searchParams.get('chapterId'), '3003');
assert.equal(context.isDirectory(), true);

context.window.location.pathname = '/mooc2-ans/mycourse/studentstudy';
context.window.location.search = '?courseid=1001&clazzid=2002&chapterId=3003';
assert.equal(context.isDirectory(), false);

function anchor(href, textContent) {
    return {
        textContent,
        getAttribute: (name) => name === 'href' ? href : ''
    };
}

console.log('OCS chapter navigation tests passed');
