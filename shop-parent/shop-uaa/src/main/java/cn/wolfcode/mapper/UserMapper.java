package cn.wolfcode.mapper;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.domain.LoginLog;
import cn.wolfcode.domain.UserLogin;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * Created by wolfcode
 */
public interface UserMapper {
    /**
     * 根据用户手机号码查询用户登录信息对象
     * @param phone
     * @return
     */
    UserLogin selectUserLoginByPhone(Long phone);

    /**
     * 根据用户手机号码查询用户的基础信息
     * @param phone
     * @return
     */
    UserInfo selectUserInfoByPhone(Long phone);

    /**
     * 插入登录日志
     * @param loginLog
     * @return
     */
    int insertLoginLong(LoginLog loginLog);

    /**
     * 插入用户登录信息
     */
    int insertUserLogin(@Param("phone") Long phone, @Param("password") String password, @Param("salt") String salt);

    /**
     * 插入用户基础信息
     */
    int insertUserInfo(@Param("phone") Long phone, @Param("nickName") String nickName, @Param("head") String head, @Param("registerIp") String registerIp, @Param("registerTime") Date registerTime);
}
