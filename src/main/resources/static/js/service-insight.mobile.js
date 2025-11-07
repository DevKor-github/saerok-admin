// ===== resources/static/js/service-insight.mobile.js =====
// 모바일 전용 보조 스크립트: 데이터 패널(#si-data-panel) + 타이틀(#si-target-title) 이동
(function () {
    const MOBILE_MAX = 991.98;

    function q(id){ return document.getElementById(id); }

    function relocate() {
        const panel = q('si-data-panel');
        const desktopPanelAnchor = q('si-groups-desktop-anchor');
        const mobilePanelHost = q('si-data-panel-mobile-host');

        const title = q('si-target-title');
        const desktopTitleAnchor = q('si-target-title-desktop-anchor');
        const mobileTitleHost = q('si-target-title-mobile-host');

        if (!panel || !desktopPanelAnchor || !mobilePanelHost) return;
        if (!title || !desktopTitleAnchor || !mobileTitleHost) return;

        const isMobile = window.innerWidth <= MOBILE_MAX;

        // 패널 이동
        const panelParent = panel.parentElement;
        if (isMobile && panelParent !== mobilePanelHost) {
            mobilePanelHost.appendChild(panel);
        } else if (!isMobile && panelParent !== desktopPanelAnchor) {
            desktopPanelAnchor.appendChild(panel);
        }

        // 타이틀 이동 (단일 표시 지점 보장)
        const titleParent = title.parentElement;
        if (isMobile && titleParent !== mobileTitleHost) {
            mobileTitleHost.prepend(title);
        } else if (!isMobile && titleParent !== desktopTitleAnchor) {
            desktopTitleAnchor.prepend(title);
        }
    }

    // FAB 가시성 제어: 바텀시트 열릴 때 숨기고, 닫히면 복원
    function setupFabVisibility(){
        const fab = q('si-fab');            // FAB 버튼
        const sheet = q('siDataPicker');    // 데이터 선택 바텀시트(Offcanvas)
        if (!fab || !sheet) return;

        const hideFab = () => {
            // 모바일 CSS가 #si-fab { display: inline-flex !important; }이므로
            // inline + !important로 안전하게 가린다.
            try {
                fab.style.setProperty('display', 'none', 'important');
            } catch {
                fab.style.display = 'none';
            }
        };
        const showFab = () => {
            fab.style.removeProperty('display'); // CSS 원래 규칙으로 복귀
        };

        sheet.addEventListener('show.bs.offcanvas', hideFab);
        sheet.addEventListener('hidden.bs.offcanvas', showFab);

        // 새로고침 등으로 시트가 이미 열려있는 경우 대비
        if (sheet.classList.contains('show')) hideFab();
    }

    // helper: 실제 화면에 보이는지 판단 (fixed 요소 대응)
    function isElementShown(el){
        if (!el) return false;
        const cs = window.getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
        // fixed 요소는 offsetParent가 null일 수 있으므로 rect 기반으로 판단
        return el.getClientRects().length > 0;
    }

    // 최초 진입 툴팁: "보고 싶은 데이터를 선택하세요"
    function setupFabFirstRunTooltip(){
        const fab   = q('si-fab');
        const sheet = q('siDataPicker');
        // Bootstrap 전역 객체가 없으면 조용히 패스 (필수는 아님)
        if (!fab || !window.bootstrap || !window.bootstrap.Tooltip) return;

        const KEY = 'si.fab.tooltip.dismissed';
        const isMobile = window.innerWidth <= MOBILE_MAX;

        // 세션 저장소로만 체크: 이 세션에서 한 번 닫으면 다시 안 뜨지만, 새 세션에서는 다시 뜸
        let dismissed = false;
        try {
            dismissed = (sessionStorage.getItem(KEY) === '1');
        } catch (_) {
            // sessionStorage 접근 불가 환경에서는 안내를 계속 띄우도록 false 유지
        }

        if (!isMobile || dismissed) return;

        // 혹시 기존 인스턴스가 있다면 정리
        const prev = window.bootstrap.Tooltip.getInstance(fab);
        if (prev) { try { prev.dispose(); } catch(_){} }

        const tip = new window.bootstrap.Tooltip(fab, {
            trigger: 'manual',
            placement: 'left',           // FAB 위치 특성상 좌측이 자연스러움
            container: 'body',
            customClass: 'tooltip-bubble',
            title: '보고 싶은 데이터를 선택하세요'
        });

        const maybeShow = () => {
            // 시트가 열려 있으면 미표시
            if (sheet && sheet.classList.contains('show')) return;
            if (!isElementShown(fab)) return;
            try { tip.show(); } catch(_) {}
        };

        // 사용자 상호작용 시: 이 세션에서만 영구 비표시
        const onClick = () => {
            try { tip.hide(); } catch(_) {}
            try { sessionStorage.setItem(KEY, '1'); } catch(_) {}
            fab.removeEventListener('click', onClick);
            window.removeEventListener('resize', onResizeCheck);
        };

        // 레이아웃 안정 후 노출
        setTimeout(maybeShow, 500);

        // 바텀시트가 열릴 때는 숨기고, 닫히면(아직 미해제라면) 다시 한 번 보여줌
        if (sheet) {
            sheet.addEventListener('show.bs.offcanvas', () => { try { tip.hide(); } catch(_) {} });
            sheet.addEventListener('hidden.bs.offcanvas', () => {
                // 세션에서만 체크
                let stillDismissed = false;
                try { stillDismissed = (sessionStorage.getItem(KEY) === '1'); } catch(_) {}
                if (!stillDismissed) setTimeout(maybeShow, 250);
            });
        }

        // 리사이즈 시 모바일 유지되는 동안만 안내 (데스크탑 전환 시 숨김)
        const onResizeCheck = () => {
            if (window.innerWidth > MOBILE_MAX) { try { tip.hide(); } catch(_) {} }
        };

        fab.addEventListener('click', onClick, { once: true });
        window.addEventListener('resize', onResizeCheck);
    }

    // === 그룹 헤더 테마 색 + 기본 접힘 + 드롭다운 인디케이터(왼쪽) ===
    function injectGroupThemeCss(){
        if (document.getElementById('si-group-theme-style')) return;
        const style = document.createElement('style');
        style.id = 'si-group-theme-style';
        style.textContent = `
/* 공통 스코프: 데스크탑(.service-insight) + 모바일 바텀시트(#siDataPicker) */
:is(.service-insight,#siDataPicker) details.si-group {
  border-radius: 10px;
  overflow: hidden;
}
:is(.service-insight,#siDataPicker) .si-group__summary{
  position: relative;
  display: flex;
  align-items: center;
  gap: .5rem;
  padding: .75rem .875rem .75rem 2rem; /* 왼쪽 chevron 공간 확보 */
  background: linear-gradient(90deg, rgba(var(--group-accent-rgb), .10), rgba(var(--group-accent-rgb), 0) 70%);
  border-left: 6px solid rgba(var(--group-accent-rgb), .90);
  cursor: pointer;
  user-select: none;
  transition: background-color .18s ease, box-shadow .18s ease;
}
:is(.service-insight,#siDataPicker) .si-group[open] .si-group__summary{
  background: linear-gradient(90deg, rgba(var(--group-accent-rgb), .14), rgba(var(--group-accent-rgb), .03) 70%);
}
:is(.service-insight,#siDataPicker) .si-group__summary:hover{
  box-shadow: inset 0 0 0 999px rgba(var(--group-accent-rgb), .03);
}
/* 드롭다운 꺾쇠(왼쪽) */
:is(.service-insight,#siDataPicker) .si-group__summary::before{
  content: "";
  position: absolute;
  left: .625rem;
  top: 50%;
  width: .6rem;
  height: .6rem;
  margin-top: -.3rem;
  border-right: 2px solid rgba(var(--group-accent-rgb), .80);
  border-bottom: 2px solid rgba(var(--group-accent-rgb), .80);
  transform: translateY(-50%) rotate(-45deg); /* 닫힘: ∨ 느낌 */
  transition: transform .16s ease, border-color .16s ease, opacity .16s ease;
  opacity: .95;
}
:is(.service-insight,#siDataPicker) .si-group[open] .si-group__summary::before{
  transform: translateY(-50%) rotate(45deg); /* 펼침: ∧ 느낌 */
  opacity: 1;
}
/* 액션 버튼 hover에 포커스 색상 */
:is(.service-insight,#siDataPicker) .si-group__btn:hover{
  color: rgb(var(--group-accent-rgb)) !important;
  border-color: rgba(var(--group-accent-rgb), .6) !important;
}
/* 그룹별 팔레트: hue 대비를 키워 확실히 구분 (collection=블루, user=그린, id=레드, others=퍼플) */
:is(.service-insight,#siDataPicker) .si-group { --group-accent-rgb: 59,130,246; }            /* 기본 파랑 */
:is(.service-insight,#siDataPicker) .si-group--collection { --group-accent-rgb: 37,99,235; }  /* 새록: blue-600 */
:is(.service-insight,#siDataPicker) .si-group--user       { --group-accent-rgb: 5,150,105; }  /* 유저: emerald-600 */
:is(.service-insight,#siDataPicker) .si-group--id         { --group-accent-rgb: 220,38,38; }  /* 동정 요청: red-600 */
:is(.service-insight,#siDataPicker) .si-group--others     { --group-accent-rgb: 139,92,246; } /* 기타: violet-500 */

/* [+ 전체] [- 전체] 버튼 미관 개선 */
:is(.service-insight,#siDataPicker) .si-bulk-btn {
  --btn-bg: rgba(0,0,0,.02);
  --btn-bd: rgba(0,0,0,.12);
  --btn-bg-h: rgba(0,0,0,.04);
  --btn-bd-h: rgba(0,0,0,.28);
  display: inline-flex;
  align-items: center;
  gap: .35rem;
  padding: .25rem .6rem;
  font-size: .85rem;
  line-height: 1.1;
  border-radius: 999px;
  border: 1px solid var(--btn-bd);
  background: var(--btn-bg);
  text-decoration: none !important;
}
:is(.service-insight,#siDataPicker) .si-bulk-btn:hover{
  background: var(--btn-bg-h);
  border-color: var(--btn-bd-h);
}
:is(.service-insight,#siDataPicker) .si-bulk-toolbar{
  display: flex;
  gap: .5rem;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}
        `.trim();
        document.head.appendChild(style);
    }

    function setupGroupThemingAndCollapse(){
        const containers = [
            document.getElementById('si-groups'),
            document.getElementById('siDataPicker')
        ].filter(Boolean);

        const applyTo = (root) => {
            const groups = Array.from(root.querySelectorAll('details.si-group'));
            if (!groups.length) return false;

            groups.forEach((details) => {
                // 1) 기본 접힘
                details.open = false;

                // 2) 이름 -> 키 매핑 후 테마 클래스 부여 (새록 => collection)
                const name = details.querySelector('.si-group__title')?.textContent?.trim() || '';
                let key = 'others';
                if (name.includes('새록')) key = 'collection';
                else if (name.includes('유저')) key = 'user';
                else if (name.includes('동정')) key = 'id';

                details.classList.remove('si-group--privacy','si-group--collection','si-group--user','si-group--id','si-group--others');
                details.classList.add(`si-group--${key}`);

                // 3) summary 기본 클래스 보강 (없다면 붙여 UI 일관화)
                const summary = details.querySelector('summary');
                if (summary && !summary.classList.contains('si-group__summary')) {
                    summary.classList.add('si-group__summary');
                }
            });
            return true;
        };

        // 즉시 적용 시도
        let foundAny = false;
        containers.forEach(root => { if (applyTo(root)) foundAny = true; });

        // 렌더가 늦는 경우를 대비해 컨테이너별로 감시
        if (!foundAny) {
            containers.forEach(root => {
                const mo = new MutationObserver(() => {
                    if (applyTo(root)) mo.disconnect();
                });
                mo.observe(root, { childList: true, subtree: true });
            });
        }
    }

    // [+ 전체] / [- 전체] 컨트롤을 세련된 pill 버튼으로 치장 (기존 클릭 동작 유지)
    function setupBulkControlStyling(){
        const SCOPE_SEL = '#si-groups, #siDataPicker';
        const enhance = (root) => {
            const nodes = Array.from(root.querySelectorAll('button, a'));
            if (!nodes.length) return;

            const isPlus = (t) => /\[\+\s*전체\]/.test(t) || /모두\s*펼치기/.test(t);
            const isMinus = (t) => /\[-\s*전체\]/.test(t) || /모두\s*접기/.test(t);

            let plusEl = null, minusEl = null;

            nodes.forEach(el => {
                if (el.dataset.siEnhanced) return;
                const text = (el.textContent || '').trim();

                if (isPlus(text)) {
                    el.classList.add('si-bulk-btn');
                    el.setAttribute('data-si-enhanced','1');
                    el.innerHTML = '<i class="bi bi-chevron-double-down"></i><span>모두 펼치기</span>';
                    if (el.tagName === 'BUTTON' && !el.getAttribute('type')) el.setAttribute('type','button');
                    plusEl = el;
                } else if (isMinus(text)) {
                    el.classList.add('si-bulk-btn');
                    el.setAttribute('data-si-enhanced','1');
                    el.innerHTML = '<i class="bi bi-chevron-double-up"></i><span>모두 접기</span>';
                    if (el.tagName === 'BUTTON' && !el.getAttribute('type')) el.setAttribute('type','button');
                    minusEl = el;
                }
            });

            // 두 버튼의 부모에 툴바 정렬 보강
            const host = plusEl?.parentElement || minusEl?.parentElement;
            if (host && !host.classList.contains('si-bulk-toolbar')) {
                host.classList.add('si-bulk-toolbar');
            }
        };

        document.querySelectorAll(SCOPE_SEL).forEach(enhance);

        // 동적 렌더 대응
        const roots = document.querySelectorAll(SCOPE_SEL);
        roots.forEach(root => {
            const mo = new MutationObserver(() => enhance(root));
            mo.observe(root, { childList: true, subtree: true });
        });
    }

    // 초기/리사이즈
    let t = null;
    function onResize(){
        clearTimeout(t);
        t = setTimeout(relocate, 120);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            relocate();
            setupFabVisibility();
            setupFabFirstRunTooltip();
            injectGroupThemeCss();
            setupGroupThemingAndCollapse();
            setupBulkControlStyling();
            window.addEventListener('resize', onResize);
        });
    } else {
        relocate();
        setupFabVisibility();
        setupFabFirstRunTooltip();
        injectGroupThemeCss();
        setupGroupThemingAndCollapse();
        setupBulkControlStyling();
        window.addEventListener('resize', onResize);
    }
})();
