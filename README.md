# touplist-plugin

> [!CAUTION]
> **法律声明 & 免责条款**
>
> 1. **“按原样”提供 (AS IS)**：本插件仅供学习和研究使用，按“现状”提供，**不提供任何形式的明示或暗示担保**，包括但不限于适销性、特定用途适用性及不侵权的担保。
> 2. **使用风险自担**：因使用本插件导致的任何直接、间接、偶然、特殊或惩罚性损害（包括但不限于服务器崩溃、数据丢失、游戏账号封禁、商业利益损失），**作者及贡献者均不承担任何责任**。
> 3. **合规性**：本项目因上游许可证（GPL/AGPL）要求强制开源。若您为商业服主或涉及大规模公开运营，请自行评估合规性，作者不对此提供任何保证。
> 4. **侵权处理**：若您认为本项目的部分代码或资源侵犯了您的合法权益，请仅通过 **dongserver@126.com** 书面联系，我们将在核实后 72 小时内处理。请勿直接向平台发起无差别举报，以免误伤合规项目。
> 5. **适用法律**：本声明及使用条款适用中华人民共和国法律，如有争议，由作者所在地法院管辖。
>
> **重要提示**：本插件由 AI 辅助编写，作者对代码逻辑的正确性不做任何保证，且**仅面向个人服主“折腾”场景**，不建议生产环境或商业化使用。**若您下载、复制或使用本插件，即视为已完全理解并接受本免责声明的全部条款。**

一个用于 RW-HPS 的铁锈战争列表公布插件，基于 API 代理实现稳定上列表。  
本插件改编自 [example-uplist-plugin](https://github.com/RukkitDev/example-uplist-plugin) 插件，添加了配置持久化、热重载、同步地图等功能。

由于本项目由 豆包 + TraeAI + DeepSeek 协助编写，如有 许可证 问题请联系 **dongserver@126.com**，我会立即处理。请不要直接举报，谢谢！

---

## ✨ 功能特性

- **配置持久化**：支持 `config.json` 配置文件（带注释），所有参数可热重载
- **自定义版本号**：支持自定义游戏版本显示（如 `1.15-UR`、`1.15-FG`）
- **MOTD 同步开关**：可选择 MOTD 是否作为地图名称显示
- **代理支持**：内置 SOCKS5/HTTP 代理，解决内网穿透时的端口自检问题

---

## 📦 编译

在 Gradle 环境下执行：
```shell
gradle shadowJar
```
编译时请把RW-HPS的 Server-All.jar 放在 touplist根目录/libs 内
将 `build/libs/` 下生成的 jar 文件放入 Rukkit 服务器的 `plugins` 目录即可。

## 🔧 使用教程

### 第一步：悉知你需要的 API 
插件本身不直接向官方发送请求，而是先找 API 要一个“签名后的请求串”，然后插件拿着这个串自己去和官方沟通。

> **我开放了 API 吗？**  
> 目前列表环境比较乱，为了避免被滥用，请像我一样的个人服主前往uplist文件夹申请 API 地址的。或者你可以参考文末的资料自己搭一个。

> [!WARNING]
> 注意，目前api正在内测暂不开放.此插件暂时不可公开用。

---

### 第二步：配置并启动插件
1. 把编译好的插件 jar 放到 RW-HPS 的 `plugins` 文件夹，启动服务器。
2. 首次运行时，插件会自动在服务器根目录生成 `config.json` 配置文件，你可以直接打开修改，保存后用 `publish reload` 热加载。
3. 修改 `config.json` 中的 `public_ip` 和 `public_port` 为你自己的公网 IP(或与代理同ip的内网穿透，文后会讲)/域名和端口。

**`config.json` 默认内容**：
```json
// ===================================================================
//  不要试图更改格式，请更改能更改的区域
//  (开过minecraft的腐竹们不可能不知道吧:)
//  任何更改其格式都可能导致插件爆炸
// ===================================================================
//
//  可用指令：
//   publish start               启动列表公开
//   publish stop                关闭列表公开
//   publish reload              热重载此配置文件
//   publish ip <公网IP或域名>    设置你的公网IP地址
//   publish port <端口号>       设置公网映射端口
//   publish motd <内容>         设置服务器MOTD(地图描述)
//   publish name <房间名>       设置服务器房间名称
//   publish version <版本号>    设置游戏版本字符串(如1.15-Uplistpluginsreload)
//   publish maxplayers <数量>   设置最大玩家数(-1为自动计算)
//   publish syncmap             切换MOTD是否作为地图名显示
//   publish proxy <SOCKS|HTTP> <主机> [端口]  设置代理
//   publish proxy off           关闭代理
//   publish state               查看当前公开状态与配置
//   publish help                显示此帮助信息
//
{
  // 你的公网IP或域名。留空则使用内网IP占位符。
  "public_ip": "",
  // 公网端口，必须与端口映射/防火墙放行的端口一致。(内网穿透推荐端口和你的rukkit.yml中的port一致。)
  "public_port": 5123,
  // 服务器MOTD(地图描述)。留空则自动使用Rukkit默认MOTD。
  "motd": "",
  // 房间名称。留空则自动使用Rukkit的serverUser。
  "server_name": "",
  // 游戏版本字符串，可自定义(如1.15-AA, 1.15BB)。
  "version": "1.15-Uplistpluginsreload",
  // 游戏版本号（整数），固定为176即可。
  "version_int": 176,
  // 房间密码，"0"表示无密码。
  "password": "0",
  // 游戏模式，固定为"skirmishMap"。
  "game_mode": "skirmishMap",
  // 服务器状态，"battleroom"(等待中) 或 "ingame"(游戏中)。
  "game_status": "battleroom",
  // 最大玩家数。填-1则自动使用Rukkit的maxPlayer * maxRoom计算。
  "maxplayers": -1,
  // 是否将MOTD作为地图名称显示。true=同步，false=使用房间名作为地图名。
  "sync_motd_to_map": true,
  // 是否启用代理（解决内网穿透时端口自检失败的问题）(目前由于1.15api更新了导致此方法失效)
  "proxy_enabled": false,
  // 代理类型，SOCKS 或 HTTP
  "proxy_type": "SOCKS",
  // 代理服务器地址
  "proxy_host": "127.0.0.1",
  // 代理服务器端口
  "proxy_port": 1080
}
```

---

### 第三步：公网及内网穿透配置（必读）

#### 🟢 情况一：你的服务器有独立公网 IP
直接在游戏控制台执行：
```shell
publish ip 你的公网IP          # 例如 123.45.67.89
publish port 你的游戏端口      # 例如 5123
publish start                  # 启动公开
```
同时请确保你的防火墙/安全组已放行该端口的 **TCP 和 UDP** 协议。  
这种情况下**无需开启代理**（`proxy_enabled` 保持 `false` 即可）。

---

#### 🔵 情况二：拥有一台有公网IP的云服务器（VPS）—— 彻底解决端口自检失败，房间状态变 Y

如果你已经拥有或准备购买一台有独立公网 IP 的云服务器（例如甲骨文永久免费、阿里云/腾讯云学生机、阿贝云免费云服务器），就可以通过 SOCKS5/HTTP 代理让官方自检顺利通过。即使游戏服务器仍然在你家里的电脑上，也可以通过 FRP 内网穿透配合代理实现 Y 公网状态。

**整体思路**：

1. 在云服务器上搭建一个 SOCKS5/HTTP 代理。
2. 把你家里的游戏端口通过 FRP 映射到云服务器的公网端口。
3. 配置插件使用云服务器的 SOCKS5/HTTP 代理。
4. 插件所有请求（包括端口自检）都会从云服务器发出，官方看到的源 IP 就是云服务器的 IP，而这个 IP 的游戏端口已经通过 FRP 开放，自检成功，房间状态 由 L 变 Y 。

**详细步骤**：

详见 本仓库内 startubantu.md 文件

> **注意**：如果你已经拥有云服务器，也可以直接将 Rukkit 安装在云服务器上运行，那样连 FRP 都不需要，直接 `publish ip 云服务器IP`，`publish port 游戏端口`，开启或不开启代理均可，房间直接就是 Y 状态。

---

## 🎮 控制台命令

| 命令 | 说明 |
|------|------|
| `publish start` | 启动列表公开 |
| `publish stop` | 停止列表公开 |
| `publish reload` | 热重载配置文件 |
| `publish ip <公网IP或域名>` | 设置公网 IP |
| `publish port <端口号>` | 设置公网端口 |
| `publish motd <内容>` | 设置服务器 MOTD(地图描述) |
| `publish name <房间名>` | 设置房间名称 |
| `publish version <版本号>` | 设置游戏版本字符串(如1.15-FG) |
| `publish maxplayers <数量>` | 设置最大玩家数(-1为自动计算) |
| `publish syncmap` | 切换 MOTD 是否作为地图名显示 |
| `publish proxy <SOCKS\|HTTP> <主机> [端口]` | 设置代理 |
| `publish proxy off` | 关闭代理 |
| `publish state` | 查看当前公开状态与配置 |
| `publish help` | 显示帮助信息 |

---

## 🙏 特别鸣谢

感谢 api.data.der.kim ([@deng-rui](https://github.com/deng-rui)) 提供有关列表公开的 API 支持。  
感谢 RELAY-CN 的开源脚本提供的签名算法参考。([FakeListRoom](https://github.com/RELAY-CN/FakeListRoom/blob/main/fake.sh))  
感谢 chmlfrp 提供的内网穿透服务。([chmlfrp](https://chmlfrp.net/))  
感谢 corrodinggames 提供的 API 服务器列表。  
感谢 ([Rukkit 提供的演示插件](https://github.com/RukkitDev/example-uplist-plugin))，本插件基于此插件修改。  

由于我是用豆包+TraeAI+DeepSeek编写的，如有许可问题请联系我更改或删除！不要先举报，谢谢！

（以上内容仅用于说明，不构成任何法律或合同关系。）





















