#### 附上🔵 情况二：拥有一台有公网IP的云服务器的方案(实操，我已经跟着走一遍了实测能用)
本文中的云服务器指崭新的，没装系统的云服务器，如果你已经在服务器上有东西存在了请如实告诉ai并把本教程一并复制到ai，使用ai提供的教程即。

请注意备份，本文中可能有部分破坏性内容，请注意甄别！本文不做任何担保.

首先你需要拥有一个云服务器，一定是独享公网ipv4的ip，不能是共享的ipv4，不能ipv6

然后你需要在云服务器上安装ubuntu 22.04
## 🔐 安装完成后，立刻做三件事
1. SSH 登录
用你电脑的终端或 PowerShell 执行：

bash
ssh root@你的云服务器IP

可能会有提示

```bash
The authenticity of host '103.217.186.229 (103.217.186.229)' can't be established.
ED25519 key fingerprint is SHA256:ramCLPEW/tKTwpDJREvRQXitVeMiVEtX+GQEiFgdENI.
This host key is known by the following other names/addresses:
    C:\Users\Administrator/.ssh/known_hosts:5: 103.217.186.86
Are you sure you want to continue connecting (yes/no/[fingerprint])?
无特殊要求输入yes即可。
```

输入刚才设置的 root 密码。(输入的时候不会显示密码也就是你无论打什么都不会显示在屏幕上，只是回车确认就可以了。也就是说你看着没有，实际上你已经输入了 ，这是安全策略。)

2. 更新系统
bash
```
apt update && apt upgrade -y
```
(更新时的一些问题不会就去问ai千万不要拿不准主意就自己弄了)
过程中出现问你怎么处理的详见: updateserverer.md

4. 放行端口
(免费/其他云服务器通常没有独立的安全组/防火墙面板。你先用 SSH 连接测试一下，如果 ssh root@你云服务器的公网IP 能连通，就说明默认所有端口都是放行的，这是最好的情况)
在云服务商的面板“安全组/防火墙”中添加规则：

```

TCP 22（SSH）

TCP 1080（SOCKS5/http 代理端口，以你的喜好为准。推荐直接用1080端口，以下示范用1080端口）

TCP 7000（FRP 服务端）

TCP 16675（服务端口，按需修改）

UDP 16675（服务端游戏 UDP，与TCP端口一致）

```

##系统已经更新完了，环境干净，现在可以继续搭建 http 代理了。

2. 安装并配置 Tinyproxy（HTTP 代理）
5123   ✅ 保留（官方自检会访问你的 16675 端口,就是你的RW-HPS服务端游戏端口）
80	HTTP 标准端口（官方列表接口 http://gs1.corrodinggames.com 用的是 80 端口）
443	HTTPS 标准端口

```bash
apt install tinyproxy -y
```
```bash
cat > /etc/tinyproxy/tinyproxy.conf << 'EOF'
Port 8888
Listen 0.0.0.0
Timeout 600
Allow 0.0.0.0/0
ConnectPort 80
ConnectPort 443
ConnectPort 5123
ViaProxyName "tinyproxy"
EOF
```
```bash
pkill -9 tinyproxy
```
```bash
tinyproxy -c /etc/tinyproxy/tinyproxy.conf
```
检查代理是否运行：

```bash
ss -tlnp | grep 8888   # 应显示 LISTEN
```
恭喜你完成代理搭建！现在进行下一步

## 启动frp服务端(射影RW-HPS端口到云服)
一、云服务器端（Ubuntu）—— 安装 FRP 服务端
1. 下载 FRP(下载慢是正常的)(非常慢或超时可以去BING搜github镜像站)（使用 专用于下载Release、压缩包 的镜像链接）

> 例如将https://github.com/fatedier/frp/releases/download/v0.57.0/frp_0.57.0_linux_amd64.tar.gz 复制并 转换链接 即可得到 加速拉取链接

> 替换到原有链接

> wget <加速拉取链接>

```bash
wget https://github.com/fatedier/frp/releases/download/v0.57.0/frp_0.57.0_linux_amd64.tar.gz
```
此步骤报错的(例如文件不完全之类)直接带着日志找ai即可

2. 解压
```bash
tar -zxvf frp_0.57.0_linux_amd64.tar.gz
```
```bash
cd frp_0.57.0_linux_amd64
```
3. 云服务器端：修改 frps.ini
```bash
cd ~/frp_0.57.0_linux_amd64
```
```bash
cat > frps.ini << 'EOF'
bindPort = 7000
auth.token = "你自己编一个复杂的密码，比如 x8Fk2Lp9qR5tY"
EOF
```
4.  停掉所有 FRP 进程
```bash
pkill frps
```
 确认端口已释放
```bash
ss -tlnp | grep 7000
```
如果没有任何输出，表示端口已释放。

 重新启动 FRP（使用你的 token 配置）
确保 frps.ini 里内容正确（包含 token）：

```bash
cat frps.ini
```
应显示类似：
```
text
bindPort = 7000
auth.token = "你设置的密码"
```
5. 启动 FRP 服务端（后台运行）
```bash
nohup ./frps -c frps.ini > frps.log 2>&1 &
```
6. 检查是否成功
```bash
ss -tlnp | grep 7000
```
看到 LISTEN 0 128 ... :::7000 就说明服务端已运行。
看到监听 7000 就说明成功。


二、本地 Windows 电脑 —— 配置 FRP 客户端
1. 下载 FRP Windows 版
打开浏览器访问：
https://github.com/fatedier/frp/releases/download/v0.57.0/frp_0.57.0_windows_amd64.zip
下载并解压到任意文件夹（例如 C:\frpc）。

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

## 现在云服务器以及本地frp已经全部设置完成，让我们回到插件.请转到本仓库中 pluginstart.md

### 安全相关
你可能已经知道SOCKS/HTTP 代理都没有防护措施，这意味着任何知道你 IP 的人都能直接使用这个 SOCKS/HTTP 代理。
这句话是正确的，但实测时故障较多。故本文中安全方面基本没有涉及，如果你需要安全方面问题请复制本文并与ai沟通。
frp有密钥基本上不怕，但是如果你有需求请一并与ai沟通


### 🚀 云服务器开机后操作步骤(安装向导走完以后用这个步骤)
1. SSH 登录
bash
ssh root@103.999.465.13
2. 启动 SOCKS5/HTTP 代理（Dante）
bash
systemctl start danted
检查是否成功：

bash
ss -tlnp | grep 1080
看到 LISTEN 说明代理已运行。

3. 启动 FRP 服务端
bash
cd ~/frp_0.57.0_linux_amd64
nohup ./frps -c frps.toml > frps.log 2>&1 &
检查：

bash
ss -tlnp | grep 7000
看到 LISTEN 说明 FRP 服务端已运行。

（把 你家公网IP 换成你当下的公网 IP）

FRP 服务端目前没有任何验证，任何人知道你的 IP 和端口 7000 都能直接连上你的 FRP，把自己的内网服务映射到你的公网 IP 上，消耗你的带宽，甚至做坏事。

