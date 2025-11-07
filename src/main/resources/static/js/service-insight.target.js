// ===== resources/static/js/service-insight.target.js =====
(function () {
    const grid   = document.getElementById('si-plot-grid');
    const select = document.getElementById('si-target-select');
    const groups = document.getElementById('si-groups');
    const offcanvasEl = document.getElementById('siDataPicker');
    if (!grid || !select || !groups) return;

    /* ---------- Helpers ---------- */
    function getActivePlotCard() {
        return grid.querySelector('.si-plot-card.si-plot--active');
    }

    function getActivePlotIdFromDOM() {
        const active = getActivePlotCard();
        return active ? active.getAttribute('data-plot-id') : null;
    }

    function listPlots() {
        return Array.from(grid.querySelectorAll('.si-plot-card')).map((card, idx) => {
            const id = card.getAttribute('data-plot-id') || String(idx + 1);
            const titleEl = card.querySelector('.si-plot__title');
            const title = (titleEl?.textContent?.trim()) || `플롯 ${idx + 1}`;
            return { id, title, index: idx + 1 };
        });
    }

    function ensureActivePlotViaDom(id) {
        const card = id && grid.querySelector(`.si-plot-card[data-plot-id="${CSS.escape(id)}"]`);
        if (!card) return;
        const ev = new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window });
        card.dispatchEvent(ev);
    }

    // 🔧 FIX: aside 칩에서 metric 키 추출
    function getChipKey(chip) {
        return chip.getAttribute('data-metric')
            || chip.getAttribute('data-key')
            || chip.getAttribute('data-dsid')
            || chip.getAttribute('data-dataset-id')
            || chip.getAttribute('data-id')
            || null;
    }

    // 🔧 FIX: 플롯 카드 안에서 해당 metric이 포함되어 있는지 확인
    // 플롯 내부에는 data-group-id로 저장되어 있음!
    function activePlotContainsKey(key) {
        if (!key) return false;
        const card = getActivePlotCard();
        if (!card) return false;

        // 플롯 카드 안의 데이터셋 칩들을 검색 (data-group-id로 저장됨)
        const selector = `[data-group-id="${CSS.escape(key)}"]`;
        const found = !!card.querySelector(selector);

        return found;
    }

    /* ---------- Select ↔ Focus 동기화 ---------- */
    function refreshSelectOptions() {
        const active = getActivePlotIdFromDOM();
        const items = listPlots();
        const prev = select.value;

        select.innerHTML = '';
        items.forEach(({id, index}) => {
            const opt = document.createElement('option');
            opt.value = id;
            opt.textContent = String(index);
            select.appendChild(opt);
        });

        if (items.length) {
            const toSelect = (prev && items.some(i => i.id === prev)) ? prev : (active || items[0].id);
            select.value = toSelect;
        }
    }

    function syncSelectToActive() {
        const active = getActivePlotIdFromDOM();
        if (active && select.value !== active) select.value = active;
    }

    select.addEventListener('change', () => {
        const picked = select.value;
        if (picked) ensureActivePlotViaDom(picked);
        scheduleChecklist();
    });

    /* ---------- 체크 인디케이터 ---------- */
    function computeChipChecked(chip) {
        const key = getChipKey(chip);

        // 1순위: 활성 플롯에 실제로 포함되어 있는지 확인
        if (key) {
            const isInPlot = activePlotContainsKey(key);
            return isInPlot;
        }

        // 2순위: 휴리스틱 (key가 없는 경우)
        const ap = chip.getAttribute('aria-pressed');
        const ac = chip.getAttribute('aria-checked');
        const cls = chip.className || '';
        const truthyAttr = (v) => v === 'true' || v === '1';
        return truthyAttr(ap) || truthyAttr(ac) || /\b(is-)?(active|selected|on)\b/.test(cls);
    }

    function updateChecklist() {
        // PC와 모바일 모두의 칩을 찾아서 업데이트
        const chips = document.querySelectorAll('#si-data-panel .si-chip.si-chip--toggle');

        chips.forEach(chip => {
            const checked = computeChipChecked(chip);
            chip.setAttribute('data-checked', checked ? 'true' : 'false');

            // 디버깅용 로그 (필요시 주석 해제)
            // const key = getChipKey(chip);
            // console.log('[Checklist]', key, 'checked:', checked);
        });
    }

    // 즉시 + 다음 프레임 조합으로 확실하게 갱신
    const scheduleChecklist = (() => {
        let rafId = null;
        let timeoutId = null;

        return function () {
            // 즉시 실행
            updateChecklist();

            // RAF로 한 번 더
            if (rafId) cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => {
                updateChecklist();
                rafId = null;

                // 그래도 안전하게 한 번 더 (비동기 처리 대비)
                if (timeoutId) clearTimeout(timeoutId);
                timeoutId = setTimeout(() => {
                    updateChecklist();
                    timeoutId = null;
                }, 50);
            });
        };
    })();

    // 칩 상호작용 이벤트
    groups.addEventListener('click', scheduleChecklist, false);
    groups.addEventListener('pointerup', scheduleChecklist, false);
    groups.addEventListener('keyup', (e) => {
        if (e.key === 'Enter' || e.key === ' ') scheduleChecklist();
    }, false);
    groups.addEventListener('change', scheduleChecklist, false);

    // DOM 변경 관찰
    const obsGroups = new MutationObserver(() => {
        scheduleChecklist();
    });
    obsGroups.observe(groups, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ['class', 'aria-pressed', 'aria-checked']
    });

    // 플롯 구조 변경 관찰
    const obsGrid = new MutationObserver(() => {
        refreshSelectOptions();
        syncSelectToActive();
        scheduleChecklist();
    });
    obsGrid.observe(grid, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class', 'data-plot-id']
    });

    // Offcanvas 이벤트
    if (offcanvasEl) {
        offcanvasEl.addEventListener('show.bs.offcanvas', () => {
            refreshSelectOptions();
            syncSelectToActive();
            scheduleChecklist();
        });

        offcanvasEl.addEventListener('shown.bs.offcanvas', () => {
            scheduleChecklist();
        });

        // 닫힐 때도 갱신 (다음 열림을 대비)
        offcanvasEl.addEventListener('hidden.bs.offcanvas', () => {
            scheduleChecklist();
        });
    }

    // 초기 동기화
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            refreshSelectOptions();
            syncSelectToActive();
            scheduleChecklist();
        });
    } else {
        refreshSelectOptions();
        syncSelectToActive();
        scheduleChecklist();
    }

    /* ---------- 코어 syncAsideActiveStates 오버라이드 ---------- */
    try {
        const original = window.syncAsideActiveStates;

        window.syncAsideActiveStates = function patchedSyncAsideActiveStates() {
            // 1) 코어 로직 실행 (데스크톱 칩 갱신)
            if (typeof original === 'function') {
                try {
                    original();
                } catch (e) {
                    console.warn('[SyncAsideActiveStates] Original failed:', e);
                }
            }

            // 2) 모바일 포함 모든 칩 갱신
            const allChips = document.querySelectorAll('#si-data-panel .si-chip.si-chip--toggle');

            allChips.forEach(chip => {
                const key = getChipKey(chip);
                const isActive = key ? activePlotContainsKey(key) : false;

                chip.classList.toggle('is-active', isActive);
                chip.setAttribute('aria-pressed', isActive ? 'true' : 'false');
            });

            // 3) 체크 인디케이터도 갱신
            scheduleChecklist();
        };

        console.log('[ServiceInsight] syncAsideActiveStates overridden successfully');

    } catch (e) {
        console.error('[ServiceInsight] Failed to override syncAsideActiveStates:', e);
    }
})();