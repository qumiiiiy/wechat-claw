package com.weida.bot.wechatilinkbot.capability;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Deterministic fallback classifier that determines whether an email requires user action.
 *
 * <p>This is intentionally stateless and does not call any AI model. It runs inside the IMAP
 * adapter's fetch loop as the zero-latency fallback when the model-backed
 * {@link EmailActionClassifierPort} is unavailable, disabled or fails.</p>
 */
final class EmailActionClassifier {

    private EmailActionClassifier() { /* utility class */ }

    // ── noise patterns: clearly not actionable ──

    private static final Pattern[] NOISE = {
            Pattern.compile("退订|unsubscribe|订阅|subscribe|newsletter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("验证码|verification|验证|激活|activate", Pattern.CASE_INSENSITIVE),
            Pattern.compile("您收到一条新消息|系统通知|自动回复|out.?of.?office",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("已收到您的|已处理|已受理|已安排|已发货|订单|物流",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("no.?reply|noreply|donotreply", Pattern.CASE_INSENSITIVE),
            Pattern.compile("广告|促销|优惠|折扣|秒杀|特价|红包|福利|抽奖|活动",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("账单|消费|扣款|缴费|发票|收据|receipt|payment|billing",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("微博|抖音|知乎|B站|bilibili|关注了你|点赞|评论|收藏|好友申请|动态提醒"),
            Pattern.compile("升级|更新|新版|版本|update|upgrade|安全提醒", Pattern.CASE_INSENSITIVE),
            Pattern.compile("云盘|网盘|备份|存储空间|cloud|onedrive", Pattern.CASE_INSENSITIVE),
    };

    // ── strong action signals ──

    private static final Pattern[] ACTION_SIGNALS = {
            Pattern.compile("请.{0,10}(回复|完成|提交|确认|处理|审批|查看|查收|安排|准备|参加|出席)"),
            Pattern.compile("截止|deadline|due.?date|到期|到期日|最后期限"),
            Pattern.compile("请尽快|请立即|务必|速回|急|紧急|urgent", Pattern.CASE_INSENSITIVE),
            Pattern.compile("请在.{0,20}前|请于.{0,20}前|请.{0,5}之前"),
            Pattern.compile("要求|required|必须|mandatory|必须完成", Pattern.CASE_INSENSITIVE),
            Pattern.compile("审批|approval|approve|请批|请审核", Pattern.CASE_INSENSITIVE),
            Pattern.compile("交报告|交作业|交论文|交材料|交表|上交|提交|投递"),
            Pattern.compile("报名|填报|填写|注册|签到|登记"),
            Pattern.compile("\\d{1,2}[月/]\\d{1,2}.{0,6}(日|号|前|截止|之前)"), // "8月5日前交"
    };


    enum Result {
        ACTION_REQUIRED,
        NOT_ACTIONABLE
    }

    /**
     * Classifies one email. Returns {@code ACTION_REQUIRED} only when the subject
     * clearly contains an action signal or deadline. Everything else is filtered out.
     */
    static Result classify(String subject, String body, Instant receivedAt) {
        String text = (subject + " " + (body != null ? body : "")).strip();

        // 1. Noise filter
        for (Pattern p : NOISE) {
            if (p.matcher(text).find()) {
                return Result.NOT_ACTIONABLE;
            }
        }

        // 2. Stale filter
        if (receivedAt != null
                && Duration.between(receivedAt, Instant.now()).toDays() > 14) {
            return Result.NOT_ACTIONABLE;
        }

        // 3. Strong action signals — only these pass
        for (Pattern p : ACTION_SIGNALS) {
            if (p.matcher(text).find()) {
                return Result.ACTION_REQUIRED;
            }
        }

        // 4. Default: don't show — require explicit evidence of action
        return Result.NOT_ACTIONABLE;
    }
}
