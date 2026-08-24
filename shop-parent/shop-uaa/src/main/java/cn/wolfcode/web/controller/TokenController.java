package cn.wolfcode.web.controller;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * JMeter 压测辅助：批量创建测试用户并生成登录 token
 */
@RestController
@RequestMapping("/token")
@Slf4j
public class TokenController {
    @Autowired
    private IUserService userService;

    @PostMapping("/create")
    public Result<String> createUsers(@RequestParam(value = "count", defaultValue = "1000") Integer count) {
        String filePath = "C:/Users/qjz/Downloads/tokens.txt";
        int success = 0;
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            for (int i = 0; i < count; i++) {
                long phone = 13800000000L + i;
                String token = userService.registerAndCreateToken(phone, "123456", "127.0.0.1");
                writer.println(phone + "," + token);
                success++;
            }
        } catch (IOException e) {
            log.error("[批量生成测试用户] 写入 token 文件失败", e);
            throw new BusinessException(new CodeMsg(500, "写入 token 文件失败: " + e.getMessage()));
        }
        log.info("[批量生成测试用户] 成功生成 {} 个用户，token 已写入 {}", success, filePath);
        return Result.success("成功生成 " + success + " 个用户，token 已写入 " + filePath);
    }
}
