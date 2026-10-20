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

assert.doesNotMatch(source, /if \(window\.top !== window\.self\)\s*\{\s*return;/);
assert.match(source, /if \(!IS_TOP_FRAME\)[\s\S]*resumeEmbeddedSession\(activeSession\)/);

const listeners = new Map();
let playCalls = 0;
const video = {
    currentTime: 30,
    duration: 120,
    paused: true,
    ended: false,
    readyState: 1,
    textTracks: [],
    addEventListener: (name, listener) => listeners.set(name, listener),
    play: () => {
        playCalls++;
        return Promise.resolve();
    }
};
const events = [];
const context = {
    URL,
    Date,
    WeakSet,
    PROGRESS_REPORT_MILLIS: 15000,
    attachedVideos: new WeakSet(),
    document: {
        title: '合成视频页',
        querySelectorAll: (selector) => selector === 'video' ? [video] : [],
        querySelector: (selector) => selector === 'video' ? video : null
    },
    window: {
        location: {
            href: 'https://mooc1.chaoxing.com/ananas/modules/video/index.html',
            hostname: 'mooc1.chaoxing.com',
            pathname: '/ananas/modules/video/index.html',
            search: ''
        }
    },
    reportEvent: (eventType, details) => events.push({ eventType, details }),
    currentLessonTitle: () => '第一章 第一节',
    attachTextTrack: () => {},
    isLoopbackHost: (hostname) => ['127.0.0.1', 'localhost'].includes(hostname)
};
vm.createContext(context);
vm.runInContext([
    declaration('attachVideos', 'reportVideoProgress'),
    declaration('reportVideoProgress', 'startReviewPlayback'),
    declaration('startReviewPlayback', 'attachTextTrack'),
    declaration('percentage', 'currentLessonTitle'),
    declaration('isEmbeddedLearningPage', 'answerResultMessage'),
    declaration('isActiveSessionPage', 'isChaoxingLoginPage'),
    declaration('isChaoxingHost', 'request'),
    'this.attach = attachVideos;',
    'this.percentage = percentage;',
    'this.startPlayback = startReviewPlayback;',
    'this.isEmbedded = isEmbeddedLearningPage;',
    'this.isSessionPage = isActiveSessionPage;'
].join('\n'), context);

assert.equal(context.isEmbedded(), true, 'legacy video iframe must be recognized');
assert.equal(context.percentage(0, 3600), 0);
assert.equal(context.percentage(1, 3600), 1, 'started long video must not remain at zero');
assert.equal(context.percentage(3599, 3600), 99);
assert.equal(context.percentage(3600, 3600), 100);
assert.equal(context.isSessionPage({ platform: 'chaoxing' }), true);
context.window.location.hostname = 'untrusted.example';
assert.equal(
    context.isSessionPage({ platform: 'chaoxing' }),
    false,
    'untrusted HTTPS frame must not report study events');
context.window.location.hostname = 'mooc1.chaoxing.com';
context.attach();
assert.equal(playCalls, 1, 'completed task state must not block review playback');

listeners.get('play')();
assert.ok(events.some((event) => event.eventType === 'LESSON_CHANGED'
    && event.details.lessonTitle === '第一章 第一节'));
assert.ok(events.some((event) => event.eventType === 'PROGRESS'
    && event.details.progressPercent === 25));

video.duration = Number.NaN;
const progressCount = events.filter((event) => event.eventType === 'PROGRESS').length;
listeners.get('timeupdate')();
assert.equal(
    events.filter((event) => event.eventType === 'PROGRESS').length,
    progressCount,
    'unknown duration must not overwrite real progress with zero');

let completedPlayCalls = 0;
const completedVideo = {
    currentTime: 120,
    duration: 120,
    paused: true,
    ended: true,
    play: () => {
        completedPlayCalls++;
        return Promise.resolve();
    }
};
context.startPlayback(completedVideo);
assert.equal(completedVideo.currentTime, 0, 'completed video must restart for review');
assert.equal(completedPlayCalls, 1);

console.log('OCS legacy frame video tests passed');
