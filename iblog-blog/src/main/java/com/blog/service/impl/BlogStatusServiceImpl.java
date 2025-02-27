package com.blog.service.impl;

import com.blog.dao.BlogStatusDao;
import com.blog.entity.model.PopularBlog;
import com.blog.service.ArticleService;
import com.blog.service.BlogStatusService;
import com.blog.service.RedisService;
import com.common.entity.pojo.BlogStatus;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <pre>BlogStatusServiceImpl</pre>
 *
 * @author <p>ADROITWOLF</p> 2021-05-07
 */
@Service
@Slf4j
public class BlogStatusServiceImpl implements BlogStatusService {
    @Autowired
    RedisService redisService;


    @Autowired
    ArticleService articleService;

    @Autowired
    BlogStatusDao blogStatusDao;

    private static final String LOCAL_TRANS_CLICK_UPDATE_KEY = "LOCAL_TRANS_CLICK_UPDATE_KEY";

    private static final String LOCAL_TRANS_CLICK_UPDATE_VALUE = "LOCAL_TRANS_CLICK_UPDATE_KEY";

    @Override
    @Transactional
    public void transClickedCountFromRedis2DB() {
        Boolean lock = redisService.getLock(LOCAL_TRANS_CLICK_UPDATE_KEY, LOCAL_TRANS_CLICK_UPDATE_VALUE, 1, TimeUnit.DAYS);
        if (!lock) {
            log.info("redis正在添加缓存...请稍等"); //此时锁有人占用
            log.info("=====更新数据库文章点击任务失败=====");
            return;
        }
        try {
            log.info("=====更新数据库文章点击任务开始=====");
            List<BlogStatus> statusList = redisService.listBlogClickedCounts();

            statusList.stream().filter(Objects::nonNull).forEach(entity -> {

                if (StringUtils.isEmpty(articleService.getArticleNameByBlogId(entity.getId()))) { //说明这个博客id无效
                    return;
                }

                BlogStatus status = blogStatusDao.selectByPrimaryKey(entity.getId());

                if (null != status) {
                    status.setClickcount(status.getClickcount() + entity.getClickcount());
                    status.setId(entity.getId());
                    blogStatusDao.updateByPrimaryKey(status);
                } else {
                    blogStatusDao.insert(entity);
                }
            });
        } finally {
            redisService.delete(LOCAL_TRANS_CLICK_UPDATE_KEY);
        }
    }

    @Override
    public List<PopularBlog> listTop5Posts() {

        Example example = Example.builder(BlogStatus.class).orderByDesc("clickcount").build();
        PageHelper.startPage(0, 5);
        return blogStatusDao.selectByExample(example).stream().filter(Objects::nonNull).map(status -> {
            PopularBlog popularBlog = new PopularBlog();
            BeanUtils.copyProperties(status, popularBlog);
            popularBlog.setBlogName(articleService.getArticleNameByBlogId(popularBlog.getId()));
            return popularBlog;
        }).collect(Collectors.toList());
    }
}
