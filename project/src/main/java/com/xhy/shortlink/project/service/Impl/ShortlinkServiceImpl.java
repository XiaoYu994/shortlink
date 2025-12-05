package com.xhy.shortlink.project.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.dao.entity.ShortlinkDO;
import com.xhy.shortlink.project.dao.mapper.ShortlinkMapper;
import com.xhy.shortlink.project.dto.req.ShortlinkCreateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortlinkCreateRespDTO;
import com.xhy.shortlink.project.service.ShortlinkService;
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
public class ShortlinkServiceImpl extends ServiceImpl<ShortlinkMapper, ShortlinkDO> implements ShortlinkService {
    private final RBloomFilter<String> shortlinkCachePenetrationBloomFilter;
    @Override
    public ShortlinkCreateRespDTO createShortlink(ShortlinkCreateReqDTO requestParam) {
        String fullShortUrl = requestParam.getDomain() + "/" + generateSuffix(requestParam);
        final ShortlinkDO shortlinkDO = ShortlinkDO.builder()
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreateType())
                .domain(requestParam.getDomain())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .fullShortUrl(fullShortUrl)
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
        return ShortlinkCreateRespDTO.builder()
                .fullShortUrl(shortlinkDO.getFullShortUrl())
                .gid(shortlinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
    }
    /*
    * 生成短链接后缀
    * */
    private String generateSuffix(ShortlinkCreateReqDTO requestParam) {
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
