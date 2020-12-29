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

import java.io.IOException;
import java.sql.*;
import java.util.stream.Collectors;

public class Crawler extends Thread{
    public CrawlerDao dao;

    public Crawler(CrawlerDao dao) {
        this.dao = dao;
    }

    public void run()  {
        String link;
        //从需要爬取数据库中 获取 本次爬取的链接循环条件为本次查询结果不为null
        while ((link = dao.ConsumptionUnusedUrl()) != null) {
            //查询使用过数据库 如果链接在使用过的数据库中存在 则跳过本次链接处理
            boolean isLinkExist = false;
            try {
                isLinkExist = dao.queryLinkUsed(link);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            if (isLinkExist) {
                //进行下次处理
                continue;
            }
            //初步判断链接是否符合爬取要求
            if (Concerned(link)) {
                //链接转换为文档对象
                Document document = HttpGetParseHtml(link);
                if (document != null) {
                    //爬取所有文档对象中的链接 并写入未处理链接
                    LoopWriteDataBase(document);
                    //筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
                    try {
                        WriteToDatabase(document, link);
                    } catch (SQLException throwables) {
                        throwables.printStackTrace();
                    }
                }

                //把本次使用过的链接放入响应数据库中 进行过滤操作
                int isInsert = dao.WriteUseCrawl(link);
                if (isInsert > 0) {
                    System.out.println("处理过链接-插入" + link);
                }
            }

        }
    }


    /**
     * 爬虫循环条件 写入下次循环的链接
     *
     * @param document   解析出的文档对象
     */
    public void LoopWriteDataBase(Document document) {
        //拿出所有<a/>标签 返回集合转换为stream流对象集合
        //格式化集合 筛选集合中a标签携带的链接 放入爬取连接池 以便进行下次循环爬取
        document.select("a")
                .stream()
                .map(tag -> tag.attr("href"))
                .forEach(href -> {
                    //链接全部转换为小写后 开头不包含javascript 的路径才插入到数据库中
                    if (InsertDataBaseCheck(href)) {
                        dao.WriteNotCrawl(MergeUrl(href));
                    }
                });
    }

    /**
     * 筛选有价值的数据 判断条件是带有article元素 即正文标题所包裹的标签 存入数据库 无价值则什么都不做
     *
     * @param document HTML转换后的文档对象
     * @param link 当前爬取链接
     */
    public void WriteToDatabase(Document document, String link) throws SQLException {
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

                dao.WriteToDatabaseNews(resultTitle, collect, MergeUrl(link));
            }
        }
    }

    /**
     * 链接入库检索
     * @param href 链接
     * @return true 符合要求 false 反之
     */
    public boolean InsertDataBaseCheck(String href) {
        return !href.toLowerCase().startsWith("javascript") && !"#".equals(href) && href.length() > 0;
    }

    /**
     * 处理带斜杠不带协议头的链接
     *
     * @param link 要判断的链接
     * @return 拼接后的链接
     */
    public String MergeUrl(String link) {
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
    public Document HttpGetParseHtml(String link) {
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
     * 检测链接是否需要爬取
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public boolean Concerned(String link) {
        return IsIndexPage(link) || IsNewsPage(link) && !IsLoginPage(link);
    }

    /**
     * 判断链接是否为主页
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public boolean IsIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    /**
     * 判断链接是否为新闻页面
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public boolean IsNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    /**
     * 判断链接是否为登录页面
     *
     * @param link 本次爬取的链接
     * @return boolean
     */
    public boolean IsLoginPage(String link) {
        return link.contains("passport.sina.cn");
    }
}
