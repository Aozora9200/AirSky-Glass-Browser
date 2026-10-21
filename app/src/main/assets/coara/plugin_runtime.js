(function(CH){
// Coara Browser 拡張機能(プラグイン)の実行環境。AirSky(GeckoView)版。
// Coara の WebView 版は addJavascriptInterface の同期ブリッジを使うが、Gecko には無いので、
// 組み込み拡張のコンテンツスクリプト(coara_cs.js)と同期の CustomEvent でやり取りする。
// CH はページごとに作る乱数の名前(コンテンツスクリプトが渡す)。
var D=EventTarget.prototype.dispatchEvent;
var CE=CustomEvent;
var REQ="coara-req-"+CH,RES="coara-res-"+CH,EVT="coara-evt-"+CH;
var META=%META%;
var CSS=%CSS%;
var KEY="%KEY%";
var last=null;
try{window.addEventListener(RES,function(e){last=e.detail;},true);}catch(e){}
function inv(op,a,b,c){
try{
last=null;
D.call(window,new CE(REQ,{detail:JSON.stringify([META.id,KEY,op,a==null?"":String(a),b==null?"":String(b),c==null?"":String(c)])}));
var r=last;
last=null;
return r;
}catch(e){}
return null;
}
// 最初に呼ぶ: コンテンツスクリプトはこれで「ページ側で動いた」と分かる(CSP で止められたら別の方法で動かす)
inv("boot");
var listeners={};
try{
window.addEventListener(EVT,function(e){
var m;
try{m=JSON.parse(e.detail);}catch(x){return;}
if(!m||m.k!==KEY)return;
var f=listeners[m.t];
if(typeof f==="function")f(m);
},true);
}catch(e){}
return (function(PLG){
var isTop=false;
try{isTop=window.top===window;}catch(e){}
function jparse(s){try{return JSON.parse(s);}catch(e){return null;}}
function rx(list,u){
for(var i=0;i<list.length;i++){
try{if(new RegExp(list[i]).test(u))return true;}catch(e){}
}
return false;
}
var href="";
try{href=String(location.href);}catch(e){}
if(!META.allFrames&&!isTop)return"0";
if(META.excludes.length&&rx(META.excludes,href))return"0";
if(!rx(META.matches,href))return"0";
var reports=0;
function rep(kind,message,stack){
if(reports>=5)return;
reports++;
var o={};
try{o.url=String(location.href);}catch(e){o.url="";}
o.stack=stack==null?"":String(stack).substring(0,3000);
inv("report",kind,message==null?"":String(message).substring(0,1000),JSON.stringify(o));
}
function fmt(args){
var out=[];
for(var i=0;i<args.length;i++){
var v=args[i];
if(typeof v==="string"){out.push(v);}
else{
try{
var s=JSON.stringify(v);
out.push(s===undefined?String(v):s);
}catch(e){out.push(String(v));}
}
}
return out.join(" ");
}
function safe(f){
try{f(api);}catch(e){rep("runtime",e&&e.message?e.message:String(e),e&&e.stack);}
}
function addStyle(css){
var text=String(css);
var handle={};
var sheet=null;
var el=null;
try{
if(typeof CSSStyleSheet==="function"&&("adoptedStyleSheets" in document)){
sheet=new CSSStyleSheet();
sheet.replaceSync(text);
document.adoptedStyleSheets=document.adoptedStyleSheets.concat([sheet]);
}
}catch(e){sheet=null;}
if(!sheet){
var attach=function(){
try{
el=document.createElement("style");
el.textContent=text;
(document.head||document.documentElement).appendChild(el);
}catch(e){}
};
if(document.head||document.documentElement){attach();}
else{document.addEventListener("DOMContentLoaded",attach,{once:true});}
}
handle.remove=function(){
try{
if(sheet){
document.adoptedStyleSheets=document.adoptedStyleSheets.filter(function(s){return s!==sheet;});
sheet=null;
}
if(el&&el.parentNode){
el.parentNode.removeChild(el);
el=null;
}
}catch(e){}
};
return handle;
}
function onReady(f){
if(typeof f!=="function")return;
if(document.readyState!=="loading"){setTimeout(function(){safe(f);},0);}
else{document.addEventListener("DOMContentLoaded",function(){safe(f);},{once:true});}
}
var cmds={};
var cmdSeq=0;
listeners.cmd=function(m){var f=cmds[m.cid];if(typeof f==="function")safe(function(){f();});};
function registerCommand(name,fn){
if(!isTop||typeof fn!=="function")return null;
var cid=KEY+":"+(++cmdSeq);
cmds[cid]=fn;
inv("cmd",cid,String(name));
return cid;
}
function unregisterCommand(cid){
if(cid==null)return;
delete cmds[cid];
inv("uncmd",cid);
}
function toB64(buf){
var bytes=buf instanceof ArrayBuffer?new Uint8Array(buf):new Uint8Array(buf.buffer,buf.byteOffset,buf.byteLength);
var s="";
for(var i=0;i<bytes.length;i+=8192){s+=String.fromCharCode.apply(null,bytes.subarray(i,i+8192));}
return btoa(s);
}
function fromB64(b64){
var s=atob(b64);
var u=new Uint8Array(s.length);
for(var i=0;i<s.length;i++)u[i]=s.charCodeAt(i);
return u.buffer;
}
var xseq=0;
var xpending={};
listeners.xhr=function(m){var f=xpending[m.rid];if(typeof f==="function"){delete xpending[m.rid];f(m.res);}};
function xhr(o){
o=o||{};
var handle={abort:function(){}};
var finished=false;
function emit(name,payload){
if(finished&&name!=="onabort")return;
finished=true;
var f=o[name];
if(typeof f==="function"){try{f(payload);}catch(e){rep("runtime",e&&e.message?e.message:String(e),e&&e.stack);}}
}
var url;
try{url=new URL(String(o.url),location.href).href;}catch(e){
setTimeout(function(){emit("onerror",{error:"invalid url",status:0,readyState:4});},0);
return handle;
}
var opts={url:url,method:o.method||"GET",headers:o.headers||{},responseType:o.responseType||"",timeout:o.timeout||0,anonymous:!!o.anonymous,user:o.user||"",password:o.password||"",origin:(function(){try{return location.hostname;}catch(e){return "";}})(),binary:!!o.binary,noRedirect:o.redirect==="manual"||o.redirect==="error",ua:(function(){try{return navigator.userAgent;}catch(e){return "";}})()};
if(o.overrideMimeType&&/charset=([\w-]+)/i.test(o.overrideMimeType))opts.charset=RegExp.$1;
var d=o.data;
if(d!=null){
if(typeof d==="string")opts.data=d;
else if(typeof URLSearchParams!=="undefined"&&d instanceof URLSearchParams)opts.data=d.toString();
else if(d instanceof ArrayBuffer||(d&&d.buffer instanceof ArrayBuffer&&typeof d.byteLength==="number"))opts.dataB64=toB64(d);
else opts.data=String(d);
}
var rid=KEY+"x"+(++xseq)+Math.floor(Math.random()*1679616).toString(36);
function build(res){
var out={readyState:4,status:res.status||0,statusText:res.statusText||"",finalUrl:res.finalUrl||url,responseHeaders:res.headers||"",context:o.context};
if(res.b64!==undefined){
var ab=fromB64(res.b64);
out.response=opts.responseType==="blob"&&typeof Blob!=="undefined"?new Blob([ab],{type:res.mime||""}):ab;
out.responseText="";
}else{
out.responseText=res.text||"";
if(opts.responseType==="json"){out.response=jparse(out.responseText);}
else out.response=out.responseText;
}
return out;
}
xpending[rid]=function(res){
if(finished)return;
if(!res){emit("onerror",{error:"bad response",status:0,readyState:4});return;}
if(res.aborted){emit("onabort",{error:"aborted",status:0,readyState:4});return;}
if(res.error){
var tag=/Timeout/i.test(res.error)?"ontimeout":"onerror";
emit(tag,{error:res.error,status:0,readyState:4,context:o.context});
return;
}
var b=build(res);
emit("onload",b);
if(typeof o.onloadend==="function"){try{o.onloadend(b);}catch(e){}}
};
var ok=inv("xhr",rid,JSON.stringify(opts));
if(ok!=="1"){
delete xpending[rid];
setTimeout(function(){emit("onerror",{error:"request rejected (permission/limit)",status:0,readyState:4});},0);
return handle;
}
handle.abort=function(){
if(finished)return;
delete xpending[rid];
inv("xhrAbort",rid);
emit("onabort",{error:"aborted",status:0,readyState:4});
};
return handle;
}
var api={
id:META.id,
title:META.title,
version:META.version,
meta:META,
isTop:isTop,
url:href,
log:function(){inv("log","info",fmt(arguments));},
warn:function(){inv("log","warn",fmt(arguments));},
error:function(){inv("log","error",fmt(arguments));},
storage:{
get:function(k){var v=inv("sget",k);return v===undefined?null:v;},
set:function(k,v){return inv("sset",k,v)==="1";},
remove:function(k){return inv("sdel",k)==="1";},
keys:function(){return jparse(inv("skeys"))||[];},
clear:function(){return inv("sclear")==="1";}
},
getResource:function(n){return inv("res",n);},
getResourceURL:function(n){return inv("resurl",n);},
i18n:function(k,subs){var v=inv("i18n",k,subs==null?"":JSON.stringify(subs));return v==null?"":v;},
uiLanguage:function(){return inv("uilang")||"";},
addStyle:addStyle,
onReady:onReady,
xhr:xhr,
copy:function(t){return inv("clip",t)==="1";},
notify:function(text,title){return inv("notify",title==null?META.title:title,text==null?"":text)==="1";},
openTab:function(u){return inv("open",u)==="1";},
registerCommand:registerCommand,
unregisterCommand:unregisterCommand,
_inv:inv,
_safe:safe,
_rep:rep,
_fmt:fmt
};
var SU="coara-plugin/"+META.id+"/";
window.addEventListener("error",function(ev){
try{
var f=String(ev&&ev.filename||"");
if(f.indexOf(SU)<0)return;
rep("error",ev.message,ev.error&&ev.error.stack);
}catch(e){}
},true);
window.addEventListener("unhandledrejection",function(ev){
try{
var r=ev&&ev.reason;
var s=String(r&&r.stack||"");
if(s.indexOf(SU)<0)return;
rep("promise",(r&&r.message)?r.message:String(r),s);
}catch(e){}
},true);
var ran=false;
function run(){
if(ran)return;
ran=true;
try{
for(var i=0;i<CSS.length;i++){api.addStyle(CSS[i]);}
PLG.call(window,api);
inv("ok","","","");
}catch(e){
rep("runtime",e&&e.message?e.message:String(e),e&&e.stack);
}
}
var at=META.runAt;
if(at==="document_start"){
run();
}else if(at==="document_idle"){
var idle=function(){setTimeout(run,0);};
if(document.readyState==="complete"){idle();}
else{window.addEventListener("load",idle,{once:true});}
}else{
if(document.readyState==="loading"){document.addEventListener("DOMContentLoaded",run,{once:true});}
else{run();}
}
return"1";
})(function(coara){
%PRELUDE%
%BODY%
});
})
