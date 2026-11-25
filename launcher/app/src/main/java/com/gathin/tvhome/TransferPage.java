package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** The phone page. It follows the TV's language, and the page itself can switch AR / EN. */
final class TransferPage {
    private static volatile Context app;

    private TransferPage() {}

    static void bind(Context ctx) {
        app = ctx == null ? null : ctx.getApplicationContext();
    }

    static String tvLanguage() {
        Context ctx = app;
        return ctx == null ? "ar" : LauncherLocale.pageLanguage(ctx);
    }

    static String app(String lang) {
        return app(lang, "");
    }

    static String app(String lang, String tvName) {
        String code = language(lang);
        String drop = text(code, R.string.web_drop, code.equals("en") ? "Drop files here" : "أسقط الملفات هنا");
        String hint = text(code, R.string.web_drop_hint, code.equals("en") ? "or tap to choose" : "أو اضغط للاختيار");
        String note = text(code, R.string.web_note, code.equals("en") ? "Text or a link" : "نص أو رابط");
        String noteHint = text(code, R.string.web_note_hint, code.equals("en") ? "Type a note or a link" : "اكتب نصًا أو رابطًا");
        String send = text(code, R.string.web_note_send, code.equals("en") ? "Send to the TV" : "إرسال إلى التلفاز");
        String files = text(code, R.string.web_files, code.equals("en") ? "Files" : "الملفات");
        String download = text(code, R.string.web_download, code.equals("en") ? "Download" : "تنزيل");
        String delete = text(code, R.string.web_delete, code.equals("en") ? "Delete" : "حذف");
        String who = tvName == null || tvName.isEmpty() ? "" : "<p class=\"who\">" + escape(tvName) + "</p>";
        return page(code, ""
                + "<h1>Alkhamis TV Transfer</h1>"
                + who
                + switcher(code)
                + "<section class=\"card\">"
                + "<label class=\"drop\" id=\"drop\">"
                + "<input id=\"pick\" type=\"file\" multiple hidden>"
                + "<strong>" + drop + "</strong>"
                + "<span>" + hint + "</span>"
                + "</label>"
                + "<div id=\"jobs\"></div>"
                + "</section>"
                + "<section class=\"card\">"
                + "<h2>" + note + "</h2>"
                + "<textarea id=\"note\" rows=\"3\" placeholder=\"" + noteHint + "\"></textarea>"
                + "<button class=\"gold\" id=\"noteSend\" type=\"button\">" + send + "</button>"
                + "<div id=\"notes\"></div>"
                + "</section>"
                + "<section class=\"card\">"
                + "<h2>" + files + "</h2>"
                + "<div id=\"files\"></div>"
                + "</section>"
                + "<script>" + script(download, delete) + "</script>");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String language(String lang) {
        return "en".equals(lang) ? "en" : "ar";
    }

    private static String switcher(String lang) {
        String ar = "ar".equals(lang) ? " class=\"on\"" : "";
        String en = "en".equals(lang) ? " class=\"on\"" : "";
        return "<nav class=\"langs\"><a" + ar + " href=\"?lang=ar\">AR</a><a" + en + " href=\"?lang=en\">EN</a></nav>";
    }

    private static String text(String lang, int id, String fallback) {
        Context ctx = app;
        if (ctx == null) return fallback;
        Configuration c = new Configuration(ctx.getResources().getConfiguration());
        Locale locale = Locale.forLanguageTag(lang);
        c.setLocale(locale);
        c.setLayoutDirection(locale);
        return ctx.createConfigurationContext(c).getString(id);
    }

    private static String page(String lang, String body) {
        String dir = "en".equals(lang) ? "ltr" : "rtl";
        return "<!doctype html><html lang=\"" + lang + "\" dir=\"" + dir + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Alkhamis TV Transfer</title>" + style() + "</head><body><main class=\"wrap\">"
                + body + "</main></body></html>";
    }

    private static String style() {
        return "<style>"
                + "body{margin:0;background:#101419;color:#fff;font-family:system-ui,sans-serif}"
                + ".wrap{max-width:520px;margin:0 auto;padding:20px}"
                + "h1{font-size:24px;margin:0 0 8px;color:#E6C57A}"
                + ".who{margin:0 0 12px;color:#fff;font-size:18px}"
                + "h2{font-size:18px;margin:0 0 8px;color:#fff}"
                + "p{color:#ACB8C1;margin:0 0 12px;font-size:16px}"
                + ".langs{display:flex;gap:8px;margin:0 0 14px}"
                + ".langs a{color:#E6C57A;text-decoration:none;border:1px solid #E6C57A;border-radius:12px;padding:8px 16px;font-size:16px}"
                + ".langs a.on{background:#E6C57A;color:#101419}"
                + ".card{background:#1A2129;border-radius:18px;padding:16px;margin:0 0 14px}"
                + ".drop{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;"
                + "border:1.5px dashed #E6C57A;border-radius:16px;padding:28px 16px;text-align:center;color:#ACB8C1;cursor:pointer}"
                + ".drop strong{color:#fff;font-size:18px}"
                + ".drop.over{background:#243028;border-color:#B9E7C7}"
                + ".job{margin-top:12px}"
                + ".job .name{font-size:16px}"
                + ".track{height:8px;background:#242B34;border-radius:8px;overflow:hidden;margin-top:6px}"
                + ".bar{height:100%;width:0;background:#E6C57A}"
                + ".file{display:flex;align-items:center;gap:10px;background:#242B34;border-radius:14px;padding:10px;margin:8px 0}"
                + ".kind{width:42px;height:42px;border-radius:12px;background:#1A2129;color:#E6C57A;"
                + "display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;flex:none}"
                + ".meta{flex:1;min-width:0}"
                + ".meta b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:600}"
                + ".meta span{display:block;direction:ltr;unicode-bidi:isolate;color:#ACB8C1;font-size:14px;white-space:nowrap}"
                + ".actions{display:flex;gap:6px;flex:none}"
                + ".actions a,.actions button{background:#2C3A34;color:#fff;border:1px solid #3d4d44;border-radius:12px;"
                + "padding:8px 12px;font-size:16px;text-decoration:none}"
                + "textarea,.pin{width:100%;box-sizing:border-box;background:#242B34;color:#fff;border:0;border-radius:14px;padding:14px;font-size:16px}"
                + ".pin{color:#E6C57A;font-size:28px;text-align:center;letter-spacing:6px}"
                + "button.gold{background:#E6C57A;color:#101419;font-weight:700;width:100%;margin-top:10px;"
                + "padding:14px;border:0;border-radius:14px;font-size:16px}"
                + "</style>";
    }

    private static String script(String download, String delete) {
        return "const pick=document.getElementById('pick');"
                + "const drop=document.getElementById('drop');"
                + "drop.onclick=()=>pick.click();"
                + "['dragover','dragenter'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.add('over');}));"
                + "['dragleave','drop'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.remove('over');}));"
                + "drop.addEventListener('drop',e=>sendFiles(e.dataTransfer.files));"
                + "pick.onchange=()=>sendFiles(pick.files);"
                + "function kind(name){const n=name.toLowerCase();"
                + "if(n.endsWith('.apk'))return 'APK';"
                + "if(n.endsWith('.mp4')||n.endsWith('.mkv')||n.endsWith('.webm'))return '▶';"
                + "if(n.endsWith('.jpg')||n.endsWith('.jpeg')||n.endsWith('.png')||n.endsWith('.webp'))return 'IMG';"
                + "if(n.endsWith('.mp3')||n.endsWith('.m4a'))return '♪';"
                + "if(n.endsWith('.txt')||n.endsWith('.pdf'))return 'TXT';"
                + "return 'FILE';}"
                + "function size(n){if(n<1024)return n+' B';if(n<1048576)return Math.round(n/1024)+' KB';"
                + "if(n<1073741824)return (n/1048576).toFixed(1)+' MB';return (n/1073741824).toFixed(2)+' GB';}"
                + "function when(ms){const d=new Date(ms);"
                + "const p=v=>String(v).padStart(2,'0');"
                + "return '\\u2066'+d.getFullYear()+'/'+p(d.getMonth()+1)+'/'+p(d.getDate())+' '+p(d.getHours())+':'+p(d.getMinutes())+'\\u2069';}"
                + "function sendFiles(files){const box=document.getElementById('jobs');box.innerHTML='';"
                + "const all=[].slice.call(files||[]);"
                + "(async()=>{for(const file of all){const job=document.createElement('div');job.className='job';"
                + "const name=document.createElement('div');name.className='name';name.textContent=file.name;"
                + "const track=document.createElement('div');track.className='track';"
                + "const bar=document.createElement('div');bar.className='bar';track.appendChild(bar);"
                + "job.appendChild(name);job.appendChild(track);box.appendChild(job);"
                + "await new Promise((ok,bad)=>{const fd=new FormData();fd.append('file',file);"
                + "const x=new XMLHttpRequest();x.open('POST','/upload');"
                + "x.upload.onprogress=e=>{if(e.lengthComputable)bar.style.width=(e.loaded*100/e.total)+'%';};"
                + "x.onload=()=>x.status===200?ok():bad();x.onerror=bad;x.send(fd);});"
                + "bar.style.width='100%';}listFiles();})().catch(()=>{});}"
                + "function listFiles(){fetch('/list').then(r=>r.json()).then(d=>{"
                + "const f=document.getElementById('files');f.innerHTML='';"
                + "(d.files||[]).forEach(x=>{const row=document.createElement('div');row.className='file';"
                + "const k=document.createElement('div');k.className='kind';k.textContent=kind(x.name);"
                + "const meta=document.createElement('div');meta.className='meta';"
                + "const b=document.createElement('b');b.textContent=x.name;"
                + "const s=document.createElement('span');s.textContent=size(x.size)+'  '+when(x.time);"
                + "meta.appendChild(b);meta.appendChild(s);"
                + "const actions=document.createElement('div');actions.className='actions';"
                + "const a=document.createElement('a');a.href='/files/'+encodeURIComponent(x.name);a.textContent='"
                + js(download) + "';"
                + "const del=document.createElement('button');del.type='button';del.textContent='" + js(delete) + "';"
                + "del.onclick=()=>fetch('/delete',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'name='+encodeURIComponent(x.name)}).then(listFiles);"
                + "actions.appendChild(a);actions.appendChild(del);"
                + "row.appendChild(k);row.appendChild(meta);row.appendChild(actions);f.appendChild(row);});"
                + "const n=document.getElementById('notes');n.innerHTML='';"
                + "(d.notes||[]).forEach(t=>{const row=document.createElement('div');row.className='file';row.textContent=t;n.appendChild(row);});"
                + "}).catch(()=>{});}"
                + "document.getElementById('noteSend').onclick=()=>{"
                + "fetch('/note',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'text='+encodeURIComponent(document.getElementById('note').value)}).then(()=>{"
                + "document.getElementById('note').value='';listFiles();});};"
                + "listFiles();setInterval(listFiles,4000);";
    }

    private static String js(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c");
    }
}
