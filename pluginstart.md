### 现在进行插件部分
## 有两个方案但结果一致，我们只提供更简便的方案
# 直接编辑配置文件
打开服务器根目录，找到config.json文件
找到下列内容
```json
"
  // 是否启用代理（第二步骤的人要设置为true）
  "proxy_enabled": false,

  // 代理类型，SOCKS(不推荐) 或 HTTP(推荐)
  "proxy_type": "SOCKS",

  // 代理服务器地址(你云服IP)
  "proxy_host": "127.0.0.1",

  // 代理服务器端口(你前面云服的tinyproxy.conf端口(port)是啥就是啥)
  "proxy_port": 8888
"
```
```yml
现在为你解析
proxy_enabled 将其设置为true开启以下内容(如果你本地已经有了公网 IP，就完全不需要开代理。如果你向我一样用方法二你就要开)
  "proxy_enabled": true,

proxy_type 将其设置为http开启http代理即可
  "proxy_type": "HTTP",

proxy_host 将其设置为 你云服IP即可(例如云服ip164.157.465.14)
  "proxy_host": "164.157.465.14",

proxy_port 将其设置为8888(你前面云服的tinyproxy.conf端口(port)是啥就是啥)开启http代理
  "proxy_port": 8888
```

## 📋 使用须知
在完成所有配置后，于服务器控制台输入以下命令即可开放列表：

```cmd
publish start
```
⚠️ 重要提醒
为了维护良好的服务器列表环境，请您务必阅读并遵守以下约定：

- 未确认服务端稳定性时，请勿开放列表。
如果您尚不确定服务端能否正常运行或顺利开启游戏，请暂时不要公开列表。

- 严禁用于广告服或任何恶意用途。
本插件不欢迎任何形式的滥用。如果您需要此插件或API来开设广告服，请停止使用。

- 不建议大规模批量开放列表。
本插件主要服务于个人服主（腐竹）。大规模集中使用可能导致API通道堵塞，引发连接超时，影响所有人的体验。


### 全部配置解释
```json
{
  // 你的公网IP或域名。留空则使用内网IP占位符。
  "public_ip": "",
  // 公网端口，必须与端口映射/防火墙放行的端口一致。(内网穿透推荐端口和你的rukkit.yml中的port一致。)
  "public_port": 1145,
  // 服务器MOTD(地图描述)。留空则使用空字符串。
  "motd": "",
  // 房间名称。留空则自动使用Rukkit的serverName。
  "server_name": "",
  // 游戏版本字符串，可自定义(如1.15-AA, 1.15BB)。
  "version": "1.15-RW-HPS",
  // 游戏版本号（整数），固定为176即可。
  "version_int": 176,
  // 房间密码，"0"表示无密码。
  "password": "0",
  // 游戏模式，固定为"skirmishMap"。
  "game_mode": "skirmishMap",
  // 服务器状态，"battleroom"(等待中) 或 "ingame"(游戏中)。
  "game_status": "battleroom",
  // 最大玩家数。填-1则自动使用Rukkit的maxPlayer设置。
  "maxplayers": -1,
  // 是否将MOTD作为地图名称显示。true=同步，false=使用房间名作为地图名。
  "sync_motd_to_map": true,
  // 是否启用代理（配合云服务器可将房间状态从 L 变为 Y）
  "proxy_enabled": false,
  // 代理类型，SOCKS 或 HTTP
  "proxy_type": "SOCKS",
  // 代理服务器地址（填写你的云服务器公网 IP）
  "proxy_host": "127.0.0.1",
  // 代理服务器端口
  "proxy_port": 1080
}
```
详解
```json
public_ip
```
指你想向官方报告你的列表服的IP是什么(不含端口)，推荐与公网IP一致

有公网的或者在云服上的:填写你的公网IP

用内网穿透并方案二云服代理的:填写你的云服公网IP，并且你的frp穿透的IP要与云服公网IP一致。(也就是说你的代理和frp全部必须要在同一个云服上!!!)

指令控制也可:`publish ip <你的IP>`

```json
public_port
```
与你的RUKKIT设置的端口一致，且与你的frp(tpc,udp)穿透的端口一致

指令:`publish port <你的端口>`
```json
motd
```
填写你的motd(也叫地图名map)

指令:`publish motd <你的motd>`
```json
server_name
```
就是你的服务器名字(也叫房主名)

指令:`publish name <服务器名字>`
```json
version
```
这个是列表上展示的你的服务器版本号

比如：一般开RELAY都用R房，R房的版本号就是1.15-R。但是实际上仅作为展示，并不会影响你的正常进入。

推荐默认值，或者改成1.15即可

命令:`publish version <版本号>`
```json
version_int
```
不要乱改，默认值即可。此为游戏的协议号(可能也叫版本包，但是整数形式)
```json
game_mode
```
保持默认即可
```json
game_status
```
"battleroom"(等待中/战役室) 或 "ingame"(游戏中)

由于当前的插件还是测试版所以没能同步游戏内的游戏进度，后续会逐步优化。
```json
maxplayers
```
对外公开你的最大人数是多少 -1跟随RW-HPS设置。

命令:`publish maxplayers <数量>`

```json
sync_motd_to_map
```
插件的实现是坏的别用。手动改成`false`

命令:`publish syncmap`(输入进行直接切换)
```yml
现在为你解析
proxy_enabled 将其设置为true开启以下内容(如果你本地已经有了公网 IP，就完全不需要开代理。如果你向我一样用方法二你就要开)
  "proxy_enabled": true,

proxy_type 将其设置为http开启http代理即可
  "proxy_type": "HTTP",

proxy_host 将其设置为 你云服IP即可(例如云服公网ip164.157.465.14)
  "proxy_host": "164.157.465.14",

proxy_port 将其设置为8888(你前面云服的tinyproxy.conf端口(port)是啥就是啥)开启http代理
  "proxy_port": 8888
```
### ❤️ 写在最后
感谢您选择 touplist-plugins！
希望它能帮助您的服务器被更多玩家发现。祝您游戏愉快！
