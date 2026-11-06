"""Remote navigation regression suite for a disposable Android TV emulator.
Uses fixture_server.py; keeps existing app data and adds named QA sources.
"""
import argparse
import time
from pathlib import Path
from android_tv_suite import TvSuite, ACTIVITY, PACKAGE, SOURCE

class Audit(TvSuite):
    def dump(self):
        for attempt in range(3):
            try:
                return super().dump()
            except RuntimeError:
                if attempt == 2:
                    raise
                time.sleep(.3)

    def cold(self):
        self.run("shell", "am", "force-stop", PACKAGE)
        self.run("shell", "am", "start", "-W", "-n", ACTIVITY)
        self.wait(lambda r:self.has(r,"全屏","content-desc"))

    def manager(self):
        self.click("源管理", "content-desc")
        self.wait(lambda r:self.has(r,"添加源","content-desc"))

    def action(self, name, action):
        for _ in range(15):
            root=self.dump()
            labels=[n for n in root.iter("node") if n.get("text")==name]
            if labels:
                _,top,_,bottom=self.bounds(labels[0])
                candidates=[n for n in root.iter("node") if n.get("content-desc")==action]
                if candidates:
                    item=min(candidates,key=lambda n:abs((self.bounds(n)[1]+self.bounds(n)[3])/2-(top+bottom)/2))
                    if abs((self.bounds(item)[1]+self.bounds(item)[3])/2-(top+bottom)/2)<110:
                        self.tap(item)
                        return
            self.run("shell","input","swipe","1050","740","1050","370","250")
        raise AssertionError("Source action unavailable: "+name+" / "+action)

    def field(self,index,value):
        for attempt in range(3):
            super().field(index,value)
            root=self.dump()
            fields=[n for n in root.iter("node") if n.get("class")=="android.widget.EditText"]
            if len(fields)>index and fields[index].get("text")==value:
                return
        raise AssertionError("Text input did not commit")

    def ensure_source(self,name,endpoint):
        existing=next((src for src in self.library()["sources"] if src["name"]==name),None)
        if existing and existing["url"]==SOURCE+endpoint:
            return
        self.manager()
        if existing:
            self.action(name,"编辑源")
        else:
            self.click("添加源","content-desc")
        self.field(0,name)
        self.field(1,SOURCE+endpoint)
        self.dismiss_keyboard()
        self.click("保存","content-desc")
        self.wait(lambda r:self.has(r,"添加源","content-desc"))
        self.click("关闭","content-desc")

    def activate(self,name):
        src=next(src for src in self.library()["sources"] if src["name"]==name)
        if self.library()["activeSourceId"]!=src["id"]:
            self.manager()
            self.action(name,"启用")
        self.wait(lambda r:self.has(r,name) and self.has(r,"全屏","content-desc"))

    @staticmethod
    def row(root,number):
        return any(n.get("focused")=="true" and any(c.get("text")==f"{number:03}" for c in n.iter("node")) for n in root.iter("node"))

    @staticmethod
    def video(root):
        return any(n.get("focused")=="true" and n.get("content-desc")=="全屏" for n in root.iter("node"))

    def roundtrip(self,number,identifier):
        self.wait(lambda r:self.row(r,number))
        self.key(22)
        if not self.video(self.dump()):
            self.key(19)
        self.wait(self.video)
        self.key(21)
        self.wait(lambda r:self.row(r,number))
        self.record(identifier,f"列表第{number}项往返", "交互回归",f"离开第{number}项到视频，再按左键回到相同节目。",{"row":number})

    def focus(self):
        self.cold()
        self.wait(lambda r:self.row(r,1))
        self.record("Q01","冷启动首项焦点","交互回归","没有方向键干预，冷启动后首个节目获得焦点。")
        self.ensure_source("Audit 200","/many.m3u")
        self.ensure_source("Audit Main","/main.m3u")
        self.activate("Audit 200")
        self.wait(lambda r:self.row(r,1))
        self.record("Q02","切源首项焦点","交互回归","关闭源面板后，200节目源的第一项获得焦点。")
        self.roundtrip(1,"Q03")
        for _ in range(39): self.key(20)
        self.roundtrip(40,"Q04")
        self.key(22)
        self.wait(self.video)
        self.key(23)
        self.wait(lambda r:not self.has(r,"全屏","content-desc"))
        self.key(4)
        self.wait(self.video)
        self.key(21)
        self.wait(lambda r:self.row(r,40))
        self.record("Q05","全屏返回与列表定位","交互回归","第40项进入全屏，返回后先定位视频，再按左键回到第40项。")
        self.activate("Audit Main")
        self.wait(lambda r:self.row(r,1))
        self.record("Q06","长列表切回短列表","交互回归","从第40项切到5节目源，焦点落在新源首项，没有继承旧索引。")

    def flows(self):
        self.cold()
        self.activate("Audit Main")
        self.wait(lambda r:self.row(r,1))
        self.key(22)
        self.wait(self.video)
        self.key(23)
        self.wait(lambda r:self.has(r,"暂停","content-desc"))
        root=self.dump()
        assert any(n.get("focused")=="true" and any(c.get("content-desc")=="暂停" for c in n.iter("node")) for n in root.iter("node"))
        self.record("Q07","全屏播放控件默认焦点","交互回归","全屏播放成功，焦点在暂停按钮。")
        self.key(165)
        self.key(23)
        self.wait(lambda r:self.has(r,"已暂停"))
        self.record("Q08","确定键暂停","交互回归","按确定仅暂停，没有误触收藏。")
        self.key(126)
        self.key(20)
        self.wait(lambda r:self.has(r,"测试节目 B"))
        self.record("Q09","全屏下键切台","交互回归","从 A 切到 B，保持全屏。")
        time.sleep(7)
        self.key(19)
        self.wait(lambda r:self.has(r,"测试节目 A"))
        self.record("Q10","隐藏浮层上键切台","交互回归","浮层隐藏时上键切回 A 并显示信息。")
        self.key(4)
        self.wait(self.video)
        self.click("收藏节目","content-desc")
        self.wait(lambda r:self.has(r,"取消收藏","content-desc"))
        self.record("Q11","节目收藏","交互回归","主界面收藏当前节目后图标状态更新。")
        self.manager()
        self.action("收藏","启用")
        self.wait(lambda r:self.row(r,1))
        self.key(22)
        self.wait(self.video)
        self.key(23)
        self.wait(lambda r:not self.has(r,"全屏","content-desc"))
        self.key(165)
        self.click("取消收藏","content-desc")
        self.wait(lambda r:self.has(r,"全屏","content-desc") and self.has(r,"暂无收藏"))
        self.record("Q12","最后收藏移除后退出全屏","交互回归","在收藏播放中取消最后一个收藏，返回空收藏列表，没有滞留空白全屏。")
        self.tail()

    def tail(self):
        if not self.has(self.dump(),"IP 地址"):
            self.click("HTTP 代理","content-desc")
        self.wait(lambda r:self.has(r,"IP 地址"))
        self.key(4)
        self.wait(lambda r:self.has(r,"全屏","content-desc"))
        self.record("Q13","设置取消返回","交互回归","代理设置按返回关闭，未修改配置。")
        self.activate("Audit Main")
        self.click("测试节目 C")
        self.wait(lambda r:self.has(r,"线路不可用"))
        self.record("Q14","全部线路失败状态","交互回归","两条受控失败线路轮询后显示错误，界面仍可操作。")
        self.resume()

    def resume(self):
        self.wait(lambda r:self.has(r,"线路不可用"))
        before=sum(event["path"].startswith("/fail-c") for event in self.events())
        self.click("重试频道","content-desc")
        self.wait(lambda r:self.has(r,"线路不可用"))
        after=sum(event["path"].startswith("/fail-c") for event in self.events())
        assert after>before
        self.record("Q15","失败后重试","交互回归","重试确实重新请求失败线路，之后仍能切换节目。",{"additionalRequests":after-before})
        self.click("测试节目 B")
        self.wait(lambda r:self.has(r,"暂停","content-desc"))
        self.key(127)
        self.wait(lambda r:self.has(r,"已暂停"))
        self.key(3)
        self.run("shell","am","start","-W","-n",ACTIVITY)
        self.wait(lambda r:self.has(r,"已暂停"))
        self.record("Q16","后台返回保持暂停","交互回归","暂停后切到桌面再回应用，保持暂停。")
        self.key(126)
        self.wait(lambda r:self.has(r,"暂停","content-desc"))
        self.record("Q17","后台返回后恢复播放","交互回归","按播放键重新建立播放器并成功播放。")

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("stage",choices=["focus","flows","tail","resume"])
    args=parser.parse_args()
    audit=Audit(Path("app/build/outputs/qa"),"emulator-5580")
    getattr(audit,args.stage)()
