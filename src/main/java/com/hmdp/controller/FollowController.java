package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;

    /**
     * 关注/取消关注
     * @param id
     * @param isFollowed
     * @return
     */
    @PutMapping("/{id}/{isFollowed}")
    public Result tryFollowUser(@PathVariable Long id, @PathVariable Boolean isFollowed) {
        return followService.tryFollowUser(id, isFollowed);
    }

    /**
     * 查询是否关注
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result getIsFollowed(@PathVariable Long id) {
        return followService.getIsFollowed(id);
    }

    @GetMapping("/common/{id}")
    public Result getCommonUser(@PathVariable Long id){
        return followService.getCommonUser(id);
    }
}
