// 첫 화면이 깜빡이지 않게 그리기 전에 테마를 붙인다.
(function () {
  var t = localStorage.getItem('theme');
  document.documentElement.dataset.theme = t === 'light' ? 'light' : 'dark';
})();
