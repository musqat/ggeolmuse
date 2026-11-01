// 그리기 전 테마 적용(첫 화면 깜빡임 방지)
(function () {
  var t = localStorage.getItem('theme');
  document.documentElement.dataset.theme = t === 'light' ? 'light' : 'dark';
})();
