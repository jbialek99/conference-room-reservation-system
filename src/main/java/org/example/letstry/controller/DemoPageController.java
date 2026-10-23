package org.example.letstry.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

@Controller
@ConditionalOnProperty(name = "booking.mode", havingValue = "demo")
public class DemoPageController {

    @GetMapping(value = "/demo", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> demo(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
        String token = csrf == null ? "" : HtmlUtils.htmlEscape(csrf.getToken());
        String html = """
                <!doctype html>
                <html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="csrf-token" content="__CSRF_TOKEN__"><title>Conference - Demo</title>
                <style>
                body{font:16px system-ui,sans-serif;max-width:850px;margin:45px auto;padding:0 20px;color:#172331;background:#f8fafc}
                h1{margin-bottom:8px}p{color:#536273}section{background:white;padding:24px;margin-top:18px;border:1px solid #dce2e8;border-radius:12px}
                label{display:block;margin:13px 0 5px}input,select,button{font:inherit;padding:10px;border:1px solid #c5cdd7;border-radius:7px}
                input,select{max-width:100%;box-sizing:border-box}button{background:#172331;color:white;cursor:pointer;margin:12px 6px 0 0}
                li{margin:10px 0}#message{white-space:pre-wrap} .danger{background:#842c3a}
                </style></head><body>
                <h1>Conference Room Reservation - DEMO</h1><p>Tryb lokalny, bez Microsoft Graph. Konta: demo1 / demo2. Haslo ustawia administrator lokalnego DEMO.</p>
                <form method="post" action="/logout"><input type="hidden" name="_csrf" value="__CSRF_TOKEN__"><button>Wyloguj</button></form>
                <section><h2>Nowa rezerwacja</h2>
                <label for="hall">Sala</label><select id="hall"></select>
                <label for="title">Tytul</label><input id="title" value="Spotkanie demonstracyjne">
                <label for="start">Od</label><input id="start" type="datetime-local">
                <label for="end">Do</label><input id="end" type="datetime-local">
                <div><button id="reserve">Zarezerwuj</button><button id="refresh">Odswiez kalendarz</button></div></section>
                <section><h2>Rezerwacje sali</h2><ul id="events"></ul></section><section><h2>Status</h2><pre id="message"></pre></section>
                <script>
                const csrf=document.querySelector('meta[name="csrf-token"]').content;
                const hall=document.getElementById('hall'),events=document.getElementById('events'),message=document.getElementById('message');
                const headers={'Content-Type':'application/json','X-CSRF-TOKEN':csrf};
                async function api(url,options={}){const r=await fetch(url,options);const body=await r.text();if(!r.ok)throw Error('HTTP '+r.status+': '+body);try{return JSON.parse(body)}catch{return body}}
                function show(x){message.textContent=typeof x==='string'?x:JSON.stringify(x,null,2)}
                async function loadHalls(){const rooms=await api('/api/demo/halls');hall.replaceChildren();for(const room of rooms){const option=document.createElement('option');option.value=room.id;option.textContent=room.name;hall.append(option)}await loadEvents()}
                async function loadEvents(){events.replaceChildren();if(!hall.value)return;const items=await api('/api/reservations/hall/'+encodeURIComponent(hall.value));for(const item of items){const li=document.createElement('li');li.append(document.createTextNode(item.title+' | '+item.start+' - '+item.end+' '));const btn=document.createElement('button');btn.className='danger';btn.textContent='Anuluj';btn.onclick=async()=>{try{show(await api('/api/reservations/delete/'+encodeURIComponent(item.id),{method:'DELETE',headers}));await loadEvents()}catch(error){show(error.message)}};li.append(btn);events.append(li)}}
                document.getElementById('reserve').onclick=async()=>{try{const title=document.getElementById('title').value,start=document.getElementById('start').value,end=document.getElementById('end').value;if(!start||!end)throw Error('Wybierz godziny.');const body={title,startMeeting:new Date(start).toISOString(),endMeeting:new Date(end).toISOString()};show(await api('/api/reservations/hall/'+encodeURIComponent(hall.value)+'/reserve',{method:'POST',headers,body:JSON.stringify(body)}));await loadEvents()}catch(error){show(error.message)}};
                document.getElementById('refresh').onclick=()=>loadEvents().catch(e=>show(e.message));hall.onchange=()=>loadEvents().catch(e=>show(e.message));
                loadHalls().catch(e=>show(e.message));
                </script></body></html>
                """;
        return ResponseEntity.ok(html.replace("__CSRF_TOKEN__", token));
    }
}