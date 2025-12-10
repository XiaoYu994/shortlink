package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.toolkit.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/*
* 短链接接口实现层
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final RBloomFilter<String> shortlinkCachePenetrationBloomFilter;
    @Override
    public ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam) {
        String fullShortUrl = requestParam.getDomain() + "/" + generateSuffix(requestParam);
        final ShortLinkDO shortlinkDO = ShortLinkDO.builder()
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreateType())
                .domain(requestParam.getDomain())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .fullShortUrl(fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .shortUri(generateSuffix(requestParam))
                .build();
        // 如果发生布隆冲突，就说明短链接重复了，这个时候数据库就会报key冲突 之前设置了 唯一索引 full_short_url
        try {
            baseMapper.insert(shortlinkDO);
        } catch (DuplicateKeyException e) {
            log.warn("短链接：{} 重复入库",fullShortUrl);
            throw new ServiceException("短链接：" + fullShortUrl + " 已存在");
        }
        // 短链接没有问题就将这个短链接加入布隆过滤器
        shortlinkCachePenetrationBloomFilter.add(shortlinkDO.getShortUri());
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(shortlinkDO.getFullShortUrl())
                .gid(shortlinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam) {
        final LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus,0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        final IPage<ShortLinkDO>  resultPage = baseMapper.selectPage(requestParam, queryWrapper);
        return resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }

    /*
    * 生成短链接后缀
    * */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            // 使用UUID避免返回的短链接重复，降低这个重复的概率
            originUrl += UUID.randomUUID().toString();
            shortUri = HashUtil.hashToBase62(originUrl);
            if(!shortlinkCachePenetrationBloomFilter.contains(requestParam.getDomain() + "/" + shortUri)){
                break;
            }
                customGenerateCount++;
        }
        return shortUri;
    }
}
