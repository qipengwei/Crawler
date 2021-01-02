# 多线程爬虫与ES新闻搜索引擎

### 运行此项目环境

* windows 10 || Mac

* JDK 8 未安装JDK 请参阅 [Window10 Java JDK环境变量配置](https://www.cnblogs.com/jingxin-blog/p/12037630.html)

* ElasticSearch-7.10.1 未安装ElasticSearch 请参阅 [ElasticSearch安装windows平台](https://www.cnblogs.com/coderxz/p/13268417.html)

* Mysql 8.0.21 未安装Mysql 请参阅 [win10安装MySql教程](https://www.cnblogs.com/xiaokang01/p/12092160.html)

### 开发过程中使用的依赖 （非必要情况 依赖不可改动版本）

* h2database-1.4.196 测试时数据库驱动

* jsoup-1.13.1 操作HTML结构
 
* httpclient-4.5.12 模拟客户端请求 抓取HTML节点

* mysql-connector-java-8.0.21 mysql驱动

* mybatis-3.5.6 数据库ORM操作 

* elasticsearch-rest-high-level-client-7.10.1 ElasticSearch驱动

* flywaydb-5.2.4 数据库版本管理

* spotbugs-4.1.3 代码检查潜在bug 及 规范

### 起手式 (已安装运行项目所需要的环境)

* 新建爬虫数据库 

```sql
   CREATE DATABASE Crawler
```

* 在pom.xml中配置你的数据库地址以及账号密码 可先试用IDEA自带数据库版本管理工具测试连接
```XML
  <plugin>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-maven-plugin</artifactId>
      <version>5.2.4</version>
      <configuration>
          <url>jdbc:mysql://localhost:3306/Crawler</url>
          <user>root</user>
          <password>root</password>
      </configuration>
  </plugin>
```
* IDEA内控制台运行maven命令 clean 清除缓存
```java
   mvn flyway:clean
```
* IDEA内控制台运行maven命令 migrate 初始化表结构
```java
   mvn flyway:migrate
```
* 运行com.github.hcsp 中的Main类 进行一些基础数据的爬取

* 爬取过程如果过慢 可以在爬取一些基础数据后Mock一些数据 运行 com.github.hcsp 中的 MockData类

* 有了一些基础数据后 运行ElasticSearch 打开ElasticSearch安装的文件夹 找到bin目录下elasticsearch.bat文件 并打开 本项目链接elasticsearch默认端口都为9200

* 为了实现全文检索的高性能查询 运行 com.github.hcsp 中的 ElasticSearch 类 main 方法 将爬取到的新闻数据写入到ElasticSearch 

* 做完以上步骤以后 运行com.github.hcsp 中的 ElasticSearchEngine类中的 main 方法 并在控制台输入你要搜索的关键词


