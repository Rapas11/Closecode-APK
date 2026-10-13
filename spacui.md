<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Closecode – Android IDE</title>
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;600;700&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
<style>
:root{--bg:#eceff4;--pn:#fff;--pn2:#f3f5f9;--ln:#dde2ea;--tx:#101826;--mu:#66728a;--ac:#2457ff;--acs:#e3eaff;--ok:#12805c;--wn:#a86a00;--er:#d1344b;--ed:#fbfcfe;
--k:#7b3fe4;--s:#0d7a55;--c:#8793a8;--f:#2457ff;--n:#c2410c;--a:#b45309}
@media(prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0b0f16;--pn:#131923;--pn2:#1a2230;--ln:#263043;--tx:#e7ecf5;--mu:#8b98b0;--ac:#7b9bff;--acs:#1c2a52;--ok:#3ecf9b;--wn:#f0b429;--er:#ff6b81;--ed:#0f141d;--k:#b794ff;--s:#6fe0b0;--c:#5d6a82;--f:#7fb2ff;--n:#ffa86b;--a:#f5c26b}}
:root[data-theme="dark"]{--bg:#0b0f16;--pn:#131923;--pn2:#1a2230;--ln:#263043;--tx:#e7ecf5;--mu:#8b98b0;--ac:#7b9bff;--acs:#1c2a52;--ok:#3ecf9b;--wn:#f0b429;--er:#ff6b81;--ed:#0f141d;--k:#b794ff;--s:#6fe0b0;--c:#5d6a82;--f:#7fb2ff;--n:#ffa86b;--a:#f5c26b}
*{box-sizing:border-box;margin:0}
:root{--erbg:#fde3e6;--ert:#a1232f;--wnbg:#fff0cf;--wnt:#8a5a00;--ty:#946200;--bu:#c98a00;--bub:#fff;--bud:#8a93a6;--pu:#5b3fd1}
@media(prefers-color-scheme:dark){:root:not([data-theme="light"]){--erbg:#4a2a31;--ert:#ffb4bd;--wnbg:#473a1c;--wnt:#f5c26b;--ty:#e5c07b;--bu:#f5dc8a;--bub:#1f1f26;--bud:#9c9cab}}
:root[data-theme="dark"]{--erbg:#4a2a31;--ert:#ffb4bd;--wnbg:#473a1c;--wnt:#f5c26b;--ty:#e5c07b;--bu:#f5dc8a;--bub:#1f1f26;--bud:#9c9cab}
:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}
html{scroll-padding-top:env(safe-area-inset-top,0px)}
html,body{height:100%}
body{background:var(--bg);color:var(--tx);font:14px/1.45 Manrope,system-ui,sans-serif;overflow:hidden}
button{font:inherit;color:inherit;background:none;border:0;cursor:pointer}
:focus-visible{outline:2px solid var(--ac);outline-offset:2px}
.app{height:100%;display:grid;grid-template-rows:auto 1fr auto;max-width:1200px;margin:auto;background:var(--pn)}
header{display:flex;align-items:center;gap:8px;padding:8px 10px;border-bottom:1px solid var(--ln)}
.logo{font-weight:700;letter-spacing:-.02em;font-size:16px}
.crumb{flex:1;min-width:0;color:var(--mu);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.crumb b{color:var(--tx)}
.ib{height:34px;min-width:34px;padding:0 10px;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;gap:6px;font-weight:600}
.ib:hover{background:var(--pn2)}
.run{background:var(--ac);color:#fff}.run:hover{background:var(--ac);filter:brightness(1.1)}
svg.i{width:18px;height:18px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.body{display:grid;grid-template-columns:1fr;min-height:0;position:relative}
@media(min-width:900px){.body{grid-template-columns:220px 1fr 300px}.tree{position:static!important;transform:none!important;box-shadow:none!important}#v-code,#v-prev{display:flex!important}#v-ai,#v-log,#v-build{grid-column:2/4;grid-row:1;background:var(--pn);z-index:2}#v-prev{border-left:1px solid var(--ln)}.tabbar button[data-v=prev],#scrim,#exp{display:none}}
.tree{position:absolute;inset:0 auto 0 0;width:250px;background:var(--pn);border-right:1px solid var(--ln);z-index:5;transform:translateX(-102%);transition:transform .2s;padding:10px 6px;overflow:auto;box-shadow:6px 0 24px rgba(0,0,0,.18)}
.tree.on{transform:none}
#scrim{position:absolute;inset:0;background:rgba(0,0,0,.35);z-index:4;display:none}#scrim.on{display:block}
.tree h3{font-size:12px;color:var(--mu);font-weight:600;padding:4px 8px}
.tree button{display:flex;align-items:center;gap:8px;width:100%;padding:7px 8px;border-radius:8px;text-align:left}
.tree button:hover{background:var(--pn2)}.tree button.on{background:var(--acs);color:var(--ac);font-weight:600}
.tree .d{padding-left:8px;color:var(--mu)}.tree .l{padding-left:26px}
.badge{margin-left:auto;font-size:11px;padding:0 6px;border-radius:9px;background:var(--er);color:#fff}
.view{display:none;flex-direction:column;min-height:0;min-width:0}.view.on{display:flex}
.tabs{display:flex;gap:8px;overflow-x:auto;padding:8px 10px;background:var(--pn);border-bottom:1px solid var(--ln)}
.tab{display:flex;align-items:center;gap:8px;padding:6px 6px 6px 6px;border-radius:14px;color:var(--mu);white-space:nowrap;font-weight:600;cursor:pointer}
.tab.on{background:var(--pn2);color:var(--tx)}
.fi{width:22px;height:22px;border-radius:7px;display:grid;place-items:center;font:700 11px "Fira Code",monospace}
.fi.j{background:rgba(217,119,6,.2);color:#e08a1e}.fi.k{background:rgba(123,63,228,.2);color:var(--k)}.fi.x{background:rgba(36,87,255,.18);color:var(--f)}
.ud{width:8px;height:8px;border-radius:50%;background:var(--er)}
.cl{width:22px;height:22px;display:grid;place-items:center;border-radius:6px;color:var(--mu)}.cl:hover{background:var(--ln)}
.path{display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid var(--ln);font-weight:600}
.path .cr{flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:var(--mu)}.path .cr b{color:var(--tx)}
.pb2{width:44px;height:34px;border-radius:9px;display:grid;place-items:center;color:var(--mu)}.pb2.on{background:var(--pu);color:#fff}
.ed{flex:1;position:relative;min-height:0;display:flex;background:var(--ed)}
.gut{width:62px;flex:none;overflow:hidden;text-align:right;padding:12px 0;color:var(--c);border-right:1px solid var(--ln);user-select:none}
.gut div{position:relative;padding-right:26px}.gut .dt{position:absolute;left:11px;top:7px;width:7px;height:7px;border-radius:50%;background:currentColor}
.gut .fd{position:absolute;right:8px;top:0;font-size:10px;color:var(--mu);cursor:pointer}
.mono,.gut,#hl,#ta{font:13px/20px "Fira Code",ui-monospace,monospace;tab-size:2}
.gut div.e{color:var(--er);font-weight:700}.gut div.w{color:var(--wn);font-weight:700}
.code{position:relative;flex:1;min-width:0}
#hl,#ta{position:absolute;inset:0;padding:12px 14px;white-space:pre;overflow:auto;border:0;margin:0}
#hl{pointer-events:none;overflow:hidden;color:var(--tx)}
#ta{background:transparent;color:transparent;caret-color:var(--ac);resize:none;outline:none;-webkit-text-fill-color:transparent}
.k{color:var(--k)}.s{color:var(--s)}.c{color:var(--c);font-style:italic}.f{color:var(--f)}.n{color:var(--n)}.a{color:var(--a)}
.sq{text-decoration:underline wavy var(--er);text-underline-offset:3px}.sw{text-decoration:underline wavy var(--wn);text-underline-offset:3px}
.sug{display:flex;gap:6px;overflow-x:auto;padding:6px 8px;border-top:1px solid var(--ln);min-height:40px;background:var(--pn)}
.sug button{padding:4px 11px;border-radius:8px;background:var(--pn2);font:13px "Fira Code",monospace;white-space:nowrap}
.sug button:first-child{background:var(--acs);color:var(--ac)}
.sug em{color:var(--mu);font-style:normal;align-self:center}
.status{display:flex;gap:14px;padding:4px 12px;font-size:12px;color:var(--mu);border-top:1px solid var(--ln)}
.ed{overflow:hidden}.ty{color:var(--ty)}
.ov{position:absolute;left:0;top:0;pointer-events:none;will-change:transform}
.sqg{position:absolute;height:4px;background-repeat:repeat-x;background-position:left bottom;background-size:6px 4px}
.sqg.e{background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='6' height='4'%3E%3Cpath d='M0 3L1.5 1L4.5 3L6 1' stroke='%23ef4444' fill='none'/%3E%3C/svg%3E")}
.sqg.w{background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='6' height='4'%3E%3Cpath d='M0 3L1.5 1L4.5 3L6 1' stroke='%23e0a100' fill='none'/%3E%3C/svg%3E")}
.pill{position:absolute;display:flex;align-items:center;gap:7px;height:22px;padding:0 10px 0 7px;border-radius:11px;font:600 12.5px Manrope,system-ui,sans-serif;white-space:nowrap}
.pill.e{background:var(--erbg);color:var(--ert)}.pill.w{background:var(--wnbg);color:var(--wnt)}
.pill svg{width:15px;height:15px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.pill code{font:500 11px "Fira Code",monospace;opacity:.75}
.pill .bg{display:inline-flex;align-items:center;gap:3px;background:rgba(128,128,128,.28);border-radius:9px;padding:0 6px;font-size:11px}.pill .bg svg{width:11px;height:11px}
.bulb{position:absolute;left:3px;width:38px;height:33px;padding:0;border-radius:11px;border:1.5px solid var(--bud);background:var(--bub);display:grid;place-items:center;color:var(--bu);cursor:pointer;z-index:3}
.bulb[hidden]{display:none}
.bulb svg{width:19px;height:19px;stroke:currentColor;fill:none;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
.pop{position:absolute;z-index:6;background:var(--pn);border:1.5px solid var(--ln);border-radius:16px;overflow:hidden;box-shadow:0 12px 32px rgba(0,0,0,.35)}
.pop .pr{display:flex;align-items:center;gap:12px;padding:8px 12px;cursor:pointer}.pop .pr.on{background:var(--pu);color:#fff}
.pop .pr i{width:30px;height:30px;border-radius:8px;display:grid;place-items:center;font:700 13px "Fira Code",monospace;font-style:normal;background:rgba(217,160,40,.22);color:#d9a028}.pop .pr.on i{background:rgba(255,255,255,.22);color:#fff}
.pop .pr b{display:block;font:500 14px "Fira Code",monospace}.pop .pr small{display:block;color:var(--mu);font:400 12px "Fira Code",monospace}.pop .pr.on small{color:rgba(255,255,255,.75)}
.tools{display:flex;align-items:center;overflow-x:auto;border-top:1px solid var(--ln);background:var(--pn)}
.tools button{flex:none;min-width:46px;height:46px;display:grid;place-items:center;padding:0 10px;color:var(--tx);font:500 15px "Fira Code",monospace}
.tools i{width:1px;height:28px;background:var(--ln);flex:none}.tools svg{width:20px;height:20px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.cd{font:600 11px "Fira Code",monospace;padding:1px 6px;border-radius:6px}.cd.e{background:var(--erbg);color:var(--ert)}.cd.w{background:var(--wnbg);color:var(--wnt)}
.pad{padding:12px;overflow:auto;flex:1}
.item{display:flex;gap:10px;padding:10px;border-radius:10px;border:1px solid var(--ln);margin-bottom:8px;width:100%;text-align:left;align-items:flex-start}
.item:hover{background:var(--pn2)}
.dot{width:10px;height:10px;border-radius:50%;margin-top:5px;flex:none}
.empty{color:var(--mu);text-align:center;padding:40px 10px}
.chips{display:flex;gap:6px;padding:8px 12px;border-bottom:1px solid var(--ln)}
.chips button{padding:3px 11px;border-radius:99px;border:1px solid var(--ln);color:var(--mu);font-weight:600}
.chips button.on{background:var(--ac);border-color:var(--ac);color:#fff}
.log div{padding:2px 0;border-bottom:1px solid var(--ln);white-space:pre-wrap;word-break:break-word}
.log .I{color:var(--ok)}.log .W{color:var(--wn)}.log .E{color:var(--er)}.log .D{color:var(--ac)}.log .V{color:var(--mu)}
.task{display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid var(--ln)}
.task .st{width:18px;text-align:center;font-weight:700}
.prog{height:6px;background:var(--pn2);border-radius:4px;overflow:hidden;margin:10px 0}.prog i{display:block;height:100%;width:0;background:var(--ac);transition:width .3s}
.stage{flex:1;display:grid;place-items:center;background:var(--pn2);padding:14px;overflow:auto}
.phone{width:230px;min-height:440px;border-radius:32px;border:7px solid #202733;background:#fffdf9;color:#18141c;padding:40px 16px 20px;position:relative;box-shadow:0 14px 34px rgba(0,0,0,.28);display:flex;flex-direction:column;gap:10px}
.phone::before{content:"";position:absolute;top:9px;left:50%;width:54px;height:6px;margin-left:-27px;border-radius:4px;background:#202733}
.phone button{background:#2457ff;color:#fff;border-radius:20px;padding:10px 14px;font-weight:600;align-self:flex-start}
.phone small{margin-top:auto;color:#8a8794;text-align:center}
.pal{position:fixed;inset:0;background:rgba(0,0,0,.45);display:none;align-items:flex-start;justify-content:center;padding-top:12vh;z-index:20}.pal.on{display:flex}
.pal .box{width:min(480px,92vw);background:var(--pn);border-radius:14px;border:1px solid var(--ln);overflow:hidden;box-shadow:0 20px 50px rgba(0,0,0,.35)}
.pal input{width:100%;padding:14px;border:0;border-bottom:1px solid var(--ln);background:none;color:var(--tx);font:inherit;font-size:15px;outline:none}
.pal button{display:flex;justify-content:space-between;width:100%;padding:11px 14px;text-align:left}.pal button.on{background:var(--acs);color:var(--ac)}
.pal kbd{color:var(--mu);font:12px "Fira Code",monospace}
.tabbar{display:flex;border-top:1px solid var(--ln)}
.tabbar button{flex:1;padding:9px 0;color:var(--mu);font-weight:600;font-size:12px;display:flex;flex-direction:column;align-items:center;gap:2px;position:relative}
.tabbar button.on{color:var(--ac)}
.tabbar .badge{position:absolute;top:3px;left:55%;margin:0}
.lt{width:28px;height:28px;border-radius:8px;background:#fff;color:#12151c;border:1px solid var(--ln);display:grid;place-items:center;flex:none}
.crumb{font-size:13px}
.pop .pr{min-height:53px}
.aihd{padding:10px 14px;font-weight:700;border-bottom:1px solid var(--ln)}
.am{max-width:85%;padding:9px 12px;border-radius:14px;margin-bottom:10px;background:var(--pn2)}.am.u{margin-left:auto;background:var(--ac);color:#fff}
.aic{display:flex;gap:8px;padding:10px 12px;border-top:1px solid var(--ln)}
.aic input{flex:1;min-width:0;height:42px;padding:0 14px;border-radius:21px;border:1px solid var(--ln);background:var(--pn2);color:var(--tx);font:inherit;outline:none}
.aig{height:42px;padding:0 16px;border-radius:21px;background:var(--ac);color:#fff;font-weight:700}
.shov{position:fixed;left:0;right:0;top:0;bottom:0;background:rgba(0,0,0,.35);z-index:25;display:flex;align-items:flex-end;justify-content:center}.shov[hidden]{display:none}
.shs{width:100%;max-width:720px;max-height:60%;display:flex;flex-direction:column;background:var(--pn);border:1px solid var(--ln);border-bottom:0;border-radius:20px 20px 0 0;box-shadow:0 -12px 40px rgba(0,0,0,.3);animation:shup .25s ease-out}
@keyframes shup{from{transform:translateY(30px);opacity:0}}
@media(prefers-reduced-motion:reduce){.shs{animation:none}}
.hdl{width:36px;height:4px;border-radius:2px;background:var(--ln);margin:8px auto 0;flex:none}
.shh{display:flex;align-items:center;gap:8px;padding:8px 16px;flex:none}.shh b{font-size:16px;margin-right:4px}
.chp{font:600 11px Manrope,system-ui,sans-serif;padding:2px 8px;border-radius:99px}.chp.e{background:var(--erbg);color:var(--ert)}.chp.w{background:var(--wnbg);color:var(--wnt)}
.cl2{margin-left:auto;width:34px;height:34px;border-radius:9px;color:var(--mu)}.cl2:hover{background:var(--pn2)}
.item.hi{background:var(--acs);border-color:var(--ac)}
.nm{display:flex;flex-direction:column;line-height:1.2;min-width:0;flex:1}.nm b{font-size:16px;letter-spacing:-.02em}
.nm small{font-size:12px;color:var(--mu);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ib.sv.on{background:var(--acs);color:var(--ac)}
.tabrow{display:flex;align-items:stretch;background:var(--pn);border-bottom:1px solid var(--ln)}
.tabrow .tabs{border-bottom:0;flex:1;min-width:0}
.exp{width:44px;flex:none;border-right:1px solid var(--ln);color:var(--mu);display:grid;place-items:center}
.exp svg{transition:transform .2s}.exp[aria-expanded=true] svg{transform:rotate(180deg)}
.mnu{position:fixed;right:8px;top:calc(env(safe-area-inset-top,0px) + 50px);z-index:30;min-width:210px;background:var(--pn);border:1px solid var(--ln);border-radius:12px;box-shadow:0 12px 32px rgba(0,0,0,.3);padding:6px}
.mnu[hidden]{display:none}.mnu button{display:block;width:100%;text-align:left;padding:10px 12px;border-radius:8px}.mnu button:hover{background:var(--pn2)}
.toast{position:fixed;left:50%;bottom:78px;transform:translateX(-50%);background:var(--tx);color:var(--pn);padding:8px 14px;border-radius:10px;opacity:0;transition:opacity .2s;pointer-events:none;z-index:30}.toast.on{opacity:1}
</style>
</head>
<body>
<div class="app">
<header>
  <span class="lt"><svg class="i" viewBox="0 0 24 24" style="width:16px;height:16px"><path d="M8 8l-4 4 4 4M16 8l4 4-4 4"/></svg></span>
  <span class="nm"><b>Closecode</b><small id="crumb">Blank app</small></span>
  <button class="ib sv" id="sv" aria-label="Save"><svg class="i" viewBox="0 0 24 24"><path d="M5 4h11l3 3v13H5z"/><path d="M8 4v5h7V4M8 20v-6h8v6"/></svg></button>
  <button class="ib" id="un" aria-label="Undo"><svg class="i" viewBox="0 0 24 24"><path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/></svg></button>
  <button class="ib" id="rd" aria-label="Redo"><svg class="i" viewBox="0 0 24 24"><path d="M15 14l5-5-5-5"/><path d="M20 9H10a6 6 0 0 0 0 12h3"/></svg></button>
  <button class="ib run" id="run"><svg class="i" viewBox="0 0 24 24"><path d="M7 4l13 8-13 8z"/></svg>Run</button>
  <button class="ib" id="more" aria-label="More"><svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/></svg></button>
</header>

<div class="body">
  <div id="scrim"></div>
  <aside class="tree" id="tree"><h3>app / src / main</h3><div id="ft"></div></aside>

  <section class="view on" id="v-code">
    <div class="tabrow"><button class="exp" id="exp" aria-label="Files" aria-expanded="false"><svg class="i" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg></button><div class="tabs" id="tabs" role="tablist"></div></div>
    
    <div class="ed">
      <div class="gut" id="gut"></div>
      <div class="code"><pre id="hl" aria-hidden="true"></pre><div class="ov" id="ov"></div><textarea id="ta" spellcheck="false" autocapitalize="off" autocomplete="off" aria-label="Code editor"></textarea><button class="bulb" id="bulb" hidden aria-label="Quick fix"><svg viewBox="0 0 24 24"><path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"/></svg></button><div class="pop" id="pop" hidden></div></div>
    </div>
    <div class="tools" id="tools"><button data-k="tab">Tab</button><button data-k="cm">//</button><button data-k="up" aria-label="Up"><svg viewBox="0 0 24 24"><path d="M6 15l6-6 6 6"/></svg></button><button data-k="dn" aria-label="Down"><svg viewBox="0 0 24 24"><path d="M6 9l6 6 6-6"/></svg></button><button data-k="cp" aria-label="Copy"><svg viewBox="0 0 24 24"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h8"/></svg></button><button data-k="wn" aria-label="Problems"><svg viewBox="0 0 24 24"><path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18h.01"/></svg></button><i></i><button data-k="{">{</button><button data-k="}">}</button><button data-k="(">(</button><button data-k=")">)</button><button data-k=";">;</button><button data-k="=">=</button></div>
    <div class="status"><span id="pos">Ln 1, Col 1</span><span id="lg">Kotlin</span><span id="sum" style="margin-left:auto"></span></div>
  </section>

  <section class="view" id="v-prev">
    <div class="stage"><div class="phone" id="phone"></div></div>
  </section>

  <section class="view" id="v-ai"><div class="aihd">AI assistant</div><div class="pad" id="aif"><div class="am">Hi. Ask me to explain, fix or write code in this project.</div></div><div class="aic"><input id="aii" placeholder="Ask about your code" aria-label="Ask AI"><button class="aig" id="aig">Send</button></div></section>

  <section class="view" id="v-log">
    <div class="chips" id="lvl"><button class="on" data-l="V">All</button><button data-l="D">Debug</button><button data-l="I">Info</button><button data-l="W">Warn</button><button data-l="E">Error</button></div>
    <div class="pad log mono" id="log"></div>
  </section>

  <section class="view" id="v-build"><div class="pad"><div class="prog"><i id="bar"></i></div><div id="tasks"></div><div id="bres" style="margin-top:12px;font-weight:600"></div></div></section>
</div>

<nav class="tabbar" id="tb">
  <button data-v="code" class="on">Code</button>
  <button data-v="prev">Preview</button>
  <button data-v="ai">AI</button>
  <button data-v="log">Logcat</button>
  <button data-v="build">Build</button>
</nav>
</div>

<div class="pal" id="pal"><div class="box"><input id="pi" placeholder="Type a command or file name" aria-label="Command"><div id="pl"></div></div></div>
<div class="mnu" id="mnu" hidden><button data-a="pal">Search and commands</button><button data-a="fmt">Format file</button><button data-a="theme">Toggle theme</button></div>
<div class="shov" id="psh" hidden><div class="shs" role="dialog" aria-label="Problems"><div class="hdl"></div><div class="shh"><b>Problems</b><span class="chp e" id="ce"></span><span class="chp w" id="cw2"></span><button class="cl2" id="shx" aria-label="Close">✕</button></div><div class="pad" id="prob"></div></div></div>
<div class="toast" id="toast"></div>

<script>
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const toast=t=>{const e=$('#toast');e.textContent=t;e.classList.add('on');clearTimeout(toast.t);toast.t=setTimeout(()=>e.classList.remove('on'),1800)};
const files={
'MainActivity.kt':`package com.closecode.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import java.util.UUID

// Entry point of the app
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      NotesScreen()
    }
  }
}`,
'NotesScreen.kt':`package com.closecode.notes

import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.Text

@Composable
fun NotesScreen() {
  Text("My Notes", fontSize = 28.sp)
  Text("3 notes saved on this device")
  Button(onClick = { addNote() }) {
    Text("New note")
  }
  Text("Everything stays offline", fontSize = 12.sp)
}

fun addNote() {
  // TODO: write to Room database
}`,
'AndroidManifest.xml':`<manifest package="com.closecode.notes">
  <application android:label="Notes">
    <activity android:name=".MainActivity" />
  </application>
</manifest>`};
const JAVA=`package com.closecode.blankapp;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}b
f`;
const ICON=`<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@color/ic_launcher_background" />
  <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>`;
Object.assign(files,{'MainActivity.java':JAVA,'ic_launcher_round.xml':ICON});
const order=['MainActivity.java','ic_launcher_round.xml',...Object.keys(files).filter(f=>f!=='MainActivity.java'&&f!=='ic_launcher_round.xml')];
let cur='MainActivity.java',open=['MainActivity.java','ic_launcher_round.xml'];
const saved={...files};saved['MainActivity.java']=JAVA.replace('}b\nf','}\n');
const PATH={'MainActivity.java':'Blank app/app/src/main/java/com/closecode/blankapp/MainActivity.java','ic_launcher_round.xml':'app/res/mipmap-anydpi/ic_launcher_round.xml','MainActivity.kt':'app/closecode/notes/MainActivity.kt','NotesScreen.kt':'app/closecode/notes/NotesScreen.kt','AndroidManifest.xml':'app/manifests/AndroidManifest.xml'};
const ta=$('#ta'),hl=$('#hl'),gut=$('#gut');
const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const KW='package|import|class|fun|val|var|override|private|object|return|if|else|when|for|while|is|in|null|true|false|this|super|data|interface|suspend|public|protected|void|extends|implements|new|int|static|final|boolean';
let diags=[];
/* ---------- lint ---------- */
function lintBase(src,name){const out=[],L=src.split('\n');if(name.endsWith('.xml'))return out;
 const st=[],pair={')':'(',']':'[','}':'{'};let inBlock=false;
 L.forEach((raw,i)=>{let line=raw;if(inBlock){const e=line.indexOf('*/');if(e<0)return;line=line.slice(e+2);inBlock=false}
  line=line.replace(/\/\*.*?\*\//g,'');if(line.includes('/*')){line=line.slice(0,line.indexOf('/*'));inBlock=true}
  const q=(line.replace(/\\"/g,'').match(/"/g)||[]).length;
  if(q%2)out.push({l:i,s:'e',m:'Unterminated string literal',c:line.indexOf('"')});
  const bare=line.replace(/"(?:\\.|[^"\\])*"/g,'""').replace(/\/\/.*$/,'');
  [...bare].forEach((ch,j)=>{if('([{'.includes(ch))st.push({ch,l:i,c:j});else if(pair[ch]){const t=st.pop();if(!t||t.ch!==pair[ch])out.push({l:i,s:'e',m:`Unexpected '${ch}'`,c:j})}});
  if(/\/\/\s*TODO/.test(raw))out.push({l:i,s:'w',m:'TODO: '+raw.split(/TODO:?/)[1].trim(),c:raw.indexOf('//')});});
 st.forEach(t=>out.push({l:t.l,s:'e',m:`Unclosed '${t.ch}'`,c:t.c}));
 L.forEach((raw,i)=>{const m=raw.match(/^import\s+[\w.]+\.(\w+)$/);if(m){const rest=L.filter(x=>!/^import/.test(x)).join('\n');if(!new RegExp('\\b'+m[1]+'\\b').test(rest))out.push({l:i,s:'w',m:`Unused import '${m[1]}'`,c:raw.lastIndexOf(m[1])})}});
 return out}
const CODES=[[/^Unterminated/,'E0105'],[/^Unexpected/,'E0104'],[/^Unclosed/,'E0103'],[/^Unused import/,'W0201'],[/^TODO/,'W0202']];
function lint(src,name){const L=src.split('\n'),out=lintBase(src,name).map(d=>({...d,code:(CODES.find(c=>c[0].test(d.m))||[0,''])[1]}));
 const add=(l,s,code,m,c,n)=>out.push({l,s,code,m,c,n});
 if(name.endsWith('.java'))L.forEach((raw,i)=>{const t=raw.trim(),m=t.match(/^\}\s*([A-Za-z_]\w*)$/);
  if(m)add(i,'e','E0101',`Cannot resolve symbol '${m[1]}'`,raw.lastIndexOf(m[1]),m[1].length);
  else if(t&&!/^(\/\/|\/\*|\*|@|\}|\{)/.test(t)&&!/[;{},(:>+\-*\/=&|.?]$/.test(t)&&!/\*\/$/.test(t))add(i,'e','E0102',"Syntax error: ';' expected",Math.max(0,raw.length-1),1)});
 if(name.endsWith('.kt'))L.forEach((raw,i)=>{const m=raw.match(/\b(?:val|var)\s+(\w+)/);if(m&&(src.match(new RegExp('\\b'+m[1]+'\\b','g'))||[]).length===1)add(i,'w','W0203',`Variable '${m[1]}' is never used`,raw.indexOf(m[1]),m[1].length)});
 return out}
/* ---------- highlight ---------- */
function paint(){const src=ta.value,L=src.split('\n'),isx=cur.endsWith('.xml');
 hl.innerHTML=L.map((ln,i)=>{let h=esc(ln);
  if(isx)h=h.replace(/(&lt;\/?)([\w.]+)/g,'$1<span class="k">$2</span>').replace(/(android:\w+)/g,'<span class="f">$1</span>').replace(/&quot;|"([^"]*)"/g,m=>m);
  else{const ph=[];const hold=x=>{ph.push(x);return '\u0001'+(ph.length-1)+'\u0002'};
   h=h.replace(/(\/\/.*)/,(_,m)=>hold('<span class="c">'+m+'</span>')).replace(/"(?:\\.|[^"\\])*"/g,m=>hold('<span class="s">'+m+'</span>')).replace(/(@\w+)/g,'<span class="a">$1</span>').replace(new RegExp('\\b('+KW+')\\b','g'),'<span class="k">$1</span>').replace(/\b(\d+)(\.sp|\.dp)?\b/g,'<span class="n">$&</span>').replace(/\b([A-Za-z_]\w*)(?=\()/g,'<span class="f">$1</span>').replace(/(?<![>\w])([A-Z]\w*)/g,'<span class="ty">$1</span>').replace(/\u0001(\d+)\u0002/g,(_,k)=>ph[k])}
  
  return h}).join('\n')+'\n';
 const fi=isx?-1:L.findIndex(x=>/^import /.test(x));
 gut.innerHTML=L.map((_,i)=>{const d=diags.find(x=>x.l===i&&x.s==='e')||diags.find(x=>x.l===i);return `<div class="${d?d.s:''}">${d?'<b class="dt"></b>':''}${i+1}${i===fi?'<b class="fd">▸</b>':''}</div>`}).join('');overlay();
 sync()}
function sync(){hl.scrollTop=ta.scrollTop;hl.scrollLeft=ta.scrollLeft;gut.scrollTop=ta.scrollTop;$('#ov').style.transform=`translate(${-ta.scrollLeft}px,${-ta.scrollTop}px)`;bulbPos()}
let cw=7.8,cd=[];
function measure(){const m=document.createElement('span');m.style.cssText='position:absolute;visibility:hidden;white-space:pre;font:13px "Fira Code",ui-monospace,monospace';m.textContent='M'.repeat(40);document.body.appendChild(m);cw=m.getBoundingClientRect().width/40;m.remove()}
measure();if(document.fonts)document.fonts.ready.then(()=>{measure();analyze()});
const ICO={e:'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6M15 9l-6 6"/></svg>',w:'<svg viewBox="0 0 24 24"><path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18h.01"/></svg>',st:'<svg viewBox="0 0 24 24"><path d="M12 3l9 5-9 5-9-5zM3 13l9 5 9-5"/></svg>'};
function overlay(){cd=diags.slice();const L=ta.value.split('\n'),by={};cd.forEach(d=>(by[d.l]=by[d.l]||[]).push(d));let h='';
 Object.keys(by).forEach(k=>{const i=+k,ds=by[k].sort((a,b)=>a.s<b.s?-1:1),top=12+i*20;
  ds.forEach(d=>{const n=d.n||((L[i].slice(d.c).match(/^\w+|^./)||[' '])[0].length);h+=`<i class="sqg ${d.s}" style="left:${14+d.c*cw}px;top:${top+16}px;width:${Math.max(n,1)*cw}px"></i>`});
  const f=ds[0];h+=`<span class="pill ${f.s}" data-l="${i}" style="left:${14+(L[i].length+2)*cw}px;top:${top-1}px">${ICO[f.s]}${esc(f.m)} <code>${f.code||''}</code>${ds.length>1?`<span class="bg">${ICO.st}${ds.length}</span>`:''}</span>`});
 $('#ov').innerHTML=h;bulbPos()}
function bulbPos(){const b=$('#bulb');if(!b)return;const i=ta.value.slice(0,ta.selectionStart).split('\n').length-1;
 b.hidden=!cd.some(d=>d.l===i)||!$('#pop').hidden;if(!b.hidden)b.style.top=Math.max(2,i*20-31-ta.scrollTop)+'px'}
function off(l,c){return ta.value.split('\n').slice(0,l).join('\n').length+(l?1:0)+c}
function quickFix(d){const raw=ta.value.split('\n')[d.l];let a,b,t='',msg;
 if(d.code==='E0101'){a=off(d.l,d.c);b=a+d.n;msg=`removed '${raw.substr(d.c,d.n)}'`}
 else if(d.code==='E0102'){a=b=off(d.l,raw.length);t=';';msg="added ';'"}
 else if(d.code==='W0201'){a=off(d.l,0);b=a+raw.length+1;msg='removed unused import'}
 else return toast('No quick fix for '+(d.code||'this problem'));
 ta.focus();ta.setSelectionRange(a,b);t?document.execCommand('insertText',false,t):document.execCommand('delete');toast('Quick fix: '+msg)}
$('#bulb').onclick=()=>{const i=ta.value.slice(0,ta.selectionStart).split('\n').length-1,d=cd.find(x=>x.l===i);if(d)quickFix(d)};
gut.onclick=e=>{if(e.target.classList.contains('fd'))toast('Code folding is not available yet')};
ta.addEventListener('scroll',sync);
function analyze(){drawTabs();diags=[];order.forEach(f=>lint(files[f],f).forEach(d=>diags.push({...d,f})));
 const mine=diags.filter(d=>d.f===cur);const saved=diags;diags=mine;paint();diags=saved;
 const e=saved.filter(d=>d.s==='e').length,w=saved.filter(d=>d.s==='w').length;
 $('#sum').innerHTML=`<span style="color:var(--er)">${e} errors</span> · <span style="color:var(--wn)">${w} warnings</span>`;
 renderProb();renderTree()}
function renderProb(){const P=diags;$('#prob').innerHTML=P.length?P.sort((a,b)=>(a.s<b.s?-1:1)).map((d,i)=>`<button class="item" data-i="${i}"><span class="dot" style="background:var(--${d.s==='e'?'er':'wn'})"></span><span><b>${d.m}</b> <code class="cd ${d.s}">${d.code||''}</code><br><span style="color:var(--mu)">${d.f}:${d.l+1}</span></span></button>`).join(''):'<div class="empty">No problems. Code is clean.</div>';
 $$('#prob .item').forEach(b=>b.onclick=()=>{const d=P[b.dataset.i];openFile(d.f);view('code');const off=files[d.f].split('\n').slice(0,d.l).join('\n').length+(d.l?1:0)+(d.c||0);ta.focus();ta.setSelectionRange(off,off);ta.scrollTop=Math.max(0,d.l*20-80);sync()})}
/* ---------- files ---------- */
function renderTree(){$('#ft').innerHTML='<button class="d">▾ java/com/closecode/blankapp</button>'+order.filter(f=>/\.(kt|java)$/.test(f)).map(f=>btn(f)).join('')+'<button class="d">▾ manifests</button>'+btn('AndroidManifest.xml')+btn('ic_launcher_round.xml')}
function btn(f){const n=diags.filter(d=>d.f===f&&d.s==='e').length;return `<button class="l ${f===cur?'on':''}" data-f="${f}">${f}${n?`<span class="badge">${n}</span>`:''}</button>`}
$('#ft').onclick=e=>{const b=e.target.closest('[data-f]');if(b){openFile(b.dataset.f);view('code');tree(false)}};
function openFile(f){if(!open.includes(f))open.push(f);cur=f;ta.value=files[f];ta.setSelectionRange(ta.value.length,ta.value.length);$('#crumb').textContent='Blank app';analyze();preview()}
const KIND=f=>f.endsWith('.java')?['j','J']:f.endsWith('.kt')?['k','K']:['x','‹›'];
function drawTabs(){$('#tabs').innerHTML=open.map(x=>{const[c,g]=KIND(x);return `<div class="tab ${x===cur?'on':''}" data-f="${x}" role="tab"><span class="fi ${c}">${g}</span>${x}${files[x]!==saved[x]?'<span class="ud"></span>':''}<button class="cl" data-x="${x}" aria-label="Close ${x}">✕</button></div>`}).join('');
 $('#sv').classList.toggle('on',files[cur]!==saved[cur]);$('#lg').textContent=cur.endsWith('.java')?'Java':cur.endsWith('.kt')?'Kotlin':'XML'}
function saveFile(){saved[cur]=files[cur];drawTabs();toast('Saved '+cur)}
$('#tabs').onclick=e=>{const x=e.target.closest('[data-x]');if(x){const f=x.dataset.x;if(open.length>1){open=open.filter(o=>o!==f);if(cur===f)openFile(open[open.length-1]);else drawTabs()}return}const t=e.target.closest('[data-f]');if(t)openFile(t.dataset.f)};

/* ---------- suggestions ---------- */
const PK={Bundle:'android.os',BuildConfig:'com.closecode.blankapp',Boolean:'java.lang',BootstrapMethodError:'java.lang',Byte:'java.lang',Activity:'android.app',AppCompatActivity:'androidx.appcompat.app',TextView:'android.widget',Button:'android.widget',View:'android.view',Intent:'android.content',Context:'android.content',String:'java.lang',ArrayList:'java.util',List:'java.util',Toast:'android.widget',Log:'android.util'};
const API=Object.keys(PK).concat(['setContent','setOnClickListener','savedInstanceState','Bundle','Composable','fontSize','Modifier','padding','fillMaxSize','remember','mutableStateOf','LaunchedEffect','Column','Row','LazyColumn','Button','Text','onClick','override','private','suspend','viewModel','StateFlow','coroutineScope','println','listOf','mapOf','forEach','filter','NotesScreen']);
function suggest(){const pre=ta.value.slice(0,ta.selectionStart),m=pre.match(/[A-Za-z_]\w*$/);const w=m?m[0]:'';
 const words=new Set([...(ta.value.match(/[A-Za-z_]\w{3,}/g)||[]),...API]);
 const r=w.length>=1&&document.activeElement===ta?[...words].filter(x=>x!==w&&x.toLowerCase().startsWith(w.toLowerCase())).slice(0,5):[];
 showPop(r,w,pre);bulbPos();
 const ln=pre.split('\n');$('#pos').textContent=`Ln ${ln.length}, Col ${ln.at(-1).length+1}`;return{w,r}}
function accept(x){const{w}=suggest();ta.setRangeText(x,ta.selectionStart-w.length,ta.selectionStart,'end');input()}
function showPop(r,w,pre){const p=$('#pop');if(!r.length){hidePop();return}
 const box=$('.code'),ln=pre.split('\n'),i=ln.length-1,col=ln[i].length-w.length,W=Math.min(430,box.clientWidth-16);
 p.style.width=W+'px';p.style.left=Math.max(6,Math.min(14+col*cw-ta.scrollLeft,box.clientWidth-W-6))+'px';p.style.top=(12+(i+1)*20+6-ta.scrollTop)+'px';
 p.innerHTML=r.map((x,k)=>`<div class="pr${k?'':' on'}" data-w="${x}"><i>C</i><span><b>${x}</b><small>${PK[x]||'this file'}</small></span></div>`).join('');p.hidden=false;if(parseFloat(p.style.top)+p.offsetHeight>box.clientHeight-4)p.style.top=Math.max(4,12+i*20-ta.scrollTop-6-p.offsetHeight)+'px'}
function hidePop(){const p=$('#pop');if(!p.hidden){p.hidden=true;bulbPos()}}
$('#pop').addEventListener('pointerdown',e=>e.preventDefault());
$('#pop').onclick=e=>{const r=e.target.closest('[data-w]');if(r){accept(r.dataset.w);ta.focus()}};
ta.addEventListener('blur',()=>setTimeout(hidePop,120));ta.addEventListener('scroll',hidePop);
const ins=t=>{ta.focus();document.execCommand('insertText',false,t)};
$('#tools').onclick=e=>{const b=e.target.closest('button');if(!b)return;const k=b.dataset.k;ta.focus();
 if(k==='tab')ins('  ');else if(k==='wn')view('prob');
 else if(k==='cp'){const s=ta.value.slice(ta.selectionStart,ta.selectionEnd)||ta.value.split('\n')[ta.value.slice(0,ta.selectionStart).split('\n').length-1];navigator.clipboard&&navigator.clipboard.writeText(s).catch(()=>{});toast('Copied')}
 else if(k==='up'||k==='dn'){const v=ta.value,p=ta.selectionStart,ls=v.lastIndexOf('\n',p-1)+1,col=p-ls;let np;
  if(k==='up'){if(!ls)return;const ps=v.lastIndexOf('\n',ls-2)+1;np=ps+Math.min(col,ls-1-ps)}
  else{const le=v.indexOf('\n',p);if(le<0)return;const ne=v.indexOf('\n',le+1),e2=ne<0?v.length:ne;np=le+1+Math.min(col,e2-le-1)}
  ta.setSelectionRange(np,np);suggest()}
 else if(k==='cm'){const v=ta.value,a=v.lastIndexOf('\n',ta.selectionStart-1)+1;let z=v.indexOf('\n',ta.selectionEnd);if(z<0)z=v.length;
  const ls=v.slice(a,z).split('\n'),un=ls.every(l=>/^\s*\/\//.test(l)||!l.trim()),out=ls.map(l=>l.trim()?(un?l.replace(/^(\s*)\/\/ ?/,'$1'):l.replace(/^(\s*)/,'$1// ')):l).join('\n');
  ta.setSelectionRange(a,z);document.execCommand('insertText',false,out)}
 else ins(k)};
function input(){files[cur]=ta.value;analyze();suggest();preview()}
ta.addEventListener('input',input);ta.addEventListener('keyup',suggest);ta.addEventListener('click',suggest);
ta.addEventListener('keydown',e=>{if(e.key==='Tab'){e.preventDefault();const{r}=suggest();if(r.length)accept(r[0]);else document.execCommand('insertText',false,'  ')}
 if(e.key==='Enter'){const pre=ta.value.slice(0,ta.selectionStart),ln=pre.split('\n').at(-1),ind=ln.match(/^\s*/)[0]+(/[{(]\s*$/.test(ln)?'  ':'');e.preventDefault();document.execCommand('insertText',false,'\n'+ind)}
 if((e.ctrlKey||e.metaKey)&&e.key==='k'){e.preventDefault();pal(true)}});
/* ---------- format ---------- */
function format(){if(cur.endsWith('.xml'))return toast('Format supports Kotlin files');let d=0;ta.value=ta.value.split('\n').map(l=>{const t=l.trim();if(/^[}\])]/.test(t))d=Math.max(0,d-1);const o=t?'  '.repeat(d)+t:'';const bare=t.replace(/"(?:\\.|[^"\\])*"/g,'').replace(/\/\/.*/,'');d=Math.max(0,d+(bare.match(/[{(\[]/g)||[]).length-(bare.match(/[})\]]/g)||[]).length+(/^[}\])]/.test(t)?1:0));return o}).join('\n');input();toast('Formatted')}

/* ---------- live preview ---------- */
function preview(){const src=files['NotesScreen.kt'],items=[],re=/Button\s*\([^)]*\)\s*\{\s*Text\(\s*"([^"]*)"|Text\(\s*"([^"]*)"(?:[^)\n]*?fontSize\s*=\s*(\d+)\.sp)?/g;let m;
 const body=src.slice(src.indexOf('fun NotesScreen'));
 while((m=re.exec(body))){if(m[1]!==undefined)items.push(`<button>${esc(m[1])}</button>`);else items.push(`<div style="font-size:${m[3]||16}px;${m[3]>20?'font-weight:700;':''}${m[3]&&m[3]<14?'color:#8a8794;':''}">${esc(m[2])}</div>`)}
 $('#phone').innerHTML=items.join('')+'<small>Live preview of NotesScreen.kt</small>'}
/* ---------- views ---------- */
function view(v){if(v==='prob'){openSheet();return}closeSheet();$$('.view').forEach(x=>x.classList.toggle('on',x.id==='v-'+v));$$('#tb button').forEach(b=>b.classList.toggle('on',b.dataset.v===v))}
$('#tb').onclick=e=>{const b=e.target.closest('button');if(b)view(b.dataset.v)};
const tree=o=>{$('#tree').classList.toggle('on',o);$('#scrim').classList.toggle('on',o);$('#exp').setAttribute('aria-expanded',o)};
$('#exp').onclick=()=>tree(!$('#tree').classList.contains('on'));$('#scrim').onclick=()=>tree(false);
/* ---------- logcat ---------- */
let lv='V';const LOG=[];
function log(l,t,m){LOG.push({l,t,m});drawLog()}
function drawLog(){const rk={V:0,D:1,I:2,W:3,E:4};$('#log').innerHTML=LOG.filter(x=>rk[x.l]>=rk[lv]).map(x=>`<div class="${x.l}">${x.t} ${x.l}/${esc(x.m)}</div>`).join('')||'<div class="empty">Run the app to see logs.</div>';$('#log').scrollTop=1e9}
$('#lvl').onclick=e=>{const b=e.target.closest('button');if(!b)return;lv=b.dataset.l;$$('#lvl button').forEach(x=>x.classList.toggle('on',x===b));drawLog()};
const ts=()=>new Date().toTimeString().slice(0,8)+'.'+String(Date.now()%1000).padStart(3,'0');
function runApp(){const e=diags.filter(d=>d.s==='e').length;if(e){view('prob');return toast(`Fix ${e} error${e>1?'s':''} before running`)}
 LOG.length=0;view('prev');const seq=[['I','ActivityManager','Start proc com.closecode.notes for activity MainActivity'],['D','Compose','Recomposing NotesScreen'],['I','Room','Opening notes.db (3 rows)'],['W','Choreographer','Skipped 31 frames; main thread is busy'],['D','NotesScreen','render: '+$$('#phone > *').length+' nodes'],['I','Closecode','App running']];
 seq.forEach((s,i)=>setTimeout(()=>log(s[0],ts(),s[1]+': '+s[2]),i*260));toast('Installed and launched')}
$('#run').onclick=runApp;
/* ---------- build ---------- */
const TASKS=['preDebugBuild','compileDebugKotlin','mergeDebugResources','dexBuilderDebug','packageDebug','assembleDebug'];
let building=false;
function build(){if(building)return;building=true;view('build');const e=diags.filter(d=>d.s==='e').length;$('#bres').textContent='';$('#bar').style.width='0';
 $('#tasks').innerHTML=TASKS.map((t,i)=>`<div class="task"><span class="st" id="t${i}">·</span>:app:${t}</div>`).join('');
 let i=0;(function step(){if(i>=TASKS.length){$('#bres').innerHTML='<span style="color:var(--ok)">BUILD SUCCESSFUL · app-debug.apk (4.2 MB)</span>';building=false;return}
  $('#t'+i).textContent='…';setTimeout(()=>{if(i===1&&e){$('#t1').innerHTML='<span style="color:var(--er)">✕</span>';$('#bres').innerHTML=`<span style="color:var(--er)">BUILD FAILED · ${e} compile error${e>1?'s':''}. Open Problems to fix.</span>`;building=false;return}
   $('#t'+i).innerHTML='<span style="color:var(--ok)">✓</span>';$('#bar').style.width=((++i)/TASKS.length*100)+'%';step()},420)})()}
/* ---------- palette ---------- */
const CMDS=[['Save file','Ctrl S',saveFile],['Run app','Ctrl R',runApp],['Build debug APK','',build],['Format file','',format],['Show problems','',()=>view('prob')],['Show logcat','',()=>view('log')],['Toggle theme','',()=>{const d=getComputedStyle(document.body).backgroundColor==='rgb(11, 15, 22)';document.documentElement.dataset.theme=d?'light':'dark'}],...order.map(f=>['Open '+f,'',()=>{openFile(f);view('code')}])];
let sel=0,list=[];
function pal(o){$('#pal').classList.toggle('on',o);if(o){$('#pi').value='';drawPal();$('#pi').focus()}}
function drawPal(){const q=$('#pi').value.toLowerCase();list=CMDS.filter(c=>q.split('').every((ch,i,a)=>c[0].toLowerCase().includes(ch)&&true)&&(c[0].toLowerCase().includes(q)||[...q].reduce((p,ch)=>{const j=c[0].toLowerCase().indexOf(ch,p);return j<0?1e9:j+1},0)<1e9));sel=0;$('#pl').innerHTML=list.map((c,i)=>`<button class="${i===sel?'on':''}" data-i="${i}">${c[0]}<kbd>${c[1]}</kbd></button>`).join('')}
$('#pi').oninput=drawPal;
$('#pi').onkeydown=e=>{if(e.key==='Escape')pal(false);if(e.key==='ArrowDown'||e.key==='ArrowUp'){sel=(sel+(e.key==='ArrowDown'?1:-1)+list.length)%list.length;$$('#pl button').forEach((b,i)=>b.classList.toggle('on',i===sel))}if(e.key==='Enter'&&list[sel]){pal(false);list[sel][2]()}};
$('#pl').onclick=e=>{const b=e.target.closest('button');if(b){pal(false);list[b.dataset.i][2]()}};
$('#pal').onclick=e=>{if(e.target.id==='pal')pal(false)};
document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='k'){e.preventDefault();pal(true)}if((e.ctrlKey||e.metaKey)&&e.key==='s'){e.preventDefault();saveFile()}});
function openSheet(line){const o=$('#psh');o.style.bottom=$('#tb').offsetHeight+'px';o.hidden=false;
 const e=diags.filter(d=>d.s==='e').length,w=diags.length-e;$('#ce').textContent=e+(e===1?' error':' errors');$('#cw2').textContent=w+(w===1?' warning':' warnings');
 renderProb();if(line!=null)$$('#prob .item').forEach(b=>{const d=diags[b.dataset.i];if(d&&d.f===cur&&d.l===line)b.classList.add('hi')})}
function closeSheet(){$('#psh').hidden=true}
$('#shx').onclick=closeSheet;$('#psh').onclick=e=>{if(e.target.id==='psh')closeSheet()};
let sy=null;$('.shs').addEventListener('touchstart',e=>{sy=e.touches[0].clientY},{passive:true});
$('.shs').addEventListener('touchend',e=>{if(sy!=null&&e.changedTouches[0].clientY-sy>70&&$('#prob').scrollTop===0)closeSheet();sy=null});
ta.addEventListener('click',e=>{const hit=[...document.querySelectorAll('.pill')].find(p=>{const r=p.getBoundingClientRect();return e.clientX>=r.left&&e.clientX<=r.right&&e.clientY>=r.top&&e.clientY<=r.bottom});if(hit)openSheet(+hit.dataset.l)});
$('#sum').style.cursor='pointer';$('#sum').onclick=()=>openSheet();
$('#sv').onclick=saveFile;
$('#un').onclick=()=>{ta.focus();document.execCommand('undo')};
$('#rd').onclick=()=>{ta.focus();document.execCommand('redo')};
const mnu=$('#mnu');$('#more').onclick=e=>{e.stopPropagation();mnu.hidden=!mnu.hidden};
document.addEventListener('click',()=>{mnu.hidden=true});
mnu.onclick=e=>{const b=e.target.closest('button');if(!b)return;const a=b.dataset.a;if(a==='pal')pal(true);else CMDS.find(c=>c[0]===(a==='fmt'?'Format file':'Toggle theme'))[2]()};
function aiSend(){const v=$('#aii').value.trim();if(!v)return;const f=$('#aif');f.insertAdjacentHTML('beforeend','<div class="am u"></div>');f.lastChild.textContent=v;$('#aii').value='';f.scrollTop=f.scrollHeight;
 setTimeout(()=>{f.insertAdjacentHTML('beforeend','<div class="am">This prototype does not call a model. Your real AI panel goes here.</div>');f.scrollTop=f.scrollHeight},500)}
$('#aig').onclick=aiSend;$('#aii').onkeydown=e=>{if(e.key==='Enter')aiSend()};
openFile('MainActivity.java');drawLog();
</script>
</body>
</html>
