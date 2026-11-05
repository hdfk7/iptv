"""Build an offline image gallery from untouched Android screencaps."""

import argparse
import html
import json
import zipfile
from pathlib import Path


LIMITATIONS = [
    "没有连接小米实体电视：原厂遥控器、真实 HDMI/CEC 与固定音量路由未实测。",
    "后台任务核对了 24 小时间隔并强制触发；没有实际等待完整 24 小时。",
    "模拟器以无音频输出模式运行：验证系统音量数值，未验证实际听感。",
    "存储异常通过损坏测试 JSON、临时移除测试目录写权限模拟；未制造真实磁盘满。",
    "真实源验证属于抽样播放，不代表仓库每个节目或每条地址长期可用。",
]


def build(output):
    records = json.loads((output / "scenarios.json").read_text(encoding="utf-8"))
    records.sort(key=lambda record: record["id"])
    visible = [record for record in records if not record["id"].startswith("FAIL-")]
    for record in visible:
        assert (output / record["screenshot"]).is_file()
    counts = {status: sum(record["status"] == status for record in visible) for status in ["passed", "failed", "blocked", "fixed"]}
    summary = {"scenarios": len(visible), **counts, "screenshots": len(visible), "limitations": LIMITATIONS}
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    data = json.dumps(visible, ensure_ascii=False).replace("</", "<\\/")
    cards = []
    for index, record in enumerate(visible):
        title = html.escape(record["title"])
        identifier = html.escape(record["id"])
        image = html.escape(record["screenshot"])
        kind = "真实直播源" if record["kind"] == "real" else "受控测试数据"
        details = "".join(f"<dt>{html.escape(str(key))}</dt><dd>{html.escape(str(value))}</dd>" for key, value in record["data"].items())
        cards.append(f'''<article data-index="{index}" data-group="{html.escape(record['group'])}" data-search="{title} {identifier}" data-status="{record['status']}">
<button class="picture" onclick="show({index})"><img src="{image}" loading="lazy" alt="{title}"></button>
<div class="body"><div class="meta"><span>{identifier} · {html.escape(record['group'])}</span><b class="{record['status']}">{ {'passed':'通过','fixed':'已修复','failed':'失败','blocked':'未完成'}.get(record['status'],record['status']) }</b></div>
<h2>{title}</h2><p class="kind">{kind} · {html.escape(record['capturedAt'])}</p><p>{html.escape(record['observation'])}</p><dl>{details}</dl></div></article>''')
    template = '''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>iptv 场景测试图册</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#0b1220;color:#e8eff8;font:15px/1.6 'Segoe UI','Microsoft YaHei',sans-serif}header{padding:38px 4vw 25px;background:linear-gradient(125deg,#173447,#111a2b)}h1{margin:0;font-size:30px}header p{color:#a8bbce;margin:10px 0}strong{color:#83e4cc}.stats{display:flex;gap:24px;margin-top:18px}.stats b{font-size:26px;margin-right:6px;color:#83e4cc}nav{position:sticky;top:0;z-index:2;background:#101b2ceF;backdrop-filter:blur(12px);padding:14px 4vw;display:flex;gap:12px;align-items:center;border-bottom:1px solid #29384d}select,input,button{font:inherit}select,input{color:#edf5ff;background:#17253a;border:1px solid #38516a;border-radius:9px;padding:8px 12px}input{flex:1;min-width:100px}main{padding:24px 4vw;display:grid;grid-template-columns:repeat(auto-fit,minmax(370px,1fr));gap:22px}article{overflow:hidden;background:#142035;border:1px solid #2a3b50;border-radius:15px}article[hidden]{display:none}.picture{display:block;width:100%;border:0;padding:0;cursor:zoom-in;background:#050a11;aspect-ratio:16/9}.picture img{width:100%;height:100%;object-fit:contain}.body{padding:17px}.meta{display:flex;justify-content:space-between;color:#93aac3;font-size:12px}b.passed{color:#83e4cc}b.failed{color:#ff9c9c}h2{font-size:19px;margin:9px 0}.kind{font-size:11px;color:#91a4bd}.body p{color:#c1cedd;font-size:13px}dl{display:grid;grid-template-columns:auto 1fr;gap:2px 12px;font-size:12px;color:#a9bed3}dt{color:#8ea2bd}dd{margin:0;word-break:break-word}aside{padding:22px 4vw 38px;color:#adbdce}aside h2{font-size:19px}aside li{margin:5px 0}dialog{padding:0;border:1px solid #456079;border-radius:14px;width:min(95vw,1600px);max-height:96vh;color:white;background:#101b2c}dialog::backdrop{background:#000d}dialog img{width:100%;max-height:75vh;object-fit:contain;display:block;background:#000}.dialog-footer{padding:14px 20px;display:flex;justify-content:space-between;gap:14px;align-items:center}dialog button{background:#223a4e;border:1px solid #51738a;color:white;border-radius:8px;padding:7px 14px;cursor:pointer}.caption{flex:1}.caption small{display:block;color:#a9bbd0}#remaining{color:#9dafc5;white-space:nowrap}
</style></head><body><header><h1>iptv · 场景测试图册</h1><p>Android 14 TV 模拟器 · 原始截图未修改 · 点击图片放大，方向键翻页</p><div class="stats"><span><b>__TOTAL__</b>场景 / 截图</span><span><b>__PASS__</b>通过</span><span><b>__FAIL__</b>失败</span></div></header>
<nav><select id="group" onchange="filter()"><option value="">全部界面</option></select><select id="status" onchange="filter()"><option value="">全部结果</option><option value="passed">通过</option><option value="failed">失败</option></select><input id="search" placeholder="搜索：收藏、全屏、超时、断网……" oninput="filter()"><span id="remaining"></span></nav>
<main>__CARDS__</main><aside><h2>覆盖边界</h2><ul>__LIMITS__</ul><p>受控场景使用本机生成的测试信号与故意设置的错误接口；不会把彩条测试图当作真实直播成功。真实仓库验证单独标记。</p><p>结构化数据：<a href="scenarios.json">scenarios.json</a> · <a href="summary.json">summary.json</a></p></aside>
<dialog id="zoom"><img id="large"><div class="dialog-footer"><button onclick="move(-1)">上一张</button><div class="caption" id="caption"></div><button onclick="move(1)">下一张</button><button onclick="document.getElementById('zoom').close()">关闭</button></div></dialog>
<script>
const records=__DATA__;let current=0;const groups=[...new Set(records.map(r=>r.group))];for(const name of groups){const option=document.createElement('option');option.value=name;option.textContent=name;document.getElementById('group').append(option)}
function filter(){const group=document.getElementById('group').value,status=document.getElementById('status').value,text=document.getElementById('search').value.toLowerCase();let count=0;for(const card of document.querySelectorAll('article')){const visible=(!group||card.dataset.group===group)&&(!status||card.dataset.status===status)&&card.dataset.search.toLowerCase().includes(text);card.hidden=!visible;if(visible)count++}document.getElementById('remaining').textContent=`${count} / ${records.length}`}
function show(index){current=index;const record=records[index];document.getElementById('large').src=record.screenshot;const caption=document.getElementById('caption');caption.textContent=record.id+' · '+record.title;const small=document.createElement('small');small.textContent=record.observation;caption.append(small);const dialog=document.getElementById('zoom');if(!dialog.open)dialog.showModal()}
function move(direction){const indices=[...document.querySelectorAll('article:not([hidden])')].map(card=>Number(card.dataset.index));if(indices.length)show(indices[(indices.indexOf(current)+direction+indices.length)%indices.length])}document.addEventListener('keydown',event=>{if(document.getElementById('zoom').open){if(event.key==='ArrowRight')move(1);if(event.key==='ArrowLeft')move(-1)}});filter();
</script></body></html>'''
    content = template.replace("__TOTAL__", str(len(visible))).replace("__PASS__", str(counts["passed"])).replace("__FAIL__", str(counts["failed"]))
    content = content.replace("__CARDS__", "\n".join(cards)).replace("__LIMITS__", "".join("<li>" + html.escape(item) + "</li>" for item in LIMITATIONS)).replace("__DATA__", data)
    (output / "index.html").write_text(content, encoding="utf-8")
    with zipfile.ZipFile(output.parent / "iptv-scenario-screenshots.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for filename in ["index.html", "scenarios.json", "summary.json"]:
            archive.write(output / filename, filename)
        for record in visible:
            archive.write(output / record["screenshot"], record["screenshot"])
    print(json.dumps(summary, ensure_ascii=True))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path, nargs="?", default=Path("app/build/outputs/qa"))
    build(parser.parse_args().output)
