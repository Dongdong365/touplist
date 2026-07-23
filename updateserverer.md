### 以下是更新服务器中经常出现的Y/N选择

#1
```Configuring openssh-server
--------------------------

A new version (/tmp/tmp.c3JCZOqVY5) of configuration file /etc/ssh/sshd_config is available, but the version installed
currently has been locally modified.

  1. install the package maintainer's version             5. show a 3-way difference between available versions
  2. keep the local version currently installed           6. do a 3-way merge between available versions
  3. show the differences between the versions            7. start a new shell to examine the situation
  4. show a side-by-side difference between the versions
What do you want to do about modified configuration file sshd_config?
```
这是系统更新时发现 SSH 配置文件被修改过，问你怎么处理。选择 2（保留本地版本），这样不会改乱你当前的 SSH 设置，保证你不会突然断连。


#2
```
----------------------

Newer kernel available

The currently running kernel version is 5.15.0-118-generic which is not the expected kernel version 5.15.0-185-generic.

Restarting the system to load the new kernel will not be handled automatically, so you should consider rebooting.


Restarting services...
Daemons using outdated libraries
--------------------------------

  1. dbus.service        3. multipathd.service           5. systemd-logind.service       7. user@0.service
  2. getty@tty1.service  4. networkd-dispatcher.service  6. unattended-upgrades.service  8. none of the above

(Enter the items or ranges you want to select, separated by spaces.)

Which services should be restarted?
```

输入 1 2 3 4 5 6 7，然后按回车。

这会重启所有需要更新的服务，确保系统组件都是最新版本，避免后续搭建代理时出现奇怪的兼容性问题。重启完后，你就可以继续执行安装 HTTP 代理的命令了。
