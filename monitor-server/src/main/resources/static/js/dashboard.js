/* 监控大屏：数据源 tab 单选切换（控制 DOM 规模）+ 每线程池 4 图
 * （队列仪表盘 / 线程活动 / 队列堆积 / 任务吞吐）
 * 刷新策略：轮询间隔自适应数据源设置的拉取频率——
 * overview 取最快启用源的频率（夹在 1~10s，刷新角标/统计/仪表盘），
 * history 取当前 tab 源的频率（夹在 1~30s，仅拉当前 tab 折线）；
 * 修改拉取频率后，下一轮询周期自动按新间隔执行，document.hidden 时暂停。
 * 注意：setup 必须返回 reactive 的 state 本体，模板才能保持响应性。 */
(function () {
    'use strict';

    const { createApp, reactive, computed, nextTick, onMounted, watch } = Vue;
    const { api, fmtTime, safeId } = AppUtil;

    const OVERVIEW_MIN_MS = 1000, OVERVIEW_MAX_MS = 10000;
    const HISTORY_MIN_MS = 1000, HISTORY_MAX_MS = 30000;
    const AXIS_TEXT = { color: '#909399', fontSize: 10 };
    const GRID = { left: 38, right: 12, top: 24, bottom: 22 };

    /* ---------- ECharts 选项 ---------- */

    function gaugeOption(threshold) {
        const th = threshold / 100;
        return {
            series: [{
                type: 'gauge',
                min: 0, max: 100,
                radius: '98%', center: ['50%', '60%'],
                startAngle: 210, endAngle: -30,
                axisLine: {
                    lineStyle: {
                        width: 12,
                        color: [[Math.max(0, th - 0.2), '#67C23A'], [th, '#E6A23C'], [1, '#F56C6C']]
                    }
                },
                pointer: { length: '55%', width: 4, itemStyle: { color: 'auto' } },
                axisTick: { show: false },
                splitLine: { length: 6, distance: -16, lineStyle: { color: '#fff', width: 1 } },
                axisLabel: { show: false },
                detail: {
                    formatter: '{value}%',
                    fontSize: 16, fontWeight: 600,
                    offsetCenter: [0, '62%'], color: '#303133'
                },
                data: [{ value: 0 }]
            }]
        };
    }

    /** 时间轴标签格式随范围自适应：≤10分钟带秒，≤6小时仅时分，24小时带日期 */
    function axisFormatter(rangeMs) {
        return rangeMs <= 10 * 60 * 1000 ? '{HH}:{mm}:{ss}'
            : rangeMs <= 6 * 60 * 60 * 1000 ? '{HH}:{mm}'
            : '{MM}-{dd} {HH}:{mm}';
    }

    function timeAxis(rangeMs) {
        return {
            type: 'time',
            axisLabel: Object.assign(
                { formatter: axisFormatter(rangeMs), hideOverlap: true }, AXIS_TEXT),
            axisLine: { lineStyle: { color: '#DCDFE6' } },
            splitLine: { show: false }
        };
    }

    function valueAxis() {
        return {
            type: 'value',
            minInterval: 1,
            axisLabel: AXIS_TEXT,
            splitLine: { lineStyle: { color: '#F2F6FC' } }
        };
    }

    function activeOption(rangeMs) {
        return {
            animation: false,
            grid: GRID,
            tooltip: { trigger: 'axis', textStyle: { fontSize: 11 } },
            xAxis: timeAxis(rangeMs),
            yAxis: valueAxis(),
            series: [
                {
                    name: '活跃线程', type: 'line', showSymbol: false, data: [],
                    lineStyle: { width: 1.5, color: '#409EFF' }, itemStyle: { color: '#409EFF' }
                },
                {
                    name: '当前线程', type: 'line', showSymbol: false, data: [],
                    lineStyle: { width: 1.2, color: '#909399' }, itemStyle: { color: '#909399' }
                }
            ]
        };
    }

    function queueOption(rangeMs) {
        return {
            animation: false,
            grid: GRID,
            tooltip: { trigger: 'axis', textStyle: { fontSize: 11 } },
            xAxis: timeAxis(rangeMs),
            yAxis: valueAxis(),
            series: [{
                name: '队列任务数', type: 'line', showSymbol: false, data: [],
                lineStyle: { width: 1.5, color: '#E6A23C' }, itemStyle: { color: '#E6A23C' },
                areaStyle: { opacity: 0.15 }
            }]
        };
    }

    function tputOption(rangeMs) {
        return {
            animation: false,
            grid: GRID,
            tooltip: {
                trigger: 'axis', textStyle: { fontSize: 11 },
                valueFormatter: v => (v == null ? '-' : v.toFixed(1) + ' /s')
            },
            xAxis: timeAxis(rangeMs),
            yAxis: valueAxis(),
            series: [{
                name: '任务吞吐', type: 'line', showSymbol: false, data: [],
                lineStyle: { width: 1.5, color: '#67C23A' }, itemStyle: { color: '#67C23A' },
                areaStyle: { opacity: 0.12 }
            }]
        };
    }

    createApp({
        setup() {
            const state = reactive({
                sources: [],          // 全部数据源（tab 列表与全局统计）
                activeDsId: null,     // 当前 tab 选中的数据源 id
                pickedPools: [],      // 当前 tab 手动指定的线程池名；空 = 展示全部
                rangeMs: 5 * 60 * 1000,
                serverOffset: 0,      // 服务器时钟 - 客户端时钟（毫秒），history 取数用服务器时间线
                analysis: null,       // 当前展示的容量分析报告（实时计算，不缓存）
                analysisLoadingId: null, // 正在分析的数据源 id（null = 无）
                analysisOpen: false,
                layout: (function () {
                    try { return localStorage.getItem('tpm.layout') || 'flow'; }
                    catch (e) { return 'flow'; }   // flow=网格流式 / tiled=横向平铺
                })()
            });
            const ranges = [
                { label: '5分钟', ms: 5 * 60 * 1000 },
                { label: '10分钟', ms: 10 * 60 * 1000 },
                { label: '30分钟', ms: 30 * 60 * 1000 },
                { label: '1小时', ms: 60 * 60 * 1000 },
                { label: '6小时', ms: 6 * 60 * 60 * 1000 },
                { label: '24小时', ms: 24 * 60 * 60 * 1000 }
            ];

            // key: "{dsId}-{poolName}" -> {gauge, active, queue, tput}
            const charts = new Map();
            let overviewTimer = null;
            let historyTimer = null;

            /** 当前 tab 的数据源（只渲染这一组线程池卡片，控制 DOM 规模） */
            const activeSource = computed(() =>
                state.sources.find(s => s.id === state.activeDsId) || null);

            /** 当前 tab 实际展示的线程池：手动指定时只渲染选中的，未指定 = 全部 */
            const visiblePools = computed(() => {
                const ds = activeSource.value;
                if (!ds) { return []; }
                const pools = ds.pools || [];
                return state.pickedPools.length
                    ? pools.filter(p => state.pickedPools.includes(p.poolName))
                    : pools;
            });

            /** 全局统计：基于所有数据源 */
            const stats = computed(() => ({
                total: state.sources.length,
                online: state.sources.filter(s => s.status === 'OK').length,
                alertPools: state.sources.reduce((acc, s) => acc + (s.alertPoolCount || 0), 0)
            }));

            /** 最近一次成功拉取任一数据源的时间（服务器时钟），顶栏"数据更新于" */
            const latestSuccess = computed(() => {
                let max = 0;
                state.sources.forEach(s => { if (s.lastSuccessTime && s.lastSuccessTime > max) { max = s.lastSuccessTime; } });
                return max || null;
            });

            function selectDs(id) {
                if (state.activeDsId === id) { return; }
                state.activeDsId = id;
                // 分析报告与数据源绑定：切 tab 关闭弹窗，避免误读旧源报告
                state.analysis = null;
                state.analysisOpen = false;
                state.pickedPools = [];   // 切换数据源后池筛选重置为"全部"
                nextTick(() => rebuildCharts());
            }

            /** 手动指定/取消展示某个线程池（多选） */
            function togglePool(name) {
                const i = state.pickedPools.indexOf(name);
                if (i >= 0) { state.pickedPools.splice(i, 1); }
                else { state.pickedPools.push(name); }
                nextTick(() => rebuildCharts());
            }

            /** 清空指定 = 展示全部线程池 */
            function clearPools() {
                if (!state.pickedPools.length) { return; }
                state.pickedPools = [];
                nextTick(() => rebuildCharts());
            }

            function setRange(ms) {
                state.rangeMs = ms;
                updateAxisFormat();
                refreshHistory();
            }

            /** 时间范围变化后，联动更新已渲染折线图的时间轴标签格式 */
            function updateAxisFormat() {
                const label = { formatter: axisFormatter(state.rangeMs), hideOverlap: true };
                charts.forEach(c => {
                    [c.active, c.queue, c.tput].forEach(ch => {
                        if (ch) { ch.setOption({ xAxis: { axisLabel: label } }); }
                    });
                });
            }

            function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '…' : s; }

            /* ---------- 容量分析（按数据源隔离） ---------- */

            const LEVEL_TEXT = {
                GOOD: '匹配良好', OK: '基本匹配', WARN: '需关注',
                CRIT: '线程不足', IDLE: '空闲', NO_DATA: '样本不足'
            };
            function levelText(level) { return LEVEL_TEXT[level] || level; }

            /** 当前 tab 的分析结果（报告或错误态），弹窗内容随之切换 */
            /** 当前展示的分析报告（实时计算，切源/重开都会重新计算） */
            const activeAnalysis = computed(() => state.analysis);

            /** 分析报告中的池条目：跟随界面上选择的线程池过滤（"全部"= 不过滤） */
            const analysisPools = computed(() => {
                const a = activeAnalysis.value;
                if (!a || a.error || !a.pools) { return []; }
                if (!state.pickedPools.length) { return a.pools; }
                const picked = state.pickedPools;
                return a.pools.filter(p => picked.includes(p.poolName));
            });

            /** 各评估等级的池数量（弹窗概览条统计用） */
            const levelCounts = computed(() => {
                const c = {};
                analysisPools.value.forEach(p => { c[p.level] = (c[p.level] || 0) + 1; });
                return c;
            });

            /** 弹窗内池导航：滚动列表定位到目标池卡片并闪烁提示 */
            function jumpTo(poolName) {
                const list = document.querySelector('.analysis-modal .an-list');
                const el = document.getElementById('anp-' + AppUtil.safeId(poolName));
                if (!list || !el) { return; }
                // 视口坐标差 = 列表需要滚动的增量（比 scrollIntoView 在嵌套滚动容器下行为更确定）
                list.scrollTop += el.getBoundingClientRect().top - list.getBoundingClientRect().top;
                el.classList.remove('flash');
                void el.offsetWidth;   // 强制重绘以重启动画
                el.classList.add('flash');
            }

            const ANALYSIS_TIMEOUT_MS = 15000;

            /**
             * 容量分析：实时计算。每次点击都向后端发起分析请求（后端基于窗口快照即时计算），
             * 弹窗立即打开展示"分析中"骨架，完成后渲染报告。
             * @param force true = 失败重试，保留弹窗中已有内容直到新结果返回
             * 注意：模板中必须写 showAnalysis() / showAnalysis(true)，
             * 直接 @click="showAnalysis" 会把鼠标事件对象误传为 force。
             */
            async function showAnalysis(force) {
                const ds = activeSource.value;
                if (!ds || state.analysisLoadingId) { return; }
                state.analysisOpen = true;   // 立即开窗展示 loading 态
                if (!force) { state.analysis = null; }  // 实时计算：每次打开都重新请求
                state.analysisLoadingId = ds.id;
                try {
                    // fetch 无内建超时，后端挂起时 Promise.race 保证 15s 后释放按钮，
                    // 避免分析永远转圈、需要刷新页面
                    const data = await Promise.race([
                        api('/api/metrics/analysis?datasourceId=' + ds.id),
                        new Promise((_, rej) => setTimeout(
                            () => rej(new Error('分析请求超时，请稍后重试')), ANALYSIS_TIMEOUT_MS))
                    ]);
                    state.analysis = data;
                    // 重开/刷新报告后列表回到顶部，避免停留在上次的滚动位置
                    nextTick(() => {
                        const list = document.querySelector('.analysis-modal .an-list');
                        if (list) { list.scrollTop = 0; }
                    });
                } catch (e) {
                    // 错误展示在弹窗内并提供重试，不弹 alert 阻塞页面
                    state.analysis = { error: e.message || '分析请求失败' };
                } finally {
                    state.analysisLoadingId = null;
                }
            }
            function closeAnalysis() { state.analysisOpen = false; }

            /** 布局切换：flow=网格流式 / tiled=横向平铺（localStorage 持久化） */
            function setLayout(l) {
                if (state.layout === l) { return; }
                state.layout = l;
                try { localStorage.setItem('tpm.layout', l); } catch (e) { /* 隐私模式忽略 */ }
                // 卡片尺寸变化后 echarts 容器尺寸已变，需要 resize 重算画布
                nextTick(() => charts.forEach(c =>
                    ['gauge', 'active', 'queue', 'tput'].forEach(k => c[k] && c[k].resize())));
            }

            /** 弹窗打开时锁定主页面滚动（html+body 双锁），
             *  配合 CSS overscroll-behavior: contain 阻断弹窗内滚动穿透 */
            watch(() => state.analysisOpen, (open) => {
                const v = open ? 'hidden' : '';
                document.documentElement.style.overflow = v;
                document.body.style.overflow = v;
            });

            /* ---------- 图表生命周期 ---------- */

            function initCardCharts(key, threshold) {
                if (charts.has(key)) { return; }
                const gaugeEl = document.getElementById('gauge-' + safeId(key));
                const activeEl = document.getElementById('active-' + safeId(key));
                const queueEl = document.getElementById('queue-' + safeId(key));
                const tputEl = document.getElementById('tput-' + safeId(key));
                if (!gaugeEl || !activeEl || !queueEl || !tputEl) { return; }

                // 注意：echarts 的 setOption() 无返回值，必须先 init 拿到实例再 setOption
                const rangeMs = state.rangeMs;
                const gauge = echarts.init(gaugeEl);
                gauge.setOption(gaugeOption(threshold));
                const active = echarts.init(activeEl);
                active.setOption(activeOption(rangeMs));
                const queue = echarts.init(queueEl);
                queue.setOption(queueOption(rangeMs));
                const tput = echarts.init(tputEl);
                tput.setOption(tputOption(rangeMs));
                charts.set(key, { gauge: gauge, active: active, queue: queue, tput: tput });
            }

            function initAllCharts() {
                const ds = activeSource.value;
                if (!ds) { return; }
                visiblePools.value.forEach(p => {
                    initCardCharts(ds.id + '-' + p.poolName, ds.queueAlertThreshold || 80);
                });
            }

            /** 销毁不再展示的图表实例（tab 切换 / 池筛选变化 / 池被移除时） */
            function disposeRemovedCharts() {
                const alive = new Set();
                visiblePools.value.forEach(p => alive.add(activeSource.value.id + '-' + p.poolName));
                charts.forEach((c, key) => {
                    if (!alive.has(key)) {
                        ['gauge', 'active', 'queue', 'tput'].forEach(k => c[k] && c[k].dispose());
                        charts.delete(key);
                    }
                });
            }

            /** tab 切换后的完整重建：清理旧实例 → 初始化新卡片 → 刷新数据 */
            function rebuildCharts() {
                disposeRemovedCharts();
                initAllCharts();
                updateGauges();
                refreshHistory();
            }

            /* ---------- 图表更新 ---------- */

            function updateGauges() {
                if (!activeSource.value) { return; }
                const ds = activeSource.value;
                visiblePools.value.forEach(p => {
                    const c = charts.get(ds.id + '-' + p.poolName);
                    if (c) {
                        c.gauge.setOption({ series: [{ data: [{ value: p.queueUsagePercent || 0 }] }] });
                    }
                });
            }

            function markLine(yValue, label, color) {
                return {
                    silent: true, symbol: 'none',
                    lineStyle: { type: 'dashed', color: color },
                    label: { formatter: label, fontSize: 10, color: color },
                    data: [{ yAxis: yValue }]
                };
            }

            function updateLineCharts(key, rows, threshold) {
                const c = charts.get(key);
                if (!c || !rows.length) { return; }
                // 高频拉取（最小 1s）+ 长时间范围可能产生数万点位，>2000 时等间隔抽样
                //（吞吐按 diff/实际时间差计算，不受抽样影响）；保留末点保证标记线取最新值
                if (rows.length > 2000) {
                    const step = Math.ceil(rows.length / 2000);
                    const sampled = [];
                    for (let i = 0; i < rows.length; i += step) { sampled.push(rows[i]); }
                    if (sampled[sampled.length - 1] !== rows[rows.length - 1]) {
                        sampled.push(rows[rows.length - 1]);
                    }
                    rows = sampled;
                }
                const latest = rows[rows.length - 1];

                // 线程活动：活跃 + 当前线程，虚线 = 最大线程数
                c.active.setOption({
                    series: [
                        {
                            data: rows.map(r => [r.time, r.activeCount]),
                            markLine: markLine(latest.maxPoolSize, 'max ' + latest.maxPoolSize, '#C0C4CC')
                        },
                        { data: rows.map(r => [r.time, r.poolSize]) }
                    ]
                });

                // 队列堆积：面积 + 告警线（队列容量 × 阈值）
                const alertLine = Math.round(latest.queueCapacity * threshold / 100);
                c.queue.setOption({
                    series: [{
                        data: rows.map(r => [r.time, r.queueSize]),
                        markLine: markLine(alertLine, '告警 ' + alertLine, '#F56C6C')
                    }]
                });

                // 任务吞吐：completedTaskCount 相邻差值 / 时间差（个数/秒）
                const tput = [];
                for (let i = 1; i < rows.length; i++) {
                    const dt = (rows[i].time - rows[i - 1].time) / 1000;
                    if (dt > 0) {
                        const diff = rows[i].completedTaskCount - rows[i - 1].completedTaskCount;
                        tput.push([rows[i].time, Math.max(0, diff / dt)]);
                    }
                }
                c.tput.setOption({ series: [{ data: tput }] });
            }

            /* ---------- 数据拉取 ---------- */

            async function refreshOverview() {
                if (document.hidden) { return; }
                try {
                    const data = await api('/api/metrics/overview');
                    state.sources = data.datasources || [];
                    // 时钟校准：以服务器时间为基准（兼容服务器与客户端时钟不一致）
                    if (data.serverTime) { state.serverOffset = data.serverTime - Date.now(); }
                    // 选中 tab 无效（首次加载 / 数据源被删）时落到第一个
                    if (!state.sources.some(s => s.id === state.activeDsId)) {
                        state.activeDsId = state.sources.length ? state.sources[0].id : null;
                        state.pickedPools = [];
                    }
                    disposeRemovedCharts();
                    await nextTick();
                    initAllCharts();
                    updateGauges();
                } catch (e) {
                    console.warn('overview 刷新失败', e);
                }
            }

            /** 只拉取当前 tab 数据源的历史，控制请求量与渲染量 */
            async function refreshHistory() {
                if (document.hidden || !activeSource.value) { return; }
                const ds = activeSource.value;
                // 窗口锚定服务器时间线：end = 客户端当前时刻 + 服务器时钟偏移
                const end = Date.now() + state.serverOffset;
                const start = end - state.rangeMs;
                const tasks = [];
                visiblePools.value.forEach(p => {
                    const key = ds.id + '-' + p.poolName;
                    if (!charts.has(key)) { return; }
                    tasks.push(
                        api('/api/metrics/history?datasourceId=' + ds.id
                            + '&poolName=' + encodeURIComponent(p.poolName)
                            + '&start=' + start + '&end=' + end)
                            .then(rows => updateLineCharts(key, rows, ds.queueAlertThreshold || 80))
                            .catch(e => console.warn('history 拉取失败 ' + key, e))
                    );
                });
                await Promise.all(tasks);
            }

            /* ---------- 自适应轮询：间隔跟随数据源设置的拉取频率 ---------- */

            /** overview 间隔 = 最快启用源的频率（夹在 1~10s），保证角标/统计/仪表盘跟手 */
            function overviewIntervalMs() {
                const minSec = state.sources
                    .filter(s => s.enabled !== false)
                    .reduce((m, s) => Math.min(m, s.pullIntervalSec || 30), Infinity);
                const ms = (isFinite(minSec) ? minSec : 30) * 1000;
                return Math.min(OVERVIEW_MAX_MS, Math.max(OVERVIEW_MIN_MS, ms));
            }

            /** history 间隔 = 当前 tab 源的频率（夹在 1~30s），折线粒度跟随设定 */
            function historyIntervalMs() {
                const ds = activeSource.value;
                const ms = (ds && ds.pullIntervalSec ? ds.pullIntervalSec : 30) * 1000;
                return Math.min(HISTORY_MAX_MS, Math.max(HISTORY_MIN_MS, ms));
            }

            // 每轮刷新结束后按最新配置重新调度（固定 setInterval 无法跟随配置变化）
            function scheduleOverview() {
                clearTimeout(overviewTimer);
                overviewTimer = setTimeout(async () => {
                    await refreshOverview();
                    scheduleOverview();
                }, overviewIntervalMs());
            }

            function scheduleHistory() {
                clearTimeout(historyTimer);
                historyTimer = setTimeout(async () => {
                    await refreshHistory();
                    scheduleHistory();
                }, historyIntervalMs());
            }

            onMounted(() => {
                refreshOverview().then(refreshHistory).then(() => {
                    scheduleOverview();
                    scheduleHistory();
                });
                window.addEventListener('resize', () => {
                    charts.forEach(c => ['gauge', 'active', 'queue', 'tput'].forEach(k => c[k] && c[k].resize()));
                });
            });

            // 直接返回 reactive state，保证模板响应性（解构会丢失响应式导致高亮/时间不更新）
            return { state, ranges, activeSource, visiblePools, activeAnalysis, analysisPools, levelCounts, stats, latestSuccess,
                     selectDs, togglePool, clearPools, setRange, setLayout, truncate, jumpTo,
                     showAnalysis, closeAnalysis, levelText, AppUtil };
        }
    }).mount('#app');
})();
