package com.xhy.shortlink.project.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.dao.entity.ShortlinkDO;
import com.xhy.shortlink.project.dao.mapper.ShortlinkMapper;
import com.xhy.shortlink.project.service.ShortlinkService;
import org.springframework.stereotype.Service;

/*
* 短链接接口实现层
* */
@Service
public class ShortlinkServiceImpl extends ServiceImpl<ShortlinkMapper, ShortlinkDO> implements ShortlinkService {
}
