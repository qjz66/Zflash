package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.CodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.config.AlipayProperties;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.RefundVo;
import cn.wolfcode.web.msg.PayCodeMsg;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController {
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private AlipayProperties alipayProperties;

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
}
