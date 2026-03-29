package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.UserInterest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户兴趣 Mapper
 */
@Mapper
public interface UserInterestMapper extends BaseMapper<UserInterest> {

    /**
     * 根据标签查询
     */
    @Select("SELECT * FROM user_interest WHERE tag = #{tag}")
    UserInterest selectByTag(String tag);

    /**
     * 获取所有兴趣标签（按权重排序）
     */
    @Select("SELECT * FROM user_interest ORDER BY weight DESC, search_count DESC")
    List<UserInterest> selectAllOrderByWeight();

    /**
     * 获取用户主要兴趣（权重最高的几个）
     */
    @Select("SELECT * FROM user_interest ORDER BY weight DESC LIMIT #{limit}")
    List<UserInterest> selectTopInterests(int limit);

    /**
     * 增加兴趣权重
     */
    @Update("UPDATE user_interest SET weight = weight + #{increment}, " +
            "search_count = search_count + 1, " +
            "last_search_time = NOW(), " +
            "update_time = NOW() WHERE tag = #{tag}")
    int increaseWeight(String tag, int increment);
}