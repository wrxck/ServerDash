let editor = null;
let isReady = false;

require.config({ paths: { vs: 'monaco/vs' } });

require(['vs/editor/editor.main'], function () {
  editor = monaco.editor.create(document.getElementById('editor'), {
    value: '',
    language: 'plaintext',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 14,
    minimap: { enabled: false },
    wordWrap: 'off',
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    renderWhitespace: 'none',
    tabSize: 4,
    insertSpaces: true,
    fixedOverflowWidgets: true,
  });

  editor.onDidChangeModelContent(function () {
    if (typeof AndroidBridge !== 'undefined') {
      AndroidBridge.onContentChanged(editor.getValue());
    }
  });

  editor.onDidChangeCursorPosition(function (e) {
    if (typeof AndroidBridge !== 'undefined') {
      AndroidBridge.onCursorChange(e.position.lineNumber, e.position.column);
    }
  });

  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function () {
    if (typeof AndroidBridge !== 'undefined') {
      AndroidBridge.onSave();
    }
  });

  isReady = true;
  if (typeof AndroidBridge !== 'undefined') {
    AndroidBridge.onReady();
  }
});

function setContent(content, language) {
  if (!editor) return;
  var model = editor.getModel();
  if (model) {
    monaco.editor.setModelLanguage(model, language);
    model.setValue(content);
  }
}

function getContent() {
  return editor ? editor.getValue() : '';
}

function setLanguage(lang) {
  if (!editor) return;
  var model = editor.getModel();
  if (model) monaco.editor.setModelLanguage(model, lang);
}

function setThemeColors(themeJson) {
  try {
    var theme = JSON.parse(themeJson);
    monaco.editor.defineTheme('serverdash', theme);
    monaco.editor.setTheme('serverdash');
  } catch (e) { console.error('Failed to set theme:', e); }
}

function setOptions(optionsJson) {
  if (!editor) return;
  try {
    editor.updateOptions(JSON.parse(optionsJson));
  } catch (e) { console.error('Failed to set options:', e); }
}

function editorUndo() { if (editor) editor.trigger('bridge', 'undo'); }
function editorRedo() { if (editor) editor.trigger('bridge', 'redo'); }
function findText() { if (editor) editor.getAction('actions.find').run(); }

function setCursorPosition(line, column) {
  if (!editor) return;
  editor.setPosition({ lineNumber: line, column: column });
  editor.revealLineInCenter(line);
}

function revealLine(line) { if (editor) editor.revealLineInCenter(line); }
function setReadOnly(readOnly) { if (editor) editor.updateOptions({ readOnly: readOnly }); }
