package com.github.hcsp;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.stream.Collectors;

public class Main {

    /**
     * 从数据库中拿出下一个集合进行爬取
     *
     * @param connection 数据库地址链接
     * @param sql        sql语句
     * @return List<String>
     */
    public static String queryNextUrl(Connection connection, String sql) throws SQLException {
        String queryUrl = null;
        ResultSet resultSet = null;
        try {
            //sql语句
            PreparedStatement statement = connection.prepareStatement(sql);
            //拿到结果集
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                queryUrl = resultSet.getString(1);
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return queryUrl;
    }

    /**
     * 更新数据库链接操作 delete/insert
     *
     * @param connection 数据库连接
     * @param sql        sql语句
     * @param link       要删除的链接
     * @return 删除成功返回 true 反之 false
     */
    public static boolean updateLinks(Connection connection, String sql, String link) throws SQLException {
        int resultSet;
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            statement.setString(1, link);
            //executeUpdate 方法返回影响数据库行数 返回0 则更新失败
            resultSet = statement.executeUpdate();
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
        }

        return resultSet > 0;
    }

    /**
     * 查询链接是否在数据库中存在
     *
     * @param connection 链接池
     * @param sql        sql查询
     * @param link       要查询的链接
     * @return true存在 false反之
     */
    public static boolean queryLinkExist(Connection connection, String sql, String link) throws SQLException {
        ResultSet resultSet = null;
        boolean exist = false;
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, link);
            //executeUpdate 方法返回影响数据库行数 返回0 则更新失败
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                exist = true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return exist;
    }

    /**
     * 拿出本次爬取的链接
     *
     * @param connection 数据库连接
     * @return 返回本次爬取的链接 没有则为 null
     */
    public static String alterationDataBaseUrl(Connection connection) {
        String link = null;
        try {
            //从需要爬取数据库 拿出 本次使用的链
            link = queryNextUrl(connection, "select * from LINKS_TO_BE_PROCESSED limit 1");
            //拿出本次爬取链接从原数据库删除
            if (link != null) {
                updateLinks(connection, "delete from LINKS_TO_BE_PROCESSED where link = ?", link);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return link;
    }

    public static void main(String[] args) throws SQLException {
        //从数据库加载即将处理链接的代码
        File projectDir = new File(System.getProperty("basedir", System.getProperty("user.dir")));
        String jdbcUrl = "jdbc:h2:file:" + new File(projectDir, "target/mall").getAbsolutePath();
        Connection connection = DriverManager.getConnection(jdbcUrl);
        String link;
        //从需要爬取数据库中 获取 本次爬取的链接循环条件为本次查询结果不为null
        while ((link = alterationDataBaseUrl(connection)) != null) {
            //查询使用过数据库 如果链接在使用过的数据库中存在 则跳过本次链接处理
            boolean isLinkExist = queryLinkExist(connection, "select link from LINKS_ALREADY_PROCESSED where link = ?", link);
            if (isLinkExist) {
                //进行下次处理
                continue;
            }
            //处理链接中带有特定域名的链接
            if (Concerned(link)) {
                //链接转换为文档对象
                Document document = HttpGetParseHtml(link);
                //爬取所有文档对象中的链接 并写入未处理链接
                LoopWriteDataBase(connection, document);
                //筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
                WriteToDatabase(connection, document, link);
                //把本次使用过的链接放入响应数据库中
                boolean isInsert = updateLinks(connection, "insert into LINKS_ALREADY_PROCESSED (link) values(?)", link);
                if (isInsert) {
                    System.out.println("处理过链接-插入" + link);
                }
            }

        }

    }

    /**
     * 爬虫循环条件 写入下次循环的链接
     *
     * @param connection 数据库连接
     * @param document   解析出的文档对象
     */
    public static void LoopWriteDataBase(Connection connection, Document document) {
        //拿出所有<a/>标签 返回集合转换为stream流对象集合
        //格式化集合 筛选集合中a标签携带的链接 放入爬取连接池 以便进行下次循环爬取
        document.select("a")
                .stream()
                .map(tag -> tag.attr("href"))
                .forEach(href -> {
                    try {
                        //链接全部转换为小写后 开头不包含javascript 的路径才插入到数据库中
                        if (!href.toLowerCase().startsWith("javascript") && !"#".equals(href) && href.length() > 0) {
                            updateLinks(connection, "insert into LINKS_TO_BE_PROCESSED (link) values(?)", MergeUrl(href));
                        }
                    } catch (SQLException throwables) {
                        throwables.printStackTrace();
                    }
                });
    }

    /**
     * 筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
     *
     * @param document HTML转换后的文档对象
     * @param connection 数据库连接
     * @param link 当前爬取链接
     */
    public static void WriteToDatabase(Connection connection, Document document, String link) throws SQLException {
        //筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
        Elements articleTags = document.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                //爬取标题
                String resultTitle = articleTag.select(".art_tit_h1").get(0).text();
                //爬取文章内容 所有得到的字符串用换行符分隔
                String collect = articleTag
                        .select("p")
                        .stream()
                        .map(tag -> tag.text())
                        .collect(Collectors.joining("\n"));

                try (PreparedStatement statement = connection.prepareStatement("insert into news(title, content, url, created_at, update_at) values (?, ?, ?, now(), now())")) {
                    statement.setString(1, resultTitle);
                    statement.setString(2, collect);
                    statement.setString(3, link);
                    statement.executeUpdate();
                }
            }
        }
    }

    /**
     * 处理带斜杠不带协议头的链接
     *
     * @param link 要判断的链接
     * @return 拼接后的链接
     */
    public static String MergeUrl(String link) {
        String url = link;
        if (link.startsWith("//")) {
            url = "https:" + link;
            System.out.println(url + "加上https协议头链接");
        }
        return url;
    }

    /**
     * 爬取链接转换为HTML节点
     *
     * @param link 本次爬取的链接
     * @return Document
     */
    public static Document HttpGetParseHtml(String link) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(link);
        Document doc = null;
        //伪造浏览器请求 设置请求头
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");
        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            //输出请求状态
            System.out.println(response.getStatusLine());
            //获取请求响应转换为HTML字符串
            HttpEntity entity = response.getEntity();
            String HTML = EntityUtils.toString(entity);
            //解析HTML
            doc = Jsoup.parse(HTML);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc;
    }

    /**
     * 检测链接是否有价值
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public static boolean Concerned(String link) {
        return IsIndexPage(link) || IsNewsPage(link) && !IsLoginPage(link);
    }

    /**
     * 判断链接是否为主页
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public static boolean IsIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    /**
     * 判断链接是否为新闻页面
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public static boolean IsNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    /**
     * 判断链接是否为登录页面
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public static boolean IsLoginPage(String link) {
        return link.contains("passport.sina.cn");
    }
}
