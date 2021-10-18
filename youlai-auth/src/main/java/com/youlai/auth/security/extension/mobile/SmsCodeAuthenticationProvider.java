package com.youlai.auth.security.extension.mobile;

import cn.hutool.core.util.StrUtil;
import com.youlai.auth.security.core.userdetails.member.MemberUserDetailsServiceImpl;
import com.youlai.auth.security.extension.wechat.WechatAuthenticationToken;
import com.youlai.common.constant.AuthConstants;
import com.youlai.common.web.exception.BizException;
import com.youlai.mall.ums.api.MemberFeignClient;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashSet;

/**
 * 短信验证码认证授权提供者
 *
 * @author <a href="mailto:xianrui0365@163.com">xianrui</a>
 * @date 2021/9/25
 */
@Data
public class SmsCodeAuthenticationProvider implements AuthenticationProvider {

    private UserDetailsService userDetailsService;
    private MemberFeignClient memberFeignClient;
    private StringRedisTemplate redisTemplate;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        SmsCodeAuthenticationToken authenticationToken = (SmsCodeAuthenticationToken) authentication;
        String mobile = (String) authenticationToken.getPrincipal();
        String code = (String) authenticationToken.getCredentials();

        String codeKey = AuthConstants.SMS_CODE_PREFIX + mobile;
        String correctCode = redisTemplate.opsForValue().get(codeKey);
        // 短信验证码 666666 是后门，短信验证码发送功能【有来】考虑费用问题线上环境关闭，真实环境移除后门即可
        if (!code.equals("666666")) {
            if (StrUtil.isBlank(correctCode) || !code.equals(correctCode)) {
                throw new BizException("验证码不正确");
            } else {
                redisTemplate.delete(codeKey);
            }
        }
        UserDetails userDetails = ((MemberUserDetailsServiceImpl) userDetailsService).loadUserByMobile(mobile);
        WechatAuthenticationToken result = new WechatAuthenticationToken(userDetails, new HashSet<>());
        result.setDetails(authentication.getDetails());
        return result;
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return SmsCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
