package com.example.demo.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 用户人脸特征档案实体,保存刷脸登录所需的人脸向量、维度与质量分。
 * <p>
 * 与 FaceAuthService 配合,用于人脸注册时的特征入库以及登录时的相似度比对;enabled 控制该档案是否参与校验。
 */
@TableName("user_face_profile")
public class UserFaceProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String embedding;

    private Integer vectorDimension;

    private Double qualityScore;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
