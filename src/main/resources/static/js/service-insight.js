(function () {
    // ====== 0) 데이터 파싱 ======
    const dataElement = document.getElementById('serviceInsightData');
    if (!dataElement) return;

    let payload = {};
    try {
        payload = JSON.parse(dataElement.textContent || '{}');
    } catch (err) {
        console.warn('Failed to parse service insight payload.', err);
    }

    const metricOptions = Array.isArray(payload.metricOptions) ? payload.metricOptions : [];
    const seriesList    = Array.isArray(payload.series)        ? payload.series        : [];
    const componentLabels = (payload.componentLabels && typeof payload.componentLabels === 'object')
        ? payload.componentLabels : {};

    // metric → meta/series 매핑
    const optionMap = new Map(metricOptions.map(o => [o.metric, o]));
    const seriesMap = new Map(seriesList.map(s => [s.metric, s]));

    // 서버 enum 키 (백엔드와 1:1) :
    // COLLECTION_TOTAL_COUNT, COLLECTION_PRIVATE_RATIO,
    // BIRD_ID_PENDING_COUNT, BIRD_ID_RESOLVED_COUNT, BIRD_ID_RESOLUTION_STATS
    const METRICS = {
        TOTAL_COUNT: 'COLLECTION_TOTAL_COUNT',
        PRIVATE_RATIO: 'COLLECTION_PRIVATE_RATIO',
        PENDING_COUNT: 'BIRD_ID_PENDING_COUNT',
        RESOLVED_COUNT: 'BIRD_ID_RESOLVED_COUNT',
        RESOLUTION_STATS: 'BIRD_ID_RESOLUTION_STATS'
    };

    // ====== 1) DOM 핸들 ======
    const questionButtons = Array.from(document.querySelectorAll('[data-role="si-question"]'));
    const viewPrivacy     = document.getElementById('siq-privacy');
    const viewIdRequests  = document.getElementById('siq-idRequests');
    const emptyState      = document.getElementById('serviceInsightEmptyState');

    // ====== 2) 차트 생성 유틸 ======
    const PALETTE = [
        '#2563eb', '#16a34a', '#dc2626', '#f97316', '#9333ea',
        '#0ea5e9', '#059669', '#ea580c', '#3b82f6', '#14b8a6'
    ];
    let colorCursor = 0;
    const nextColor = () => PALETTE[(colorCursor++) % PALETTE.length];

    const axisByUnit = { COUNT: 'count', RATIO: 'ratio', HOURS: 'hours' };

    function createBaseOptions(yAxisKey) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'nearest', intersect: false },
            elements: { line: { borderWidth: 2 }, point: { radius: 0, hoverRadius: 4, hitRadius: 8 } },
            scales: {
                x: {
                    type: 'time',
                    time: { unit: 'day', tooltipFormat: 'yyyy-LL-dd' },
                    ticks: { autoSkip: true, maxRotation: 0 },
                    grid: { color: 'rgba(148, 163, 184, 0.2)' }
                },
                count: {
                    display: yAxisKey === 'count',
                    position: 'left',
                    beginAtZero: true,
                    ticks: {
                        precision: 0,
                        maxTicksLimit: 8,
                        callback: (v) => (Number.isInteger(v) ? v.toLocaleString() : '')
                    }
                },
                ratio: {
                    display: yAxisKey === 'ratio',
                    position: 'right',
                    beginAtZero: true,
                    min: 0, max: 100,
                    grid: { drawOnChartArea: false },
                    ticks: { stepSize: 10, maxTicksLimit: 6, callback: (v) => `${v}%` }
                },
                hours: {
                    display: yAxisKey === 'hours',
                    position: 'right',
                    beginAtZero: true,
                    grid: { drawOnChartArea: false },
                    ticks: { maxTicksLimit: 6, callback: (v) => (typeof v === 'number' ? v.toFixed(1) : v) }
                }
            },
            plugins: {
                legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8, boxHeight: 8 } },
                tooltip: {
                    mode: 'index', intersect: false,
                    callbacks: {
                        label(ctx) {
                            const unit = ctx.dataset?._saUnit;
                            const label = ctx.dataset?.label || '';
                            const value = ctx.parsed.y;
                            const formatted = formatValue(value, unit);
                            return label ? `${label}: ${formatted}` : formatted;
                        }
                    }
                }
            }
        };
    }

    function newLineChart(canvasId, yAxisKey) {
        const canvas = document.getElementById(canvasId);
        if (!canvas || typeof Chart === 'undefined') return null;
        return new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: { datasets: [] },
            options: createBaseOptions(yAxisKey)
        });
    }

    function toChartPoints(points, unit) {
        if (!Array.isArray(points)) return [];
        return points
            .filter(p => p && p.date != null && p.value != null)
            .map(p => ({ x: p.date, y: normalizeValue(p.value, unit) }));
    }

    function normalizeValue(num, unit) {
        const v = typeof num === 'number' ? num : Number(num);
        if (!Number.isFinite(v)) return null;
        if (unit === 'RATIO') return v <= 1.000001 ? v * 100 : v;
        return v;
    }

    function formatValue(v, unit) {
        if (!Number.isFinite(v)) return '-';
        switch (unit) {
            case 'RATIO': return `${v.toFixed(1)}%`;
            case 'HOURS': return `${v.toFixed(2)}시간`;
            default:      return Math.round(v).toLocaleString();
        }
    }

    // nice scale per chart
    function applyNiceYAxis(chart, axisKey) {
        if (!chart) return;
        const ds = chart.data.datasets || [];
        let min = Infinity, max = -Infinity, used = false;
        ds.forEach(d => {
            if (d.yAxisID !== axisKey) return;
            (d.data || []).forEach(pt => {
                if (pt && Number.isFinite(pt.y)) { used = true; min = Math.min(min, pt.y); max = Math.max(max, pt.y); }
            });
        });
        if (!used) return;

        if (min === max) { min -= 1; max += 1; }
        const nice = niceScale(min, max, axisKey === 'count' ? 7 : 6, axisKey === 'count');
        const s = chart.options.scales[axisKey];
        s.min = nice.min;
        s.max = nice.max;
        s.ticks.stepSize = nice.step;
        if (axisKey === 'count') s.ticks.precision = 0;
    }

    function niceScale(min, max, maxTicks = 6, integerOnly = false) {
        const range = Math.max(1e-9, max - min);
        let step = niceNum(range / Math.max(2, (maxTicks - 1)), true);
        if (integerOnly) step = Math.max(1, Math.round(step));
        const niceMin = Math.floor(min / step) * step;
        const niceMax = Math.ceil(max / step) * step;
        return { min: niceMin, max: niceMax, step };
    }
    function niceNum(x, round) {
        const exp = Math.floor(Math.log10(x));
        const f = x / Math.pow(10, exp);
        let nf;
        if (round) { nf = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10; }
        else { nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10; }
        return nf * Math.pow(10, exp);
    }

    // ====== 3) 시리즈 접근 ======
    function getPoints(metricKey) {
        const s = seriesMap.get(metricKey);
        if (!s) return [];
        const unit = optionMap.get(metricKey)?.unit || 'COUNT';
        return toChartPoints(s.points || [], unit);
    }

    function getResolutionComponents() {
        const s = seriesMap.get(METRICS.RESOLUTION_STATS);
        const comps = Array.isArray(s?.components) ? s.components : [];
        // key → label 매핑
        const labels = componentLabels[METRICS.RESOLUTION_STATS] || {};
        // 필요한 키만 추출
        const byKey = new Map();
        comps.forEach(c => byKey.set(c.key, c.points || []));
        return {
            min: { label: labels['min_hours'] || '최소', points: byKey.get('min_hours') || [] },
            avg: { label: labels['avg_hours'] || '평균', points: byKey.get('avg_hours') || [] },
            max: { label: labels['max_hours'] || '최대', points: byKey.get('max_hours') || [] }
            // stddev는 시각적 혼잡도 때문에 일단 제외
        };
    }

    // ====== 4) 차트 인스턴스 (필요할 때 생성) ======
    const charts = {
        total: null,
        ratio: null,
        idSummary: null,
        idTime: null
    };

    function ensurePrivacyCharts() {
        if (!charts.total) charts.total = newLineChart('chart-total-count', 'count');
        if (!charts.ratio) charts.ratio = newLineChart('chart-private-ratio', 'ratio');

        // 데이터 바인딩
        if (charts.total) {
            const unit = optionMap.get(METRICS.TOTAL_COUNT)?.unit || 'COUNT';
            charts.total.data.datasets = [{
                label: optionMap.get(METRICS.TOTAL_COUNT)?.label || '누적 새록 수',
                data: getPoints(METRICS.TOTAL_COUNT),
                borderColor: '#2563eb',
                backgroundColor: '#2563eb',
                tension: 0.25,
                yAxisID: axisByUnit[unit] || 'count',
                _saUnit: unit
            }];
            applyNiceYAxis(charts.total, 'count');
            charts.total.update();
        }

        if (charts.ratio) {
            const unit = optionMap.get(METRICS.PRIVATE_RATIO)?.unit || 'RATIO';
            charts.ratio.data.datasets = [{
                label: optionMap.get(METRICS.PRIVATE_RATIO)?.label || '비공개 새록 비율',
                data: getPoints(METRICS.PRIVATE_RATIO),
                borderColor: '#16a34a',
                backgroundColor: '#16a34a',
                tension: 0.25,
                yAxisID: axisByUnit[unit] || 'ratio',
                _saUnit: unit
            }];
            applyNiceYAxis(charts.ratio, 'ratio'); // 0~100 내에서 step 조정
            charts.ratio.update();
        }
    }

    function ensureIdCharts() {
        if (!charts.idSummary) charts.idSummary = newLineChart('chart-id-summary', 'count');
        if (!charts.idTime)    charts.idTime    = newLineChart('chart-id-resolution-time', 'hours');

        // 요약(동정 요청/해결) : 두 라인
        if (charts.idSummary) {
            const pUnit = optionMap.get(METRICS.PENDING_COUNT)?.unit || 'COUNT';
            const rUnit = optionMap.get(METRICS.RESOLVED_COUNT)?.unit || 'COUNT';
            charts.idSummary.data.datasets = [
                {
                    label: optionMap.get(METRICS.PENDING_COUNT)?.label || '진행 중인 동정 요청',
                    data: getPoints(METRICS.PENDING_COUNT),
                    borderColor: '#dc2626',
                    backgroundColor: '#dc2626',
                    tension: 0.25,
                    yAxisID: axisByUnit[pUnit] || 'count',
                    _saUnit: pUnit
                },
                {
                    label: optionMap.get(METRICS.RESOLVED_COUNT)?.label || '누적 동정 해결 수',
                    data: getPoints(METRICS.RESOLVED_COUNT),
                    borderColor: '#9333ea',
                    backgroundColor: '#9333ea',
                    tension: 0.25,
                    borderDash: [6, 4],
                    yAxisID: axisByUnit[rUnit] || 'count',
                    _saUnit: rUnit
                }
            ];
            applyNiceYAxis(charts.idSummary, 'count');
            charts.idSummary.update();
        }

        // 해결 시간 : 평균(굵게) + 최소/최대(점선)
        if (charts.idTime) {
            const unit = optionMap.get(METRICS.RESOLUTION_STATS)?.unit || 'HOURS';
            const comps = getResolutionComponents();
            charts.idTime.data.datasets = [
                {
                    label: `평균`,
                    data: toChartPoints(comps.avg.points, unit),
                    borderColor: '#0ea5e9',
                    backgroundColor: '#0ea5e9',
                    borderWidth: 3,
                    tension: 0.25,
                    yAxisID: axisByUnit[unit] || 'hours',
                    _saUnit: unit
                },
                {
                    label: `최소`,
                    data: toChartPoints(comps.min.points, unit),
                    borderColor: '#14b8a6',
                    backgroundColor: '#14b8a6',
                    tension: 0.25,
                    borderDash: [4, 4],
                    yAxisID: axisByUnit[unit] || 'hours',
                    _saUnit: unit
                },
                {
                    label: `최대`,
                    data: toChartPoints(comps.max.points, unit),
                    borderColor: '#f97316',
                    backgroundColor: '#f97316',
                    tension: 0.25,
                    borderDash: [4, 4],
                    yAxisID: axisByUnit[unit] || 'hours',
                    _saUnit: unit
                }
            ];
            applyNiceYAxis(charts.idTime, 'hours');
            charts.idTime.update();
        }
    }

    // ====== 5) 질문 토글 (동시에 하나만 on) ======
    function showQuestion(key) {
        // 버튼 상태
        questionButtons.forEach(btn => {
            const isActive = btn.getAttribute('data-question-key') === key;
            btn.classList.toggle('is-active', isActive);
        });

        // 뷰 전환
        viewPrivacy.classList.toggle('d-none', key !== 'privacy');
        viewIdRequests.classList.toggle('d-none', key !== 'idRequests');

        // 해당 그룹 차트 lazy 생성/업데이트
        if (key === 'privacy') ensurePrivacyCharts();
        if (key === 'idRequests') ensureIdCharts();

        // 빈 상태 처리
        const anyData =
            (charts.total?.data.datasets?.[0]?.data?.length || 0) +
            (charts.ratio?.data.datasets?.[0]?.data?.length || 0) +
            (charts.idSummary?.data.datasets?.reduce((a,d)=>a+(d.data?.length||0),0) || 0) +
            (charts.idTime?.data.datasets?.reduce((a,d)=>a+(d.data?.length||0),0) || 0);
        emptyState?.classList.toggle('d-none', anyData > 0);
    }

    questionButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const key = btn.getAttribute('data-question-key');
            if (!key) return;
            showQuestion(key);
        });
    });

    // 초기에는 아무것도 선택 안 함(기획 의도)
    // 필요하면 여기서 기본으로 'privacy' 또는 'idRequests'를 선택하도록 바꿔도 됨.
})();
