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

const constants = source.match(
    /const MAX_COURSE_JSON_CHARACTERS = \d+;\s+const MAX_COURSE_JSON_ITEMS = \d+;/);
assert.ok(constants, 'missing JSON bounds');
const context = {
    URL,
    document: {
        body: { textContent: '' },
        querySelectorAll: () => []
    }
};
vm.createContext(context);
vm.runInContext([
    constants[0],
    declaration('findChaoxingCourseLinks', 'findChaoxingJsonCourses'),
    declaration('findChaoxingJsonCourses', 'boundedText'),
    declaration('boundedText', 'numericIdentifier'),
    declaration('numericIdentifier', 'chaoxingCourseKey'),
    declaration('chaoxingCourseKey', 'normalizeCourseName'),
    declaration('normalizeCourseName', 'isCourseNameMatch'),
    declaration('isCourseNameMatch', 'looksLikeChaoxingCourseUrl'),
    'this.findCourses = findChaoxingCourseLinks;'
].join('\n'), context);

context.document.body.textContent = JSON.stringify({
    result: 1,
    channelList: [{
        key: 2002,
        cpi: 3003,
        content: {
            id: 2002,
            name: '合成课程-0001',
            course: { data: [{ id: 1001, name: '合成课程' }] }
        }
    }]
});
const matches = context.findCourses('合成课程');
assert.equal(matches.length, 1);
assert.equal(matches[0].name, '合成课程-0001');
assert.equal(matches[0].url.hostname, 'mooc2-ans.chaoxing.com');
assert.equal(matches[0].url.searchParams.get('courseid'), '1001');
assert.equal(matches[0].url.searchParams.get('clazzid'), '2002');
assert.equal(matches[0].url.searchParams.get('cpi'), '3003');

context.document.body.textContent = JSON.stringify({
    result: 1,
    channelList: Array.from({ length: 17 }, (_, index) => ({
        key: index === 0 ? '../invalid' : 2000 + index,
        cpi: 3003,
        content: {
            id: 2000 + index,
            name: index === 8 || index === 16 ? '同名合成班级' : `合成班级-${index + 1}`,
            course: {
                data: [{
                    id: 1000 + index,
                    name: index === 8 || index === 16 ? '同名合成课程' : `合成课程-${index + 1}`
                }]
            }
        }
    }))
});
assert.equal(context.findCourses('同名合成课程').length, 2);

context.document.body.textContent = JSON.stringify({
    result: 1,
    channelList: [{
        key: 4101,
        cpi: 5101,
        content: {
            name: '',
            course: { data: [{ id: 3101, name: '【大学体育－合成项目】' }] }
        }
    }, {
        key: 4102,
        cpi: 5102,
        content: {
            name: '2026级（大学体育—合成班）',
            course: { data: [{ id: 3102, name: '' }] }
        }
    }]
});
const courseNameOnly = context.findCourses('大学体育-合成项目');
assert.equal(courseNameOnly.length, 1);
assert.equal(courseNameOnly[0].url.searchParams.get('courseid'), '3101');
const classNameOnly = context.findCourses('大学体育合成班');
assert.equal(classNameOnly.length, 1);
assert.equal(classNameOnly[0].url.searchParams.get('courseid'), '3102');

context.document.body.textContent = '{invalid';
assert.deepEqual(Array.from(context.findCourses('合成课程')), []);
context.document.body.textContent = JSON.stringify({ result: 0, channelList: [] });
assert.deepEqual(Array.from(context.findCourses('合成课程')), []);
context.document.body.textContent = JSON.stringify({
    result: 1,
    channelList: [{
        key: '../unsafe',
        cpi: 3003,
        content: {
            name: '合成课程',
            course: { data: [{ id: 1001, name: '合成课程' }] }
        }
    }]
});
assert.deepEqual(Array.from(context.findCourses('合成课程')), []);
context.document.body.textContent = JSON.stringify({
    result: 1,
    channelList: [{
        key: 2002,
        cpi: 3003,
        content: {
            name: '',
            course: { data: [{ id: 1001, name: '' }] }
        }
    }]
});
assert.deepEqual(Array.from(context.findCourses('不存在的课程')), []);

console.log('OCS course JSON adapter tests passed');
