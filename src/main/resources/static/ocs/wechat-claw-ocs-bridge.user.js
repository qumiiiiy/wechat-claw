// ==UserScript==
// @name         WeChat Claw OCS Bridge
// @namespace    https://github.com/wechat-claw
// @version      0.17.2
// @description  Thin bridge between WeChat Claw and the OCS plugin for Chaoxing study sessions.
// @match        http://*/*
// @match        https://*/*
// @run-at       document-idle
// @grant        GM_xmlhttpRequest
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_deleteValue
// @grant        GM_registerMenuCommand
// @grant        GM_getTab
// @grant        GM_saveTab
// @grant        unsafeWindow
// @connect      127.0.0.1
// @connect      localhost
// ==/UserScript==

(function () {
    'use strict';

    var DEFAULT_BRIDGE_URL = 'http://127.0.0.1:8080/api/local/ocs';
    var COMPANION_VERSION = '0.17.2';
    var CLIENT_HEADER = 'X-Wechat-Claw-Ocs-Client';
    var CLIENT_MARKER = 'companion-v1';
    var MAX_EVENT_CHARACTERS = 4000;
    var COURSE_DISCOVERY_ATTEMPTS = 10;
    var LESSON_DISCOVERY_ATTEMPTS = 8;
    const MAX_COURSE_JSON_CHARACTERS = 200000;
    const MAX_COURSE_JSON_ITEMS = 50;
    var IS_TOP_FRAME = window.top === window.self;
    var LOGIN_POLL_INTERVAL_MS = 3000;
    var LOGIN_POLL_MAX_ATTEMPTS = 60;
    var initialized = false;
    var tabState = null;
    var savedOcsConfig = null;

    // Register menu commands immediately
    if (IS_TOP_FRAME) {
        GM_registerMenuCommand('\u914D\u7F6E WeChat Claw \u6865\u63A5\u5730\u5740', configureBridgeUrl);
        GM_registerMenuCommand('\u6807\u8BB0\u5F53\u524D\u5B66\u4E60\u4F1A\u8BDD\u5B8C\u6210', finishActiveSession);
        GM_registerMenuCommand('\u6E05\u9664\u5F53\u524D\u5B66\u4E60\u4F1A\u8BDD', clearActiveSession);
    }

    // Try GM_getTab with timeout fallback
    try {
        GM_getTab(function (saved) {
            if (initialized) return;
            initialized = true;
            tabState = saved || {};
            onReady();
        });
    } catch (e) {
        initialized = true;
        tabState = {};
        onReady();
    }
    // Fallback if GM_getTab never calls back
    window.setTimeout(function () {
        if (initialized) return;
        initialized = true;
        tabState = {};
        onReady();
    }, 3000);

    function onReady() {
        console.log('[WeChat Claw] companion v' + COMPANION_VERSION + ' loaded on ' + window.location.hostname);
        if (!IS_TOP_FRAME) return;

        if (isChaoxingHost(window.location.hostname)) {
            var activeSession = getActiveSession();
            if (activeSession && isActiveSessionPage(activeSession)) {
                resumeActiveSession(activeSession);
            } else {
                showCompanionStatus('\u6B63\u5728\u67E5\u627E\u5B66\u4E60\u4F1A\u8BDD\u2026\u2026');
                findAndResumeSession();
            }
        }
    }

    async function findAndResumeSession() {
        try {
            var url = bridgeUrl() + '/sessions/find';
            console.log('[WeChat Claw] finding session at ' + url);
            var response = await request('POST', url,
                JSON.stringify({ hostHint: window.location.hostname }));
            console.log('[WeChat Claw] find session response: ' + response.status);
            if (response.status === 200 && response.responseText) {
                var session = JSON.parse(response.responseText);
                console.log('[WeChat Claw] found session: ' + session.sessionId);
                setActiveSession({
                    sessionId: session.sessionId,
                    courseName: session.courseName,
                    courseReference: session.courseReference || '',
                    chapterName: session.chapterName || '',
                    courseUrl: window.location.href,
                    targetCourseUrl: '',
                    courseHost: window.location.hostname,
                    platform: 'chaoxing',
                    courseSelected: true,
                    chapterSelected: false
                });
                showCompanionStatus('\u5DF2\u627E\u5230\u4F1A\u8BDD ' + session.sessionId + ' \uFF0C\u6B63\u5728\u8FDE\u63A5\u2026\u2026');
                await resumeActiveSession(getActiveSession());
            } else if (response.status === 204) {
                showCompanionStatus('\u672A\u627E\u5230\u5F85\u5904\u7406\u7684\u5B66\u4E60\u4F1A\u8BDD\uFF0C\u8BF7\u5148\u4ECE\u5FAE\u4FE1\u53D1\u8D77 OCS \u5B66\u4E60\u3002');
            } else {
                showCompanionStatus('\u8FDE\u63A5\u672C\u673A Bot \u5931\u8D25\uFF08HTTP ' + response.status + '\uFF09');
            }
        } catch (error) {
            console.error('[WeChat Claw] find session error:', error);
            showCompanionStatus('\u65E0\u6CD5\u8FDE\u63A5\u672C\u673A Bot\uFF1A' + safeError(error)
                + '\u3002\u8BF7\u786E\u8BA4 Bot \u5E94\u7528\u6B63\u5728\u8FD0\u884C\uFF08http://127.0.0.1:8080\uFF09\u3002');
        }
    }

    function showCompanionStatus(message) {
        if (!IS_TOP_FRAME) return;
        if (!document.body) {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', function () { showCompanionStatus(message); });
            } else {
                window.setTimeout(function () { showCompanionStatus(message); }, 100);
            }
            return;
        }
        console.log('[WeChat Claw] ' + message);
        var panel = document.getElementById('wechat-claw-ocs-control');
        if (!panel) {
            panel = document.createElement('aside');
            panel.id = 'wechat-claw-ocs-control';
            panel.style.cssText = 'position:fixed;right:18px;bottom:18px;z-index:2147483647;width:min(340px,calc(100vw - 36px));box-sizing:border-box;padding:16px;border:1px solid rgba(37,99,235,.25);border-radius:14px;background:#fff;box-shadow:0 16px 48px rgba(15,23,42,.22);color:#172033;font:14px/1.55 "Microsoft YaHei UI","PingFang SC",sans-serif';
            var title = document.createElement('strong');
            title.textContent = 'WeChat Claw \u5B66\u4E60\u63A7\u5236\u53F0';
            title.style.cssText = 'display:block;font-size:15px;margin-bottom:6px';
            panel.appendChild(title);
            var status = document.createElement('div');
            status.dataset.field = 'status';
            status.style.cssText = 'font-weight:600;margin-bottom:12px';
            panel.appendChild(status);
            document.body.appendChild(panel);
        }
        var statusEl = panel.querySelector('[data-field="status"]');
        if (statusEl) statusEl.textContent = message;
    }

    async function resumeActiveSession(session) {
        showCompanionStatus('\u6B63\u5728\u8FDE\u63A5\u672C\u673A Bot');
        var loginPage = isChaoxingLoginPage();
        var ocsInfo = loginPage
            ? { detected: false, version: '' }
            : await waitForOcsPlugin(15000);
        var config = await reportCompanionReady(session, ocsInfo.version);
        if (loginPage) {
            showCompanionStatus('\u68C0\u6D4B\u5230\u767B\u5F55\u9875\uFF0C\u6B63\u5728\u622A\u53D6\u4E8C\u7EF4\u7801\u2026\u2026');
            await handleLoginQrRelay(session);
            return;
        }
        if (!ocsInfo.detected) {
            showCompanionStatus('\u672A\u68C0\u6D4B\u5230 OCS \u63D2\u4EF6\uFF0C\u4ECD\u5C1D\u8BD5\u8FDB\u5165\u7AE0\u8282', 'waiting');
            await reportPluginMissing(
                session.sessionId,
                '\u6D4F\u89C8\u5668\u4E2D\u672A\u5B89\u88C5 OCS\uFF08\u5DF2\u7EE7\u7EED\u7AE0\u8282\u5BFC\u822A\uFF09');
        }
        if (config) {
            await injectAndVerifyConfig(session, config);
        }
        observeOcsState(session);
        selectChapterIfNeeded(session);
    }

    function detectOcsPlugin() {
        try {
            // Check for OCS UI elements
            var selectors = [
                '.ocs-container', '.ocs-panel', '.ocs-work-container',
                '[class*="ocs-"]', '[class*="OCS"]', '[id*="ocs"]',
                '.cx-container', '.zhs-container',
                '.app-container', '.ocs-app'
            ];
            for (var i = 0; i < selectors.length; i++) {
                var el = document.querySelector(selectors[i]);
                if (el) {
                    console.log('[WeChat Claw] OCS detected via ' + selectors[i]);
                    return { detected: true, version: el.dataset ? el.dataset.ocsVersion || '' : '' };
                }
            }
            // Check for OCS global variables
            try {
                if (typeof unsafeWindow !== 'undefined') {
                    if (unsafeWindow.OCS || unsafeWindow.ocsLoaded
                            || unsafeWindow.__OCS__ || unsafeWindow.ocs) {
                        console.log('[WeChat Claw] OCS detected via unsafeWindow');
                        return { detected: true, version: '' };
                    }
                }
            } catch (e2) {}
            // Check for OCS-style elements in body
            var bodyText = document.body ? document.body.className : '';
            if (bodyText && bodyText.indexOf('ocs') >= 0) {
                console.log('[WeChat Claw] OCS detected via body class');
                return { detected: true, version: '' };
            }
            // Check for any element with OCS-related data attributes
            var allEls = document.querySelectorAll('[data-ocs], [data-script]');
            if (allEls.length > 0) {
                console.log('[WeChat Claw] OCS detected via data attribute');
                return { detected: true, version: '' };
            }
        } catch (e) { console.debug('[WeChat Claw] detect error', e); }
        console.log('[WeChat Claw] OCS not detected');
        return { detected: false, version: '' };
    }

    async function waitForOcsPlugin(maxWaitMs) {
        maxWaitMs = maxWaitMs || 10000;
        var interval = 1000;
        var waited = 0;
        while (waited < maxWaitMs) {
            var info = detectOcsPlugin();
            if (info.detected) return info;
            await sleep(interval);
            waited += interval;
        }
        return { detected: false, version: '' };
    }

    async function reportCompanionReady(session, pluginVersion) {
        try {
            var response = await request('POST',
                bridgeUrl() + '/sessions/' + encodeURIComponent(session.sessionId) + '/companion/ready',
                JSON.stringify({ companionVersion: COMPANION_VERSION, pluginVersion: pluginVersion, pageUrl: window.location.href }));
            if (response.status === 200 && response.responseText) {
                return JSON.parse(response.responseText);
            }
        } catch (e) { console.debug('[WeChat Claw] companion ready failed', safeError(e)); }
        return null;
    }

    async function injectAndVerifyConfig(session, config) {
        savedOcsConfig = snapshotOcsConfig();
        setActiveSession(Object.assign({}, session, { savedOcsConfig: savedOcsConfig }));
        injectOcsConfig(config);
        var verification = verifyOcsConfig(config);
        await reportPluginConfigApplied(session.sessionId, verification);
        updateControlPanel(session, verification.applied ? 'OCS \u914D\u7F6E\u5DF2\u751F\u6548' : 'OCS \u914D\u7F6E\u90E8\u5206\u7F3A\u5931');
    }

    function injectOcsConfig(config) {
        GM_setValue('cx.new.study.restudy', config.videoReview);
        GM_setValue('cx.new.study.mode', config.autoChapterSwitch ? 'next' : 'manually');
        GM_setValue('cx.new.study.enableChapterTest', config.answerEnabled);
        GM_setValue('cx.new.study.enableMedia', true);
        GM_setValue('cx.new.study.playbackRate', config.playbackRate || '1');
        if (config.questionBankUrl && config.questionBankEnabled) {
            GM_setValue('common.settings.answererWrappers', [{
                name: 'WeChat Claw AI', url: config.questionBankUrl,
                method: 'POST', type: 'http',
                headers: { 'Content-Type': 'application/json' }, data: '{{question}}'
            }]);
        }
        GM_setValue('common.settings.upload', 'save');
    }

    function snapshotOcsConfig() {
        return {
            restudy: GM_getValue('cx.new.study.restudy'),
            mode: GM_getValue('cx.new.study.mode'),
            enableChapterTest: GM_getValue('cx.new.study.enableChapterTest'),
            enableMedia: GM_getValue('cx.new.study.enableMedia'),
            playbackRate: GM_getValue('cx.new.study.playbackRate'),
            answererWrappers: GM_getValue('common.settings.answererWrappers'),
            upload: GM_getValue('common.settings.upload')
        };
    }

    function restoreOcsConfig(s) {
        if (!s) return;
        if (s.restudy !== undefined) GM_setValue('cx.new.study.restudy', s.restudy);
        if (s.mode !== undefined) GM_setValue('cx.new.study.mode', s.mode);
        if (s.enableChapterTest !== undefined) GM_setValue('cx.new.study.enableChapterTest', s.enableChapterTest);
        if (s.enableMedia !== undefined) GM_setValue('cx.new.study.enableMedia', s.enableMedia);
        if (s.playbackRate !== undefined) GM_setValue('cx.new.study.playbackRate', s.playbackRate);
        if (s.answererWrappers !== undefined) GM_setValue('common.settings.answererWrappers', s.answererWrappers);
        if (s.upload !== undefined) GM_setValue('common.settings.upload', s.upload);
    }

    function verifyOcsConfig(expected) {
        var missingKeys = [];
        if (GM_getValue('cx.new.study.restudy') !== expected.videoReview) missingKeys.push('cx.new.study.restudy');
        var expectedMode = expected.autoChapterSwitch ? 'next' : 'manually';
        if (GM_getValue('cx.new.study.mode') !== expectedMode) missingKeys.push('cx.new.study.mode');
        if (GM_getValue('cx.new.study.enableChapterTest') !== expected.answerEnabled) missingKeys.push('cx.new.study.enableChapterTest');
        return { applied: missingKeys.length === 0, missingKeys: missingKeys };
    }

    async function handleLoginQrRelay(session) {
        var qrDataUrl = extractQrFromLoginPage();
        if (!qrDataUrl) {
            updateControlPanel(session, '\u672A\u627E\u5230\u767B\u5F55\u4E8C\u7EF4\u7801', 'error');
            return;
        }
        try {
            var response = await request('POST',
                bridgeUrl() + '/sessions/' + encodeURIComponent(session.sessionId) + '/login/qr',
                JSON.stringify({ qrDataUrl: qrDataUrl, pageUrl: window.location.href }));
            if (response.status === 200) {
                updateControlPanel(session, '\u5DF2\u53D1\u9001\u767B\u5F55\u4E8C\u7EF4\u7801\u5230\u624B\u673A\uFF0C\u7B49\u5F85\u626B\u7801', 'waiting');
                await pollLoginCompletion(session);
            } else {
                updateControlPanel(session, '\u4E8C\u7EF4\u7801\u53D1\u9001\u5931\u8D25', 'error');
            }
        } catch (e) {
            updateControlPanel(session, '\u4E8C\u7EF4\u7801\u4E2D\u7EE7\u5931\u8D25', 'error');
        }
    }

    function extractQrFromLoginPage() {
        var selectors = ['#quickCode img', '.qr-code img', '.login-qrcode img', 'img[src*="qr"]', 'canvas.qr-code'];
        for (var i = 0; i < selectors.length; i++) {
            var el = document.querySelector(selectors[i]);
            if (!el) continue;
            if (el.tagName === 'CANVAS') {
                try { return el.toDataURL('image/png'); } catch (e) { continue; }
            }
            if (el.tagName === 'IMG' && el.src) {
                if (el.src.startsWith('data:image/')) return el.src;
                try {
                    var canvas = document.createElement('canvas');
                    canvas.width = el.naturalWidth || el.width;
                    canvas.height = el.naturalHeight || el.height;
                    if (canvas.width === 0 || canvas.height === 0) continue;
                    canvas.getContext('2d').drawImage(el, 0, 0);
                    return canvas.toDataURL('image/png');
                } catch (e) { continue; }
            }
        }
        return null;
    }

    async function pollLoginCompletion(session) {
        for (var i = 0; i < LOGIN_POLL_MAX_ATTEMPTS; i++) {
            await sleep(LOGIN_POLL_INTERVAL_MS);
            if (!isChaoxingLoginPage()) {
                try {
                    await request('POST',
                        bridgeUrl() + '/sessions/' + encodeURIComponent(session.sessionId) + '/login/confirmed', '{}');
                } catch (e) {}
                updateControlPanel(session, '\u767B\u5F55\u5DF2\u786E\u8BA4\uFF0C\u7EE7\u7EED\u5B66\u4E60');
                var active = getActiveSession();
                if (active) await resumeActiveSession(active);
                return;
            }
        }
        updateControlPanel(session, '\u767B\u5F55\u8D85\u65F6\uFF0C\u8BF7\u91CD\u65B0\u53D1\u8D77', 'error');
    }

    async function reportPluginMissing(sessionId, reason) {
        try {
            await request('POST', bridgeUrl() + '/sessions/' + encodeURIComponent(sessionId) + '/plugin/missing',
                JSON.stringify({ reason: reason }));
        } catch (e) {}
    }

    async function reportPluginConfigApplied(sessionId, verification) {
        try {
            await request('POST', bridgeUrl() + '/sessions/' + encodeURIComponent(sessionId) + '/config/applied',
                JSON.stringify(verification));
        } catch (e) {}
    }

    function observeOcsState(session) {
        window.addEventListener('wechat-claw-ocs-question', handleQuestionEvent);
        window.addEventListener('wechat-claw-ocs-answer-result', handleAnswerResultEvent);
        window.setInterval(function () {
            reportEvent(session.sessionId, 'HEARTBEAT', { lessonTitle: document.title });
        }, 30000);
    }

    async function openResolvedChaoxingCourse(session) {
        if (!session || !session.targetCourseUrl) return;
        var targetCourseUrl = safeUrl(session.targetCourseUrl);
        if (!targetCourseUrl || targetCourseUrl.protocol !== 'https:'
                || !isChaoxingHost(targetCourseUrl.hostname)) {
            updateControlPanel(session, '\u5DF2\u89E3\u6790\u7684\u8BFE\u7A0B\u5730\u5740\u65E0\u6548\uFF0C\u6539\u7528\u8BFE\u7A0B\u540D\u79F0\u67E5\u627E', 'error');
            openMatchingChaoxingCourse(session);
            return;
        }
        setActiveSession(Object.assign({}, session, {
            courseSelected: true,
            courseUrl: targetCourseUrl.href,
            courseHost: targetCourseUrl.hostname,
            chapterSelected: false
        }));
        updateControlPanel(session, '\u5DF2\u89E3\u6790\u8BFE\u7A0B\u6807\u8BC6\uFF0C\u6B63\u5728\u6253\u5F00\u8BFE\u7A0B');
        window.location.assign(targetCourseUrl.href);
    }

    async function openMatchingChaoxingCourse(session) {
        updateControlPanel(session, '\u6B63\u5728\u67E5\u627E\u8BFE\u7A0B');
        var expectedUrl = safeUrl(session.courseUrl || window.location.href);
        var expectedCourseId = expectedUrl
            ? queryValue(expectedUrl, 'courseid', 'courseId') : '';
        for (var attempt = 0; attempt < COURSE_DISCOVERY_ATTEMPTS; attempt++) {
            var entry = findCourseEntryLink(session);
            if (entry) {
                if (samePageUrl(entry.url)) {
                    setActiveSession(Object.assign({}, session, {
                        courseSelected: true,
                        courseUrl: entry.url.href,
                        courseHost: entry.url.hostname
                    }));
                    openFirstChaoxingLesson(getActiveSession());
                    return;
                }
                setActiveSession(Object.assign({}, session, {
                    courseSelected: true,
                    courseUrl: entry.url.href,
                    courseHost: entry.url.hostname
                }));
                updateControlPanel(session, '\u5DF2\u5B9A\u4F4D\u8BFE\u7A0B\uFF0C\u6B63\u5728\u6253\u5F00');
                window.location.assign(entry.url.href);
                return;
            }
            var card = findCourseCard(session.courseName, expectedCourseId);
            if (card) {
                enterCourseCard(card, session);
                return;
            }
            await sleep(1000);
        }
        var candidates = [];
        for (var attempt = 0; attempt < COURSE_DISCOVERY_ATTEMPTS; attempt++) {
            candidates = findChaoxingCourseLinks(session.courseName);
            if (candidates.length > 0) break;
            await sleep(1000);
        }
        if (candidates.length === 1) {
            var selected = candidates[0];
            if (samePageUrl(selected.url)) {
                setActiveSession(Object.assign({}, session, {
                    courseSelected: true,
                    courseUrl: selected.url.href,
                    courseHost: selected.url.hostname
                }));
                openFirstChaoxingLesson(getActiveSession());
                return;
            }
            setActiveSession(Object.assign({}, session, {
                courseSelected: true,
                courseUrl: selected.url.href,
                courseHost: selected.url.hostname,
                chapterSelected: false
            }));
            updateControlPanel(session, '\u5DF2\u5B9A\u4F4D\u8BFE\u7A0B\uFF0C\u6B63\u5728\u6253\u5F00');
            window.location.assign(selected.url.href);
            return;
        }
        updateControlPanel(session, '\u672A\u627E\u5230\u8BFE\u7A0B\uFF1A' + session.courseName, 'error');
        reportEvent(session.sessionId, 'FAILED', {
            lessonTitle: document.title,
            content: '\u8BFE\u7A0B\u5217\u8868\u672A\u627E\u5230\u8BFE\u7A0B\uFF1A' + session.courseName
        });
    }

    function findCourseEntryLink(session) {
        var expectedUrl = safeUrl(session.courseUrl || window.location.href);
        var expectedCourseId = expectedUrl
            ? queryValue(expectedUrl, 'courseid', 'courseId') : '';
        var expectedClassId = expectedUrl
            ? queryValue(expectedUrl, 'clazzid', 'classId') : '';
        if (!expectedCourseId && !expectedClassId) return null;
        var roots = [document];
        Array.prototype.slice.call(document.querySelectorAll('iframe')).forEach(
            function (frame) {
                try {
                    var frameDocument = frame.contentDocument;
                    if (frameDocument && frameDocument.querySelectorAll) {
                        roots.push(frameDocument);
                    }
                } catch (error) {
                    // ignore cross-origin frames
                }
            });
        for (var r = 0; r < roots.length; r++) {
            var anchors = Array.prototype.slice.call(roots[r].querySelectorAll('a[href]'));
            for (var i = 0; i < anchors.length; i++) {
                var url = safeUrl(anchors[i].getAttribute('href') || anchors[i].href);
                if (!url || url.protocol !== 'https:' || !isChaoxingHost(url.hostname)
                        || !looksLikeChaoxingCourseUrl(url)) {
                    continue;
                }
                var courseId = queryValue(url, 'courseid', 'courseId');
                var classId = queryValue(url, 'clazzid', 'classId');
                var matches = (expectedCourseId && courseId && courseId === expectedCourseId)
                    || (expectedClassId && classId && classId === expectedClassId);
                if (matches) return { element: anchors[i], url: url };
            }
        }
        return null;
    }

    function enterCourseCard(card, session) {
        var link = card.querySelector(
            '.course-cover a, a[href*="courseid"], a[href*="courseId"], a[href*="clazzid"]');
        if (link && link.href) {
            var url = safeUrl(link.href);
            if (url && isChaoxingHost(url.hostname)) {
                setActiveSession(Object.assign({}, session, {
                    courseSelected: true,
                    courseUrl: url.href,
                    courseHost: url.hostname
                }));
                updateControlPanel(session, '\u5DF2\u70B9\u51FB\u8BFE\u7A0B\uFF0C\u6B63\u5728\u8FDB\u5165\u2026\u2026');
                window.location.assign(url.href);
                return;
            }
        }
        if (typeof card.click === 'function') card.click();
    }

    async function openFirstChaoxingLesson(session) {
        updateControlPanel(session, '\u8BFE\u7A0B\u76EE\u5F55\u5DF2\u6253\u5F00\uFF0C\u6B63\u5728\u8FDB\u5165\u7AE0\u8282\u2026\u2026');
        ensureChapterTab();
        var candidates = [];
        for (var attempt = 0; attempt < LESSON_DISCOVERY_ATTEMPTS; attempt++) {
            candidates = findChaoxingLessonLinks(session);
            if (candidates.length > 0) break;
            await sleep(1000);
        }
        if (candidates.length > 0) {
            var selected = chooseLesson(candidates, session.chapterName);
            var targetUrl = enrichChaoxingUrl(selected.url, session);
            if (targetUrl) {
                var nextSession = Object.assign({}, session, {
                    chapterSelected: true,
                    courseUrl: targetUrl.href,
                    courseHost: targetUrl.hostname
                });
                setActiveSession(nextSession);
                updateControlPanel(nextSession, '\u5DF2\u627E\u5230\u7AE0\u8282\uFF0C\u6B63\u5728\u6253\u5F00\uFF1A' + selected.title);
                window.location.assign(targetUrl.href);
                return;
            }
        }
        var tabUrl = findChapterTabUrl();
        if (tabUrl) tabUrl = enrichChaoxingUrl(tabUrl, session);
        if (tabUrl && !samePageUrl(tabUrl) && !session.studyRedirected) {
            var tabSession = Object.assign({}, session, { studyRedirected: true });
            setActiveSession(tabSession);
            updateControlPanel(tabSession, '\u6B63\u5728\u5207\u6362\u5230\u7AE0\u8282\u5217\u8868\u9875');
            window.location.assign(tabUrl.href);
            return;
        }
        var directoryUrl = buildDirectoryPageUrl();
        if (directoryUrl && !samePageUrl(directoryUrl) && !session.studyRedirected) {
            var directorySession = Object.assign({}, session, { studyRedirected: true });
            setActiveSession(directorySession);
            updateControlPanel(directorySession, '\u6B63\u5728\u5207\u6362\u5230\u7AE0\u8282\u5217\u8868\u9875');
            window.location.assign(directoryUrl.href);
            return;
        }
        updateControlPanel(session, '\u672A\u8BC6\u522B\u5230\u7AE0\u8282\u94FE\u63A5\uFF0C\u5C1D\u8BD5\u70B9\u51FB\u7AE0\u8282\u76EE\u5F55', 'waiting');
        reportEvent(session.sessionId, 'DISCOVERING', {
            lessonTitle: document.title,
            content: '\u8BFE\u7A0B\u76EE\u5F55\u672A\u63D0\u4F9B\u53EF\u8DF3\u8F6C\u94FE\u63A5\uFF0C\u6539\u4E3A\u70B9\u51FB\u76EE\u5F55'
        });
        ensureChapterTab();
        waitForChapterAndSelect(session, 0);
    }

    function chooseLesson(candidates, chapterName) {
        var target = normalizeText(chapterName || '');
        if (target) {
            for (var i = 0; i < candidates.length; i++) {
                var text = normalizeText(candidates[i].title);
                if (text.indexOf(target) >= 0 || target.indexOf(text) >= 0) {
                    return candidates[i];
                }
            }
        }
        return candidates[0];
    }

    function pickChapterRow(session, rows) {
        if (!rows || rows.length === 0) return null;
        var target = normalizeText(session.chapterName || '');
        if (target) {
            for (var i = 0; i < rows.length; i++) {
                var text = normalizeText(chapterText(rows[i].element));
                if (text.indexOf(target) >= 0 || target.indexOf(text) >= 0) {
                    return rows[i];
                }
            }
        }
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].isJob && rows[i].unFinishCount > 0) return rows[i];
        }
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].isJob) return rows[i];
        }
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].chapterId) return rows[i];
        }
        return rows[0];
    }

    function buildCourseListUrl() {
        return new URL('https://mooc1.chaoxing.com/mycourse');
    }

    function buildDirectoryPageUrl() {
        var current = safeUrl(window.location.href);
        if (!current) return null;
        var courseId = queryValue(current, 'courseid', 'courseId');
        var classId = queryValue(current, 'clazzid', 'classId');
        var cpi = current.searchParams.get('cpi') || '';
        if (!courseId || !classId || !cpi) return null;
        var enc = findPageEnc();
        if (!enc) return null;
        var url = new URL('https://mooc2-ans.chaoxing.com');
        url.pathname = '/mooc2-ans/mycourse/stu';
        url.search = '';
        url.searchParams.set('courseid', courseId);
        url.searchParams.set('clazzid', classId);
        url.searchParams.set('cpi', cpi);
        url.searchParams.set('enc', enc);
        url.searchParams.set('pageHeader', '1');
        url.searchParams.set('v', '2');
        url.searchParams.set('hideHead', '0');
        url.searchParams.set('t', String(Date.now()));
        return url;
    }

    function findPageEnc() {
        var pattern = /[?&]enc=([a-f0-9]{32})/i;
        var current = safeUrl(window.location.href);
        var currentMatch = current && pattern.exec(current.href);
        if (currentMatch) return currentMatch[1];
        var roots = [document];
        Array.prototype.slice.call(document.querySelectorAll('iframe')).forEach(
            function (frame) {
                try {
                    var frameDocument = frame.contentDocument;
                    if (frameDocument && frameDocument.querySelectorAll) {
                        roots.push(frameDocument);
                    }
                } catch (error) {
                    // ignore cross-origin frames
                }
            });
        for (var r = 0; r < roots.length; r++) {
            var anchors = Array.prototype.slice.call(roots[r].querySelectorAll('a[href]'));
            for (var i = 0; i < anchors.length; i++) {
                var href = String(anchors[i].getAttribute('href') || anchors[i].href || '');
                var match = pattern.exec(href);
                if (match && (/\/stu([/?]|$)|pageHeader/i.test(href))) {
                    return match[1];
                }
            }
        }
        for (var r = 0; r < roots.length; r++) {
            var anchors = Array.prototype.slice.call(roots[r].querySelectorAll('a[href]'));
            for (var i = 0; i < anchors.length; i++) {
                var match = pattern.exec(String(
                    anchors[i].getAttribute('href') || anchors[i].href || ''));
                if (match) return match[1];
            }
            var input = roots[r].querySelector(
                'input[name="enc"][value], input#enc[value]');
            if (input && input.value) return String(input.value).trim();
        }
        var frames = Array.prototype.slice.call(document.querySelectorAll('iframe[src], frame[src]'));
        for (var f = 0; f < frames.length; f++) {
            var src = String(frames[f].getAttribute('src') || frames[f].src || '');
            var frameMatch = pattern.exec(src);
            if (frameMatch) return frameMatch[1];
        }
        var scripts = Array.prototype.slice.call(document.querySelectorAll('script'));
        for (var s = 0; s < scripts.length; s++) {
            var scriptText = String(scripts[s].textContent || '');
            var scriptMatch = /enc\s*[=:]\s*["']([a-f0-9]{32})["']/i.exec(scriptText);
            if (scriptMatch) return scriptMatch[1];
        }
        return '';
    }

    function enrichChaoxingUrl(url, session) {
        if (!url) return null;
        var current = safeUrl(window.location.href);
        var fallbackSources = [current];
        if (session) {
            fallbackSources.push(safeUrl(session.targetCourseUrl));
            fallbackSources.push(safeUrl(session.courseUrl));
        }
        var courseId = queryValue(url, 'courseid', 'courseId');
        var classId = queryValue(url, 'clazzid', 'classId');
        var cpi = url.searchParams.get('cpi');
        for (var i = 0; i < fallbackSources.length; i++) {
            var source = fallbackSources[i];
            if (!source) continue;
            if (!courseId) courseId = queryValue(source, 'courseid', 'courseId');
            if (!classId) classId = queryValue(source, 'clazzid', 'classId');
            if (!cpi) cpi = source.searchParams.get('cpi');
        }
        if (!courseId || !classId || !cpi) return null;
        var enc = url.searchParams.get('enc') || findPageEnc();
        if (!enc) return null;
        if (!url.searchParams.get('courseid') && !url.searchParams.get('courseId')) {
            url.searchParams.set('courseid', courseId);
        }
        if (!url.searchParams.get('clazzid') && !url.searchParams.get('classId')) {
            url.searchParams.set('clazzid', classId);
        }
        if (!url.searchParams.get('cpi')) url.searchParams.set('cpi', cpi);
        if (!url.searchParams.get('enc')) url.searchParams.set('enc', enc);
        if (!url.searchParams.get('ut')) url.searchParams.set('ut', 's');
        return url;
    }

    function findChapterTabUrl() {
        var tab = document.querySelector(
            'a[title="\u7AE0\u8282"], a[title*="\u7AE0\u8282"], a[href*="pageHeader=1"]');
        if (tab && tab.href) return safeUrl(tab.href);
        var anchors = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
        for (var i = 0; i < anchors.length; i++) {
            var title = String(anchors[i].getAttribute('title') || '');
            var text = normalizeText(anchors[i].textContent || '');
            if (title.indexOf('\u7AE0\u8282') >= 0 || text.indexOf('\u7AE0\u8282') >= 0) {
                var url = safeUrl(anchors[i].getAttribute('href') || anchors[i].href);
                if (url && isChaoxingHost(url.hostname)) return url;
            }
        }
        return null;
    }

    function findChaoxingLessonLinks(session) {
        var expectedUrl = safeUrl(
            (session && (session.targetCourseUrl || session.courseUrl))
                || window.location.href);
        var candidates = new Map();
        var roots = [document];
        Array.prototype.slice.call(document.querySelectorAll('iframe')).forEach(
            function (frame) {
                try {
                    var frameDocument = frame.contentDocument;
                    if (frameDocument && frameDocument.querySelectorAll) {
                        roots.push(frameDocument);
                    }
                } catch (error) {
                    // ignore cross-origin frames
                }
            });
        roots.forEach(function (root) {
            Array.prototype.slice.call(root.querySelectorAll('a[href]')).forEach(
                function (anchor) {
                    var url = safeUrl(anchor.getAttribute('href') || anchor.href);
                    if (!url || url.protocol !== 'https:'
                            || !isChaoxingHost(url.hostname)
                            || !looksLikeChaoxingLessonUrl(url)
                            || !matchesCourseIdentity(url, expectedUrl)) {
                        return;
                    }
                    url.hash = '';
                    var title = boundedText(
                        anchor.getAttribute('title') || anchor.textContent,
                        300);
                    candidates.set(url.href, { title: title, url: url });
                });
        });
        return Array.from(candidates.values());
    }

    function safeUrl(value) {
        try {
            return new URL(String(value || ''), window.location.href);
        } catch (error) {
            return null;
        }
    }

    function matchesCourseIdentity(candidate, expected) {
        if (!expected) return true;
        var expectedCourseId = queryValue(expected, 'courseid', 'courseId');
        var expectedClassId = queryValue(expected, 'clazzid', 'classId');
        var candidateCourseId = queryValue(candidate, 'courseid', 'courseId');
        var candidateClassId = queryValue(candidate, 'clazzid', 'classId');
        return (!expectedCourseId || !candidateCourseId
                || expectedCourseId === candidateCourseId)
            && (!expectedClassId || !candidateClassId
                || expectedClassId === candidateClassId);
    }

    function queryValue(url, firstName, secondName) {
        return url.searchParams.get(firstName)
            || url.searchParams.get(secondName)
            || '';
    }

    function looksLikeChaoxingLessonUrl(url) {
        var value = url.pathname + '?' + url.searchParams.toString();
        return /studentstudy|knowledge\/cards|chapterid|knowledgeid/i.test(value);
    }

    function samePageUrl(url) {
        var current = safeUrl(window.location.href);
        if (!current) return false;
        return current.href.split('#')[0] === url.href.split('#')[0];
    }

    function findChaoxingCourseLinks(courseName) {
        var query = normalizeCourseName(courseName);
        if (!query) return [];
        var jsonCandidates = findChaoxingJsonCourses(query);
        var candidateMap = new Map();
        jsonCandidates.forEach(function (candidate) {
            candidateMap.set(chaoxingCourseKey(candidate.url), candidate);
        });
        Array.prototype.slice.call(document.querySelectorAll('a[href]')).forEach(
            function (anchor) {
                var name = String(
                    anchor.getAttribute('title') || anchor.textContent || '').trim();
                var normalizedName = normalizeCourseName(name);
                if (!normalizedName || !isCourseNameMatch(normalizedName, query)) {
                    return;
                }
                var url;
                try {
                    url = new URL(anchor.getAttribute('href') || anchor.href, window.location.href);
                } catch (error) {
                    return;
                }
                if (url.protocol !== 'https:' || !isChaoxingHost(url.hostname)
                        || !looksLikeChaoxingCourseUrl(url)) {
                    return;
                }
                url.hash = '';
                candidateMap.set(chaoxingCourseKey(url), { name: name, url: url });
            });
        return Array.from(candidateMap.values());
    }

    function findChaoxingJsonCourses(query) {
        var text = String(document.body && document.body.textContent || '').trim();
        if (!text.startsWith('{') || text.length > MAX_COURSE_JSON_CHARACTERS) {
            text = extractEmbeddedCourseJson();
        }
        if (!text.startsWith('{')) {
            return [];
        }
        var payload;
        try {
            payload = JSON.parse(text);
        } catch (error) {
            return [];
        }
        if (!payload || Number(payload.result) !== 1 || !Array.isArray(payload.channelList)) {
            return [];
        }
        var candidates = [];
        var boundedChannels = payload.channelList.slice(0, MAX_COURSE_JSON_ITEMS);
        boundedChannels.forEach(function (channel) {
            var content = channel && channel.content;
            var data = content && content.course && content.course.data;
            var course = Array.isArray(data) ? data[0] : null;
            var courseName = boundedText(course && course.name, 200);
            var className = boundedText(content && content.name, 200);
            var normalizedCourseName = normalizeCourseName(courseName);
            var normalizedClassName = normalizeCourseName(className);
            if (!isCourseNameMatch(normalizedCourseName, query)
                    && !isCourseNameMatch(normalizedClassName, query)) {
                return;
            }
            var courseId = numericIdentifier(course && course.id);
            var classId = numericIdentifier(channel && channel.key)
                || numericIdentifier(content && content.id);
            var cpi = numericIdentifier(channel && channel.cpi)
                || numericIdentifier(content && content.cpi);
            if (!courseId || !classId || !cpi) return;
            var url = new URL(
                'https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse');
            url.searchParams.set('courseid', courseId);
            url.searchParams.set('clazzid', classId);
            url.searchParams.set('cpi', cpi);
            url.searchParams.set('ut', 's');
            candidates.push({ name: className || courseName, url: url });
        });
        return candidates;
    }

    function extractEmbeddedCourseJson() {
        var scripts = Array.prototype.slice.call(document.querySelectorAll('script'));
        for (var i = 0; i < scripts.length; i++) {
            var content = String(scripts[i].textContent || '');
            var start = content.indexOf('"channelList"');
            if (start < 0) start = content.indexOf('channelList');
            if (start < 0) continue;
            var jsonStart = content.lastIndexOf('{', start);
            if (jsonStart < 0) continue;
            var braceCount = 0;
            for (var j = jsonStart; j < content.length; j++) {
                if (content.charAt(j) === '{') braceCount++;
                else if (content.charAt(j) === '}') braceCount--;
                if (braceCount === 0) {
                    return content.substring(jsonStart, j + 1);
                }
            }
        }
        return '';
    }

    function boundedText(value, maxCharacters) {
        var text = typeof value === 'string' ? value.trim() : '';
        return text.length <= maxCharacters ? text : '';
    }

    function numericIdentifier(value) {
        var text = String(value == null ? '' : value).trim();
        return /^[1-9][0-9]{0,19}$/.test(text) ? text : '';
    }

    function chaoxingCourseKey(url) {
        var courseId = url.searchParams.get('courseId')
            || url.searchParams.get('courseid') || '';
        var classId = url.searchParams.get('clazzid')
            || url.searchParams.get('classId') || '';
        return courseId || classId ? courseId + '|' + classId : url.href;
    }

    function normalizeCourseName(value) {
        return String(value || '')
            .normalize('NFKC')
            .toLocaleLowerCase('zh-CN')
            .replace(/[\s\p{P}\p{S}]+/gu, '');
    }

    function isCourseNameMatch(candidate, query) {
        return Boolean(candidate) && Boolean(query)
            && (candidate === query
                || candidate.includes(query)
                || query.includes(candidate));
    }

    function looksLikeChaoxingCourseUrl(url) {
        var value = url.pathname + '?' + url.searchParams.toString();
        return /mycourse|studentcourse|studentstudy|courseid|clazzid|mooc-ans|mooc2-ans/i.test(value);
    }

    function isChaoxingCourseDirectoryPage() {
        var pathname = String(window.location.pathname || '');
        var value = pathname + '?' + window.location.search;
        if (/\/mycourse\/stu([/?]|$)/i.test(pathname)) return true;
        return /studentcourse/i.test(value)
            && !/studentstudy|chapterid|knowledgeid/i.test(value);
    }

    function isEmbeddedLearningPage() {
        if (document.querySelector('video')) return true;
        var value = window.location.pathname + '?' + window.location.search;
        return /studentstudy|knowledge\/cards|ananas|video|multimedia/i.test(value);
    }

    function currentLessonTitle() {
        var selectors = [
            '.posCatalog_active .posCatalog_name',
            '.lesson-title',
            '[class*="chapter"] .title',
            '.video-title'
        ];
        for (var i = 0; i < selectors.length; i++) {
            var element = document.querySelector(selectors[i]);
            var value = boundedText(
                element && (element.getAttribute('title') || element.textContent),
                300);
            if (value) return value;
        }
        var title = boundedText(document.title, 300);
        if (title) return title;
        var url = new URL(window.location.href);
        var chapterId = url.searchParams.get('chapterId')
            || url.searchParams.get('chapterid');
        return chapterId ? '\u7AE0\u8282 ' + chapterId : '\u5DF2\u6253\u5F00\u89C6\u9891\u7AE0\u8282';
    }

    function selectChapterIfNeeded(session) {
        if (!session || !isChaoxingHost(window.location.hostname)) return;
        if (isChaoxingLoginPage()) return;
        var active = getActiveSession();
        if (!active) return;
        if (active.chapterSelected) {
            reportLessonIfNeeded(active);
            return;
        }
        if (isCourseListPage()) {
            updateControlPanel(session, '\u6B63\u5728\u8FDB\u5165\u8BFE\u7A0B\u2026\u2026');
            if (active.targetCourseUrl) {
                openResolvedChaoxingCourse(active);
            } else {
                openMatchingChaoxingCourse(active);
            }
            return;
        }
        if (isChaoxingCourseDirectoryPage()) {
            openFirstChaoxingLesson(active);
            return;
        }
        if (isEmbeddedLearningPage()) {
            if (isChapterLoaded()) {
                var lessonSession = Object.assign({}, active, { chapterSelected: true });
                setActiveSession(lessonSession);
                reportLessonIfNeeded(lessonSession);
                return;
            }
            waitForChapterLoadThenSelect(session, 0);
            return;
        }
        ensureChapterTab();
        waitForChapterAndSelect(session, 0);
    }

    function isChapterLoaded() {
        if (document.querySelector('video')) return true;
        try {
            var url = new URL(window.location.href);
            if (url.searchParams.get('chapterId') || url.searchParams.get('chapterid')
                    || url.searchParams.get('knowledgeId') || url.searchParams.get('knowledgeid')) {
                return true;
            }
        } catch (error) {
            // keep checking the DOM below
        }
        return document.querySelector('.posCatalog_active') !== null;
    }

    function waitForChapterLoadThenSelect(session, attempt) {
        if (isChapterLoaded()) {
            var active = getActiveSession();
            if (active) {
                active.chapterSelected = true;
                setActiveSession(active);
            }
            reportLessonIfNeeded(active || session);
            return;
        }
        if (attempt >= 15) {
            updateControlPanel(session, '\u7AE0\u8282\u5C1A\u672A\u81EA\u52A8\u52A0\u8F7D\uFF0C\u6539\u4E3A\u70B9\u51FB\u7AE0\u8282\u76EE\u5F55', 'waiting');
            ensureChapterTab();
            waitForChapterAndSelect(session, 0);
            return;
        }
        setTimeout(function () { waitForChapterLoadThenSelect(session, attempt + 1); }, 1000);
    }

    function reportLessonIfNeeded(session) {
        if (!session || session.lessonReported) return;
        var nextSession = Object.assign({}, session, { lessonReported: true });
        setActiveSession(nextSession);
        reportEvent(session.sessionId, 'LESSON_CHANGED', {
            lessonTitle: currentLessonTitle()
        });
    }

    function ensureChapterTab() {
        var tab = document.querySelector('[title="\u7AE0\u8282"]');
        if (tab && tab.className && tab.className.indexOf('active') < 0) {
            tab.click();
        }
    }

    function isCourseListPage() {
        var path = String(window.location.pathname || '');
        if (path === '/mycourse' || path.indexOf('/mycourse?') === 0) return true;
        return /^\/visit\/(courses|interaction|courselistdata)([/?]|$)/i.test(path);
    }

    function enterCourseIfNeeded(session) {
        var card = findCourseCard(session.courseName);
        if (!card) {
            updateControlPanel(session, '\u672A\u627E\u5230\u8BFE\u7A0B\uFF1A' + session.courseName, 'error');
            return;
        }
        var link = card.querySelector('.course-cover a')
            || card.querySelector('a[href*="courseid"], a[href*="courseId"]');
        if (link) {
            link.click();
            updateControlPanel(session, '\u5DF2\u70B9\u51FB\u8BFE\u7A0B\uFF0C\u6B63\u5728\u8FDB\u5165\u2026\u2026');
            return;
        }
        if (card.click) card.click();
    }

    function findCourseCard(courseName, expectedCourseId) {
        var target = normalizeText(courseName);
        var cards = document.querySelectorAll(
            'li.course, .course-item, [class*="course"], [class*="Course"],'
            + ' [class*="courselist"] li, [class*="courseList"] li, [class*="list"] li');
        for (var i = 0; i < cards.length; i++) {
            var card = cards[i];
            if (expectedCourseId) {
                var html = String(card.outerHTML || '');
                if (html.indexOf('courseid=' + expectedCourseId) >= 0
                        || html.indexOf('"courseid":"' + expectedCourseId) >= 0
                        || html.indexOf('courseId=' + expectedCourseId) >= 0
                        || html.indexOf('"courseId":"' + expectedCourseId) >= 0) {
                    return card;
                }
            }
            if (!target) continue;
            var nameEl = card.querySelector(
                '.course-name, [class*="course-name"], [class*="Course-name"], h3, [class*="title"]');
            var text = nameEl ? nameEl.textContent : cards[i].textContent;
            if (normalizeText(text).indexOf(target) >= 0) return card;
        }
        return null;
    }

    function waitForChapterAndSelect(session, attempt) {
        if (attempt > 40) {
            updateControlPanel(session, '\u672A\u627E\u5230\u7AE0\u8282\u76EE\u5F55\uFF0C\u8BF7\u624B\u52A8\u70B9\u51FB\u7AE0\u8282', 'error');
            return;
        }
        var rows = parseChapterRows();
        if (rows.length === 0) {
            setTimeout(function () { waitForChapterAndSelect(session, attempt + 1); }, 1000);
            return;
        }
        var jobRows = rows.filter(function (row) { return row.isJob; });
        if (jobRows.length === 0 && attempt < 8) {
            expandChapterHeader(session, attempt);
            setTimeout(function () { waitForChapterAndSelect(session, attempt + 1); }, 1500);
            return;
        }
        if (jobRows.length === 0) {
            updateControlPanel(session, '\u672A\u627E\u5230\u7AE0\u8282\u4EFB\u52A1\u70B9\uFF0C\u8BF7\u624B\u52A8\u70B9\u51FB\u7AE0\u8282', 'error');
            reportEvent(session.sessionId, 'FAILED', {
                lessonTitle: document.title,
                content: '\u76EE\u5F55\u672A\u8BC6\u522B\u5230\u7AE0\u8282\u4EFB\u52A1\u70B9\uFF0C\u7B49\u5F85\u624B\u52A8\u70B9\u51FB'
            });
            return;
        }
        selectChapter(session, jobRows);
    }

    function parseChapterRows() {
        var rows = [];
        var seen = new Set();
        var roots = [document];
        Array.prototype.slice.call(document.querySelectorAll('iframe')).forEach(
            function (frame) {
                try {
                    var frameDocument = frame.contentDocument;
                    if (frameDocument && frameDocument.querySelectorAll) {
                        roots.push(frameDocument);
                    }
                } catch (error) {
                    // ignore cross-origin frames
                }
            });
        roots.forEach(function (root) {
            var clickables = Array.prototype.slice.call(
                root.querySelectorAll('[onclick*="getTeacherAjax"]'));
            if (clickables.length === 0) {
                clickables = Array.prototype.slice.call(
                    root.querySelectorAll('.posCatalog_click, .posCatalog_select, .posCatalog_name'));
            }
            clickables.forEach(function (element) {
                if (seen.has(element)) return;
                seen.add(element);
                var onclick = String(
                    element.getAttribute && element.getAttribute('onclick') || '');
                var args = parseTeacherAjaxArgs(onclick);
                var parent = element.parentElement;
                var parentClass = parent && parent.className
                    ? String(parent.className) : '';
                var elementClass = element.className ? String(element.className) : '';
                var countEl = element.querySelector('.jobUnfinishCount')
                    || (parent ? parent.querySelector('.jobUnfinishCount') : null);
                var isJob = elementClass.indexOf('posCatalog_select') >= 0
                    || parentClass.indexOf('posCatalog_select') >= 0
                    || (elementClass.indexOf('posCatalog_click') >= 0
                        && countEl !== null);
                rows.push({
                    element: element,
                    nameElement: element.querySelector('.posCatalog_name') || element,
                    courseId: args ? args[0] : '',
                    classId: args ? args[1] : '',
                    chapterId: args ? args[2] : '',
                    unFinishCount: countEl ? parseInt(countEl.value || '0', 10) || 0 : 0,
                    isJob: isJob
                });
            });
        });
        return rows;
    }

    function parseTeacherAjaxArgs(onclick) {
        var quoted = String(onclick || '').match(
            /getTeacherAjax\s*\(\s*(['"])([^'"]*)\1\s*,\s*(['"])([^'"]*)\3\s*,\s*(['"])([^'"]*)\5\s*\)/);
        if (quoted) return [quoted[2], quoted[4], quoted[6]];
        var unquoted = String(onclick || '').match(
            /getTeacherAjax\s*\(\s*([^,\s]+)\s*,\s*([^,\s]+)\s*,\s*([^,)\s]+)\s*\)/);
        return unquoted ? [unquoted[1], unquoted[2], unquoted[3]] : null;
    }

    function expandChapterHeader(session, index) {
        var target = normalizeText(session.chapterName || '');
        var headers = [];
        var seen = new Set();
        Array.prototype.slice.call(
            document.querySelectorAll('.posCatalog_click, .posCatalog')).forEach(
            function (element) {
                if (seen.has(element)) return;
                seen.add(element);
                var name = element.querySelector('.posCatalog_name');
                if (!name) return;
                var ownClass = String(element.className || '');
                var parentClass = String(element.parentElement && element.parentElement.className || '');
                var count = element.querySelector('.jobUnfinishCount')
                    || (element.parentElement && element.parentElement.querySelector('.jobUnfinishCount'));
                if (ownClass.indexOf('posCatalog_select') >= 0
                        || parentClass.indexOf('posCatalog_select') >= 0
                        || count) {
                    return;
                }
                headers.push(element);
            });
        if (headers.length === 0) return;
        var header = headers[Math.min(index, headers.length - 1)];
        if (target) {
            for (var i = 0; i < headers.length; i++) {
                var nameEl = headers[i].querySelector('.posCatalog_name');
                var text = normalizeText(nameEl ? nameEl.textContent : headers[i].textContent);
                if (text.indexOf(target) >= 0 || target.indexOf(text) >= 0) {
                    header = headers[i];
                    break;
                }
            }
        }
        var clickTarget = header.querySelector('.posCatalog_name') || header;
        if (clickTarget && typeof clickTarget.click === 'function') {
            clickTarget.click();
        }
    }

    function selectChapter(session, rows) {
        var target = normalizeText(session.chapterName || '');
        var row = null;
        if (target) {
            for (var i = 0; i < rows.length; i++) {
                var text = normalizeText(chapterText(rows[i].element));
                if (text.indexOf(target) >= 0 || target.indexOf(text) >= 0) {
                    row = rows[i];
                    break;
                }
            }
        }
        if (!row) {
            for (var i = 0; i < rows.length; i++) {
                if (rows[i].unFinishCount > 0) {
                    row = rows[i];
                    break;
                }
            }
        }
        if (!row) row = rows[0];
        if (!row) return;
        var active = getActiveSession();
        if (active) {
            active.chapterSelected = true;
            setActiveSession(active);
        }
        var clickTarget = row.nameElement || row.element;
        var lessonUrl = safeUrl(
            clickTarget.getAttribute && clickTarget.getAttribute('href'));
        if (lessonUrl && looksLikeChaoxingLessonUrl(lessonUrl)) {
            var targetUrl = enrichChaoxingUrl(lessonUrl, session);
            if (targetUrl) {
                window.location.assign(targetUrl.href);
                return;
            }
        }
        var teacherAjax = resolveTeacherAjax();
        if (row.isJob && row.chapterId && teacherAjax) {
            try {
                teacherAjax(row.courseId, row.classId, row.chapterId);
                updateControlPanel(
                    session,
                    target ? '\u5DF2\u6253\u5F00\u7AE0\u8282\uFF1A' + session.chapterName
                        : '\u5DF2\u81EA\u52A8\u6253\u5F00\u7B2C\u4E00\u4E2A\u53EF\u5B66\u7AE0\u8282');
                setTimeout(function () {
                    reportEvent(session.sessionId, 'LESSON_CHANGED', {
                        lessonTitle: currentLessonTitle()
                    });
                }, 2000);
                return;
            } catch (error) {
                // fall through to clicking the row
            }
        }
        var rowClickTarget = row.element || clickTarget;
        if (rowClickTarget && typeof rowClickTarget.click === 'function') {
            rowClickTarget.click();
        }
        updateControlPanel(
            session,
            target ? '\u5DF2\u6253\u5F00\u7AE0\u8282\uFF1A' + session.chapterName
                : '\u5DF2\u81EA\u52A8\u6253\u5F00\u7B2C\u4E00\u4E2A\u53EF\u5B66\u7AE0\u8282');
        setTimeout(function () {
            var titleEl = document.querySelector('.posCatalog_active .posCatalog_name');
            if (titleEl) {
                reportEvent(session.sessionId, 'LESSON_CHANGED', {
                    lessonTitle: titleEl.textContent
                });
            }
        }, 2000);
    }

    function resolveTeacherAjax() {
        if (typeof unsafeWindow !== 'undefined'
                && unsafeWindow
                && typeof unsafeWindow.getTeacherAjax === 'function') {
            return unsafeWindow.getTeacherAjax.bind(unsafeWindow);
        }
        if (typeof unsafeWindow !== 'undefined'
                && unsafeWindow.top
                && typeof unsafeWindow.top.getTeacherAjax === 'function') {
            return unsafeWindow.top.getTeacherAjax.bind(unsafeWindow.top);
        }
        if (typeof window.getTeacherAjax === 'function') {
            return window.getTeacherAjax.bind(window);
        }
        return null;
    }

    function chapterText(el) {
        if (!el) return '';
        var name = el.querySelector && el.querySelector('.posCatalog_name');
        return name ? name.textContent : el.textContent;
    }

    function normalizeText(value) {
        return String(value || '').replace(/\s+/g, '').toLowerCase();
    }

    async function handleQuestionEvent(event) {
        var session = getActiveSession();
        if (!session || !event || !event.detail) return;
        var question;
        try { question = typeof event.detail === 'string' ? JSON.parse(event.detail) : event.detail; }
        catch (e) { return; }
        updateControlPanel(session, '\u53D1\u73B0\u4E92\u52A8\u9898\uFF0C\u6B63\u5728\u8BF7\u6C42\u5728\u7EBF AI');
        try {
            var response = await request('POST',
                bridgeUrl() + '/sessions/' + encodeURIComponent(session.sessionId) + '/questions',
                JSON.stringify(question), 45000);
            if (response.status !== 200 || !response.responseText) {
                updateControlPanel(session, '\u5728\u7EBF\u56DE\u7B54\u6682\u4E0D\u53EF\u7528\uFF0C\u7B49\u5F85\u4EBA\u5DE5\u5904\u7406', 'waiting');
                return;
            }
            var answerCommand = JSON.parse(response.responseText);
            updateControlPanel(session, '\u5DF2\u6536\u5230\u7B54\u6848\u5019\u9009\uFF0C\u7B49\u5F85\u9875\u9762\u9A8C\u8BC1');
            window.dispatchEvent(new CustomEvent('wechat-claw-ocs-answer', { detail: JSON.stringify(answerCommand) }));
        } catch (e) {
            updateControlPanel(session, '\u5728\u7EBF\u56DE\u7B54\u5931\u8D25\uFF0C\u7B49\u5F85\u4EBA\u5DE5\u5904\u7406', 'error');
        }
    }

    function handleAnswerResultEvent(event) {
        if (!event || !event.detail) return;
        var result;
        try { result = typeof event.detail === 'string' ? JSON.parse(event.detail) : event.detail; }
        catch (e) { return; }
        var resultType = String(result.result || '').toUpperCase();
        var allowed = { SELECTED: 1, SUBMITTED: 1, CORRECT: 1, WRONG: 1, BLOCKED: 1 };
        if (!allowed[resultType] || !result.questionId) return;
        var session = getActiveSession();
        if (!session) return;
        var msgs = {
            SELECTED: '\u9875\u9762\u5DF2\u9009\u62E9\u7B54\u6848',
            SUBMITTED: '\u7B54\u6848\u5DF2\u63D0\u4EA4',
            CORRECT: '\u9875\u9762\u786E\u8BA4\u56DE\u7B54\u6B63\u786E',
            WRONG: '\u9875\u9762\u786E\u8BA4\u56DE\u7B54\u9519\u8BEF',
            BLOCKED: '\u9875\u9762\u62D2\u7EDD\u6267\u884C'
        };
        updateControlPanel(session, msgs[resultType] || '\u5DF2\u6536\u5230\u7B54\u6848\u7ED3\u679C',
            (resultType === 'WRONG' || resultType === 'BLOCKED') ? 'error' : 'working');
        request('POST',
            bridgeUrl() + '/sessions/' + encodeURIComponent(session.sessionId) + '/answers/results',
            JSON.stringify({ questionId: String(result.questionId), result: resultType, content: String(result.content || '') })
        ).catch(function () {});
    }

    async function reportEvent(sessionId, eventType, details) {
        var session = getActiveSession();
        if (!session) return false;
        var body = {
            eventType: eventType,
            lessonTitle: String(details.lessonTitle || '').slice(0, 300),
            content: String(details.content || '').slice(0, MAX_EVENT_CHARACTERS),
            progressPercent: details.progressPercent
        };
        try {
            var response = await request('POST',
                bridgeUrl() + '/sessions/' + encodeURIComponent(sessionId) + '/events', JSON.stringify(body));
            return response.status === 200;
        } catch (e) { return false; }
    }

    async function finishActiveSession() {
        var session = getActiveSession();
        if (!session) { window.alert('\u5F53\u524D\u6CA1\u6709 OCS \u5B66\u4E60\u4F1A\u8BDD\u3002'); return; }
        if (session.savedOcsConfig) restoreOcsConfig(session.savedOcsConfig);
        var reported = await reportEvent(session.sessionId, 'FINISHED', { lessonTitle: document.title, progressPercent: 100 });
        if (reported) clearActiveSession();
        else window.alert('\u672A\u80FD\u5411\u672C\u673A WeChat Claw \u62A5\u544A\u5B8C\u6210\u72B6\u6001\u3002');
    }

    function clearActiveSession() {
        var session = getActiveSession();
        if (session && session.savedOcsConfig) restoreOcsConfig(session.savedOcsConfig);
        if (tabState) { delete tabState.activeSession; GM_saveTab(tabState); }
        var panel = document.getElementById('wechat-claw-ocs-control');
        if (panel) panel.remove();
    }

    function configureBridgeUrl() {
        var value = window.prompt('\u8BF7\u8F93\u5165 WeChat Claw OCS Bridge \u5730\u5740\uFF1A', bridgeUrl());
        if (value === null || !value.trim()) return;
        try {
            var parsed = new URL(value.trim());
            if (parsed.protocol !== 'http:' || ['127.0.0.1', 'localhost'].indexOf(parsed.hostname) < 0) throw new Error('bad');
            GM_setValue('bridgeUrl', parsed.href.replace(/\/+$/, ''));
        } catch (e) { window.alert('\u6865\u63A5\u5730\u5740\u5FC5\u987B\u662F 127.0.0.1 \u6216 localhost \u4E0A\u7684 HTTP \u5730\u5740\u3002'); }
    }

    function bridgeUrl() {
        return String(GM_getValue('bridgeUrl', DEFAULT_BRIDGE_URL) || DEFAULT_BRIDGE_URL).trim().replace(/\/+$/, '');
    }

    function getActiveSession() {
        return tabState && tabState.activeSession ? tabState.activeSession : null;
    }

    function setActiveSession(session) {
        tabState = tabState || {};
        tabState.activeSession = session;
        GM_saveTab(tabState);
    }

    function isActiveSessionPage(session) {
        if (!session) return false;
        if (session.platform === 'chaoxing') return isChaoxingHost(window.location.hostname);
        return window.location.protocol === 'https:' || isLoopbackHost(window.location.hostname);
    }

    function isChaoxingLoginPage() {
        var hostname = String(window.location.hostname || '').toLowerCase();
        var pathname = String(window.location.pathname || '');
        if (hostname === 'passport2.chaoxing.com' || hostname.endsWith('.passport2.chaoxing.com')) return true;
        if (/\/(?:login|login4login)(?:[/?]|$)/i.test(pathname)) return true;
        return Boolean(document.querySelector('#quickCode, #phone, form[action*="login"], form[action*="passport"]'));
    }

    function isChaoxingHost(hostname) {
        return hostname === 'chaoxing.com' || hostname.endsWith('.chaoxing.com');
    }

    function request(method, url, data, timeout) {
        timeout = timeout || 10000;
        return new Promise(function (resolve, reject) {
            GM_xmlhttpRequest({
                method: method, url: url, data: data, timeout: timeout,
                headers: { 'X-Wechat-Claw-Ocs-Client': 'companion-v1', 'Content-Type': 'application/json' },
                onload: resolve,
                onerror: function () { reject(new Error('network_error')); },
                ontimeout: function () { reject(new Error('request_timeout')); }
            });
        });
    }

    function updateControlPanel(session, message, tone) {
        tone = tone || 'working';
        if (!IS_TOP_FRAME || !document.body || !session) return;
        var panel = document.getElementById('wechat-claw-ocs-control');
        if (!panel) { showCompanionStatus(message); return; }
        var statusEl = panel.querySelector('[data-field="status"]');
        if (statusEl) {
            statusEl.textContent = message;
            statusEl.style.color = tone === 'error' ? '#b42318' : tone === 'waiting' ? '#9a6700' : '#176b46';
        }
    }

    function sleep(ms) { return new Promise(function (r) { window.setTimeout(r, ms); }); }
    function isLoopbackHost(h) { return h === '127.0.0.1' || h === 'localhost'; }
    function safeError(e) { return e instanceof Error ? e.message : 'unknown_error'; }
})();
