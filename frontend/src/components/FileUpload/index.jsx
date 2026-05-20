import React, { useRef, useState } from 'react';

// 文件上传组件
export function FileUpload({
  onUpload,
  accept,
  multiple = false,
  theme: T,
  maxSize = 10 * 1024 * 1024, // 10MB
}) {
  const inputRef = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const [files, setFiles] = useState([]);

  const handleFileChange = (e) => {
    const selectedFiles = Array.from(e.target.files || []);
    handleFiles(selectedFiles);
  };

  const handleFiles = (selectedFiles) => {
    const validFiles = selectedFiles.filter(file => {
      if (file.size > maxSize) {
        alert(`文件 ${file.name} 超过大小限制`);
        return false;
      }
      return true;
    });

    setFiles(prev => [...prev, ...validFiles]);
    if (onUpload) {
      onUpload(validFiles);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const droppedFiles = Array.from(e.dataTransfer.files);
    handleFiles(droppedFiles);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = () => {
    setDragOver(false);
  };

  const removeFile = (index) => {
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  return (
    <div>
      {/* 上传区域 */}
      <div
        onClick={() => inputRef.current?.click()}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        style={{
          border: `2px dashed ${dragOver ? T.accent : T.borderColor}`,
          borderRadius: 8,
          padding: '24px 16px',
          textAlign: 'center',
          background: dragOver ? T.activeItemBg : 'transparent',
          cursor: 'pointer',
          transition: 'all 0.2s',
        }}
      >
        <svg
          width="32"
          height="32"
          viewBox="0 0 24 24"
          fill="none"
          stroke={T.textMuted}
          strokeWidth="1.5"
          style={{ margin: '0 auto 8px' }}
        >
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" />
        </svg>
        <div style={{ fontSize: 12, color: T.textMuted, marginBottom: 4 }}>点击或拖拽文件到此处</div>
        <div style={{ fontSize: 11, color: T.textMuted, opacity: 0.7 }}>
          支持 {accept || '所有文件'}，单个文件不超过 {maxSize / 1024 / 1024}MB
        </div>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple={multiple}
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
      </div>

      {/* 文件列表 */}
      {files.length > 0 && (
        <div style={{ marginTop: 12 }}>
          {files.map((file, index) => (
            <div
              key={index}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 10px',
                background: T.surface2,
                borderRadius: 4,
                marginBottom: 6,
              }}
            >
              <div style={{ flex: 1, overflow: 'hidden' }}>
                <div style={{ fontSize: 12, color: T.textPrimary, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {file.name}
                </div>
                <div style={{ fontSize: 10, color: T.textMuted }}>
                  {(file.size / 1024).toFixed(1)} KB
                </div>
              </div>
              <button
                onClick={() => removeFile(index)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: T.textMuted,
                  cursor: 'pointer',
                  fontSize: 16,
                  padding: '0 4px',
                }}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default FileUpload;
