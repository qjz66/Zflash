package cn.wolfcode.service;


import cn.wolfcode.domain.UserResponse;

/**
 * Created by wolfcode
 */
public interface IUserService {
    /**
     * 登录功能
     * @param phone 用户的账号/用户的手机号码
     * @param password 用户的密码
     * @param ip 客户端IP
     * @return
     */
    UserResponse login(Long phone, String password, String ip, String token);

    /**
     * 批量测试用：创建用户（已存在则跳过）并直接生成登录 token
     * @param phone 手机号
     * @param password 明文密码
     * @param ip 登录IP
     * @return token
     */
    String registerAndCreateToken(Long phone, String password, String ip);
}
