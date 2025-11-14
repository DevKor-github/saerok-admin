(function () {
    var form = document.getElementById('adForm');
    if (!form) {
        return;
    }

    var fileInput = document.getElementById('adImageFile');
    var objectKeyInput = document.getElementById('adObjectKey');
    var contentTypeInput = document.getElementById('adContentType');
    var statusElement = document.getElementById('adImageUploadStatus');
    var previewImage = document.getElementById('adImagePreview');

    var setStatus = function (message, variantClass) {
        if (!statusElement) {
            return;
        }
        statusElement.textContent = message;
        statusElement.classList.remove('text-success', 'text-danger', 'text-muted');
        statusElement.classList.add(variantClass || 'text-muted');
    };

    var uploadFile = function (file) {
        if (!file) {
            return;
        }
        var contentType = file.type || 'application/octet-stream';
        setStatus('이미지 업로드 URL을 요청하고 있습니다...', 'text-muted');

        fetch('/ads/image/presign', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contentType: contentType })
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('Presign failed');
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
                objectKeyInput.value = data.objectKey;
                contentTypeInput.value = contentType;
                if (previewImage) {
                    var objectUrl = URL.createObjectURL(file);
                    previewImage.src = objectUrl;
                    previewImage.onload = function () {
                        URL.revokeObjectURL(objectUrl);
                    };
                }
                setStatus('이미지 업로드가 완료되었습니다.', 'text-success');
            });
        }).catch(function () {
            setStatus('이미지 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.', 'text-danger');
        });
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
})();
