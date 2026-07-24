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

  // 代理服务器端口(你云服tinyproxy的端口)
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

proxy_port 将其设置为8888(你前面云服的http端口是啥就是啥)开启http代理
  "proxy_port": 8888
```
