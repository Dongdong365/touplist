#### 附上🔵 情况二：拥有一台有公网IP的云服务器的方案(实操，我已经跟着走一遍了实测能用)

本文中的云服务器指崭新的，没装系统的云服务器，请注意备份，本文中可能有部分破坏性内容，请注意甄别！本文不做任何担保.

首先你需要拥有一个云服务器，一定是独享公网ipv4的ip，不能是共享的ipv4，不能ipv6

然后你需要在云服务器上安装`windoesserver2019`

## 🔐 安装完成后，登录

[登录云服务器-Nitwiki](https://nitwikit.8aka.org/general/process/deploy/connect-to-the-cloud/#windows)

可能会有提示，你直接点击允许即可。

2. 3proxy
免费开源、单 exe、原生 Windows、轻量，完美实现同等功能。

官网下载：https://3proxy.org/

访问不了可以用GITHUB下载：https://github.com/3proxy/3proxy

(注意下载的时候记得关杀毒软件要不然会丢失重要文件)

在`\3proxy\bin64`下新建配置文件 proxy.cfg

```ini
# DNS设置
nserver 8.8.8.8
nscache 65536

# 超时配置（对应tinyproxy Timeout 600）
timeouts 1 5 30 60 180 1800 15 600

# 监听全部网卡，开启HTTP代理 8888端口，无认证
proxy -p8888 -a

# 访问规则：允许所有人CONNECT 80,443,5123
allow * * 80
allow * * 443
allow * * 5123

# 禁止访问其他所有端口（安全加固）
deny * *
```
cmd 启动

点击路径栏输入`cmd`回车
```bat
3proxy.exe proxy.cfg
```
恭喜你完成代理搭建！现在进行下一步

## 启动frp服务端(射影RW-HPS端口到云服)
一、云服务器端—— 安装 FRP 服务端
1. 下载 FRP(下载慢是正常的)(非常慢或超时可以去BING搜github镜像站)（使用 专用于下载Release、压缩包 的镜像链接）

> 例如将https://github.com/fatedier/frp/releases/download/v0.57.0/frp_0.57.0_linux_amd64.tar.gz 复制并 转换链接 即可得到 加速拉取链接

2. 解压

3. 云服务器端：修改 frps.toml

```yml
bindPort = 7000
auth.token = "你自己编一个复杂的密码，比如 x8Fk2Lp9qR5tY"
```

 启动 FRP (在路径框中输入`cmd`)

```bat
frps.exe -c frps.toml
```

看到监听 7000 就说明成功。


二、本地 Windows 电脑 —— 配置 FRP 客户端
1. 下载 FRP Windows 版
打开浏览器访问：
https://github.com/fatedier/frp/releases
下载和服务器相同的版本并解压到任意文件夹（例如 C:\frpc）。

3. 本地 Windows 电脑：创建 frpc.ini
-auth.token，和云服务器上设置的那个密码一致
-实际书写中"# 和云服务器上的token(密码)一致"要删掉

```toml
serverAddr = "云端公网ip"
serverPort = 7000
auth.token = "x8Fk2Lp9qR5tY"    # 和云服务器上的token(密码)一致

[[proxies]]
name = "rusted_warfare_tcp"
type = "tcp"
localIP = "127.0.0.1"
localPort = 5123
remotePort = 5123

[[proxies]]
name = "rusted_warfare_udp"
type = "udp"
localIP = "127.0.0.1"
localPort = 5123
remotePort = 5123
```
4. 启动 FRP 客户端
在 C:\frpc 文件夹的地址栏输入 cmd 回车，然后运行：

```cmd
frpc.exe
```
如果看到 [proxy] [rusted_warfare_tcp] start proxy success 和 [rusted_warfare_udp] start proxy success，说明连接成功。

请进入 
pluginstart.md

## 现在云服务器以及本地frp已经全部设置完成，让我们回到插件.请转到本仓库中 pluginstart.md

### 安全相关
你可能已经知道SOCKS/HTTP 代理都没有防护措施，这意味着任何知道你 IP 的人都能直接使用这个 SOCKS/HTTP 代理。
 > 这句话是正确的，但实测时故障较多。故本文中安全方面基本没有涉及，如果你需要安全方面问题请复制本文并与ai沟通。
frp有密钥基本上不怕，但是如果你有 SOCKS/HTTP 代理安全 需求请自行与ai沟通。不在本仓库范围内。


### 🚀 云服务器开机后操作步骤(安装向导走完以后用这个步骤)
1. 登录
2. 启动 SOCKS5/HTTP 代理（3proxy）
```bat
3proxy.exe proxy.cfg
```

3. 启动 FRP 服务端
```bat
frps.exe -c frps.toml
```