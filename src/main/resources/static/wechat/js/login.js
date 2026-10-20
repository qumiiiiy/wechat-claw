(() => {
    "use strict";

    const BOTS_ENDPOINT = "/api/wechat/bots";
    const POLL_INTERVAL_MS = 1500;
    const SELECTED_SESSION_KEY = "wechat.selectedBotSession";

    const statusText = {
        NOT_STARTED: "服务尚未启动",
        NOT_LOGIN: "等待生成二维码",
        WAITING: "等待扫码",
        SCANNED: "已扫码，请在微信中确认",
        LOGGED_IN: "登录成功",
        EXPIRED: "二维码已过期，请刷新当前 Bot",
        ERROR: "登录失败，请刷新当前 Bot"
    };

    const statusElement = document.querySelector("#login-status");
    const notificationElement = document.querySelector("#status-notification");
    const notificationTitleElement = document.querySelector("#notification-title");
    const errorElement = document.querySelector("#error-message");
    const qrCodeElement = document.querySelector("#qr-code");
    const qrMaskElement = document.querySelector("#qr-mask");
    const refreshButton = document.querySelector("#refresh-button");
    const addBotButton = document.querySelector("#add-bot-button");
    const removeBotButton = document.querySelector("#remove-bot-button");
    const botSelect = document.querySelector("#bot-select");

    let bots = [];
    let selectedSessionId = localStorage.getItem(SELECTED_SESSION_KEY);

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            cache: "no-store",
            ...options,
            headers: {Accept: "application/json", ...options.headers}
        });
        const body = response.status === 204
            ? null
            : await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(body?.detail || body?.error || `请求失败（${response.status}）`);
        }
        return body;
    }

    function sessionEndpoint(path = "") {
        if (!selectedSessionId) {
            throw new Error("尚未选择 Bot 会话");
        }
        return `${BOTS_ENDPOINT}/${encodeURIComponent(selectedSessionId)}${path}`;
    }

    function botLabel(bot) {
        return bot.botId || bot.ownerUserId || `Bot ${bot.sessionId.slice(0, 8)}`;
    }

    function renderBotOptions() {
        botSelect.replaceChildren();
        for (const bot of bots) {
            const option = document.createElement("option");
            option.value = bot.sessionId;
            option.textContent = botLabel(bot);
            botSelect.append(option);
        }
        if (!bots.some(bot => bot.sessionId === selectedSessionId)) {
            selectedSessionId = bots[0]?.sessionId || null;
        }
        if (selectedSessionId) {
            botSelect.value = selectedSessionId;
            localStorage.setItem(SELECTED_SESSION_KEY, selectedSessionId);
        } else {
            localStorage.removeItem(SELECTED_SESSION_KEY);
        }
        const hasSelection = Boolean(selectedSessionId);
        botSelect.disabled = !hasSelection;
        removeBotButton.disabled = !hasSelection;
        refreshButton.disabled = !hasSelection;
    }

    function renderStatus(status) {
        const loginStatus = status?.loginStatus || "NOT_STARTED";
        statusElement.textContent = statusText[loginStatus] || loginStatus;
        const selectedBot = bots.find(bot => bot.sessionId === selectedSessionId);
        notificationTitleElement.textContent = selectedBot ? botLabel(selectedBot) : "Bot 状态";
        notificationElement.className = "notification";
        if (loginStatus === "LOGGED_IN") {
            notificationElement.classList.add("notification--success");
        } else if (["SCANNED", "WAITING"].includes(loginStatus)) {
            notificationElement.classList.add("notification--warning");
        } else if (["ERROR", "EXPIRED"].includes(loginStatus)) {
            notificationElement.classList.add("notification--error");
        } else {
            notificationElement.classList.add("notification--loading");
        }
        errorElement.textContent = status?.lastError || "";
        errorElement.hidden = !status?.lastError;
        qrMaskElement.hidden = Boolean(status?.qrCodeAvailable);
        if (status?.qrCodeAvailable && selectedSessionId) {
            const prefix = sessionEndpoint("/login/qr-code");
            if (!qrCodeElement.src.includes(prefix)) {
                qrCodeElement.src = `${prefix}?t=${Date.now()}`;
            }
        }
    }

    async function loadBots(createWhenEmpty = true) {
        bots = await requestJson(BOTS_ENDPOINT);
        if (bots.length === 0 && createWhenEmpty) {
            const created = await requestJson(BOTS_ENDPOINT, {method: "POST"});
            bots = [created];
            selectedSessionId = created.sessionId;
        }
        renderBotOptions();
    }

    async function updateStatus() {
        try {
            if (!selectedSessionId) {
                await loadBots(true);
            }
            if (!selectedSessionId) {
                renderStatus(null);
                return;
            }
            const status = await requestJson(sessionEndpoint("/login/status"));
            const index = bots.findIndex(bot => bot.sessionId === status.sessionId);
            if (index >= 0) {
                bots[index] = status;
                renderBotOptions();
            }
            renderStatus(status);
        } catch (error) {
            statusElement.textContent = "无法获取 Bot 登录状态";
            notificationElement.className = "notification notification--error";
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        }
    }

    async function createBot() {
        addBotButton.disabled = true;
        try {
            const created = await requestJson(BOTS_ENDPOINT, {method: "POST"});
            bots.push(created);
            selectedSessionId = created.sessionId;
            qrCodeElement.removeAttribute("src");
            renderBotOptions();
            renderStatus(created);
        } catch (error) {
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        } finally {
            addBotButton.disabled = false;
        }
    }

    async function refreshQrCode() {
        refreshButton.disabled = true;
        refreshButton.textContent = "正在刷新…";
        try {
            const status = await requestJson(sessionEndpoint("/login/refresh"), {method: "POST"});
            qrCodeElement.src = `${sessionEndpoint("/login/qr-code")}?t=${Date.now()}`;
            renderStatus(status);
        } catch (error) {
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        } finally {
            refreshButton.disabled = false;
            refreshButton.textContent = "刷新当前 Bot 二维码";
        }
    }

    async function removeCurrentBot() {
        if (!selectedSessionId || !window.confirm("确定移除当前 Bot 会话吗？")) {
            return;
        }
        removeBotButton.disabled = true;
        try {
            await requestJson(sessionEndpoint(), {method: "DELETE"});
            bots = bots.filter(bot => bot.sessionId !== selectedSessionId);
            selectedSessionId = bots[0]?.sessionId || null;
            qrCodeElement.removeAttribute("src");
            renderBotOptions();
            if (!selectedSessionId) {
                await loadBots(true);
            }
            await updateStatus();
        } catch (error) {
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        } finally {
            removeBotButton.disabled = false;
        }
    }

    botSelect.addEventListener("change", () => {
        selectedSessionId = botSelect.value;
        localStorage.setItem(SELECTED_SESSION_KEY, selectedSessionId);
        qrCodeElement.removeAttribute("src");
        updateStatus();
    });
    qrCodeElement.addEventListener("error", () => {
        qrMaskElement.hidden = false;
    });
    qrCodeElement.addEventListener("load", () => {
        qrMaskElement.hidden = true;
    });
    refreshButton.addEventListener("click", refreshQrCode);
    addBotButton.addEventListener("click", createBot);
    removeBotButton.addEventListener("click", removeCurrentBot);

    loadBots(true).then(updateStatus).catch(error => {
        errorElement.textContent = error.message;
        errorElement.hidden = false;
    });
    window.setInterval(updateStatus, POLL_INTERVAL_MS);
})();
