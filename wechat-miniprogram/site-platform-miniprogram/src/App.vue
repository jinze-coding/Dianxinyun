<script setup lang="ts">
</script>

<template>
  <slot />
</template>

<style>
page {
  --workspace-page: #f4f6f7;
  --workspace-surface: #ffffff;
  --workspace-tint: #eaf1f5;
  --workspace-tint-strong: #dce8f0;
  --workspace-accent: #567b96;
  --workspace-accent-deep: #315f86;
  --workspace-text: #223247;
  --workspace-text-secondary: #52687a;
  --workspace-text-muted: #7f8c9c;
  --workspace-divider: #e2eaf0;
  --workspace-shadow: 0 9rpx 28rpx rgba(49, 95, 134, 0.07);
  --workspace-font-title: 29rpx;
  --workspace-font-body: 24rpx;
  --workspace-font-meta: 21rpx;
  --workspace-font-caption: 19rpx;
  --inspection-page: var(--workspace-page);
  --inspection-surface: #ffffff;
  --inspection-soft: var(--workspace-tint);
  --inspection-soft-strong: var(--workspace-tint-strong);
  --inspection-border: #c7d9e6;
  --inspection-divider: var(--workspace-divider);
  --inspection-primary: var(--workspace-accent);
  --inspection-primary-deep: var(--workspace-accent-deep);
  --inspection-text: var(--workspace-text);
  --inspection-muted: var(--workspace-text-muted);
  --inspection-shadow: var(--workspace-shadow);
  --inspection-success-soft: #eaf6f1;
  --inspection-success: #2f8065;
  --inspection-warning-soft: #fff5e8;
  --inspection-warning: #a96f2c;
  --inspection-danger-soft: #fceeed;
  --inspection-danger: #b75353;
  --mp-page-bg: var(--workspace-page);
  --mp-nav-bg: #ffffff;
  --mp-card-bg: #ffffff;
  --mp-card-strong: #f8fafb;
  --mp-surface: #f1f5f7;
  --mp-surface-soft: var(--workspace-tint);
  --mp-border: #d5e0e7;
  --mp-border-soft: rgba(86, 123, 150, 0.22);
  --mp-primary: var(--workspace-accent);
  --mp-primary-soft: rgba(86, 123, 150, 0.14);
  --mp-cyan: #4d8793;
  --mp-success: #2f8065;
  --mp-success-soft: #eaf6f1;
  --mp-warning: #a96f2c;
  --mp-warning-soft: #fff5e8;
  --mp-danger: #b75353;
  --mp-danger-soft: #fceeed;
  --mp-purple: #6673a6;
  --mp-purple-soft: #eef0f7;
  --mp-muted-bg: #e9eff3;
  --mp-text: var(--workspace-text);
  --mp-text-secondary: var(--workspace-text-secondary);
  --mp-text-muted: var(--workspace-text-muted);
  --mp-shadow: var(--workspace-shadow);
  --mp-font-page-title: 30rpx;
  --mp-font-card-title: 28rpx;
  --mp-font-body: 24rpx;
  --mp-font-helper: 21rpx;
  --mp-font-tag: 20rpx;
  --mp-font-button: 24rpx;
  --mp-weight-title: 700;
  --mp-weight-emphasis: 700;
  --mp-weight-label: 600;
  --mp-weight-body: 500;
  min-height: 100%;
  background: var(--mp-page-bg);
  color: var(--mp-text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

view,
text,
button,
input,
textarea {
  box-sizing: border-box;
}

button {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  line-height: 1.2;
  text-align: center;
  transition: transform 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

button::after {
  border: none;
}

.page {
  min-height: 100vh;
  padding: 22rpx 24rpx calc(38rpx + env(safe-area-inset-bottom));
  background: var(--mp-page-bg);
  color: var(--mp-text);
  animation: page-fade-up 0.34s cubic-bezier(0.21, 0.75, 0.32, 1) both;
}

.page.compact {
  padding-top: 14rpx;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.card {
  padding: 22rpx;
  border: 1rpx solid var(--mp-border);
  border-radius: 18rpx;
  background: var(--mp-card-bg);
  box-shadow: var(--mp-shadow);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.soft-card {
  padding: 22rpx;
  border: 1rpx solid var(--mp-border-soft);
  border-radius: 16rpx;
  background: #ffffff;
}

.muted {
  color: var(--mp-text-muted);
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-body);
}

.section-title {
  margin-bottom: 12rpx;
  color: var(--mp-text);
  font-size: var(--mp-font-card-title);
  font-weight: var(--mp-weight-title);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 14rpx;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.primary-button,
.secondary-button,
.ghost-button,
.danger-button {
  display: flex;
  min-height: 68rpx;
  align-items: center;
  justify-content: center;
  padding: 0 24rpx;
  border-radius: 14rpx;
  font-size: var(--mp-font-button);
  font-weight: var(--mp-weight-emphasis);
  box-shadow: 0 8rpx 20rpx rgba(49, 95, 134, 0.09);
}

.primary-button {
  background: var(--mp-primary);
  color: #ffffff;
}

.secondary-button {
  background: var(--mp-success);
  color: #ffffff;
}

.ghost-button {
  border: 1rpx solid var(--mp-border);
  background: rgba(255, 255, 255, 0.82);
  color: var(--mp-text-secondary);
}

.danger-button {
  background: var(--mp-danger);
  color: #ffffff;
}

.button-scan,
.button-daily,
.button-review,
.button-rectify,
.button-summary,
.button-muted {
  color: #ffffff;
}

.button-scan {
  background: var(--mp-primary);
}

.button-daily {
  background: var(--mp-success);
}

.button-review {
  background: var(--mp-warning);
}

.button-rectify {
  background: var(--mp-danger);
}

.button-summary {
  background: var(--workspace-accent-deep);
}

.button-muted {
  background: #61778f;
}

.primary-button:active,
.secondary-button:active,
.ghost-button:active,
.danger-button:active,
.button-scan:active,
.button-daily:active,
.button-review:active,
.button-rectify:active,
.button-summary:active,
.button-muted:active {
  opacity: 0.9;
  transform: scale(0.975);
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.grid-4 {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
}

.fixed-bottom {
  position: sticky;
  bottom: 0;
  z-index: 8;
  padding: 16rpx 0 calc(10rpx + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(244, 246, 247, 0), var(--mp-page-bg) 28rpx, var(--mp-page-bg));
}

.form-label {
  margin-bottom: 10rpx;
  color: var(--mp-text-secondary);
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-label);
}

.input,
.textarea {
  width: 100%;
  min-height: 72rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid var(--mp-border);
  border-radius: 14rpx;
  background: #ffffff;
  color: var(--mp-text);
  font-size: var(--mp-font-body);
  font-weight: var(--mp-weight-body);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.input:focus,
.textarea:focus {
  border-color: var(--mp-primary);
  background: #ffffff;
  box-shadow: 0 0 0 6rpx rgba(86, 123, 150, 0.14);
}

.textarea {
  min-height: 138rpx;
}

.tiny {
  font-size: 21rpx;
}

.divider {
  height: 1rpx;
  background: var(--mp-border);
}

.stagger-in {
  opacity: 0;
  animation: card-stagger-in 0.42s cubic-bezier(0.21, 0.75, 0.32, 1) forwards;
  animation-delay: var(--delay, 0ms);
}

@keyframes page-fade-up {
  from {
    opacity: 0;
    transform: translateY(18rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes card-stagger-in {
  from {
    opacity: 0;
    transform: translateY(22rpx) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
