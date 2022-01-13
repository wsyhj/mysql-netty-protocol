# MYSQL NETTY PROTOCOL 

本项目意为实现mysql服务端的通信协议，使用户可以通过**Mysql-Connect-java**、**MYSQL命令行**、**Navicat等连接工具**实现连接
本项目只实现查询相关逻辑处理，不支持其他语句

## 本项目特点
* 支持驱动包5.5.\*-8.0.\*均可实现连接
* 支持命令行操作
* 支持数据库连接工具查看
* 封装实现大部分逻辑，用户只需关注自身业务逻辑以及拼装查询结果类即可
    >代码快逻辑参考MySQLServer.handleSelectResult

## 如何使用

* mysql命令行 mysql -P5024 -uroot -proot [-Dtest]
* 驱动包代码用户自己实现

## 鸣谢

本项目是基于[netty-mysql-codec](https://github.com/mheath/netty-mysql-codec)的二次开发，完善丰富了原项目的不足点

