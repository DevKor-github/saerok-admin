(function () {
    var form = document.getElementById('dexForm');
    if (!form) {
        return;
    }

    var fileInput = document.getElementById('dexImageFile');
    var objectKeyInput = document.getElementById('dexObjectKey');
    var contentTypeInput = document.getElementById('dexContentType');
    var statusElement = document.getElementById('dexImageUploadStatus');
    var previewImage = document.getElementById('dexImagePreview');
    var previewEmpty = document.getElementById('dexImagePreviewEmpty');
    var residencyRows = document.getElementById('residencyRows');
    var addResidencyRowButton = document.getElementById('addResidencyRow');

    var setStatus = function (message, variantClass) {
        if (!statusElement) {
            return;
        }
        statusElement.textContent = message;
        statusElement.classList.remove('text-success', 'text-danger', 'text-muted');
        statusElement.classList.add(variantClass || 'text-muted');
    };

    var setPreview = function (file) {
        if (!previewImage || !file) {
            return;
        }
        var objectUrl = URL.createObjectURL(file);
        previewImage.src = objectUrl;
        previewImage.classList.remove('d-none');
        if (previewEmpty) {
            previewEmpty.classList.add('d-none');
        }
        previewImage.onload = function () {
            URL.revokeObjectURL(objectUrl);
        };
    };

    var uploadFile = function (file) {
        if (!file) {
            return;
        }
        var contentType = file.type || 'application/octet-stream';
        if (objectKeyInput) {
            objectKeyInput.value = '';
        }
        if (fileInput) {
            fileInput.required = true;
        }
        if (contentTypeInput) {
            contentTypeInput.value = '';
        }
        setStatus('이미지 업로드 URL을 요청하고 있습니다...', 'text-muted');

        fetch('/dex/image/presign', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contentType: contentType })
        }).then(function (response) {
            if (!response.ok) {
                return response.json().catch(function () {
                    return {};
                }).then(function (body) {
                    throw new Error(body.message || 'Presign failed');
                });
            }
            return response.json();
        }).then(function (data) {
            if (!data || !data.presignedUrl || !data.objectKey) {
                throw new Error('Invalid presign response');
            }
            setStatus('이미지를 업로드하는 중입니다...', 'text-muted');
            return fetch(data.presignedUrl, {
                method: 'PUT',
                headers: { 'Content-Type': contentType },
                body: file
            }).then(function (uploadResponse) {
                if (!uploadResponse.ok) {
                    throw new Error('Upload failed');
                }
                if (objectKeyInput) {
                    objectKeyInput.value = data.objectKey;
                }
                if (fileInput) {
                    fileInput.required = false;
                }
                if (contentTypeInput) {
                    contentTypeInput.value = contentType;
                }
                setPreview(file);
                setStatus('이미지 업로드가 완료되었습니다.', 'text-success');
            });
        }).catch(function (error) {
            setStatus(error.message || '이미지 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.', 'text-danger');
        });
    };

    var refreshRemoveButtons = function () {
        if (!residencyRows) {
            return;
        }
        var rows = residencyRows.querySelectorAll('.residency-row');
        rows.forEach(function (row) {
            var removeButton = row.querySelector('.residency-row-remove');
            if (removeButton) {
                removeButton.disabled = rows.length <= 1;
            }
        });
    };

    var reindexResidencyRows = function () {
        if (!residencyRows) {
            return;
        }
        residencyRows.querySelectorAll('.residency-row').forEach(function (row, index) {
            row.querySelectorAll('[data-field]').forEach(function (control) {
                var field = control.dataset.field;
                control.name = 'residencies[' + index + '].' + field;
                if (field === 'residencyType' || field === 'rarity') {
                    var id = 'residency-' + index + '-' + (field === 'residencyType' ? 'type' : 'rarity');
                    control.id = id;
                    var label = control.parentElement.querySelector('label');
                    if (label) {
                        label.htmlFor = id;
                    }
                } else if (field === 'months') {
                    var monthId = 'residency-' + index + '-month-' + control.value;
                    control.id = monthId;
                    var monthLabel = control.parentElement.querySelector('label');
                    if (monthLabel) {
                        monthLabel.htmlFor = monthId;
                    }
                }
            });
        });
    };

    var addResidencyRow = function () {
        if (!residencyRows) {
            return;
        }
        var sourceRow = residencyRows.querySelector('.residency-row');
        if (!sourceRow) {
            return;
        }
        var row = sourceRow.cloneNode(true);
        row.querySelectorAll('select').forEach(function (select) {
            select.value = '';
        });
        row.querySelectorAll('input[type="checkbox"]').forEach(function (checkbox) {
            checkbox.checked = false;
        });
        residencyRows.appendChild(row);
        reindexResidencyRows();
        refreshRemoveButtons();
    };

    if (fileInput) {
        fileInput.addEventListener('change', function () {
            var file = fileInput.files && fileInput.files[0];
            if (!file) {
                return;
            }
            uploadFile(file);
        });
    }

    if (addResidencyRowButton) {
        addResidencyRowButton.addEventListener('click', addResidencyRow);
    }

    if (residencyRows) {
        residencyRows.addEventListener('click', function (event) {
            var button = event.target.closest('.residency-row-remove');
            if (!button || button.disabled) {
                return;
            }
            var row = button.closest('.residency-row');
            if (row) {
                row.remove();
                reindexResidencyRows();
                refreshRemoveButtons();
            }
        });
    }

    form.addEventListener('submit', function (event) {
        if (objectKeyInput && !objectKeyInput.value) {
            event.preventDefault();
            setStatus('대표 이미지 업로드가 완료된 뒤 등록해 주세요.', 'text-danger');
            if (fileInput) {
                fileInput.focus();
            }
        }
    });

    if (fileInput && objectKeyInput) {
        fileInput.required = !objectKeyInput.value;
    }
    reindexResidencyRows();
    refreshRemoveButtons();
})();
