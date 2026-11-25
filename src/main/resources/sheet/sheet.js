/*
 * 卷面交互，由 SheetRenderer 内联进页面。
 * 回传给 Java 的消息（window.__send）：
 *   ans|题目id|题号|选项|该题用时秒   作答
 *   timeout|题目id                     每题限时超时
 *   submit / submit|auto               交卷（auto = 超时自动交卷，不再确认）
 *   rev|题目id|选项                    错题重做作答
 *   pause / resume                     请求暂停（切换）/ 继续
 *   ready                              页面加载完成（Java 据此同步暂停状态）
 *   q|left|秒 / q|done / q|timeout      每题限时：当前题状态（变化时才发，显示在状态栏）
 * Java 调用页面：window.__setPaused(是否暂停, 原因, 累计暂停毫秒, 是否隐藏暂停提示)
 */
(function () {
  'use strict';
  var CFG = window.CFG || {};
  var MODE = CFG.mode || 'answer';
  var send = window.__send || function () {};
  var body = document.body;
  var $ = function (id) { return document.getElementById(id); };
  var qs = Array.prototype.slice.call(document.querySelectorAll('.q'));
  var prog = $('prog'), clock = $('clock'), flashEl = $('flash');
  var cur = -1, curStart = Date.now();
  var spent = {}, answered = {}, timedOut = {};
  var reviewDone = 0, reviewRight = 0;
  var pinOn = !!CFG.pin && !!$('b-pin'), pinnedK = null, pinBig = false;
  var submitSent = false, flashTimer = null;
  var LETTERS = 'ABCDEFGHIJ';
  // 暂停：Java 是权威，页面只负责冻结“做题时钟”和遮住题目
  var paused = false, pausedMs = 0, pauseAt = 0;
  var lastQ = '';
  /** 做题时钟 = 墙上时间 − 累计暂停时长。所有计时都用它。 */
  function now() { var t = Date.now(); return t - pausedMs - (paused ? t - pauseAt : 0); }

  function qid(q) { return q.getAttribute('data-q'); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function fmt(sec) { sec = Math.max(0, Math.floor(sec)); return pad(Math.floor(sec / 60)) + ':' + pad(sec % 60); }
  function flash(text) {
    if (!flashEl) return;
    flashEl.textContent = text;
    clearTimeout(flashTimer);
    flashTimer = setTimeout(function () { flashEl.textContent = ''; }, 3500);
  }

  qs.forEach(function (q) { if (q.querySelector('.opt.sel')) answered[qid(q)] = true; });

  // ---------------------------------------------------------------- 当前题 & 计时
  function account() {
    if (cur < 0) return;
    var id = qid(qs[cur]);
    spent[id] = (spent[id] || 0) + (now() - curStart);
    curStart = now();
  }
  function spentOf(id) {
    var ms = spent[id] || 0;
    if (cur >= 0 && qid(qs[cur]) === id) ms += now() - curStart;
    return ms;
  }
  function pending(q) { var id = qid(q); return !answered[id] && !timedOut[id]; }
  function hasPending() { return qs.some(pending); }

  function setCur(i, scroll) {
    if (i < 0 || i >= qs.length) return;
    if (i !== cur) {
      account();
      if (cur >= 0) qs[cur].classList.remove('cur');
      cur = i;
      curStart = now();
      qs[cur].classList.add('cur');
      syncPin();
    }
    if (scroll !== false) { updatePadding(); qs[cur].scrollIntoView({ block: 'start' }); }
    update();
  }
  function gotoNext() {
    for (var d = 1; d <= qs.length; d++) {
      var i = (cur + d) % qs.length;
      if (pending(qs[i])) { setCur(i); return true; }
    }
    return false;
  }
  function nextWrong() {
    if (MODE !== 'solution') return;
    for (var d = 1; d <= qs.length; d++) {
      var i = (cur + d) % qs.length;
      if (qs[i].classList.contains('wrong')) { setCur(i); return; }
    }
    flash('没有错题 👍');
  }

  function update() {
    if (!prog || !qs.length) return;
    if (MODE === 'answer') {
      var n = 0;
      qs.forEach(function (q) { if (answered[qid(q)]) n++; });
      prog.textContent = '第 ' + (cur + 1) + ' 题 · 已答 ' + n + '/' + qs.length;
      var t = now(), text, cls = '';
      if (submitSent) {
        text = '交卷中…';
      } else if (CFG.deadline > 0) {
        var left = (CFG.deadline - t) / 1000;
        if (left >= 0) { text = '剩余 ' + fmt(left); cls = left <= 60 ? 'over' : left <= 300 ? 'warn' : ''; }
        else { text = '超时 +' + fmt(-left); cls = 'over'; }
      } else if (CFG.qsec > 0 && cur >= 0) {
        var id = qid(qs[cur]), q;
        if (timedOut[id]) { text = '本题已超时'; cls = 'over'; q = 'timeout'; }
        else if (answered[id]) { text = '本题已答'; q = 'done'; }
        else {
          var l = CFG.qsec - spentOf(id) / 1000;
          text = '本题剩余 ' + fmt(l);
          cls = l <= 10 ? 'over' : l <= 20 ? 'warn' : '';
          q = 'left|' + Math.max(0, Math.floor(l));
        }
        if (q !== lastQ) { lastQ = q; send('q|' + q); }
      } else {
        text = '用时 ' + fmt((t - CFG.start) / 1000);
      }
      var pt = $('pause-time');
      if (pt) pt.textContent = '已答 ' + n + '/' + qs.length + ' · ' + text;
      if (paused) { text = '⏸ 已暂停 · ' + text; cls = ''; }
      clock.textContent = text;
      clock.className = cls;
    } else if (MODE === 'review') {
      prog.textContent = '第 ' + (cur + 1) + '/' + qs.length + ' 题';
      clock.textContent = '已做 ' + reviewDone + ' · 正确 ' + reviewRight;
    } else {
      prog.textContent = '第 ' + (cur + 1) + '/' + qs.length + ' 题';
    }
  }

  function tick() {
    if (MODE === 'answer' && CFG.qsec > 0 && cur >= 0 && !submitSent && !paused) {
      var q = qs[cur], id = qid(q);
      if (!answered[id] && !timedOut[id] && spentOf(id) >= CFG.qsec * 1000) {
        timedOut[id] = true;
        q.classList.add('timeout', 'locked');
        send('timeout|' + id);
        if (!gotoNext()) finishAll();
      }
    }
    update();
  }
  function finishAll() {
    if (CFG.autoSubmit && !submitSent) { submitSent = true; send('submit|auto'); }
    else flash('全部做完，Ctrl+Enter 交卷');
  }

  // ---------------------------------------------------------------- 作答
  function pick(li) {
    if (paused) return;
    var q = li.closest('.q');
    if (!q) return;
    var i = qs.indexOf(q);
    if (i !== cur) setCur(i, false);
    var id = qid(q), c = li.getAttribute('data-c');
    if (MODE === 'answer') {
      if (q.classList.contains('locked') || submitSent) return;
      var first = !answered[id];
      q.querySelectorAll('.opt').forEach(function (o) { o.classList.remove('sel'); });
      li.classList.add('sel');
      answered[id] = true;
      account();
      var sec = Math.max(1, Math.round((spent[id] || 0) / 1000));
      send('ans|' + id + '|' + q.getAttribute('data-i') + '|' + c + '|' + sec);
      update();
      if (!first) return;
      if (CFG.autoNext) {
        setTimeout(function () { if (!gotoNext()) flash('全部答完，Ctrl+Enter 交卷'); }, 200);
      } else if (!hasPending()) {
        flash('全部答完，Ctrl+Enter 交卷');
      }
    } else if (MODE === 'review') {
      if (q.classList.contains('revealed')) return;
      var ans = q.getAttribute('data-ans'), right = c === ans;
      q.classList.add('revealed');
      q.querySelectorAll('.opt').forEach(function (o) {
        var oc = o.getAttribute('data-c');
        if (oc === ans) o.classList.add('ok');
        else if (oc === c) o.classList.add('bad');
      });
      var v = q.querySelector('.verdict');
      if (v) {
        v.textContent = right ? '✔ 回答正确' : '✘ 回答错误，正确答案 ' + LETTERS.charAt(+ans);
        v.className = 'verdict ' + (right ? 'v-ok' : 'v-bad');
      }
      reviewDone++;
      if (right) reviewRight++;
      send('rev|' + id + '|' + c);
      update();
    }
  }

  // ---------------------------------------------------------------- 暂停
  window.__setPaused = function (p, reason, total, quiet) {
    if (typeof quiet === 'boolean') body.classList.toggle('pause-quiet', quiet);
    var t = Date.now();
    if (typeof total === 'number') pausedMs = total;
    else if (paused && !p) pausedMs += t - pauseAt;
    paused = !!p;
    pauseAt = t;
    body.classList.toggle('paused', paused);
    var r = $('pause-reason');
    if (r) r.textContent = reason || '';
    var b = $('b-pause');
    if (b) { b.textContent = paused ? '▶ 继续' : '⏸ 暂停'; b.classList.toggle('on', paused); }
    update();
  };

  // ---------------------------------------------------------------- 材料固定（资料分析）
  function matOf(q) { var m = q.getAttribute('data-m'); return m === null || m === '' ? null : m; }
  function restorePin() {
    if (pinnedK === null) return;
    var mat = $('mat-' + pinnedK), pb = $('pin-body');
    if (mat && pb.firstElementChild) { mat.appendChild(pb.firstElementChild); mat.classList.remove('away'); }
    pinnedK = null;
  }
  function syncPin() {
    var k = (pinOn && cur >= 0) ? matOf(qs[cur]) : null;
    if (k !== pinnedK) {
      restorePin();
      var mat = k === null ? null : $('mat-' + k);
      if (mat) {
        // 移动节点而不是复制：高亮、滚动位置都保留
        $('pin-body').appendChild(mat.querySelector('.mat-body'));
        mat.classList.add('away');
        $('pin-title').textContent = mat.getAttribute('data-title') || '材料';
        $('pin-body').scrollTop = 0;
        pinnedK = k;
      }
    }
    body.classList.toggle('pinned', pinnedK !== null);
    updatePadding();
  }
  function togglePin() {
    if (!$('b-pin')) return;
    pinOn = !pinOn;
    $('b-pin').classList.toggle('on', pinOn);
    syncPin();
    flash(pinOn ? '材料已固定在顶部' : '已取消固定');
  }
  function updatePadding() {
    var top = $('top');
    document.documentElement.style.scrollPaddingTop = (top ? top.offsetHeight + 6 : 0) + 'px';
  }

  // ---------------------------------------------------------------- 高亮
  function highlight() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount) { flash('先用鼠标选中要高亮的文字，再按 H'); return; }
    var r = sel.getRangeAt(0), m = document.createElement('mark');
    try { r.surroundContents(m); } catch (err) { m.appendChild(r.extractContents()); r.insertNode(m); }
    sel.removeAllRanges();
  }

  // ---------------------------------------------------------------- 弹层：帮助 / 看大图
  var lb = $('lightbox'), lbImg = lb ? lb.querySelector('img') : null, zoom = 1;
  function overlayOpen() { return !!document.querySelector('.overlay.show'); }
  function closeOverlays() { document.querySelectorAll('.overlay.show').forEach(function (o) { o.classList.remove('show'); }); }
  function toggleHelp() { var h = $('help'); if (h) h.classList.toggle('show'); }
  function sizeImg() {
    if (!lbImg) return;
    var nw = lbImg.naturalWidth || 300;
    lbImg.style.width = Math.round(Math.min(window.innerWidth * 0.96, nw * 2.5) * zoom) + 'px';
  }
  function openImg(img) {
    if (!lb) return;
    zoom = 1;
    lbImg.onload = sizeImg;
    lbImg.src = img.src;
    lb.classList.add('show');
    sizeImg();
  }

  // ---------------------------------------------------------------- 表格整理
  document.querySelectorAll('table').forEach(function (t) {
    if (t.closest('#help') || (t.parentNode.classList && t.parentNode.classList.contains('tbl'))) return;
    var w = document.createElement('div');
    w.className = 'tbl';
    t.parentNode.insertBefore(w, t);
    w.appendChild(t);
  });
  var NUM = /^[\s\d.,，%‰+\-−－—~～()（）]+$/;
  document.querySelectorAll('td').forEach(function (td) {
    if (td.closest('#help')) return;
    var s = td.textContent.trim();
    if (s && /\d/.test(s) && NUM.test(s)) td.classList.add('num');
  });

  // ---------------------------------------------------------------- 事件
  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t.closest) return;
    if (paused) { if (t.closest('#pause-ov') || t.closest('#b-pause')) send('resume'); return; }
    if (t.closest('#help')) { closeOverlays(); return; }
    if (lb && t.closest('#lightbox')) {
      if (t === lbImg) { zoom = zoom === 1 ? 2 : 1; sizeImg(); } else closeOverlays();
      return;
    }
    var btn = t.closest('button');
    if (btn) {
      switch (btn.id) {
        case 'b-pin': togglePin(); break;
        case 'b-unpin': if (pinOn) togglePin(); break;
        case 'b-pinsize':
          pinBig = !pinBig;
          document.documentElement.style.setProperty('--pinh', pinBig ? '70vh' : '38vh');
          updatePadding();
          break;
        case 'b-help': toggleHelp(); break;
        case 'b-wrong': nextWrong(); break;
        case 'b-pause': if (!submitSent) send('pause'); break;
      }
      return;
    }
    var q = t.closest('.q');
    if (t.closest('.mref') && q) {
      setCur(qs.indexOf(q), false);
      if (!pinOn) togglePin(); else syncPin();
      return;
    }
    var mh = t.closest('.mat-hd');
    if (mh) {
      var mat = mh.parentNode;
      if (mat.classList.contains('away')) togglePin(); else mat.classList.toggle('fold');
      return;
    }
    var li = t.closest('.opt');
    if (t.tagName === 'IMG' && (!li || e.altKey)) { openImg(t); return; }
    if (li && li.hasAttribute('data-c')) { pick(li); return; }
    if (q) { var i = qs.indexOf(q); if (i >= 0 && i !== cur) setCur(i, false); }
  });

  document.addEventListener('dblclick', function (e) {
    var m = e.target.closest && e.target.closest('mark');
    if (!m) return;
    var p = m.parentNode;
    while (m.firstChild) p.insertBefore(m.firstChild, m);
    p.removeChild(m);
    p.normalize();
    var sel = window.getSelection();
    if (sel) sel.removeAllRanges();
  });

  document.addEventListener('keydown', function (e) {
    var tag = (e.target && e.target.tagName) || '';
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    if (paused) {
      // 暂停时只认“继续”，其他按键（包括交卷）一律吞掉
      if (e.key === ' ' || e.key === 'Enter') send('resume');
      e.preventDefault();
      return;
    }
    if (e.key === 'Escape') { closeOverlays(); return; }
    if (e.ctrlKey && e.key === 'Enter') {
      if (MODE === 'answer' && !submitSent) send('submit');
      e.preventDefault();
      return;
    }
    if (e.ctrlKey || e.altKey || e.metaKey) return;
    if (overlayOpen()) { closeOverlays(); e.preventDefault(); return; }
    var k = e.key, idx = -1;
    if (k.length === 1) {
      if (k >= '1' && k <= '9') idx = k.charCodeAt(0) - 49;
      else {
        var lc = k.toLowerCase();
        if (lc >= 'a' && lc <= 'd') idx = lc.charCodeAt(0) - 97;
      }
    }
    if (idx >= 0) {
      if (cur >= 0 && MODE !== 'solution') {
        var opts = qs[cur].querySelectorAll('.opt[data-c]');
        if (opts[idx]) pick(opts[idx]);
      }
      e.preventDefault();
      return;
    }
    switch (k) {
      case 'ArrowRight': case 'j': case 'J': case 'n': case 'N': setCur(cur + 1); break;
      case 'ArrowLeft': case 'k': case 'K': case 'p': case 'P': setCur(cur - 1); break;
      case 'Home': setCur(0); break;
      case 'End': setCur(qs.length - 1); break;
      case 'w': case 'W': nextWrong(); break;
      case 'm': case 'M': togglePin(); break;
      case 'h': case 'H': highlight(); break;
      case '?': case '/': toggleHelp(); break;
      case ' ': if (MODE === 'answer' && !submitSent) send('pause'); break;
      default: return;
    }
    e.preventDefault();
  });

  // ---------------------------------------------------------------- 初始化
  if ($('b-pin') && pinOn) $('b-pin').classList.add('on');
  window.addEventListener('resize', function () { updatePadding(); if (lb && lb.classList.contains('show')) sizeImg(); });
  if (window.ResizeObserver && $('top')) new ResizeObserver(updatePadding).observe($('top'));
  if (qs.length) {
    var first = 0;
    if (MODE === 'answer') { for (var i = 0; i < qs.length; i++) { if (pending(qs[i])) { first = i; break; } } }
    setCur(first, first > 0);
  }
  updatePadding();
  setInterval(tick, 250);
  window.focus();
  if (MODE === 'answer') send('ready');
})();
