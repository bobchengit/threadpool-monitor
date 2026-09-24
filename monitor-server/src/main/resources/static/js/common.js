/* 公共工具：API 封装、时间格式化、状态元信息 */
(function (global) {
    'use strict';

    /**
     * 调用后端接口：code!=0 时抛错（message 为后端提示）
     */
    async function api(url, options) {
        options = options || {};
        var init = {
            method: options.method || 'GET',
            headers: { 'Content-Type': 'application/json' }
        };
        if (options.body !== undefined) {
            init.body = JSON.stringify(options.body);
        }
        var resp;
        try {
            resp = await fetch(url, init);
        } catch (e) {
            throw new Error('网络请求失败: ' + e.message);
        }
        var body = null;
        try {
            body = await resp.json();
        } catch (e) {
            /* 非 JSON 响应 */
        }
        if (!resp.ok || !body || body.code !== 0) {
            var msg = body && body.message ? body.message : 'HTTP ' + resp.status;
            throw new Error(msg);
        }
        return body.data;
    }

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    /** 毫秒时间戳 -> MM-dd HH:mm:ss */
    function fmtTime(ts) {
        if (!ts) { return '-'; }
        var d = new Date(ts);
        return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' '
            + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    }

    /** 毫秒时间戳 -> yyyy-MM-dd HH:mm:ss */
    function fmtDateTime(ts) {
        if (!ts) { return '-'; }
        var d = new Date(ts);
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' '
            + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    }

    /** 数据源拉取状态 -> {text, color} */
    function statusMeta(status, enabled) {
        if (!enabled) { return { text: '已停用', color: '#909399' }; }
        if (status === 'OK') { return { text: '在线', color: '#67C23A' }; }
        if (status === 'FAIL') { return { text: '异常', color: '#F56C6C' }; }
        return { text: '未知', color: '#909399' };
    }

    /** HTML 元素 id 安全化 */
    function safeId(s) {
        return String(s).replace(/[^a-zA-Z0-9_-]/g, '_');
    }

    global.AppUtil = {
        api: api,
        fmtTime: fmtTime,
        fmtDateTime: fmtDateTime,
        statusMeta: statusMeta,
        safeId: safeId
    };
})(window);
