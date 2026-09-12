from __future__ import annotations


def install(backend) -> None:
    """Small post-render UX polish kept separate from the core V1.2 layer."""
    if getattr(backend, "_watch_align_v120_patch_installed", False):
        return
    backend._watch_align_v120_patch_installed = True
    html_path = backend.STATIC_DIR / "v1.html"
    js_path = backend.STATIC_DIR / "v1-full.js"
    html = html_path.read_text(encoding="utf-8")
    html = html.replace(
        '<div id="preflightAdvice" class="muted" style="margin-top:8px"></div></div>',
        '<div id="preflightAdvice" class="muted" style="margin-top:8px"></div><div id="photoPreview" class="guideWrap hidden" style="margin-top:12px"><img id="photoPreviewImg" alt="Photo framing preview" style="max-width:260px;max-height:260px;display:block;border-radius:12px"><div class="muted" style="font-size:12px;margin-top:5px">Framing guide: keep the watch head inside the dashed oval and as straight to camera as possible.</div></div></div>'
    )
    html_path.write_text(html, encoding="utf-8")

    js = js_path.read_text(encoding="utf-8")
    js = js.replace(
        "$('candidate').onchange=()=>{const f=$('candidate').files[0];if(f)preflight(f)};",
        "$('candidate').onchange=()=>{const f=$('candidate').files[0];if(f){$('photoPreviewImg').src=URL.createObjectURL(f);$('photoPreview').classList.remove('hidden');preflight(f)}};"
    )
    js = js.replace(
        "function regionVerdict(name,conf,reliable=true){const text=!reliable?'Cannot judge':conf==='high'?'Looks good':conf==='medium'?'Check visually':'Possible issue';return `<div class=\"region\"><b>${name}</b><div class=\"${!reliable?'warn':cls(conf)}\">${text}</div></div>`}",
        "function regionVerdict(name,conf,reliable=true,issue=false){const text=!reliable||conf==='low'?'Cannot judge':issue?'Possible issue':conf==='high'?'Looks good':'Check visually';const cc=!reliable||conf==='low'?'warn':issue?'bad':cls(conf);return `<div class=\"region\"><b>${name}</b><div class=\"${cc}\">${text}</div></div>`}"
    )
    old = "$('regions').innerHTML=regionVerdict('Dial',regions.dial,regions.dial!=='low')+regionVerdict('Hour markers',regions.markers,markerReliable)+regionVerdict('Bezel',regions.bezel,m.bezel?.reliable!==false)+((m.date_window)?regionVerdict('Date / cyclops',regions['date/cyclops'],m.date_window?.reliable!==false):'');"
    new = "const markerIssue=(m.markers||[]).some(x=>x.reliable!==false&&x.angular_error_deg!=null&&Math.abs(x.angular_error_deg)>1.0);const bezelIssue=m.bezel?.reliable!==false&&m.bezel?.offset_deg!=null&&Math.abs(m.bezel.offset_deg)>1.0;const dateIssue=m.date_window?.reliable!==false&&m.date_window?.x_offset_percent!=null&&(Math.abs(m.date_window.x_offset_percent)>3||Math.abs(m.date_window.y_offset_percent||0)>3);$('regions').innerHTML=regionVerdict('Dial',regions.dial,regions.dial!=='low',false)+regionVerdict('Hour markers',regions.markers,markerReliable,markerIssue)+regionVerdict('Bezel',regions.bezel,m.bezel?.reliable!==false,bezelIssue)+((m.date_window)?regionVerdict('Date / cyclops',regions['date/cyclops'],m.date_window?.reliable!==false,dateIssue):'');"
    js = js.replace(old, new)
    js_path.write_text(js, encoding="utf-8")
