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

function rowElement(className, onclick, name, count, parent) {
    const nameEl = {
        textContent: name,
        className: 'posCatalog_name',
        parentElement: null,
        clickCalls: 0,
        click() {
            this.clickCalls++;
        },
        querySelector: () => null,
        getAttribute: (attr) => attr === 'href' ? '' : null
    };
    const countEl = count > 0 ? { value: String(count) } : null;
    const element = {
        className,
        textContent: name,
        parentElement: parent || null,
        clickCalls: 0,
        click() {
            this.clickCalls++;
        },
        querySelector: (selector) => {
            if (selector === '.posCatalog_name') return nameEl;
            if (selector === '.jobUnfinishCount') return countEl;
            return null;
        },
        getAttribute: (attr) => attr === 'onclick' ? onclick : ''
    };
    return { element, nameEl };
}

const chapterBlock = { className: 'posCatalog', parentElement: null, querySelector: () => null };
const headerRows = [
    rowElement('posCatalog_click', "getTeacherAjax('c1','k1','100')", '第一章 绪论', 0, chapterBlock),
    rowElement('posCatalog_click', "getTeacherAjax('c1','k1','110')", '第二章 数据链路层', 0, chapterBlock)
];
const selectList = { className: 'posCatalog_select', parentElement: chapterBlock, querySelector: () => null };
const jobRows = [
    rowElement('posCatalog_click', "getTeacherAjax('c1','k1','101')", '1.1 课程介绍', 1, selectList),
    rowElement('posCatalog_click', "getTeacherAjax('c1','k1','102')", '1.2 视频任务', 0, selectList)
];

const sessionState = {};
const calls = {
    getTeacherAjax: [],
    lessonChanged: []
};
const context = {
    window: {
        location: {
            href: 'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse'
                + '?courseid=1001&clazzid=2002&cpi=4004',
            hostname: 'mooc2-ans.chaoxing.com',
            pathname: '/mooc2-ans/mycourse/studentcourse',
            search: '?courseid=1001&clazzid=2002&cpi=4004'
        },
        getTeacherAjax: undefined
    },
    document: {
        querySelectorAll: (selector) => {
            if (selector === 'iframe') return [];
            if (selector.indexOf('getTeacherAjax') >= 0) {
                return headerRows.map((row) => row.element)
                    .concat(jobRows.map((row) => row.element));
            }
            if (selector === '.posCatalog_click, .posCatalog') {
                return [chapterBlock]
                    .concat(headerRows.map((row) => row.element));
            }
            return [];
        },
        querySelector: () => null
    },
    getActiveSession: () => sessionState.active || null,
    setActiveSession: (session) => {
        sessionState.active = session;
    },
    updateControlPanel: () => {},
    reportEvent: (sessionId, eventType, details) => {
        if (eventType === 'LESSON_CHANGED') {
            calls.lessonChanged.push(details);
        }
    },
    normalizeText: (value) => String(value || '').replace(/\s+/g, '').toLowerCase(),
    currentLessonTitle: () => '1.1 课程介绍',
    setTimeout: () => {},
    safeUrl: (value) => {
        try {
            return new URL(String(value || ''), 'https://mooc2-ans.chaoxing.com/');
        } catch (error) {
            return null;
        }
    },
    looksLikeChaoxingLessonUrl: () => false
};
vm.createContext(context);
vm.runInContext([
    declaration('parseChapterRows', 'expandChapterHeader'),
    declaration('expandChapterHeader', 'selectChapter'),
    declaration('selectChapter', 'resolveTeacherAjax'),
    declaration('resolveTeacherAjax', 'chapterText'),
    declaration('chapterText', 'normalizeText'),
    'this.parseRows = parseChapterRows;',
    'this.pickRow = selectChapter;',
    'this.expand = expandChapterHeader;'
].join('\n'), context);

const rows = context.parseRows();
assert.equal(rows.length, 4);
const jobs = rows.filter((row) => row.isJob);
const headers = rows.filter((row) => !row.isJob);
assert.equal(jobs.length, 2, 'task rows must be recognized as job rows');
assert.equal(headers.length, 2, 'chapter headers must not be treated as job rows');
assert.equal(jobs[0].chapterId, '101');
assert.equal(jobs[0].unFinishCount, 1);

// No global getTeacherAjax: must click the first unfinished job row, never a header.
context.pickRow({ sessionId: 'OS-1', chapterName: '' }, jobs);
assert.equal(jobRows[0].element.clickCalls, 1, 'the row carrying onclick must be clicked');
assert.equal(headerRows[0].nameEl.clickCalls, 0, 'chapter header must not be clicked');

// Explicit chapter name: must pick the matching job row.
context.pickRow({ sessionId: 'OS-1', chapterName: '1.2 视频任务' }, jobs);
assert.equal(jobRows[1].element.clickCalls, 1, 'chapterName match must click the matching job row');

// Global getTeacherAjax: must call it with parsed identifiers instead of clicking.
context.window.getTeacherAjax = (courseId, classId, chapterId) => {
    calls.getTeacherAjax.push({ courseId, classId, chapterId });
};
context.pickRow({ sessionId: 'OS-1', chapterName: '' }, jobs);
assert.equal(calls.getTeacherAjax.length, 1);
assert.equal(calls.getTeacherAjax[0].courseId, 'c1');
assert.equal(calls.getTeacherAjax[0].classId, 'k1');
assert.equal(calls.getTeacherAjax[0].chapterId, '101');

// Tampermonkey isolates page globals: unsafeWindow must take precedence.
context.window.getTeacherAjax = undefined;
context.unsafeWindow = {
    getTeacherAjax: (courseId, classId, chapterId) => {
        calls.getTeacherAjax.push({ courseId, classId, chapterId });
    }
};
context.pickRow({ sessionId: 'OS-1', chapterName: '' }, jobs);
assert.equal(calls.getTeacherAjax.length, 2);
assert.equal(calls.getTeacherAjax[1].chapterId, '101');

console.log('OCS catalog row selection tests passed');
