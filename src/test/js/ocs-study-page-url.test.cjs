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

let encLinks = true;
const context = {
    URL,
    Date,
    window: {
        location: {
            href: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
                + '?courseid=1001&clazzid=2002&cpi=4004&ut=s',
            hostname: 'mooc2-ans.chaoxing.com',
            origin: 'https://mooc2-ans.chaoxing.com'
        }
    },
    document: {
        querySelectorAll: (selector) => {
            if (selector === 'iframe') return [];
            if (encLinks) {
                return [{
                    getAttribute: () => 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu'
                        + '?courseid=1001&clazzid=2002&cpi=4004'
                        + '&enc=a45c62790d497598ab07d7f526dbefef&pageHeader=1'
                }];
            }
            return [];
        },
        querySelector: () => null
    },
    safeUrl: (value) => new URL(
        String(value || ''),
        'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'),
    queryValue: (url, firstName, secondName) => url.searchParams.get(firstName)
        || url.searchParams.get(secondName)
        || '',
    normalizeText: (value) => String(value || '').replace(/\s+/g, '').toLowerCase(),
    chapterText: (element) => String(element.textContent || '')
};
vm.createContext(context);
vm.runInContext([
    declaration('pickChapterRow', 'buildDirectoryPageUrl'),
    declaration('buildDirectoryPageUrl', 'findChaoxingLessonLinks'),
    'this.pick = pickChapterRow;',
    'this.build = buildDirectoryPageUrl;',
    'this.findEnc = findPageEnc;',
    'this.enrich = enrichChaoxingUrl;',
    'this.courseList = buildCourseListUrl;'
].join('\n'), context);

const rows = [
    { element: { textContent: '第一章 绪论' }, isJob: false, chapterId: '100', unFinishCount: 2 },
    { element: { textContent: '1.1 课程介绍' }, isJob: true, chapterId: '101', unFinishCount: 1 },
    { element: { textContent: '1.2 视频任务' }, isJob: true, chapterId: '102', unFinishCount: 0 }
];

assert.equal(context.pick({ chapterName: '' }, rows).chapterId, '101',
    'first unfinished job row must win');
assert.equal(context.pick({ chapterName: '1.2 视频任务' }, rows).chapterId, '102',
    'explicit chapterName must win');
assert.equal(context.pick({ chapterName: '' }, [rows[0]]).chapterId, '100',
    'header row must be usable when no job rows exist');

const url = context.build();
assert.equal(url.hostname, 'mooc2-ans.chaoxing.com');
assert.equal(url.pathname, '/mooc2-ans/mycourse/stu');
assert.equal(url.searchParams.get('courseid'), '1001');
assert.equal(url.searchParams.get('clazzid'), '2002');
assert.equal(url.searchParams.get('cpi'), '4004');
assert.equal(url.searchParams.get('enc'), 'a45c62790d497598ab07d7f526dbefef');
assert.equal(url.searchParams.get('pageHeader'), '1');
assert.equal(url.searchParams.get('v'), '2');
assert.equal(url.searchParams.get('hideHead'), '0');
assert.ok(url.searchParams.get('t'), 'timestamp must be present');

const courseList = context.courseList();
assert.equal(courseList.hostname, 'mooc1.chaoxing.com');
assert.equal(courseList.pathname, '/mycourse');

encLinks = false;
assert.equal(context.build(), null, 'directory URL must not be built without enc');

encLinks = true;
assert.equal(context.findEnc(), 'a45c62790d497598ab07d7f526dbefef',
    'page links must remain the primary enc source');

const lessonUrl = new URL('https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentstudy'
    + '?courseid=1001&clazzid=2002&chapterId=3003');
const enriched = context.enrich(lessonUrl, {
    targetCourseUrl: '',
    courseUrl: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
        + '?courseid=1001&clazzid=2002&cpi=4004&ut=s'
});
assert.ok(enriched, 'lesson URL must be completed before navigation');
assert.equal(enriched.searchParams.get('cpi'), '4004');
assert.equal(enriched.searchParams.get('enc'), 'a45c62790d497598ab07d7f526dbefef');
assert.equal(enriched.searchParams.get('chapterId'), '3003');

console.log('OCS study page URL tests passed');
