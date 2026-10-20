const assert = require('node:assert/strict');
const fs = require('node:fs');

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
    return source.slice(start, end);
}

const resolvedNavigation = declaration(
    'openResolvedChaoxingCourse',
    'openMatchingChaoxingCourse');
const matchedNavigation = declaration(
    'openMatchingChaoxingCourse',
    'findChaoxingCourseLinks');

for (const navigation of [resolvedNavigation, matchedNavigation]) {
    assert.match(navigation, /window\.location\.assign\(/);
    assert.doesNotMatch(navigation, /reportEvent\('LESSON_CHANGED'/);
}

console.log('OCS navigation evidence tests passed');
