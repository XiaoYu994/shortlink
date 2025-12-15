package com.xhy.shortlink.project.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.xhy.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.xhy.shortlink.project.service.LinkAccessStatsService;
import org.springframework.stereotype.Service;

/*
* 监控数据服务层实现类
* */
@Service
public class LinkAccessStatsServiceImpl extends ServiceImpl<LinkAccessStatsMapper, LinkAccessStatsDO> implements LinkAccessStatsService {
}
