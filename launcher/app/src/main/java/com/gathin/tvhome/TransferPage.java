package com.gathin.tvhome;

/** The phone page. Arabic first, with the English words beside the actions. */
final class TransferPage {
    private TransferPage() {}

    static String pin() {
        return "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>نقل الملفات</title>" + style() + "</head><body>"
                + "<h1>نقل الملفات</h1><p>اكتب رمز الدخول الظاهر على التلفاز.</p>"
                + "<form method=\"get\"><input name=\"p\" inputmode=\"numeric\" maxlength=\"6\" "
                + "placeholder=\"PIN\" autofocus><button>دخول</button></form></body></html>";
    }

    static String app() {
        return "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>نقل الملفات</title>" + style() + "</head><body>"
                + "<h1>نقل الملفات</h1>"
                + "<p>اختر الملفات ثم ارفع. يمكن اختيار أكثر من ملف، بما فيها الفيديو الكبير.</p>"
                + "<input id=\"pick\" type=\"file\" multiple>"
                + "<button id=\"send\" type=\"button\">رفع / Upload</button>"
                + "<progress id=\"bar\" value=\"0\" max=\"100\"></progress>"
                + "<p id=\"status\"></p>"
                + "<h2>الملفات</h2><ul id=\"files\"></ul>"
                + "<h2>نص أو رابط</h2>"
                + "<textarea id=\"note\" rows=\"3\" placeholder=\"نص أو رابط\"></textarea>"
                + "<button id=\"noteSend\" type=\"button\">إرسال إلى التلفاز</button>"
                + "<ul id=\"notes\"></ul>"
                + "<script>" + script() + "</script></body></html>";
    }

    private static String style() {
        return "<style>body{font-family:sans-serif;margin:16px;background:#101419;color:#fff}"
                + "input,textarea,button{font-size:18px;margin:8px 0;width:100%;box-sizing:border-box}"
                + "button{padding:12px;background:#2a3440;color:#fff;border:0;border-radius:8px}"
                + "progress{width:100%;height:18px}li{margin:10px 0}a{color:#9cf}</style>";
    }

    private static String script() {
        return "function list(){fetch('/list').then(r=>r.json()).then(d=>{"
                + "const f=document.getElementById('files');f.innerHTML='';"
                + "(d.files||[]).forEach(x=>{const li=document.createElement('li');"
                + "const a=document.createElement('a');a.href='/files/'+encodeURIComponent(x.name);"
                + "a.textContent=x.name+' ('+x.size+')';li.appendChild(a);"
                + "const b=document.createElement('button');b.textContent='حذف';"
                + "b.onclick=()=>fetch('/delete',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'name='+encodeURIComponent(x.name)}).then(list);li.appendChild(b);f.appendChild(li);});"
                + "const n=document.getElementById('notes');n.innerHTML='';"
                + "(d.notes||[]).forEach(t=>{const li=document.createElement('li');li.textContent=t;n.appendChild(li);});"
                + "}).catch(()=>{});}"
                + "document.getElementById('send').onclick=async()=>{"
                + "const files=document.getElementById('pick').files;const bar=document.getElementById('bar');"
                + "for(let i=0;i<files.length;i++){const fd=new FormData();fd.append('file',files[i]);"
                + "await new Promise((ok,bad)=>{const x=new XMLHttpRequest();x.open('POST','/upload');"
                + "x.upload.onprogress=e=>{if(e.lengthComputable)bar.value=e.loaded*100/e.total;};"
                + "x.onload=()=>x.status===200?ok():bad();x.onerror=bad;x.send(fd);});"
                + "document.getElementById('status').textContent=files[i].name;}"
                + "list();};"
                + "document.getElementById('noteSend').onclick=()=>{"
                + "fetch('/note',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'text='+encodeURIComponent(document.getElementById('note').value)}).then(()=>{document.getElementById('note').value='';list();});};"
                + "list();setInterval(list,4000);";
    }
}
