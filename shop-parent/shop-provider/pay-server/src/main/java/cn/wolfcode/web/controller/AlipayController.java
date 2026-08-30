package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.PaySuccessVo;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.feign.AliPaySuccessApi;
import cn.wolfcode.web.msg.PayCodeMsg;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties alipayProperties;
    @Autowired
    private AliPaySuccessApi aliPaySuccessApi;

//    @RequestMapping("/queryTradeStatus")
//    public Result<Map<String, String>> queryStatus(@RequestParam("orderNo") String orderNo) throws AlipayApiException {
//        //设置请求参数
//        AlipayTradeQueryRequest alipayRequest = new AlipayTradeQueryRequest();
//
//        //商户订单号，商户网站订单系统中唯一订单号
//        //支付宝交易号
//        String trade_no = "";
//        //请二选一设置
//
//        alipayRequest.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"," + "\"trade_no\":\"" + trade_no + "\"}");
//
//        //请求
//        String queryResult = alipayClient.execute(alipayRequest).getBody();
//        JSONObject map = JSON.parseObject(queryResult);
//
//        HashMap<String, String> hashMap = new HashMap<>();
//
//        //hashMap.put("alipay_trade_query_response", map.get("alipay_trade_query_response").toString());
//
//        map.getJSONObject("alipay_trade_query_response").forEach((k, v) -> {
//            hashMap.put(k, v.toString());
//        });
//        hashMap.put("sign", map.get("sign").toString());
//
//        Result<Boolean> signResult = this.rsaCheckV1(hashMap);
////
////        if (!signResult.getData()) {
////            throw new BusinessException(CommonCodeMsg.ILLEGAL_OPERATION);
////        }
////
////        String json = map.get("alipay_trade_query_response");
////        HashMap<String, String> result = JSON.parseObject(json, HashMap.class);
//        return Result.success(hashMap);
//    }
//
//    @RequestMapping("/refund")
//    public Result<Boolean> refund(@RequestBody RefundVo refund) throws AlipayApiException {
//        //设置请求参数
//        AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();
//        //商户订单号，商户网站订单系统中唯一订单号
//        String out_trade_no = refund.getOutTradeNo();
//        //支付宝交易号
//        String trade_no = "";
//        //请二选一设置
//        //需要退款的金额，该金额不能大于订单金额，必填
//        String refund_amount = refund.getRefundAmount();
//        //退款的原因说明
//        String refund_reason = refund.getRefundReason();
//        //标识一次退款请求，同一笔交易多次退款需要保证唯一，如需部分退款，则此参数必传
//        String out_request_no = "";
//
//        alipayRequest.setBizContent("{\"out_trade_no\":\"" + out_trade_no + "\","
//                + "\"trade_no\":\"" + trade_no + "\","
//                + "\"refund_amount\":\"" + refund_amount + "\","
//                + "\"refund_reason\":\"" + refund_reason + "\","
//                + "\"out_request_no\":\"" + out_request_no + "\"}");
//
//        //请求
//        AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);
//        log.info("[支付宝退款] 收到支付宝退款响应信息：{}", response.getBody());
//        return Result.success(response.isSuccess());
//    }
//
//    @RequestMapping("/rsaCheck")
//    public Result<Boolean> rsaCheckV1(@RequestBody HashMap<String, String> params) throws AlipayApiException {
//        boolean result = AlipaySignature.rsaCheckV1(
//                params,
//                alipayProperties.getAlipayPublicKey(),
//                alipayProperties.getCharset(),
//                alipayProperties.getSignType());//调用SDK验证签名
//        return Result.success(result);
//    }
//
//    @RequestMapping("/pay")
//    public Result<String> pay(@RequestBody PayVo payVo) throws AlipayApiException {
//        //设置请求参数
//        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
//        alipayRequest.setReturnUrl(payVo.getReturnUrl());
//        alipayRequest.setNotifyUrl(payVo.getNotifyUrl());
//
//        //商户订单号，商户网站订单系统中唯一订单号，必填
//        String out_trade_no = payVo.getOutTradeNo();
//        //付款金额，必填
//        String total_amount = payVo.getTotalAmount();
//        //订单名称，必填
//        String subject = payVo.getSubject();
//        //商品描述，可空
//        String body = payVo.getBody();
//
//        alipayRequest.setBizContent("{\"out_trade_no\":\"" + out_trade_no + "\","
//                + "\"total_amount\":\"" + total_amount + "\","
//                + "\"subject\":\"" + subject + "\","
//                + "\"body\":\"" + body + "\","
//                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
//
//        //请求
//        String result = alipayClient.pageExecute(alipayRequest).getBody();
//        System.out.println(result);
//        return Result.success(result);
//    }
    @RequestMapping("/pay")
    public Result<String> prePay(@RequestBody PayVo pay) {
        //利用支付宝api sdk向支付宝发起支付请求
        AlipayTradePagePayRequest alipayTradePagePayRequest = new AlipayTradePagePayRequest();
        alipayTradePagePayRequest.setNotifyUrl(alipayProperties.getNotifyUrl());
        alipayTradePagePayRequest.setReturnUrl(alipayProperties.getReturnUrl());

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", pay.getOutTradeNo());
        bizContent.put("total_amount", pay.getTotalAmount());
        bizContent.put("subject", pay.getSubject());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");

        alipayTradePagePayRequest.setBizContent(bizContent.toString());

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(alipayTradePagePayRequest);
            if (response.isSuccess()) {
                String form = response.getBody();
                log.info("支付宝支付结果：\n{}",form);
                return Result.success(form);
            }

            return Result.error(new CodeMsg(500601, response.getMsg()));
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        return Result.error(PayCodeMsg.PAY_FAILED);
    }

    @PostMapping("notify_url")
    public String notifyUrl(HttpServletRequest req) throws UnsupportedEncodingException, AlipayApiException {
        Map<String, String> params = getParams(req);
        // 获取支付宝POST过来反馈信息
        try{
            boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayProperties.getAlipayPublicKey(), alipayProperties.getCharset(), alipayProperties.getSignType()); //调用SDK验证签名
            if (signVerified) {
                //商户订单号
                String orderNo = params.get("out_trade_no");
                //支付宝交易号
                String tradeNo = params.get("trade_no");
                //交易状态
                String trade_status = params.get("trade_status");
                String totalAmount = params.get("total_amount");
                log.info("[支付宝异步回调] 收到订单交易完成状态通知：{}", params);

                if (trade_status.equals("TRADE_FINISHED")) {
                    //判断该笔订单是否在商户网站中已经做过处理
                    //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
                    //如果有做过处理，不执行商户的业务程序
                    log.info("[支付宝异步回调] 收到订单交易完成状态通知：{}", params);
                    //注意：
                    //退款日期超过可退款期限后（如三个月可退款），支付宝系统发送该交易状态通知
                } else if (trade_status.equals("TRADE_SUCCESS")) {
                    //判断该笔订单是否在商户网站中已经做过处理
                    log.info("[支付宝异步回调] 收到已支付成功消息，开始回调...");
                    //如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
                    //如果有做过处理，不执行商户的业务程序
                    PaySuccessVo paySuccessVo = new PaySuccessVo();
                    paySuccessVo.setOutTradeNo(orderNo);
                    paySuccessVo.setTradeNo(tradeNo);
                    paySuccessVo.setTotalAmount(totalAmount);
                    aliPaySuccessApi.paySuccess(paySuccessVo);

                    //注意：
                    //付款完成后，支付宝系统发送该交易状态通知
                }
            }

            return "success";
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "fail";
    }

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

    @GetMapping("/return_url")
    public String returnUrl(HttpServletRequest req) throws UnsupportedEncodingException {
        Map<String, String> params = getParams(req);

        log.info("同步回调 收到交易消息{}", params);

        try{
            boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayProperties.getAlipayPublicKey(), alipayProperties.getCharset(), alipayProperties.getSignType()); //调用SDK验证签名
            if (signVerified) {
                return "redirect:http://localhost/order_detail.html?orderNo=" + params.get("out_trade_no");
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return "fail";
    }

    @PostMapping("/refund")
    public Result<Boolean> refund(@RequestBody RefundVo refundVo) throws AlipayApiException {

        AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();
        //商户订单号，商户网站订单系统中唯一订单号
        String out_trade_no = refundVo.getOutTradeNo();
        //支付宝交易号
        String trade_no = "";
        //请二选一设置
        //需要退款的金额，该金额不能大于订单金额，必填
        String refund_amount = refundVo.getRefundAmount();
        //退款的原因说明
        String refund_reason = refundVo.getRefundReason();
        //标识一次退款请求，同一笔交易多次退款需要保证唯一，如需部分退款，则此参数必传
        String out_request_no = "";

        alipayRequest.setBizContent("{\"out_trade_no\":\"" + out_trade_no + "\","
                + "\"trade_no\":\"" + trade_no + "\","
                + "\"refund_amount\":\"" + refund_amount + "\","
                + "\"refund_reason\":\"" + refund_reason + "\","
                + "\"out_request_no\":\"" + out_request_no + "\"}");

        //请求
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(alipayRequest);
            log.info("[支付宝退款] 收到支付宝退款响应信息：{}", response.getBody());
            return Result.success(response.isSuccess());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(new CodeMsg(500601,"[支付宝退款] 退款失败..."));
    }
}
