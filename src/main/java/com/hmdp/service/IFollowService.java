package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    Result tryFollowUser(Long id, Boolean isFollowed);

    Result getIsFollowed(Long id);

    Result getCommonUser(Long id);
}
