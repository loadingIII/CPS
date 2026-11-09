<template>
  <router-view />
</template>

<style>
:root {
  color-scheme: light;
  --paper: #f5f6f7;
  --paper-deep: #e9ebed;
  --ink: #2b3a3c;
  --teal: #0f7b7e;
  --teal-deep: #0a5f62;
  --teal-soft: #d7ecec;
  --butter: #f4d58d;
  --blush: #f3bcc6;
  --blush-soft: #fae9ec;
  --sky: #bcdfe6;
  --line: #c6cad0;
  --muted: #718184;
  --el-color-primary: #0f7b7e;
  --el-color-primary-light-3: #5aa9b0;
  --el-color-primary-light-5: #8bc4c7;
  --el-color-primary-light-7: #b4d9da;
  --el-color-primary-light-8: #c8e4e5;
  --el-color-primary-light-9: #e8f3f3;
  --el-color-primary-dark-2: #0a5f62;
  --el-color-success: #3c8b72;
  --el-color-warning: #b98527;
  --el-color-danger: #c66575;
  --el-color-info: #71888c;
  --el-border-color: #c6cad0;
  --el-border-color-light: #d8dcdf;
  --el-border-color-lighter: #e4e7e9;
  --el-border-color-extra-light: #edf0f1;
  --el-fill-color: #f1f3f4;
  --el-fill-color-light: #f5f6f7;
  --el-fill-color-lighter: #f8f9f9;
  --el-bg-color: #fff;
  --el-bg-color-page: #f5f6f7;
  --el-text-color-primary: #2b3a3c;
  --el-text-color-regular: #536568;
  --el-text-color-secondary: #718184;
  --el-text-color-placeholder: #8b9a9c;
  --el-border-radius-base: 9px;
  --el-border-radius-small: 7px;
  --el-border-radius-round: 999px;
  --el-font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial, sans-serif;
}

* { box-sizing: border-box; }
html, body, #app { min-width: 320px; min-height: 100%; }
body {
  margin: 0;
  color: var(--ink);
  background-color: var(--paper);
  background-image:
    linear-gradient(rgba(15, 123, 126, .04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 123, 126, .04) 1px, transparent 1px);
  background-size: 32px 32px;
  font-family: var(--el-font-family);
  -webkit-font-smoothing: antialiased;
}
::selection { color: var(--ink); background: var(--butter); }
:focus-visible { outline: 2px solid var(--teal); outline-offset: 2px; }

/* Shared paper-and-ink skin for Element Plus. */
.el-card {
  overflow: hidden;
  border: 1.5px solid var(--line) !important;
  border-radius: 14px !important;
  background-color: #fff;
  color: var(--ink);
  box-shadow: 0 9px 24px rgba(43, 58, 60, .055) !important;
}
.el-card__header {
  padding: 17px 20px;
  border-bottom: 1.5px dashed var(--line) !important;
  color: var(--ink);
}
.el-card__body { color: var(--ink); }

.el-table {
  --el-table-border-color: var(--line);
  --el-table-header-bg-color: var(--paper-deep);
  --el-table-row-hover-bg-color: #f7f8f8;
  --el-table-bg-color: #fff;
  --el-table-tr-bg-color: #fff;
  color: var(--ink);
  border-radius: 8px;
}
.el-table th.el-table__cell {
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
  background: var(--paper-deep) !important;
  border-bottom: 1.5px dashed var(--line) !important;
}
.el-table td.el-table__cell { border-bottom: 1px dashed #e9ebed; }
.el-table--border .el-table__cell { border-right: 1px dashed #e1e5e7; }
.el-table--border::after, .el-table--border::before, .el-table__inner-wrapper::before { background-color: transparent !important; }
.el-table .urgent-row > td.el-table__cell { background: var(--blush-soft) !important; }
.el-table .current-row > td.el-table__cell { background: var(--teal-soft) !important; }

.el-button {
  min-height: 32px;
  border-radius: 999px;
  font-weight: 600;
  transition: transform .16s ease, box-shadow .16s ease, background-color .16s ease, border-color .16s ease;
}
.el-button:not(.is-link):not(.is-text):not(.is-circle).el-button--primary {
  box-shadow: 3px 3px 0 var(--ink);
}
.el-button:not(.is-link):not(.is-text):not(.is-circle).el-button--primary:hover {
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 var(--ink);
}
.el-button.is-link, .el-button.is-text { min-height: auto; padding: 4px 6px; }
.el-button:active { transform: translateY(1px) scale(.985); }
.el-button:focus-visible, .el-input__wrapper:focus-visible { outline: 2px solid var(--teal); outline-offset: 2px; }
.el-input__wrapper, .el-select__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
  transition: box-shadow .18s ease, background-color .18s ease;
}
.el-input__wrapper:hover, .el-select__wrapper:hover { box-shadow: 0 0 0 1px var(--teal) inset !important; }
.el-input__wrapper.is-focus, .el-select__wrapper.is-focused, .el-textarea__inner:focus {
  box-shadow: 0 0 0 1px var(--teal) inset !important;
}
.el-textarea__inner { border-radius: 8px; box-shadow: 0 0 0 1px var(--line) inset; }
.el-select-dropdown, .el-dropdown__popper, .el-picker__popper {
  border: 1.5px solid var(--line);
  border-radius: 10px;
  box-shadow: 0 10px 26px rgba(43, 58, 60, .12);
}

.el-tag { border-radius: 999px; font-weight: 600; }
.el-tag--primary, .el-tag--success { --el-tag-bg-color: var(--teal-soft); --el-tag-border-color: #acd5d7; --el-tag-text-color: var(--teal-deep); }
.el-tag--warning { --el-tag-bg-color: #fbf0d8; --el-tag-border-color: #ecd49b; --el-tag-text-color: #795915; }
.el-tag--danger { --el-tag-bg-color: var(--blush-soft); --el-tag-border-color: #edc0c8; --el-tag-text-color: #8a3b4a; }
.el-tag--info { --el-tag-bg-color: #edf1f2; --el-tag-border-color: #d9e0e1; --el-tag-text-color: #5b7074; }
.el-progress-bar__outer { background-color: var(--paper-deep); }
.el-progress-bar__inner { border-radius: 999px; }

.el-pagination { color: var(--muted); }
.el-pagination button, .el-pager li { border: 1px solid var(--line); border-radius: 8px !important; }
.el-pager li.is-active { color: #fff !important; background: var(--ink) !important; }
.el-tabs__nav-wrap::after { height: 1px; background: var(--line); }
.el-tabs__active-bar { background-color: var(--teal); }
.el-radio-button__inner { border-radius: 999px !important; box-shadow: none !important; }
.el-radio-button:first-child .el-radio-button__inner,
.el-radio-button:last-child .el-radio-button__inner { border-radius: 999px !important; }
.el-switch { --el-switch-on-color: var(--teal); }
.el-descriptions__label.el-descriptions__cell { color: var(--muted); background: var(--paper-deep) !important; }
.el-descriptions__cell { border-color: var(--line) !important; }
.el-dialog, .el-drawer {
  border: 1.5px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.el-dialog { box-shadow: 0 18px 48px rgba(43, 58, 60, .18); }
.el-dialog__header, .el-drawer__header { border-bottom: 1.5px dashed var(--line); }
.el-dialog__footer { border-top: 1px dashed #e9ebed; }
.el-empty__description p, .el-form-item__label { color: var(--muted); }
.el-message-box { border: 1.5px solid var(--line); border-radius: 14px; }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
