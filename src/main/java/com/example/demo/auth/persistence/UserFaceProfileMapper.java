package com.example.demo.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.domain.UserFaceProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户人脸档案表 MyBatis-Plus Mapper,提供 user_face_profile 表的基础 CRUD。
 * <p>
 * 由 FaceAuthService 调用,完成人脸特征向量的注册、按 userId 查询与状态更新。
 */
@Mapper
public interface UserFaceProfileMapper extends BaseMapper<UserFaceProfile> {
}
