package com.xhy.shortlink.admin.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.admin.common.biz.user.UserContext;
import com.xhy.shortlink.admin.common.convention.exception.ClientException;
import com.xhy.shortlink.admin.common.convention.result.Result;
import com.xhy.shortlink.admin.dao.entity.GroupDO;
import com.xhy.shortlink.admin.dao.mapper.GroupMapper;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupSortReqDTO;
import com.xhy.shortlink.admin.dto.req.ShortlinkGroupUpdateReqDTO;
import com.xhy.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.xhy.shortlink.admin.remote.ShortLinkRemoteService;
import com.xhy.shortlink.admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.admin.service.GroupService;
import com.xhy.shortlink.admin.toolkit.RandomCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.xhy.shortlink.admin.common.convention.errorcode.BaseErrorCode.SERVICE_SAVE_ERROR;
import static com.xhy.shortlink.admin.common.convention.errorcode.BaseErrorCode.SERVICE_UPDATE_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
    private final GroupMapper groupMapper;
    /*
     * TODO 后续重构为SpringCloud Feign调用
     * */
    ShortLinkRemoteService shortlinkRemoteService = new ShortLinkRemoteService(){
    };
    @Override
    public void addGroup(String groupName) {
        addGroup(UserContext.getUsername(), groupName);
    }
    @Override
    public void addGroup(String username,String groupName) {
        //gid 使用随机生成的6位数
        String gid = RandomCodeGenerator.generateSixDigitCode();
        // 插入之前要查询gid是否已经存在 逻辑删除的也要查询出来
        GroupDO groupDO = groupMapper.selectByGidIgnoreLogicDelete( gid);
        while (groupDO != null) {
            gid = RandomCodeGenerator.generateSixDigitCode();
            groupDO = groupMapper.selectByGidIgnoreLogicDelete( gid);
        }
        final GroupDO group = GroupDO.builder()
                .gid(gid)
                .name(groupName)
                .username(username)
                .build();
        final int insert = baseMapper.insert(group);
        if (insert <= 0) {
            throw new ClientException(SERVICE_SAVE_ERROR);
        }
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 1.  根据用户名进行查询
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getCreateTime);

        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        // 【优化1】边界判空：如果没查到分组，直接返回空列表，省去远程调用
        if (CollUtil.isEmpty(groupDOList)) {
            return Collections.emptyList();
        }
        // 2. 提取 GID 列表
        List<String> gidList = groupDOList.stream().map(GroupDO::getGid).toList();
        // 3. 远程调用获取统计数据
       Result<List<ShortLinkGroupCountRespDTO>> listResult = shortlinkRemoteService.listGroupShortlinkCount(gidList);
        // 【优化2】性能优化：将 List 转为 Map<Gid, Count>，方便后续 O(1) 查找
        // 逻辑：如果 result 为 null 或 result.getData() 为 null，则返回空列表，避免空指针
        Map<String, Integer> countMap = Optional.ofNullable(listResult)
                .map(Result::getData) // 关键：必须先取出 Data 里的 List
                .orElse(Collections.emptyList())
                .stream()
                .collect(Collectors.toMap(
                        ShortLinkGroupCountRespDTO::getGid,
                        item -> item.getShortLinkCount() == null ? 0 : item.getShortLinkCount(),
                        (existing, replacement) -> existing
                ));
        // 4. 转换并填充数据
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOS = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        shortLinkGroupRespDTOS.forEach(each -> {
         // 【优化3】安全获取：从 Map 中取值，取不到给默认值 0，彻底杜绝 NoSuchElementException
            Integer count = countMap.getOrDefault(each.getGid(), 0);
            each.setShortLinkCount(count);
        });
        return shortLinkGroupRespDTOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(ShortlinkGroupUpdateReqDTO requestParam) {
        final LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid());
        final GroupDO groupDO = GroupDO.builder().name(requestParam.getName())
                .build();
        final int update = baseMapper.update(groupDO, updateWrapper);
        if (update <= 0) {
            throw new ClientException(SERVICE_UPDATE_ERROR);
        }
    }

    @Override
    public void deleteGroup(String gid) {
        final int delete = baseMapper.delete(Wrappers.lambdaQuery(GroupDO.class).eq(GroupDO::getGid, gid).
                eq(GroupDO::getUsername, UserContext.getUsername()));
        if (delete <= 0) {
            throw new ClientException(SERVICE_UPDATE_ERROR);
        }
    }

    @Override
        public void sortGroup(List<ShortlinkGroupSortReqDTO> requestParam) {
            requestParam.forEach(item -> {
                final GroupDO groupDO = GroupDO.builder()
                        .sortOrder(item.getSortOrder())
                        .build();
                final int update = baseMapper.update(groupDO, Wrappers.lambdaUpdate(GroupDO.class)
                        .eq(GroupDO::getGid, item.getGid())
                        .eq(GroupDO::getUsername, UserContext.getUsername()));
                if(update <= 0){
                    throw new ClientException(SERVICE_UPDATE_ERROR);
                }
            });
        }
}
