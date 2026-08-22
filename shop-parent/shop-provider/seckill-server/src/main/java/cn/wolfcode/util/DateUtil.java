package cn.wolfcode.util;

import java.util.Calendar;
import java.util.Date;


public class DateUtil {
    /**
     * 根据日期和场次看是否在秒杀有效时间之内
     *
     * @param startDate
     * @param time
     * @return
     */
    public static boolean isLegalTime(Date startDate, int time) {
        // 获取一个日历类
        Calendar c = Calendar.getInstance();
        // 将开始时间设置到日期当中
        c.setTime(startDate);
        // 设置时间 2021-12-03 16:00:00
        c.set(Calendar.HOUR_OF_DAY, time);
        // 获取开始时间的毫秒值（时间戳）
        long start = c.getTime().getTime();
        // 基于开始时间增加两个小时得到结束时间
        c.add(Calendar.HOUR_OF_DAY, 2);
        // 获取到结束时间的毫秒值
        long end = c.getTime().getTime();

        // 获取当前时间的毫秒值
        long now = new Date().getTime();
        // 判断当前时间是否 >= 开始时间 并且 <= 结束时间
        return now >= start && now <= end;
    }
}
