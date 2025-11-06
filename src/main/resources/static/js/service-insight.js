// ===== resources/static/js/service-insight.js =====
(function () {
    // ---------- 0) 데이터 파싱 ----------
    const dataElement = document.getElementById('serviceInsightData');
    if (!dataElement) return;

    let payload = {};
    try { payload = JSON.parse(dataElement.textContent || '{}'); }
    catch (err) { console.warn('Failed to parse service insight payload.', err); }

    const metricOptions   = Array.isArray(payload.metricOptions) ? payload.metricOptions : [];
    const seriesList      = Array.isArray(payload.series)        ? payload.series        : [];
    const componentLabels = (payload.componentLabels && typeof payload.componentLabels === 'object') ? payload.componentLabels : {};

    const optionMap = new Map(metricOptions.map(o => [o.metric, o]));
    const seriesMap = new Map(seriesList.map(s => [s.metric, s]));

    // 서버 enum 키
    const METRICS = {
        TOTAL_COUNT:      'COLLECTION_TOTAL_COUNT',
        PRIVATE_RATIO:    'COLLECTION_PRIVATE_RATIO',
        PENDING_COUNT:    'BIRD_ID_PENDING_COUNT',
        RESOLVED_COUNT:   'BIRD_ID_RESOLVED_COUNT',
        RESOLUTION_STATS: 'BIRD_ID_RESOLUTION_STATS', // components: min_hours, avg_hours, max_hours, stddev_hours
    };

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
        if (d instanceof Date) return d;                       // 이미 Date면 그대로(대부분 시간 포함)
        if (typeof d === 'number') return new Date(d);         // epoch ms

        const s = String(d).trim();

        // 'YYYY-MM-DD' 같은 날짜만 있는 경우 → KST 자정으로 고정
        const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(s);
        // Luxon 사용 (adapter 스크립트가 로드되어 있음)
        const L = window.luxon;
        if (L && L.DateTime) {
            let dt = L.DateTime.fromISO(s, { zone: TZ });
            if (!dt.isValid) dt = L.DateTime.fromJSDate(new Date(s), { zone: TZ });
            if (!dt.isValid) return null;
            if (dateOnly) dt = dt.startOf('day');                // ✅ KST 00:00 고정
            return dt.toJSDate();
        }

        // Luxon이 없을 경우의 안전망: 날짜만 오면 로컬 타임존 자정으로 보정
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

            el.addEventListener('click',      () => { try { inst.hide(); } catch(_) {} });
            el.addEventListener('mouseleave', () => { try { inst.hide(); } catch(_) {} });
            el.addEventListener('touchend',   () => { try { inst.hide(); } catch(_) {} });
        });
    }

    // ---- 드랍 타겟 탐색 (플롯 상단 데이터셋 스트립만 드랍 허용) ----
    function pickDatasetsStripAt(x, y) {
        let el = document.elementFromPoint(x, y);
        while (el && el !== document.body) {
            if (el.classList && el.classList.contains('si-plot__datasets')) return el;
            el = el.parentElement;
        }
        return null;
    }

    // ---- 포인터 기반 DnD (플롯 칩 전용) ----
    function isInteractiveTarget(node) {
        return !!(node && (node.closest('button, a, input, textarea, select, [contenteditable="true"], [data-no-drag]')));
    }

    function enablePointerDnD(chip, payloadProvider, onDrop) {
        if (!chip) return;

        chip.setAttribute('draggable', 'false');
        chip.addEventListener('dragstart', e => e.preventDefault());
        chip.style.touchAction = 'none';

        chip.addEventListener('pointerdown', (e) => {
            if (e.button !== 0) return;
            if (isInteractiveTarget(e.target)) return;
            e.preventDefault();
            try { chip.setPointerCapture(e.pointerId); } catch(_) {}

            const start = { x: e.clientX, y: e.clientY };
            let moved = false;
            let ghost = null;

            const createGhost = () => {
                const rect = chip.getBoundingClientRect();
                ghost = chip.cloneNode(true);
                ghost.classList.add('si-chip--ghost');
                ghost.style.width = rect.width + 'px';
                ghost.style.minWidth = rect.width + 'px';
                document.body.appendChild(ghost);
                setGhost(e.clientX - rect.width/2, e.clientY - rect.height/2);
                chip.classList.add('is-drag-origin');
                document.body.classList.add('is-dragging');
            };
            const setGhost = (gx, gy) => {
                ghost.style.setProperty('--x', gx + 'px');
                ghost.style.setProperty('--y', gy + 'px');
                ghost.style.transform = `translate3d(var(--x,0px), var(--y,0px), 0)`;
            };

            const onMove = (ev) => {
                const dx = ev.clientX - start.x;
                const dy = ev.clientY - start.y;
                if (!moved && Math.hypot(dx,dy) > 2) {
                    moved = true;
                    createGhost();
                    try { window.getSelection()?.removeAllRanges?.(); } catch(_) {}
                }
                if (!moved) return;

                ev.preventDefault();
                ghost.style.setProperty('--x', (ev.clientX - ghost.offsetWidth/2) + 'px');
                ghost.style.setProperty('--y', (ev.clientY - ghost.offsetHeight/2) + 'px');

                document.querySelectorAll('.si-drop-target').forEach(t => t.classList.remove('si-drop-target'));
                const target = pickDatasetsStripAt(ev.clientX, ev.clientY);
                if (target) target.classList.add('si-drop-target');
            };

            const finish = (ev) => {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', finish, true);
                document.removeEventListener('pointercancel', finish, true);
                try { chip.releasePointerCapture(ev.pointerId); } catch(_) {}
                document.body.classList.remove('is-dragging');

                if (!moved) { chip.classList.remove('is-drag-origin'); return; }

                const target = pickDatasetsStripAt(ev.clientX, ev.clientY);
                document.querySelectorAll('.si-drop-target').forEach(t => t.classList.remove('si-drop-target'));

                if (target) {
                    const payload = payloadProvider();
                    onDrop(target, payload, () => {
                        const finalRect = target.getBoundingClientRect();
                        const gx = finalRect.left + 12;
                        const gy = finalRect.top + 8;
                        ghost.style.transition = 'transform 160ms cubic-bezier(.2,.8,.2,1)';
                        ghost.style.setProperty('--x', gx + 'px');
                        ghost.style.setProperty('--y', gy + 'px');
                        setTimeout(() => {
                            ghost?.remove();
                            chip.classList.remove('is-drag-origin');
                        }, 170);
                    });
                } else {
                    ghost?.remove();
                    chip.classList.remove('is-drag-origin');
                }
            };

            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', finish, true);
            document.addEventListener('pointercancel', finish, true);
        });
    }

    // ---------- 그룹/칩 렌더 (aside: 토글만, 드래그 없음) ----------
    const groupsWrap = document.getElementById('si-groups');

    const GROUPS = [
        { key: 'privacy', name: '새록',      metrics: [METRICS.TOTAL_COUNT, METRICS.PRIVATE_RATIO] },
        { key: 'id',      name: '동정 요청', metrics: [METRICS.PENDING_COUNT, METRICS.RESOLVED_COUNT, METRICS.RESOLUTION_STATS] },
    ];
    const known = new Set(GROUPS.flatMap(g => g.metrics));
    const others = metricOptions.map(m => m.metric).filter(m => !known.has(m));
    if (others.length) GROUPS.push({ key: 'others', name: '기타', metrics: others });

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

                // aside 툴팁: 제목+설명
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

                    ctx.save();
                    // 수염(최소~최대)
                    ctx.strokeStyle = 'rgba(148,163,184,.9)';
                    ctx.lineWidth = 2;
                    ctx.beginPath(); ctx.moveTo(x, yMin); ctx.lineTo(x, yMax); ctx.stroke();
                    ctx.beginPath();
                    ctx.moveTo(x - capHalf, yMin); ctx.lineTo(x + capHalf, yMin);
                    ctx.moveTo(x - capHalf, yMax); ctx.lineTo(x + capHalf, yMax);
                    ctx.stroke();

                    // 박스(±표준편차)
                    ctx.fillStyle = 'rgba(91,140,255,.18)';
                    ctx.strokeStyle = 'rgba(91,140,255,.9)';
                    ctx.lineWidth = 2;
                    const top = Math.min(yLo, yHi), height = Math.abs(yHi - yLo) || 2;
                    ctx.beginPath();
                    ctx.rect(x - boxHalf, top, boxHalf*2, height);
                    ctx.fill(); ctx.stroke();

                    // 평균선
                    ctx.strokeStyle = 'rgba(30,41,59,.95)';
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    ctx.moveTo(x - (boxHalf + 3), yMean);
                    ctx.lineTo(x + (boxHalf + 3), yMean);
                    ctx.stroke();
                    ctx.restore();
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
                const x = toDateKST(pt.date);                    // ✅ KST 00:00 기준
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
            const months = hours / (H_PER_DAY * D_PER_MONTH); // 30일=1개월
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
                        round:'day',                // ✅ 눈금도 일자 기준으로 스냅
                        tooltipFormat:'yyyy-LL-dd',
                        zone: TZ                    // ✅ 축 파싱 타임존 고정
                    },
                    adapters:{ date:{ zone: TZ } }, // ✅ 어댑터에도 동일 적용
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
                        // 박스플롯은 한 줄 요약만 (상세설명 제외)
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
                                    const fmt = (n)=>formatValue(n,'HOURS'); // ← 여기서 시간/일/개월 자동 환산
                                    if (mean!=null && lo!=null && hi!=null) {
                                        // 기존 문구 스타일 유지
                                        return `${base}: 평균적으로 ${fmt(lo)} ~ ${fmt(hi)} 정도 (최소: ${fmt(st.min)}, 최대: ${fmt(st.max)})`;
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
                .sort((a,b)=> a._time - b._time);

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
                    parsing: { xAxisKey: 'x', yAxisKey: 'y' }, // ✅ 명시 파싱
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

    // 플롯 상단 칩(그룹 단위) 렌더 — 이 칩들만 드래그 이동 허용
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
})();
