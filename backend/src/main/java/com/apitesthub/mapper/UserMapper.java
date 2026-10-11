package com.apitesthub.mapper;

import com.apitesthub.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 继承 BaseMapper 即获得通用 CRUD，无需手写 SQL。
 */
public interface UserMapper extends BaseMapper<User> {
}
