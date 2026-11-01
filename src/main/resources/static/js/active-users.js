// ===== resources/static/js/active-users.js =====
(function () {
    const dataElement = document.getElementById('activeUserData');
    if (!dataElement) return;

    const panelEndpoint = dataElement.dataset?.endpoint || '/service-insight/active-users/panel';

    let payload = {};
    try {
        payload = JSON.parse(dataElement.textContent || '{}');
    } catch (err) {
        console.warn('Failed to parse active user payload.', err);
    }

    // ---------- 상수 ----------
    const TZ = 'Asia/Seoul';
    const BUCKET_DAILY = 'DAILY';
    const BUCKET_WEEKLY = 'WEEKLY';
    const BUCKET_MONTHLY = 'MONTHLY';
    // 한 달은 1~7 / 8~14 / 15~21 / 22~28 / 29~ 로 끊어 최대 5주가 된다.
    const WEEK_ORDINALS = ['첫째', '둘째', '셋째', '넷째', '다섯째'];
    const MAX_WEEKS_PER_MONTH = WEEK_ORDINALS.length;
    const PANEL_COLORS = {
        DAILY: '#2563eb',
        WEEKLY: '#0ea5e9',
        MONTHLY: '#9333ea'
    };

    // ---------- 날짜 유틸 (KST 고정) ----------
    function toDateKST(value) {
        if (!value) return null;
        if (value instanceof Date) return value;
        const text = String(value).trim();
        const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(text);
        const L = window.luxon;
        if (L && L.DateTime) {
            let dt = L.DateTime.fromISO(text, { zone: TZ });
            if (!dt.isValid) dt = L.DateTime.fromJSDate(new Date(text), { zone: TZ });
            if (!dt.isValid) return null;
            if (dateOnly) dt = dt.startOf('day');
            return dt.toJSDate();
        }
        const parsed = new Date(text);
        if (isNaN(parsed.getTime())) return null;
        if (dateOnly) return new Date(parsed.getFullYear(), parsed.getMonth(), parsed.getDate());
        return parsed;
    }

    function kstParts(date) {
        const L = window.luxon;
        if (L && L.DateTime) {
            const dt = L.DateTime.fromJSDate(date, { zone: TZ });
            if (dt.isValid) return { year: dt.year, month: dt.month, day: dt.day };
        }
        return { year: date.getFullYear(), month: date.getMonth() + 1, day: date.getDate() };
    }

    function formatDateKST(date) {
        if (!date) return '';
        const { year, month, day } = kstParts(date);
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }

    function lastDayOfMonth(yearMonth) {
        const [year, month] = String(yearMonth || '').split('-').map(Number);
        if (!Number.isFinite(year) || !Number.isFinite(month)) return null;
        const last = new Date(year, month, 0);
        return `${year}-${String(month).padStart(2, '0')}-${String(last.getDate()).padStart(2, '0')}`;
    }

    // ---------- 버킷 집계 ----------
    function bucketKeyOf(date, bucket) {
        const { year, month, day } = kstParts(date);
        const monthKey = `${year}-${String(month).padStart(2, '0')}`;
        if (bucket === BUCKET_MONTHLY) return monthKey;
        return `${monthKey}-W${Math.min(MAX_WEEKS_PER_MONTH, Math.ceil(day / 7))}`;
    }

    function bucketLabelOf(key, bucket, withYear) {
        const text = String(key || '');
        const [yearPart, monthPart, weekPart] = text.split('-');
        const year = Number(yearPart);
        const month = Number(monthPart);
        if (!Number.isFinite(month)) return text;

        const yearPrefix = withYear && Number.isFinite(year) ? `${String(year).slice(2)}년 ` : '';
        if (bucket === BUCKET_MONTHLY) return `${yearPrefix}${month}월`;

        const weekIndex = Number(String(weekPart || '').replace('W', ''));
        const ordinal = WEEK_ORDINALS[weekIndex - 1] || `${weekIndex}`;
        return `${yearPrefix}${month}월 ${ordinal}주`;
    }

    /**
     * 일별 포인트를 주/월 버킷으로 묶는다.
     * WAU·MAU는 롤링 집계라서 버킷의 마지막 날 값이 그 기간 전체를 대표한다.
     */
    function aggregateToBuckets(points, bucket) {
        const byKey = new Map();
        (points || []).forEach(point => {
            const date = toDateKST(point?.date);
            const value = Number(point?.value);
            if (!date || !Number.isFinite(value)) return;
            const key = bucketKeyOf(date, bucket);
            const previous = byKey.get(key);
            if (!previous || date.getTime() >= previous.refDate.getTime()) {
                byKey.set(key, { key, value, refDate: date });
            }
        });
        return [...byKey.values()].sort((left, right) => (left.key < right.key ? -1 : (left.key > right.key ? 1 : 0)));
    }

    function toDailyPoints(points) {
        return (points || [])
            .map(point => {
                const x = toDateKST(point?.date);
                const y = Number(point?.value);
                return (x && Number.isFinite(y)) ? { x, y } : null;
            })
            .filter(Boolean);
    }

    function formatCount(value) {
        return Number.isFinite(value) ? Math.round(value).toLocaleString() : '-';
    }

    // ---------- 차트 ----------
    function makeChartOptions(bucket) {
        const bucketed = bucket !== BUCKET_DAILY;
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'nearest', intersect: false },
            elements: { line: { borderWidth: 2 }, point: { radius: 0, hoverRadius: 4, hitRadius: 8 } },
            scales: {
                x: bucketed
                    ? {
                        type: 'category',
                        offset: true,
                        ticks: { autoSkip: true, autoSkipPadding: 12, maxRotation: 0 },
                        grid: { color: 'rgba(148, 163, 184, 0.2)', drawOnChartArea: false }
                    }
                    : {
                        type: 'time',
                        time: { unit: 'day', round: 'day', tooltipFormat: 'yyyy-LL-dd', zone: TZ },
                        adapters: { date: { zone: TZ } },
                        ticks: { autoSkip: true, maxRotation: 0 },
                        grid: { color: 'rgba(148, 163, 184, 0.2)' }
                    },
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0,
                        maxTicksLimit: 6,
                        callback: value => (Number.isInteger(value) ? value.toLocaleString() : '')
                    },
                    grid: { color: 'rgba(148, 163, 184, 0.2)' }
                }
            },
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label(ctx) {
                            const dataset = ctx.dataset || {};
                            const base = dataset.label || '';
                            const text = `${base}: ${formatCount(ctx.parsed.y)}`;
                            const meta = Array.isArray(dataset._bucketMeta) ? dataset._bucketMeta[ctx.dataIndex] : null;
                            if (meta && meta.refDate) {
                                return `${text} (기준 ${formatDateKST(meta.refDate)})`;
                            }
                            return text;
                        }
                    }
                }
            }
        };
    }

    function weekIndexOf(day) {
        return Math.min(MAX_WEEKS_PER_MONTH, Math.ceil(day / 7));
    }

    function weeksInMonth(year, month) {
        const days = new Date(year, month, 0).getDate();
        return Math.min(MAX_WEEKS_PER_MONTH, Math.ceil(days / 7));
    }

    /** 버킷 키가 가리키는 첫 날짜. 키 범위를 다시 날짜로 펼칠 때 쓴다. */
    function keyAnchorDate(key, bucket) {
        const [yearPart, monthPart, weekPart] = String(key || '').split('-');
        if (bucket === BUCKET_MONTHLY) return `${yearPart}-${monthPart}-01`;
        const weekIndex = Number(String(weekPart || '').replace('W', '')) || 1;
        return `${yearPart}-${monthPart}-${String((weekIndex - 1) * 7 + 1).padStart(2, '0')}`;
    }

    /** 조회 구간에 해당하는 버킷 자리를, 데이터 유무와 상관없이 모두 만든다. */
    function enumerateBucketKeys(bucket, startValue, endValue) {
        const start = toDateKST(startValue);
        const end = toDateKST(endValue);
        if (!start || !end || start.getTime() > end.getTime()) return [];

        const from = kstParts(start);
        const to = kstParts(end);
        const keys = [];
        let year = from.year;
        let month = from.month;
        while (year < to.year || (year === to.year && month <= to.month)) {
            const monthKey = `${year}-${String(month).padStart(2, '0')}`;
            if (bucket === BUCKET_MONTHLY) {
                keys.push(monthKey);
            } else {
                const firstWeek = (year === from.year && month === from.month) ? weekIndexOf(from.day) : 1;
                const lastWeek = (year === to.year && month === to.month) ? weekIndexOf(to.day) : weeksInMonth(year, month);
                for (let week = firstWeek; week <= lastWeek; week++) {
                    keys.push(`${monthKey}-W${week}`);
                }
            }
            month += 1;
            if (month > 12) {
                month = 1;
                year += 1;
            }
        }
        return keys;
    }

    /**
     * 차트에 그릴 내용을 한 번에 계산한다.
     * 막대 자리는 선택한 구간 전체로 깔고, 값이 없는 칸은 null로 비워 둔다. (값 0과 구분하기 위해서)
     */
    function buildView(panel) {
        const bucket = panel.bucket || BUCKET_DAILY;

        if (bucket === BUCKET_DAILY) {
            const points = toDailyPoints(panel.points);
            return {
                bucket,
                labels: undefined,
                data: points,
                meta: null,
                slots: points.length,
                withData: points.length
            };
        }

        const aggregated = aggregateToBuckets(panel.points, bucket);
        const byKey = new Map(aggregated.map(item => [item.key, item]));

        let keys = enumerateBucketKeys(bucket, panel.startDate, panel.endDate);
        if (!keys.length && aggregated.length) {
            // 기간을 지정하지 않은 '전체'는 데이터가 있는 범위를 빠짐없이 채운다.
            keys = enumerateBucketKeys(
                bucket,
                keyAnchorDate(aggregated[0].key, bucket),
                keyAnchorDate(aggregated[aggregated.length - 1].key, bucket)
            );
        }

        const keySet = new Set(keys);
        aggregated.forEach(item => keySet.add(item.key));
        const orderedKeys = [...keySet].sort();
        const withYear = new Set(orderedKeys.map(key => key.slice(0, 4))).size > 1;

        return {
            bucket,
            labels: orderedKeys.map(key => bucketLabelOf(key, bucket, withYear)),
            data: orderedKeys.map(key => (byKey.has(key) ? byKey.get(key).value : null)),
            meta: orderedKeys.map(key => byKey.get(key) || null),
            slots: orderedKeys.length,
            withData: aggregated.length
        };
    }

    function buildChartData(panel, view) {
        const color = PANEL_COLORS[view.bucket] || PANEL_COLORS.DAILY;

        if (view.bucket === BUCKET_DAILY) {
            return {
                labels: undefined,
                datasets: [{
                    label: panel.label,
                    data: view.data,
                    borderColor: color,
                    backgroundColor: color,
                    tension: 0.25,
                    fill: false,
                    _bucketMeta: null
                }]
            };
        }

        return {
            labels: view.labels,
            datasets: [{
                label: panel.label,
                type: 'bar',
                data: view.data,
                borderColor: color,
                backgroundColor: color,
                borderRadius: 4,
                maxBarThickness: 56,
                categoryPercentage: 0.72,
                barPercentage: 0.9,
                _bucketMeta: view.meta
            }]
        };
    }

    function summaryOf(panel, view) {
        const span = (panel.startDate && panel.endDate)
            ? `${panel.startDate} ~ ${panel.endDate}`
            : '전체 기간';
        if (view.bucket === BUCKET_DAILY) return `${span} · 데이터 ${view.withData}일`;
        if (view.bucket === BUCKET_WEEKLY) return `${span} · 주 ${view.slots}칸 · 데이터 ${view.withData}주`;
        return `${span} · 월 ${view.slots}칸 · 데이터 ${view.withData}개월`;
    }

    // ---------- 카드 ----------
    const cards = new Map();

    function cardElements(root) {
        return {
            root,
            presets: [...root.querySelectorAll('[data-role="preset"]')],
            customToggle: root.querySelector('[data-role="custom-toggle"]'),
            customForm: root.querySelector('[data-role="custom-form"]'),
            applyButton: root.querySelector('[data-role="apply"]'),
            startDate: root.querySelector('[data-role="start-date"]'),
            endDate: root.querySelector('[data-role="end-date"]'),
            startMonth: root.querySelector('[data-role="start-month"]'),
            endMonth: root.querySelector('[data-role="end-month"]'),
            rangeSummary: root.querySelector('[data-role="range-summary"]'),
            empty: root.querySelector('[data-role="empty"]'),
            canvas: root.querySelector('canvas')
        };
    }

    function setCustomOpen(card, open) {
        const next = !!open;
        if (card.el.customForm) card.el.customForm.hidden = !next;
        if (card.el.customToggle) {
            card.el.customToggle.setAttribute('aria-expanded', next ? 'true' : 'false');
            card.el.customToggle.classList.toggle('btn-primary', next);
            card.el.customToggle.classList.toggle('btn-outline-secondary', !next);
        }
    }

    function syncFilterState(card) {
        const panel = card.panel;
        const custom = !!panel.customRange;

        card.el.presets.forEach(button => {
            const active = !custom && button.dataset.range === panel.selectedRange;
            button.classList.toggle('btn-primary', active);
            button.classList.toggle('btn-outline-primary', !active);
            button.setAttribute('aria-pressed', active ? 'true' : 'false');
        });

        if (card.el.customToggle) {
            card.el.customToggle.classList.toggle('btn-primary', custom);
            card.el.customToggle.classList.toggle('btn-outline-secondary', !custom);
        }

        if (card.el.startDate) card.el.startDate.value = panel.startDate || '';
        if (card.el.endDate) card.el.endDate.value = panel.endDate || '';
        if (card.el.startMonth && panel.startDate) card.el.startMonth.value = String(panel.startDate).slice(0, 7);
        if (card.el.endMonth && panel.endDate) card.el.endMonth.value = String(panel.endDate).slice(0, 7);

        if (card.el.rangeSummary) {
            card.el.rangeSummary.textContent = summaryOf(panel, card.view);
        }
    }

    function syncEmptyState(card) {
        const panel = card.panel;
        const count = card.view ? card.view.withData : 0;
        if (!card.el.empty) return;
        if (panel.hadError) {
            card.el.empty.textContent = '데이터를 불러오지 못했습니다.';
            card.el.empty.hidden = false;
        } else if (count === 0) {
            card.el.empty.textContent = '표시할 데이터가 없습니다.';
            card.el.empty.hidden = false;
        } else {
            card.el.empty.hidden = true;
        }
    }

    function renderCard(card) {
        const panel = card.panel;
        const view = buildView(panel);
        card.view = view;
        const data = buildChartData(panel, view);

        if (!card.chart) {
            card.chart = new Chart(card.el.canvas.getContext('2d'), {
                type: view.bucket === BUCKET_DAILY ? 'line' : 'bar',
                data,
                options: makeChartOptions(view.bucket)
            });
        } else {
            card.chart.data.labels = data.labels;
            card.chart.data.datasets = data.datasets;
        }

        // 일별 선 그래프는 카테고리 축이 없으므로 축 범위를 선택한 구간으로 직접 맞춘다.
        if (view.bucket === BUCKET_DAILY) {
            card.chart.options.scales.x.min = panel.startDate || undefined;
            card.chart.options.scales.x.max = panel.endDate || undefined;
        }
        card.chart.update();

        syncFilterState(card);
        syncEmptyState(card);
    }

    function updateBrowserUrl(card) {
        if (!window.history || typeof window.history.replaceState !== 'function') return;
        const panel = card.panel;
        const url = new URL(window.location.href);
        const prefix = panel.key;
        url.searchParams.set(`${prefix}Range`, panel.selectedRange);
        if (panel.customRange && panel.startDate && panel.endDate) {
            url.searchParams.set(`${prefix}StartDate`, panel.startDate);
            url.searchParams.set(`${prefix}EndDate`, panel.endDate);
        } else {
            url.searchParams.delete(`${prefix}StartDate`);
            url.searchParams.delete(`${prefix}EndDate`);
        }
        window.history.replaceState({}, '', url);
    }

    function setLoading(card, loading) {
        card.el.root.classList.toggle('is-loading', !!loading);
        card.el.presets.forEach(button => { button.disabled = !!loading; });
        if (card.el.applyButton) card.el.applyButton.disabled = !!loading;
    }

    async function requestPanel(card, params) {
        const query = new URLSearchParams({ panel: card.panel.key });
        if (params.range) query.set('range', params.range);
        if (params.startDate) query.set('startDate', params.startDate);
        if (params.endDate) query.set('endDate', params.endDate);

        setLoading(card, true);
        try {
            const response = await fetch(`${panelEndpoint}?${query.toString()}`, {
                headers: { Accept: 'application/json' },
                credentials: 'same-origin'
            });
            if (!response.ok) throw new Error(`Unexpected status ${response.status}`);
            const next = await response.json();
            if (!next || typeof next !== 'object') throw new Error('Malformed panel payload');
            card.panel = next;
            renderCard(card);
            updateBrowserUrl(card);
        } catch (err) {
            console.warn('Failed to refresh active user panel.', err);
            if (card.el.empty) {
                card.el.empty.textContent = '기간을 적용하지 못했습니다. 잠시 후 다시 시도해주세요.';
                card.el.empty.hidden = false;
            }
        } finally {
            setLoading(card, false);
        }
    }

    function readCustomRange(card) {
        if (card.el.startMonth && card.el.endMonth) {
            const startMonth = card.el.startMonth.value;
            const endMonth = card.el.endMonth.value;
            if (!startMonth || !endMonth) return null;
            return { range: 'custom', startDate: `${startMonth}-01`, endDate: lastDayOfMonth(endMonth) };
        }
        const startDate = card.el.startDate ? card.el.startDate.value : '';
        const endDate = card.el.endDate ? card.el.endDate.value : '';
        if (!startDate || !endDate) return null;
        return { range: 'custom', startDate, endDate };
    }

    function bindCard(card) {
        card.el.presets.forEach(button => {
            button.addEventListener('click', () => {
                setCustomOpen(card, false);
                requestPanel(card, { range: button.dataset.range });
            });
        });

        if (card.el.customToggle) {
            card.el.customToggle.addEventListener('click', () => {
                const open = card.el.customForm ? card.el.customForm.hidden : false;
                setCustomOpen(card, open);
            });
        }

        if (card.el.applyButton) {
            card.el.applyButton.addEventListener('click', () => {
                const params = readCustomRange(card);
                if (!params) {
                    if (card.el.rangeSummary) card.el.rangeSummary.textContent = '시작과 종료를 모두 선택해주세요.';
                    return;
                }
                requestPanel(card, params);
            });
        }
    }

    function init() {
        const panels = Array.isArray(payload.panels) ? payload.panels : [];
        panels.forEach(panel => {
            const root = document.querySelector(`.au-card[data-panel="${panel.key}"]`);
            if (!root) return;
            const card = { panel, el: cardElements(root), chart: null };
            cards.set(panel.key, card);
            renderCard(card);
            setCustomOpen(card, !!panel.customRange);
            bindCard(card);
        });

        window.addEventListener('resize', () => {
            for (const [, card] of cards) {
                try { card.chart?.resize(); } catch (_) { /* 무시 */ }
            }
        });
    }

    init();
})();
