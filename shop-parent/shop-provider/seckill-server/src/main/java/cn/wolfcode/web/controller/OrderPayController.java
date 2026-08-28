package cn.wolfcode.web.controller;


import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.feign.AlipayFeignApi;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;


@Slf4j
@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {

    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private AlipayFeignApi alipayFeignApi;

    @RequestMapping("/refund")
    public Result<?> refund(String orderNo) {
        orderInfoService.refund(orderNo);
        return Result.success("退款成功！");
    }

    @RequestMapping("/syncStatus")
    public Result<?> syncStatus(String orderNo) {
        orderInfoService.syncStatus(orderNo);
        return Result.success("同步成功");
    }

//    @RequestMapping("/alipay")
//    public Result<String> pay(String orderNo, Integer type) {
//        // 1. 参数校验
//        if (!StringUtils.hasLength(orderNo) || type == null) {
//            throw new BusinessException(SeckillCodeMsg.OP_ERROR);
//        }
//        // 2. 判断类型，基于不同类型选择不同支付方式
//        if (OrderInfo.PAY_TYPE_ONLINE.equals(type)) {
//            // 支付宝支付
//            String result = orderInfoService.alipay(orderNo);
//            return Result.success(result);
//        } else if (OrderInfo.PAY_TYPE_INTERGRAL.equals(type)) {
//            // 积分支付
//            orderInfoService.intergralPay(orderNo);
//            return Result.success("积分支付成功");
//        }
//
//        // 不正常的类型
//        throw new BusinessException(SeckillCodeMsg.OP_ERROR);
//    }

    @GetMapping("/pay")
    public Result<String> dopay(String orderNo, Integer payType) {
        if (Objects.equals(payType, OrderInfo.PAY_TYPE_ONLINE)) {
            return Result.success(orderInfoService.alipay(orderNo));
        }
        return null;
    }


    /**
     * 让页面重定向到指定页面
     */


    private Map<String, String> getParams(HttpServletRequest req) throws UnsupportedEncodingException {
        Map<String, String> params = new HashMap<String, String>();
        Map<String, String[]> requestParams = req.getParameterMap();
        for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            // 乱码解决，这段代码在出现乱码时使用
            // valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
            params.put(name, valueStr);
        }
        return params;
    }

    @RequestMapping("/notify_url")
    public String notifyUrl(HttpServletRequest req) throws UnsupportedEncodingException {
        Map<String, String> params = getParams(req);
        // 获取支付宝POST过来反馈信息
        // boolean signVerified = AlipaySignature.rsaCheckV1(params, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type); //调用SDK验证签名
        log.info("[支付宝异步回调] 收到支付宝异步回调请求：params={}", params);
        Result<Boolean> result = alipayFeignApi.rsaCheckV1(params);

        if (result == null || result.hasError() || !result.getData()) {
            log.warn("[支付宝异步回调] 验证签名失败：result={}, params={}", result, params);
            return "fail";
        }

        //——请在这里编写您的程序（以下代码仅作参考）——

        /* 实际验证过程建议商户务必添加以下校验：
            1、需要验证该通知数据中的out_trade_no是否为商户系统中创建的订单号，
            2、判断total_amount是否确实为该订单的实际金额（即商户订单创建时的金额），
            3、校验通知中的seller_id（或者seller_email) 是否为out_trade_no这笔单据的对应的操作方（有的时候，一个商户可能有多个seller_id/seller_email）
            4、验证app_id是否为该商户本身。
        */
        //商户订单号
        String orderNo = params.get("out_trade_no");
        //支付宝交易号
        String tradeNo = params.get("trade_no");
        //交易状态
        String trade_status = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        if (trade_status.equals("TRADE_FINISHED")) {
            //判断该笔订单是否在商户网站中已经做过处理
            //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
            //如果有做过处理，不执行商户的业务程序
            log.info("[支付宝异步回调] 收到订单交易完成状态通知：{}", params);
            //注意：
            //退款日期超过可退款期限后（如三个月可退款），支付宝系统发送该交易状态通知
        } else if (trade_status.equals("TRADE_SUCCESS")) {
            //判断该笔订单是否在商户网站中已经做过处理
            //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
            //如果有做过处理，不执行商户的业务程序
            orderInfoService.paySuccess(totalAmount, tradeNo, orderNo);

            //注意：
            //付款完成后，支付宝系统发送该交易状态通知
        }
        return "success";
    }
}
