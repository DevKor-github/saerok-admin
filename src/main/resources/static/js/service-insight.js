(function () {
    const dataElement = document.getElementById('serviceInsightData');
    if (!dataElement) {
        return;
    }

    let payload;
    try {
        payload = JSON.parse(dataElement.textContent || '{}');
    } catch (error) {
        console.warn('Failed to parse service insight payload.', error);
        payload = {};
    }

    const metricOptions = Array.isArray(payload.metricOptions) ? payload.metricOptions : [];
    const seriesList = Array.isArray(payload.series) ? payload.series : [];
    const componentLabels = payload.componentLabels && typeof payload.componentLabels === 'object'
        ? payload.componentLabels
        : {};

    const seriesMap = new Map(seriesList.map(item => [item.metric, item]));
    const optionMap = new Map(metricOptions.map(option => [option.metric, option]));

    const metricButtons = Array.from(document.querySelectorAll('[data-role="service-insight-metric"]'));
    const unitLabelElement = document.querySelector('[data-role="service-insight-unit"]');
    const emptyStateElement = document.getElementById('serviceInsightEmptyState');
    const canvas = document.getElementById('serviceInsightChart');

    if (!canvas || typeof Chart === 'undefined') {
        return;
    }

    const colorPalette = [
        '#2563eb', '#16a34a', '#dc2626', '#f97316', '#9333ea',
        '#0ea5e9', '#059669', '#ea580c', '#3b82f6', '#14b8a6'
    ];
    const unitDisplayName = {
        COUNT: '건',
        RATIO: '%',
        HOURS: '시간'
    };
    const axisByUnit = {
        COUNT: 'count',
        RATIO: 'ratio',
        HOURS: 'hours'
    };

    const chart = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: {
            datasets: []
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'nearest',
                intersect: false
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'day',
                        tooltipFormat: 'yyyy-LL-dd'
                    },
                    ticks: {
                        autoSkip: true,
                        maxRotation: 0
                    },
                    grid: {
                        color: 'rgba(148, 163, 184, 0.2)'
                    }
                },
                count: {
                    display: false,
                    position: 'left',
                    beginAtZero: true,
                    ticks: {
                        callback: value => formatCount(value)
                    }
                },
                ratio: {
                    display: false,
                    position: 'right',
                    beginAtZero: true,
                    min: 0,
                    max: 100,
                    grid: {
                        drawOnChartArea: false
                    },
                    ticks: {
                        callback: value => `${value}%`
                    }
                },
                hours: {
                    display: false,
                    position: 'right',
                    beginAtZero: true,
                    grid: {
                        drawOnChartArea: false
                    },
                    ticks: {
                        callback: value => formatHours(value)
                    }
                }
            },
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        usePointStyle: true,
                        boxHeight: 8,
                        boxWidth: 8
                    }
                },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label(context) {
                            const dataset = context.dataset || {};
                            const unit = dataset._saUnit;
                            const label = dataset.label || '';
                            const value = context.parsed.y;
                            const formatted = formatValue(value, unit);
                            return label ? `${label}: ${formatted}` : formatted;
                        }
                    }
                }
            }
        }
    });

    const activeMetrics = new Set(metricOptions
        .filter(option => Boolean(option.defaultActive))
        .map(option => option.metric));

    metricButtons.forEach(button => {
        const metric = button.getAttribute('data-metric');
        if (!metric) {
            return;
        }
        if (activeMetrics.has(metric)) {
            button.classList.add('is-active');
        }
        button.addEventListener('click', () => {
            if (activeMetrics.has(metric)) {
                activeMetrics.delete(metric);
                button.classList.remove('is-active');
            } else {
                activeMetrics.add(metric);
                button.classList.add('is-active');
            }
            refreshChart();
        });
    });

    refreshChart();

    function refreshChart() {
        const datasets = buildDatasets();
        chart.data.datasets = datasets;
        updateAxesVisibility(new Set(datasets.map(dataset => dataset.yAxisID)));
        chart.update();
        updateUnitLabel();
        updateEmptyState(datasets);
    }

    function buildDatasets() {
        const datasets = [];
        let colorIndex = 0;
        activeMetrics.forEach(metricKey => {
            const option = optionMap.get(metricKey);
            if (!option) {
                return;
            }
            const metricSeries = seriesMap.get(metricKey);
            const unit = option.unit;
            const axis = axisByUnit[unit] || 'count';

            if (option.multiSeries) {
                const knownComponents = metricSeries && Array.isArray(metricSeries.components)
                        ? metricSeries.components
                        : [];
                const componentsToRender = knownComponents.length > 0
                        ? knownComponents
                        : buildEmptyComponents(metricKey);

                if (componentsToRender.length === 0) {
                    const color = colorPalette[colorIndex % colorPalette.length];
                    colorIndex += 1;
                    datasets.push(createDataset(option.label, [], unit, color, axis));
                } else {
                    componentsToRender.forEach(component => {
                        const color = colorPalette[colorIndex % colorPalette.length];
                        colorIndex += 1;
                        datasets.push(createDataset(
                                `${option.label} · ${componentLabel(metricKey, component.key)}`,
                                component.points,
                                unit,
                                color,
                                axis
                        ));
                    });
                }
            } else {
                const color = colorPalette[colorIndex % colorPalette.length];
                colorIndex += 1;
                const points = metricSeries ? metricSeries.points : [];
                datasets.push(createDataset(option.label, points, unit, color, axis));
            }
        });
        return datasets;
    }

    function buildEmptyComponents(metricKey) {
        const labels = componentLabels[metricKey];
        if (!labels) {
            return [];
        }
        return Object.keys(labels).map(key => ({ key, points: [] }));
    }

    function createDataset(label, points, unit, color, axis) {
        return {
            label,
            data: toChartPoints(points, unit),
            borderColor: color,
            backgroundColor: color,
            tension: 0.25,
            pointRadius: 3,
            pointHoverRadius: 5,
            fill: false,
            spanGaps: true,
            yAxisID: axis,
            _saUnit: unit
        };
    }

    function toChartPoints(points, unit) {
        if (!Array.isArray(points)) {
            return [];
        }
        return points
            .filter(point => point && point.date != null && point.value != null)
            .map(point => ({
                x: point.date,
                y: normalizeValue(point.value, unit)
            }));
    }

    function normalizeValue(value, unit) {
        const numeric = typeof value === 'number' ? value : Number(value);
        if (!Number.isFinite(numeric)) {
            return null;
        }
        if (unit === 'RATIO') {
            return numeric * 100;
        }
        return numeric;
    }

    function componentLabel(metric, key) {
        const labels = componentLabels[metric];
        if (labels && Object.prototype.hasOwnProperty.call(labels, key)) {
            return labels[key];
        }
        return key;
    }

    function updateAxesVisibility(axesInUse) {
        ['count', 'ratio', 'hours'].forEach(axisKey => {
            const scale = chart.options.scales[axisKey];
            if (scale) {
                scale.display = axesInUse.has(axisKey);
            }
        });
    }

    function updateUnitLabel() {
        if (!unitLabelElement) {
            return;
        }
        const units = new Set();
        activeMetrics.forEach(metric => {
            const option = optionMap.get(metric);
            if (option) {
                units.add(option.unit);
            }
        });
        const unitText = Array.from(units)
            .map(unit => unitDisplayName[unit] || unit)
            .join(' · ');
        unitLabelElement.textContent = unitText ? `단위: ${unitText}` : '';
    }

    function updateEmptyState(datasets) {
        if (!emptyStateElement) {
            return;
        }
        const hasData = datasets.some(dataset => Array.isArray(dataset.data) && dataset.data.length > 0);
        emptyStateElement.classList.toggle('d-none', hasData);
        if (canvas.parentElement) {
            canvas.parentElement.classList.toggle('d-none', !hasData);
        }
    }

    function formatValue(value, unit) {
        if (!Number.isFinite(value)) {
            return '-';
        }
        switch (unit) {
            case 'RATIO':
                return `${value.toFixed(1)}%`;
            case 'HOURS':
                return `${value.toFixed(2)}시간`;
            default:
                return formatCount(value);
        }
    }

    function formatCount(value) {
        if (!Number.isFinite(value)) {
            return '-';
        }
        return Math.round(value).toLocaleString();
    }

    function formatHours(value) {
        if (!Number.isFinite(value)) {
            return '-';
        }
        return value.toFixed(1);
    }
})();
