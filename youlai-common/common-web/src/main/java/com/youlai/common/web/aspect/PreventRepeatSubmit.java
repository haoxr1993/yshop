package com.youlai.common.web.aspect;

import cn.hutool.core.util.StrUtil;
import com.youlai.common.result.ResultCode;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.common.web.annotation.PreventDuplicateResubmit;
import com.youlai.common.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 *
 * @author Ray
 * @since 2023/05/09
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PreventRepeatSubmit {

    private final RedissonClient redissonClient;

    private static final String RESUBMIT_LOCK_PREFIX = "LOCK:RESUBMIT:";

    /**
     * 防重复提交切点
     */
    @Pointcut("@annotation(preventDuplicateResubmit)")
    public void preventDuplicateSubmitPointCut(PreventDuplicateResubmit preventDuplicateResubmit) {
    }

    @Around(value = "preventDuplicateSubmitPointCut(preventDuplicateResubmit)", argNames = "pjp,preventDuplicateResubmit")
    public Object doAround(ProceedingJoinPoint pjp, PreventDuplicateResubmit preventDuplicateResubmit) throws Throwable {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        String jti = SecurityUtils.getJti();
        if (StrUtil.isNotBlank(jti)) {
            String resubmitLockKey = RESUBMIT_LOCK_PREFIX + jti + ":" + request.getMethod() + "-" + request.getRequestURI();
            // 防重提交锁过期时间
            int expire = preventDuplicateResubmit.expire();
            RLock lock = redissonClient.getLock(resubmitLockKey);
            // 获取锁失败，直接返回 false
            boolean lockResult = lock.tryLock(0, expire, TimeUnit.SECONDS);
            if (!lockResult) {
                // 抛出重复提交提示信息
                throw new BusinessException(ResultCode.REPEAT_SUBMIT_ERROR);
            }
        }
        return pjp.proceed();
    }


}
