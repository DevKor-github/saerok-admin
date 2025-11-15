// ===== resources/static/js/service-insight.js =====
(function () {
    // ---------- 0) 데이터 파싱 ----------
    const dataElement = document.getElementById('serviceInsightData');
    if (!dataElement) return;

    const dataEndpoint = dataElement.dataset?.endpoint || window.location.pathname;

    let payload = {};
    try { payload = JSON.parse(dataElement.textContent || '{}'); }
    catch (err) { console.warn('Failed to parse service insight payload.', err); }

    let metricOptions = [];
    let seriesList = [];
    let componentLabels = {};
    let optionMap = new Map();
    let seriesMap = new Map();

    function applyViewModelPayload(source) {
        payload = (source && typeof source === 'object') ? source : {};
        metricOptions = Array.isArray(payload.metricOptions) ? payload.metricOptions : [];
        seriesList = Array.isArray(payload.series) ? payload.series : [];
        componentLabels = (payload.componentLabels && typeof payload.componentLabels === 'object') ? payload.componentLabels : {};
        optionMap = new Map(metricOptions.map(o => [o.metric, o]));
        seriesMap = new Map(seriesList.map(s => [s.metric, s]));
        try {
            dataElement.textContent = JSON.stringify(payload);
        } catch (err) {
            console.warn('Failed to cache service insight payload JSON.', err);
        }
    }

    applyViewModelPayload(payload);

    const rangeQuickContainer = document.querySelector('.si-range__quick');
    const rangeForm = document.querySelector('.si-range__form');
    const rangeStartInput = rangeForm?.querySelector('input[name="startDate"]') || null;
    const rangeEndInput = rangeForm?.querySelector('input[name="endDate"]') || null;
    const rangeApplyButton = rangeForm?.querySelector('button[type="submit"]') || null;
    const rangeCustomToggle = document.getElementById('si-range-custom-toggle');

    function setCustomRangeOpen(open) {
        const next = !!open;
        if (rangeForm) {
            rangeForm.classList.toggle('is-open', next);
        }
        if (rangeCustomToggle) {
            rangeCustomToggle.setAttribute('aria-expanded', next ? 'true' : 'false');
            rangeCustomToggle.classList.toggle('btn-primary', next);
            rangeCustomToggle.classList.toggle('btn-outline-secondary', !next);
        }
        if (rangeApplyButton) {
            rangeApplyButton.classList.toggle('btn-primary', next);
            rangeApplyButton.classList.toggle('btn-outline-secondary', !next);
        }
    }

    setCustomRangeOpen(rangeForm ? rangeForm.classList.contains('is-open') : false);

    // 서버 enum 키
    const METRICS = {
        TOTAL_COUNT:      'COLLECTION_TOTAL_COUNT',
        PRIVATE_RATIO:    'COLLECTION_PRIVATE_RATIO',
        PENDING_COUNT:    'BIRD_ID_PENDING_COUNT',
        RESOLVED_COUNT:   'BIRD_ID_RESOLVED_COUNT',
        // ✅ 누적 제거 → 28일 지표로 박스플롯 분기 키를 교체
        RESOLUTION_STATS: 'BIRD_ID_RESOLUTION_STATS_28D', // components: min_hours, avg_hours, max_hours, stddev_hours

        // ===== 유저 지표 =====
        USER_COMPLETED_TOTAL: 'USER_COMPLETED_TOTAL',
        USER_SIGNUP_DAILY:    'USER_SIGNUP_DAILY',
        USER_WITHDRAWAL_DAILY:'USER_WITHDRAWAL_DAILY',
        USER_DAU:             'USER_DAU',
        USER_WAU:             'USER_WAU',
        USER_MAU:             'USER_MAU',
    };

    // (아래부터는 기존 로직 그대로입니다. 색상/그룹/플롯/툴팁/박스플롯 렌더링 등 전체 원본 유지)

    // 컬러 팔레트
    const PALETTE = ['#2563eb','#16a34a','#dc2626','#f97316','#9333ea','#0ea5e9','#059669','#ea580c','#3b82f6','#14b8a6'];
    const colorCache = new Map();
    const colorForMetric = key => {
        if (colorCache.has(key)) return colorCache.get(key);
        const idx = colorCache.size % PALETTE.length;
        const c = PALETTE[idx]; colorCache.set(key, c); return c;
    };

    // ---------- 레이아웃: 좌·우 스크롤 분리 ----------
    const container = document.querySelector('.service-insight__container');
    const leftCol   = document.querySelector('.service-insight__metrics');
    const rightCol  = document.querySelector('.service-insight__chart');

    function applyIndependentScroll() {
        if (!container || !leftCol || !rightCol) return;
        const rect = container.getBoundingClientRect();
        const avail = Math.max(240, window.innerHeight - rect.top - 16);
        container.style.height = avail + 'px';
        container.style.overflow = 'hidden';
        [leftCol, rightCol].forEach(el => {
            el.style.height = '100%';
            el.style.minHeight = '0';
            el.style.overflowY = 'auto';
        });
    }
    applyIndependentScroll();

    // 리사이즈 보정
    function resizeAllCharts() {
        for (const [, p] of plots) { try { p.chart.resize(); } catch(_){} }
    }
    window.addEventListener('resize', () => { applyIndependentScroll(); resizeAllCharts(); });

    // ---------- 유틸 ----------
    function escapeHtml(str) {
        return String(str ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // KST 기준 날짜 정규화
    const TZ = 'Asia/Seoul';
    function toDateKST(d) {
        if (!d) return null;
        if (d instanceof Date) return d;
        if (typeof d === 'number') return new Date(d);
        const s = String(d).trim();
        const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(s);
        const L = window.luxon;
        if (L && L.DateTime) {
            let dt = L.DateTime.fromISO(s, { zone: TZ });
            if (!dt.isValid) dt = L.DateTime.fromJSDate(new Date(s), { zone: TZ });
            if (!dt.isValid) return null;
            if (dateOnly) dt = dt.startOf('day');
            return dt.toJSDate();
        }
        const t = new Date(s);
        if (isNaN(t.getTime())) return null;
        if (dateOnly) return new Date(t.getFullYear(), t.getMonth(), t.getDate());
        return t;
    }

    // aside 툴팁 초기화
    function initTooltipsIn(scope) {
        const root = scope || document;
        if (!window.bootstrap || !window.bootstrap.Tooltip) return;
        root.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
            const prev = window.bootstrap.Tooltip.getInstance(el);
            if (prev) prev.dispose();
            const inst = new window.bootstrap.Tooltip(el, {
                trigger: 'hover focus',
                html: true,
                sanitize: true,
                boundary: 'window',
                container: 'body',
                placement: el.dataset.bsPlacement || 'right',
                customClass: el.dataset.bsCustomClass || 'tooltip-bubble'
            });
        });
    }

    // ---------- 그룹(사이드바) ----------
    const GROUPS = [
        { key:'collection', name:'새록', metrics:[METRICS.TOTAL_COUNT, METRICS.PRIVATE_RATIO] },
        { key:'user',       name:'유저', metrics:[
                METRICS.USER_COMPLETED_TOTAL, METRICS.USER_SIGNUP_DAILY, METRICS.USER_WITHDRAWAL_DAILY,
                METRICS.USER_DAU, METRICS.USER_WAU, METRICS.USER_MAU
            ] },
        { key:'id',         name:'동정 요청', metrics:[METRICS.PENDING_COUNT, METRICS.RESOLVED_COUNT, METRICS.RESOLUTION_STATS] },
    ];
    const known = new Set(GROUPS.flatMap(g => g.metrics));
    (metricOptions || []).forEach(opt => { if (!known.has(opt.metric)) known.add(opt.metric); });

    const groupsWrap = document.getElementById('si-groups');

    function renderGroups(){
        if (!groupsWrap) return;
        groupsWrap.innerHTML = '';

        GROUPS.forEach(group => {
            const details = document.createElement('details');
            details.className = 'si-group';
            details.open = true;

            const summary = document.createElement('summary');
            summary.className = 'si-group__summary';
            summary.innerHTML = `
        <span class="si-group__title">${escapeHtml(group.name)}</span>
        <span class="si-group__tools">
          <button type="button" class="si-group__btn" data-act="addAll">+ 전체</button>
          <button type="button" class="si-group__btn" data-act="removeAll">– 전체</button>
        </span>
      `;
            details.appendChild(summary);

            const body = document.createElement('div'); body.className = 'si-group__body';
            const list = document.createElement('div'); list.className = 'si-chip-list';

            (group.metrics || []).forEach(metricKey => {
                const opt = optionMap.get(metricKey); if (!opt) return;
                const color = colorForMetric(metricKey);

                const chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'si-chip si-chip--toggle';
                chip.dataset.metric = metricKey;

                chip.setAttribute('draggable','false');
                chip.addEventListener('dragstart', e => e.preventDefault());

                // aside 툴팁: 제목+설명 (설명은 서버 enum에서 내려옴 → 28일 기준으로 변경됨)
                const titleText = opt.label || metricKey;
                const descText  = opt.description || '';
                const tooltipHtml = `<b>${escapeHtml(titleText)}</b>` + (descText ? `<br>${escapeHtml(descText)}` : '');
                chip.setAttribute('data-bs-toggle', 'tooltip');
                chip.setAttribute('data-bs-placement', 'right');
                chip.setAttribute('data-bs-title', tooltipHtml);
                chip.removeAttribute('title');

                chip.innerHTML = `
          <span class="si-chip__dot" style="background:${color}"></span>
          <span class="si-chip__label">${escapeHtml(opt.label || metricKey)}</span>
          <span class="si-chip__check" aria-hidden="true"></span>
        `;

                chip.addEventListener('click', ()=>{
                    const activePlot = getActivePlotId() ?? ensureAtLeastOnePlot();
                    toggleMetricGroupOnPlot(activePlot, metricKey);
                    if (window.bootstrap?.Tooltip) {
                        const inst = window.bootstrap.Tooltip.getInstance(chip);
                        try { inst?.hide(); } catch(_) {}
                    }
                    syncAsideActiveStates();
                });

                chip.addEventListener('mouseleave', ()=>{
                    if (window.bootstrap?.Tooltip) {
                        const inst = window.bootstrap.Tooltip.getInstance(chip);
                        try { inst?.hide(); } catch(_) {}
                    }
                });

                list.appendChild(chip);
            });

            body.appendChild(list);
            details.appendChild(body);

            summary.querySelector('[data-act="addAll"]').addEventListener('click', (ev)=>{
                ev.preventDefault(); ev.stopPropagation();
                const activePlot = getActivePlotId() ?? ensureAtLeastOnePlot();
                (group.metrics||[]).forEach(m => addMetricGroupToPlot(activePlot, m));
                syncAsideActiveStates();
            });
            summary.querySelector('[data-act="removeAll"]').addEventListener('click', (ev)=>{
                ev.preventDefault(); ev.stopPropagation();
                (group.metrics||[]).forEach(m => removeMetricGroupFromAllPlots(m));
                syncAsideActiveStates();
            });

            groupsWrap.appendChild(details);
        });

        initTooltipsIn(groupsWrap);
        syncAsideActiveStates();
    }

    // ---------- 플롯 ----------
    const plotGrid   = document.getElementById('si-plot-grid');
    const plotsHost  = document.getElementById('si-plots');
    const addPlotBtn = document.getElementById('si-add-plot');
    const clearAllBtn= document.getElementById('si-clear-all');

    if (plotsHost) plotsHost.setAttribute('data-size', 'roomy');

    let plotSeq = 0;
    // plots: id -> { id, index, el, chart, datasets:Set<string>, groups: Map<metric, Set<datasetId>>, active:boolean }
    const plots = new Map();

    function refreshAllPlots() {
        for (const [plotId, plot] of plots) {
            const metrics = [...plot.groups.keys()];
            plot.chart.data.datasets = [];
            plot.datasets.clear();
            plot.groups.clear();

            if (metrics.length === 0) {
                renderPlotGroupChips(plotId);
                updateEmptyState(plotId);
                continue;
            }

            metrics.forEach(metric => addMetricGroupToPlot(plotId, metric));
        }

        resizeAllCharts();
        syncAsideActiveStates();
    }

    function ensureAtLeastOnePlot() { if (plots.size===0) return createPlot(); return [...plots.keys()][0]; }
    function getActivePlotId(){ for (const [id,p] of plots){ if (p.active) return id; } return null; }
    function setActivePlot(id){
        for (const [pid,p] of plots){
            p.active = (pid===id);
            const title = p.el.querySelector('.si-plot__title');
            if (title) title.textContent = `플롯 ${p.index}`;
            p.el.classList.toggle('si-plot--active', p.active);
        }
        syncAsideActiveStates();
    }

    // 박스플롯 플러그인(평균 중앙선, ±표준편차 상자, 최소~최대 수염)
    const boxPlotPlugin = {
        id: 'siBoxPlot',
        afterDatasetsDraw(chart) {
            const ctx = chart.ctx;
            (chart.data.datasets || []).forEach((ds, i) => {
                if (!ds._boxplot || !chart.isDatasetVisible(i)) return;
                const meta = chart.getDatasetMeta(i);
                const xScale = meta.xScale, yScale = meta.yScale;
                const boxHalf = 6;
                const capHalf = 10;

                ds._boxplot.forEach(entry => {
                    const x = xScale.getPixelForValue(entry._time);
                    const yMin  = yScale.getPixelForValue(entry.min);
                    const yMax  = yScale.getPixelForValue(entry.max);
                    const yMean = yScale.getPixelForValue(entry.mean);
                    const yLo   = yScale.getPixelForValue(entry.mean - entry.std);
                    const yHi   = yScale.getPixelForValue(entry.mean + entry.std);

                    const ctx2 = ctx; ctx2.save();
                    // 수염(최소~최대)
                    ctx2.strokeStyle = 'rgba(148,163,184,.9)';
                    ctx2.lineWidth = 2;
                    ctx2.beginPath(); ctx2.moveTo(x, yMin); ctx2.lineTo(x, yMax); ctx2.stroke();
                    ctx2.beginPath();
                    ctx2.moveTo(x - capHalf, yMin); ctx2.lineTo(x + capHalf, yMin);
                    ctx2.moveTo(x - capHalf, yMax); ctx2.lineTo(x + capHalf, yMax);
                    ctx2.stroke();

                    // 박스(±표준편차)
                    ctx2.fillStyle = 'rgba(91,140,255,.18)';
                    ctx2.strokeStyle = 'rgba(91,140,255,.9)';
                    ctx2.lineWidth = 2;
                    const top = Math.min(yLo, yHi), height = Math.abs(yHi - yLo) || 2;
                    ctx2.beginPath();
                    ctx2.rect(x - boxHalf, top, boxHalf*2, height);
                    ctx2.fill(); ctx2.stroke();

                    // 평균선
                    ctx2.strokeStyle = 'rgba(30,41,59,.95)';
                    ctx2.lineWidth = 2;
                    ctx2.beginPath();
                    ctx2.moveTo(x - (boxHalf + 3), yMean);
                    ctx2.lineTo(x + (boxHalf + 3), yMean);
                    ctx2.stroke();
                    ctx2.restore();
                });
            });
        }
    };
    if (window.Chart && Chart.register) Chart.register(boxPlotPlugin);

    function createPlot(){
        const id = `plot-${++plotSeq}`; const index = plots.size + 1;
        const card = document.createElement('div');
        card.className='si-plot-card'; card.dataset.plotId = id;
        card.innerHTML = `
      <div class="si-plot__header">
        <div class="si-plot__title">플롯 ${index}</div>
        <div class="si-plot__tools">
          <button type="button" class="btn btn-ghost btn-sm" data-act="removePlot" title="플롯 삭제">
            <i class="bi bi-trash"></i>
          </button>
        </div>
      </div>
      <div class="si-plot__datasets si-dropzone" data-dropzone="datasets" data-empty="true"></div>
      <div class="si-plot__surface">
        <canvas></canvas>
        <div class="si-empty d-none">표시할 데이터가 없습니다</div>
      </div>
    `;
        plotGrid.appendChild(card);

        card.addEventListener('mousedown', ()=> setActivePlot(id));
        card.addEventListener('focusin', ()=> setActivePlot(id));

        const canvas = card.querySelector('canvas');
        const chart = new Chart(canvas.getContext('2d'), { type:'line', data:{datasets:[]}, options: makeBaseChartOptions() });

        card.querySelector('[data-act="removePlot"]').addEventListener('click', ()=> removePlot(id));

        const dz = card.querySelector('[data-dropzone="datasets"]');
        dz.addEventListener('dragover', e=>{ e.preventDefault(); dz.classList.add('is-dragover'); });
        dz.addEventListener('dragleave', ()=> dz.classList.remove('is-dragover'));
        dz.addEventListener('drop', e=>{
            e.preventDefault(); dz.classList.remove('is-dragover');
            try{
                const payload = JSON.parse(e.dataTransfer.getData('application/json') || '{}');
                if (payload.type === 'datasetGroup' && payload.groupId && payload.fromPlotId){
                    moveGroupToPlot(payload.groupId, payload.fromPlotId, id);
                }
            }catch(_){}
        });

        plots.set(id, {
            id, index, el: card, chart,
            datasets: new Set(),
            groups: new Map(),
            active: false
        });

        if (plots.size===1) setActivePlot(id);
        updateEmptyState(id);
        return id;
    }

    function markDatasetsStripState(strip) {
        const hasChip = !!strip.querySelector('.si-ds-chip');
        strip.setAttribute('data-empty', hasChip ? 'false' : 'true');
    }

    function removePlot(id){
        const p = plots.get(id); if (!p) return;
        p.chart.destroy();
        p.el.remove();
        plots.delete(id);

        let i = 0;
        for (const [pid, plot] of plots){
            plot.index = ++i;
            const title = plot.el.querySelector('.si-plot__title');
            if (title) title.textContent = `플롯 ${plot.index}`;
        }
        if (plots.size === 0) createPlot();
        syncAsideActiveStates();
    }

    function updateEmptyState(plotId){
        const p = plots.get(plotId); if (!p) return;
        const emptyEl = p.el.querySelector('.si-empty');
        const hasAny = (p.chart.data.datasets || []).some(ds => (ds.data||[]).length>0);
        emptyEl.classList.toggle('d-none', hasAny);

        const strip = p.el.querySelector('.si-plot__datasets');
        strip.setAttribute('data-empty', (p.groups.size === 0) ? 'true' : 'false');
    }

    // ---------- 데이터 변환/축 ----------
    function toPoints(series, unit) {
        const points = Array.isArray(series?.points) ? series.points : [];
        return points
            .map(pt => {
                const x = toDateKST(pt.date);
                const y = normalizeValue(pt.value, unit);
                return (x && y != null) ? ({ x, y }) : null;
            })
            .filter(Boolean);
    }
    function formatHoursAdaptive(hours) {
        if (!Number.isFinite(hours)) return '-';
        const H_PER_DAY = 24;
        const D_PER_MONTH = 30;

        const abs = Math.abs(hours);
        if (abs >= H_PER_DAY * D_PER_MONTH) {
            const months = hours / (H_PER_DAY * D_PER_MONTH);
            const sig = Math.abs(months) >= 10 ? 0 : 1;
            return `${months.toFixed(sig)}개월`;
        }
        if (abs >= H_PER_DAY) {
            const days = hours / H_PER_DAY;
            const sig = Math.abs(days) >= 10 ? 0 : 1;
            return `${days.toFixed(sig)}일`;
        }
        return `${hours.toFixed(2)}시간`;
    }

    function formatValue(v, unit){
        if (!Number.isFinite(v)) return '-';
        if (unit==='RATIO') return `${v.toFixed(1)}%`;
        if (unit==='HOURS') return formatHoursAdaptive(v);
        return Math.round(v).toLocaleString();
    }

    function normalizeValue(num, unit){
        const v = typeof num === 'string' ? Number(num) : Number(num);
        if (!Number.isFinite(v)) return null;
        if (unit==='RATIO') return v <= 1.000001 ? v*100 : v;
        return v;
    }
    function axisByUnit(unit){ if (unit==='RATIO') return 'ratio'; if (unit==='HOURS') return 'hours'; return 'count'; }

    function makeBaseChartOptions(){
        return {
            responsive: true, maintainAspectRatio: false,
            interaction: { mode:'nearest', intersect:false },
            elements: { line:{borderWidth:2}, point:{radius:0, hoverRadius:4, hitRadius:8} },
            scales: {
                x: {
                    type:'time',
                    time:{
                        unit:'day',
                        round:'day',
                        tooltipFormat:'yyyy-LL-dd',
                        zone: TZ
                    },
                    adapters:{ date:{ zone: TZ } },
                    ticks:{ autoSkip:true, maxRotation:0 },
                    grid:{ color:'rgba(148, 163, 184, 0.2)' }
                },
                count: { display:false, position:'left', beginAtZero:true, ticks:{ precision:0, maxTicksLimit:8, callback:v => Number.isInteger(v)? v.toLocaleString():'' } },
                ratio: { display:false, position:'right', beginAtZero:true, min:0, max:100, grid:{ drawOnChartArea:false }, ticks:{ stepSize:10, maxTicksLimit:6, callback:v=>`${v}%` } },
                hours: { display:false, position:'right', beginAtZero:true, grid:{ drawOnChartArea:false }, ticks:{ maxTicksLimit:6, callback:v=>(typeof v==='number'? v.toFixed(1):v) } }
            },
            plugins: {
                legend: { position:'bottom', labels:{ usePointStyle:true, boxWidth:8, boxHeight:8 } },
                tooltip: {
                    mode:'index', intersect:false,
                    callbacks: {
                        label(ctx){
                            const ds   = ctx.dataset || {};
                            const unit = ds._saUnit;
                            const base = ds.label || '';
                            const v    = ctx.parsed.y;

                            if (ds._boxIndex) {
                                const x = ctx.raw?.x instanceof Date ? ctx.raw.x.getTime() : new Date(ctx.raw?.x).getTime();
                                const st = ds._boxIndex.get(String(x));
                                if (st) {
                                    const mean = (typeof v === 'number') ? v : null;
                                    const lo = (mean!=null && Number.isFinite(st.std)) ? (mean - st.std) : null;
                                    const hi = (mean!=null && Number.isFinite(st.std)) ? (mean + st.std) : null;
                                    const fmt = (n)=>formatValue(n,'HOURS');
                                    if (mean!=null && lo!=null && hi!=null) {
                                        return [
                                            `${base}: 평균 ${fmt(lo)} ~ ${fmt(hi)}`,
                                            `(최소: ${fmt(st.min)}, 최대: ${fmt(st.max)})`
                                        ];
                                    }
                                }
                                return `${base}: 평균 ${formatValue(v,'HOURS')}`;
                            }

                            return base ? `${base}: ${formatValue(v,unit)}` : formatValue(v,unit);
                        }
                    }
                }
            }
        };
    }

    function niceNum(x, round){
        const exp = Math.floor(Math.log10(x));
        const f = x / Math.pow(10,exp);
        let nf;
        if (round){ nf = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10; }
        else { nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10; }
        return nf * Math.pow(10,exp);
    }
    function niceScale(min,max,maxTicks=6,integerOnly=false){
        const range = Math.max(1e-9, max-min);
        let step = niceNum(range / Math.max(2,(maxTicks-1)), true);
        if (integerOnly) step = Math.max(1, Math.round(step));
        const niceMin = Math.floor(min/step)*step;
        const niceMax = Math.ceil(max/step)*step;
        return {min:niceMin,max:niceMax,step};
    }
    function bounds(chart, axis){
        let min=Infinity, max=-Infinity, used=false;
        (chart.data.datasets||[]).forEach(ds=>{
            if (ds.yAxisID!==axis || !Array.isArray(ds.data)) return;
            ds.data.forEach(pt=>{
                if (pt && Number.isFinite(pt.y)){ used=true; if (pt.y<min) min=pt.y; if (pt.y>max) max=pt.y; }
            });
        });
        if (!used) return {used:false,min:0,max:1};
        if (min===max){ min-=1; max+=1; }
        return {used:true,min,max};
    }

    // 축 자동 스케일 + 우측축 겹침 보정(offset)
    function applyAutoYAxisScaling(chart){
        const b = { count: bounds(chart,'count'), ratio: bounds(chart,'ratio'), hours: bounds(chart,'hours') };

        // count (좌)
        if (b.count.used){
            const nice = niceScale(b.count.min, b.count.max, 7, true);
            const s = chart.options.scales.count; s.display=true; s.min=nice.min; s.max=nice.max; s.ticks.stepSize=nice.step; s.ticks.precision=0;
        } else chart.options.scales.count.display=false;

        // 오른쪽 축 2개(ratio, hours) 동시 사용 시 시각 분리
        const bothRight = b.ratio.used && b.hours.used;

        if (b.ratio.used){
            const s = chart.options.scales.ratio;
            const range=Math.max(5,b.ratio.max-b.ratio.min);
            const step = range<=30?5:10;
            s.display=true; s.position='right'; s.offset = bothRight ? false : false;
            s.min=Math.max(0,Math.floor(b.ratio.min/step)*step);
            s.max=Math.min(100,Math.ceil(b.ratio.max/step)*step);
            s.ticks.stepSize=step;
        } else chart.options.scales.ratio.display=false;

        if (b.hours.used){
            const nice = niceScale(b.hours.min, b.hours.max, 6, false);
            const s = chart.options.scales.hours;
            s.display=true; s.position='right'; s.offset = bothRight ? true : false;
            s.min=nice.min; s.max=nice.max; s.ticks.stepSize=nice.step;
        } else chart.options.scales.hours.display=false;
    }

    function datasetIdFor(metric, compKey){
        return compKey ? `${metric}::${compKey}` : metric;
    }

    // ---------- 핵심: 추가/제거/이동 ----------
    function addMetricGroupToPlot(plotId, metric){
        const p = plots.get(plotId); if (!p) return;
        const opt = optionMap.get(metric); if (!opt) return;

        const color = colorForMetric(metric);
        const groupSet = p.groups.get(metric) || new Set();
        let changed = false;

        if (metric === METRICS.RESOLUTION_STATS) {
            // 박스플롯 데이터 구성 (KST 기준 병합)
            const series = seriesMap.get(metric);
            const comps = Array.isArray(series?.components) ? series.components : [];
            const mapByKey = new Map(comps.map(c => [c.key, c.points || []]));
            const get = (k) => mapByKey.get(k) || mapByKey.get(k?.toLowerCase()) || [];
            const minPts = get('min_hours');
            const maxPts = get('max_hours');
            const avgPts = get('avg_hours');
            const stdPts = get('stddev_hours');

            const byTime = new Map(); // key: ms timestamp
            const ensure = (d) => {
                const t = toDateKST(d);
                if (!t) return null;
                const k = t.getTime();
                if (!byTime.has(k)) byTime.set(k, { _time: t });
                return byTime.get(k);
            };

            avgPts.forEach(pt => { const e=ensure(pt.date); if (e) e.mean = Number(pt.value); });
            minPts.forEach(pt => { const e=ensure(pt.date); if (e) e.min  = Number(pt.value); });
            maxPts.forEach(pt => { const e=ensure(pt.date); if (e) e.max  = Number(pt.value); });
            stdPts.forEach(pt => { const e=ensure(pt.date); if (e) e.std  = Number(pt.value); });

            const merged = [...byTime.values()]
                .filter(v => Number.isFinite(v.mean) && Number.isFinite(v.min) && Number.isFinite(v.max) && Number.isFinite(v.std))
                .sort((a,b)=>a._time - b._time);

            const meanPoints = merged.map(v => ({ x: v._time, y: v.mean }));

            const id = datasetIdFor(metric, 'boxplot');
            if (!p.datasets.has(id)) {
                const indexMap = new Map(merged.map(v => [String(v._time.getTime()), { min:v.min, max:v.max, std:v.std }]));
                const ds = {
                    _id: id,
                    label: (opt.label || '동정 해결 시간'),
                    data: meanPoints,
                    parsing: { xAxisKey: 'x', yAxisKey: 'y' },
                    borderColor: color,
                    backgroundColor: color,
                    tension: 0.25,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    fill: false,
                    spanGaps: true,
                    yAxisID: 'hours',
                    _saUnit: 'HOURS',
                    _boxIndex: indexMap   // ← 툴팁 계산에만 사용
                };

                p.chart.data.datasets.push(ds);
                p.datasets.add(id);
                groupSet.add(id);
                changed = true;
            }
        } else {
            const unit = String(optionMap.get(metric)?.unit || '').toUpperCase();
            const s = seriesMap.get(metric);
            const points = toPoints(s, unit);
            const id = datasetIdFor(metric);
            if (!p.datasets.has(id)) {
                const yAxis = axisByUnit(unit);
                const ds = {
                    _id: id,
                    label: optionMap.get(metric)?.label || metric,
                    data: points,
                    parsing: { xAxisKey: 'x', yAxisKey: 'y' },
                    borderColor: color,
                    backgroundColor: color,
                    tension: 0.25,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    fill: false,
                    spanGaps: true,
                    yAxisID: yAxis,
                    _saUnit: unit
                };
                p.chart.data.datasets.push(ds);
                p.datasets.add(id);
                groupSet.add(id);
                changed = true;
            }
        }

        if (changed) {
            p.groups.set(metric, groupSet);
            renderPlotGroupChips(plotId);
            applyAutoYAxisScaling(p.chart);
            p.chart.update();
            updateEmptyState(plotId);
            resizeAllCharts();
            syncAsideActiveStates();
        }
    }

    function removeMetricGroupFromPlot(plotId, metric){
        const p = plots.get(plotId); if (!p) return;
        const ids = p.groups.get(metric) || new Set();
        p.chart.data.datasets = (p.chart.data.datasets || []).filter(ds => !ids.has(ds._id));
        ids.forEach(id => p.datasets.delete(id));
        p.groups.delete(metric);
        renderPlotGroupChips(plotId);
        applyAutoYAxisScaling(p.chart);
        p.chart.update();
        updateEmptyState(plotId);
        resizeAllCharts();
        syncAsideActiveStates();
    }

    function toggleMetricGroupOnPlot(plotId, metric){
        const p = plots.get(plotId); if (!p) return;
        if (p.groups.has(metric)) removeMetricGroupFromPlot(plotId, metric);
        else addMetricGroupToPlot(plotId, metric);
    }

    function removeMetricGroupFromAllPlots(metric){
        for (const [plotId] of plots) removeMetricGroupFromPlot(plotId, metric);
        syncAsideActiveStates();
    }

    function moveGroupToPlot(metric, fromPlotId, toPlotId){
        if (fromPlotId === toPlotId) return;
        removeMetricGroupFromPlot(fromPlotId, metric);
        addMetricGroupToPlot(toPlotId, metric);
        const fromStrip = document.querySelector(`.si-plot-card[data-plot-id="${fromPlotId}"] .si-plot__datasets`);
        const toStrip   = document.querySelector(`.si-plot-card[data-plot-id="${toPlotId}"] .si-plot__datasets`);
        if (fromStrip) markDatasetsStripState(fromStrip);
        if (toStrip)   markDatasetsStripState(toStrip);
    }

    // 플롯 상단 칩(그룹 단위) — 드래그 이동 허용
    function renderPlotGroupChips(plotId){
        const p = plots.get(plotId); if (!p) return;
        const wrap = p.el.querySelector('.si-plot__datasets');
        const hasAny = p.chart.data.datasets.length > 0;

        wrap.innerHTML = '';
        wrap.classList.add('si-dropzone');
        wrap.setAttribute('data-empty', hasAny ? 'false' : 'true');

        if (!hasAny) return;

        for (const [groupId, children] of p.groups){
            const opt = optionMap.get(groupId);
            const color = colorForMetric(groupId);
            const label = opt?.label || groupId;
            const empty = [...children].every(id => {
                const ds = p.chart.data.datasets.find(d => d._id === id);
                return !ds || !Array.isArray(ds.data) || ds.data.length === 0;
            });

            const chip = document.createElement('div');
            chip.className='si-ds-chip';
            chip.dataset.groupId = groupId;
            chip.style.touchAction = 'none';
            chip.setAttribute('draggable','false');
            chip.addEventListener('dragstart', e => e.preventDefault());
            chip.innerHTML = `
        <span class="si-grip" aria-hidden="true">⋮</span>
        <span class="si-ds-chip__dot" style="background:${color}"></span>
        <span class="si-ds-chip__label">${escapeHtml(label)}</span>
        <small class="si-ds-chip__empty" aria-hidden="true" style="color:#94a3b8;">${empty ? '(데이터 없음)' : ''}</small>
        <button type="button" class="si-ds-chip__rm" data-no-drag title="제거" aria-label="제거">×</button>
      `;

            const rm = chip.querySelector('.si-ds-chip__rm');
            rm.addEventListener('click', (ev)=> {
                ev.preventDefault();
                ev.stopPropagation();
                removeMetricGroupFromPlot(plotId, groupId);
                const strip = chip.closest('.si-plot__datasets');
                if (strip) markDatasetsStripState(strip);
            });

            enablePointerDnD(
                chip,
                () => ({ type:'datasetGroup', groupId, fromPlotId: plotId }),
                (targetStrip, payload, done) => {
                    const toPlotId = targetStrip.closest('.si-plot-card')?.dataset.plotId || plotId;
                    moveGroupToPlot(payload.groupId, payload.fromPlotId, toPlotId);
                    markDatasetsStripState(targetStrip);
                    done();
                }
            );

            wrap.appendChild(chip);
        }
    }

    // aside 칩 상태 동기화 (활성 플롯 기준)
    function syncAsideActiveStates(){
        const active = getActivePlotId();
        const activeGroups = active ? (plots.get(active)?.groups || new Map()) : new Map();
        document.querySelectorAll('.service-insight__metrics .si-chip.si-chip--toggle').forEach(chip=>{
            const metric = chip.dataset.metric;
            const on = activeGroups.has(metric);
            chip.classList.toggle('is-active', !!on);
            chip.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
    }

    function updateRangeControlsState(meta) {
        if (!meta) return;

        const selectedRange = typeof meta.selectedRange === 'string' ? meta.selectedRange : '';
        const customActive = !!meta.customRangeActive;
        const startValue = meta.startDate != null ? String(meta.startDate) : '';
        const endValue = meta.endDate != null ? String(meta.endDate) : '';

        document.querySelectorAll('.si-range__quick .si-range__btn').forEach(btn => {
            const value = btn.getAttribute('data-range') || '';
            const isActive = !customActive && selectedRange && value === selectedRange;
            btn.classList.toggle('btn-primary', isActive);
            btn.classList.toggle('btn-outline-primary', !isActive);
            btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        });

        if (rangeStartInput) rangeStartInput.value = startValue;
        if (rangeEndInput) rangeEndInput.value = endValue;

        setCustomRangeOpen(customActive);
    }

    function updateBrowserUrl(meta) {
        if (!window.history || !window.history.replaceState) return;
        const url = new URL(window.location.href);
        url.searchParams.delete('range');
        url.searchParams.delete('startDate');
        url.searchParams.delete('endDate');

        if (meta && typeof meta.selectedRange === 'string' && meta.selectedRange) {
            url.searchParams.set('range', meta.selectedRange);
        }

        if (meta && meta.customRangeActive) {
            if (meta.startDate) url.searchParams.set('startDate', meta.startDate);
            if (meta.endDate) url.searchParams.set('endDate', meta.endDate);
        }

        const query = url.searchParams.toString();
        const next = url.pathname + (query ? `?${query}` : '');
        window.history.replaceState(null, document.title, next);
    }

    async function requestAndApplyRange(target, fallbackHref) {
        let url;
        try {
            url = target instanceof URL ? target : new URL(String(target), window.location.origin || window.location.href);
        } catch (error) {
            console.warn('Invalid service insight range URL.', error);
            if (fallbackHref) window.location.href = fallbackHref;
            return;
        }

        try {
            const response = await fetch(url.toString(), {
                method: 'GET',
                headers: { 'Accept': 'application/json' },
                credentials: 'same-origin'
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();
            if (!data || typeof data !== 'object' || !data.viewModel) {
                throw new Error('Invalid response payload');
            }

            applyViewModelPayload(data.viewModel);
            refreshAllPlots();
            updateRangeControlsState(data);
            updateBrowserUrl(data);

            if (data.error) {
                console.warn('Service insight stats responded with a fallback dataset.');
            }
        } catch (error) {
            console.warn('Failed to refresh service insight data without reloading.', error);
            if (fallbackHref) {
                window.location.href = fallbackHref;
            }
        }
    }

    function setupRangeControls() {
        if (rangeQuickContainer) {
            rangeQuickContainer.querySelectorAll('.si-range__btn').forEach(btn => {
                btn.addEventListener('click', (event) => {
                    if (event.defaultPrevented) return;
                    if (event.button !== 0) return;
                    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

                    const href = btn.getAttribute('href');
                    if (!href) return;

                    event.preventDefault();
                    setCustomRangeOpen(false);
                    const absoluteHref = btn.href || href;
                    requestAndApplyRange(absoluteHref, absoluteHref);
                });
            });
        }

        if (rangeCustomToggle && rangeForm) {
            rangeCustomToggle.addEventListener('click', (event) => {
                event.preventDefault();
                const currentlyOpen = rangeForm.classList.contains('is-open');
                const next = !currentlyOpen;
                setCustomRangeOpen(next);
                if (next && rangeStartInput) {
                    try { rangeStartInput.focus(); } catch (_) {}
                }
            });
        }

        if (rangeForm) {
            rangeForm.addEventListener('submit', (event) => {
                if (event.defaultPrevented) return;
                event.preventDefault();

                const base = rangeForm.getAttribute('action') || dataEndpoint || window.location.pathname;
                let url;
                try {
                    url = new URL(base, window.location.origin || window.location.href);
                } catch (error) {
                    console.warn('Invalid service insight form action URL.', error);
                    if (typeof rangeForm.submit === 'function') {
                        rangeForm.submit();
                    }
                    return;
                }

                url.searchParams.set('range', 'custom');

                const startValue = rangeStartInput?.value ? rangeStartInput.value.trim() : '';
                const endValue = rangeEndInput?.value ? rangeEndInput.value.trim() : '';

                if (startValue) url.searchParams.set('startDate', startValue);
                else url.searchParams.delete('startDate');

                if (endValue) url.searchParams.set('endDate', endValue);
                else url.searchParams.delete('endDate');

                requestAndApplyRange(url, url.toString());
            });
        }
    }

    // 컨트롤
    if (addPlotBtn) addPlotBtn.addEventListener('click', ()=> createPlot());
    if (clearAllBtn) clearAllBtn.addEventListener('click', ()=>{
        for (const [plotId, p] of plots){
            p.chart.data.datasets = [];
            p.datasets.clear();
            p.groups.clear();
            p.chart.update();
            renderPlotGroupChips(plotId);
            updateEmptyState(plotId);
        }
        resizeAllCharts();
        syncAsideActiveStates();
    });

    // 초기 렌더
    renderGroups();
    createPlot();
    setupRangeControls();

    // 툴팁 초기화
    window.addEventListener('load', () => initTooltipsIn(document));

    // 전역: 드래그 중 기본 drag/select/스크롤 억제
    document.addEventListener('dragstart', (e) => {
        if (document.body.classList.contains('is-dragging')) e.preventDefault();
    }, true);
    document.addEventListener('selectstart', (e) => {
        if (document.body.classList.contains('is-dragging')) e.preventDefault();
    }, true);
    window.addEventListener('touchmove', (e) => {
        if (document.body.classList.contains('is-dragging')) e.preventDefault();
    }, { passive: false });

    // ===== DnD 유틸 =====
    function enablePointerDnD(el, onDragData, onDropToStrip){
        let dragging = false;
        let ghost = null;

        const onPointerDown = (e)=>{
            if (e.target?.hasAttribute?.('data-no-drag')) return;
            dragging = true;
            document.body.classList.add('is-dragging');
            el.classList.add('is-dragging');
            const rect = el.getBoundingClientRect();

            ghost = el.cloneNode(true);
            ghost.classList.add('si-ds-chip--ghost');
            ghost.style.position = 'fixed';
            ghost.style.pointerEvents = 'none';
            ghost.style.left = `${e.clientX - rect.width/2}px`;
            ghost.style.top  = `${e.clientY - rect.height/2}px`;
            ghost.style.width = `${rect.width}px`;
            ghost.style.opacity = '0.75';
            ghost.style.zIndex = '1050';
            document.body.appendChild(ghost);

            e.preventDefault();
        };

        const onPointerMove = (e)=>{
            if (!dragging || !ghost) return;
            ghost.style.left = `${e.clientX - ghost.offsetWidth/2}px`;
            ghost.style.top  = `${e.clientY - ghost.offsetHeight/2}px`;
        };

        const onPointerUp = (e)=>{
            if (!dragging) return;
            dragging = false;
            document.body.classList.remove('is-dragging');
            el.classList.remove('is-dragging');
            if (ghost) { ghost.remove(); ghost = null; }

            // drop target 판별
            const targetStrip = document.elementFromPoint(e.clientX, e.clientY)?.closest?.('.si-plot__datasets.si-dropzone');
            if (targetStrip) {
                const payload = typeof onDragData === 'function' ? onDragData() : onDragData;
                if (payload && typeof onDropToStrip === 'function') {
                    onDropToStrip(targetStrip, payload, ()=>{});
                }
            }
        };

        el.addEventListener('pointerdown', onPointerDown);
        window.addEventListener('pointermove', onPointerMove);
        window.addEventListener('pointerup', onPointerUp);
    }
})();
