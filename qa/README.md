# Android TV 场景回归

此目录提供 ADB 场景驱动、受控播放源和截图图册生成器。只在可恢复的 Android TV 模拟器上运行，不要清空真实电视的数据。

测试分为真实仓库验证与受控场景。受控媒体为自行生成的测试信号，不使用第三方样片；本地服务只监听 127.0.0.1，模拟器通过 10.0.2.2 访问。故意构造 404、503、空列表、HTML、HLS 分片清单、读取延迟和迟到响应。

运行前必须停止应用并备份 files、databases、no_backup，备份路径为 `%TEMP%/iptv-qa-original-data.tar`。测试会新增/删除测试源、清理模拟器内调试应用数据、切换模拟器网络和显示尺寸。结束时执行 restore_emulator.py 恢复原数据和设置。

依次执行 android_tv_suite.py 的 real、forms、playback、volume、library_cases、source_edges、cache_cases、offline_cases、resolution_cases、worker_cases 阶段。每项会保存未经修改的 PNG、结果断言、测试数据和时间到 `app/build/outputs/qa`。首轮发现的问题保留修复前/后证据。

```powershell
python qa/fixture_server.py --media <本机生成的测试信号.mp4> --port 18764
python qa/android_tv_suite.py real
# 按上述阶段顺序继续运行
python qa/restore_emulator.py
python qa/build_gallery.py
```

完成后打开 `app/build/outputs/qa/index.html`。可按场景分组、搜索、点击放大、方向键翻页；`iptv-scenario-screenshots.zip` 包含图册和全部原始截图。scenarios.json 保存逐项数据，summary.json 汇总结果，restoration.json 记录恢复结果。

不能把模拟器验证等同于小米实体电视测试；强制执行日更任务不代表已经等待真实 24 小时。图册会列明这些边界。
