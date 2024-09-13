package com.youlai.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.common.constant.GlobalConstants;
import com.youlai.common.constant.RedisConstants;
import com.youlai.common.constant.SystemConstants;
import com.youlai.common.core.exception.BusinessException;
import com.youlai.common.mail.service.MailService;
import com.youlai.common.security.service.PermissionService;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.common.sms.config.AliyunSmsProperties;
import com.youlai.common.sms.service.SmsService;
import com.youlai.system.converter.UserConverter;
import com.youlai.system.dto.UserAuthInfo;
import com.youlai.system.enums.ContactType;
import com.youlai.system.mapper.UserMapper;
import com.youlai.system.model.bo.UserBO;
import com.youlai.system.model.entity.User;
import com.youlai.system.model.form.*;
import com.youlai.system.model.query.UserPageQuery;
import com.youlai.system.model.vo.UserExportVO;
import com.youlai.system.model.vo.UserInfoVO;
import com.youlai.system.model.vo.UserPageVO;
import com.youlai.system.model.vo.UserProfileVO;
import com.youlai.system.service.RoleService;
import com.youlai.system.service.UserRoleService;
import com.youlai.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户业务实现类
 *
 * @author Ray
 * @since 2022/1/14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    private final UserRoleService userRoleService;

    private final RoleService roleService;

    private final UserConverter userConverter;

    private final PermissionService permissionService;

    private final SmsService smsService;
    private final MailService mailService;

    private final AliyunSmsProperties aliyunSmsProperties;

    private final StringRedisTemplate redisTemplate;

    /**
     * 获取用户分页列表
     *
     * @param queryParams 查询参数
     * @return {@link UserPageVO}
     */
    @Override
    public IPage<UserPageVO> getUserPage(UserPageQuery queryParams) {

        Page<UserBO> userPage = this.baseMapper.getUserPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );

        // 实体转换
        return userConverter.toPageVo(userPage);
    }

    /**
     * 获取用户详情
     *
     * @param userId 用户ID
     * @return {@link UserForm}
     */
    @Override
    public UserForm getUserFormData(Long userId) {
        UserBO userDetail = this.baseMapper.getUserDetail(userId);
        return userConverter.toForm(userDetail);
    }

    /**
     * 新增用户
     *
     * @param userForm 用户表单对象
     * @return true|false
     */
    @Override
    public boolean saveUser(UserForm userForm) {

        String username = userForm.getUsername();

        long count = this.count(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        Assert.isTrue(count == 0, "用户名已存在");

        // 实体转换 form->entity
        User entity = userConverter.toEntity(userForm);

        // 设置默认加密密码
        String defaultEncryptPwd = passwordEncoder.encode(SystemConstants.DEFAULT_PASSWORD);
        entity.setPassword(defaultEncryptPwd);

        // 新增用户
        boolean result = this.save(entity);

        if (result) {
            // 保存用户角色
            userRoleService.saveUserRoles(entity.getId(), userForm.getRoleIds());
        }
        return result;
    }

    /**
     * 更新用户
     *
     * @param userId   用户ID
     * @param userForm 用户表单对象
     * @return true|false 是否更新成功
     */
    @Override
    @Transactional
    public boolean updateUser(Long userId, UserForm userForm) {

        String username = userForm.getUsername();

        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .ne(User::getId, userId)
        );
        Assert.isTrue(count == 0, "用户名已存在");

        // form -> entity
        User entity = userConverter.toEntity(userForm);

        // 修改用户
        boolean result = this.updateById(entity);

        if (result) {
            // 保存用户角色
            userRoleService.saveUserRoles(entity.getId(), userForm.getRoleIds());
        }
        return result;
    }

    /**
     * 删除用户
     *
     * @param idsStr 用户ID，多个以英文逗号(,)分割
     * @return true/false 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteUsers(String idsStr) {
        List<Long> ids = Arrays.stream(idsStr.split(","))
                .map(Long::parseLong).
                collect(Collectors.toList());
        return this.removeByIds(ids);

    }

    /**
     * 修改用户密码
     *
     * @param userId   用户ID
     * @param password 用户密码
     * @return true|false
     */
    @Override
    public boolean updatePassword(Long userId, String password) {
        return this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(password))
        );
    }

    /**
     * 根据用户名获取认证信息
     *
     * @param username 用户名
     * @return 用户认证信息 {@link UserAuthInfo}
     */
    @Override
    public UserAuthInfo getUserAuthInfo(String username) {
        UserAuthInfo userAuthInfo = this.baseMapper.getUserAuthInfo(username);
        if (userAuthInfo != null) {
            Set<String> roles = userAuthInfo.getRoles();
            if (CollectionUtil.isNotEmpty(roles)) {
                // 获取最大范围的数据权限(目前设定DataScope越小，拥有的数据权限范围越大，所以获取得到角色列表中最小的DataScope)
                Integer dataScope = roleService.getMaxDataRangeDataScope(roles);
                userAuthInfo.setDataScope(dataScope);
            }
        }
        return userAuthInfo;
    }


    /**
     * 获取导出用户列表
     *
     * @param queryParams 查询参数
     * @return {@link UserExportVO}
     */
    @Override
    public List<UserExportVO> listExportUsers(UserPageQuery queryParams) {
        return this.baseMapper.listExportUsers(queryParams);
    }

    /**
     * 获取登录用户信息
     *
     * @return {@link UserInfoVO}   用户信息
     */
    @Override
    public UserInfoVO getCurrentUserInfo() {
        // 登录用户entity
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, SecurityUtils.getUsername())
                .select(
                        User::getId,
                        User::getNickname,
                        User::getAvatar
                )
        );
        // entity->VO
        UserInfoVO userInfoVO = userConverter.entity2InfoVo(user);

        // 获取用户角色集合
        Set<String> roles = SecurityUtils.getRoles();
        userInfoVO.setRoles(roles);

        // 获取用户权限集合
        if (CollectionUtil.isNotEmpty(roles)) {
            Set<String> perms = permissionService.getRolePermsFormCache(roles);
            userInfoVO.setPerms(perms);
        }

        return userInfoVO;
    }

    /**
     * 注销登录
     */
    @Override
    public boolean logout() {
        String jti = SecurityUtils.getJti();
        Optional<Long> expireTimeOpt = Optional.ofNullable(SecurityUtils.getExp()); // 使用Optional处理可能的null值

        long currentTimeInSeconds = System.currentTimeMillis() / 1000; // 当前时间（单位：秒）

        expireTimeOpt.ifPresent(expireTime -> {
            if (expireTime > currentTimeInSeconds) {
                // token未过期，添加至缓存作为黑名单，缓存时间为token剩余的有效时间
                long remainingTimeInSeconds = expireTime - currentTimeInSeconds;
                redisTemplate.opsForValue().set(RedisConstants.TOKEN_BLACKLIST_PREFIX + jti, "", remainingTimeInSeconds, TimeUnit.SECONDS);
            }
        });

        if (expireTimeOpt.isEmpty()) {
            // token 永不过期则永久加入黑名单
            redisTemplate.opsForValue().set(RedisConstants.TOKEN_BLACKLIST_PREFIX + jti, "");
        }

        return true;
    }

    /**
     * 注册用户
     *
     * @param userRegisterForm 用户注册表单对象
     * @return true|false 是否注册成功
     */
    @Override
    public boolean registerUser(UserRegisterForm userRegisterForm) {

        String mobile = userRegisterForm.getMobile();
        String code = userRegisterForm.getCode();
        // 校验验证码
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.REGISTER_SMS_CODE_PREFIX + mobile);
        if (!StrUtil.equals(code, cacheCode)) {
            log.warn("验证码不匹配或不存在: {}", mobile);
            return false; // 验证码不匹配或不存在时返回false
        }
        // 校验通过，删除验证码
        redisTemplate.delete(RedisConstants.REGISTER_SMS_CODE_PREFIX + mobile);

        // 校验手机号是否已注册
        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, mobile)
                .or()
                .eq(User::getUsername, mobile)
        );
        Assert.isTrue(count == 0, "手机号已注册");

        User entity = new User();
        entity.setUsername(mobile);
        entity.setMobile(mobile);
        entity.setStatus(GlobalConstants.STATUS_YES);

        // 设置默认加密密码
        String defaultEncryptPwd = passwordEncoder.encode(SystemConstants.DEFAULT_PASSWORD);
        entity.setPassword(defaultEncryptPwd);

        // 新增用户，并直接返回结果
        return this.save(entity);
    }

    /**
     * 发送注册短信验证码
     *
     * @param mobile 手机号
     * @return true|false 是否发送成功
     */
    @Override
    public boolean sendRegistrationSmsCode(String mobile) {
        // 获取短信模板代码
        String templateCode = aliyunSmsProperties.getTemplateCodes().get("register");

        // 生成随机4位数验证码
        String code = RandomUtil.randomNumbers(4);

        // 短信模板: 您的验证码：${code}，该验证码5分钟内有效，请勿泄漏于他人。
        // 其中 ${code} 是模板参数，使用时需要替换为实际值。
        String templateParams = JSONUtil.toJsonStr(Collections.singletonMap("code", code));

        boolean result = smsService.sendSms(mobile, templateCode, templateParams);
        if (result) {
            // 将验证码存入redis，有效期5分钟
            redisTemplate.opsForValue().set(RedisConstants.REGISTER_SMS_CODE_PREFIX + mobile, code, 5, TimeUnit.MINUTES);

            // TODO 考虑记录每次发送短信的详情，如发送时间、手机号和短信内容等，以便后续审核或分析短信发送效果。
        }
        return result;
    }

    /**
     * 获取个人中心用户信息
     *
     * @param userId 用户ID
     * @return
     */
    @Override
    public UserProfileVO getUserProfile(Long userId) {
        UserBO entity = this.baseMapper.getUserProfile(userId);
        return userConverter.toProfileVO(entity);
    }

    /**
     * 修改个人中心用户信息
     *
     * @param formData 表单数据
     * @return
     */
    @Override
    public boolean updateUserProfile(UserProfileForm formData) {
        Long userId = SecurityUtils.getUserId();
        User entity = userConverter.toEntity(formData);
        entity.setId(userId);
        return this.updateById(entity);
    }


    /**
     * 修改用户密码
     *
     * @param userId 用户ID
     * @param data   密码修改表单数据
     * @return
     */
    @Override
    public boolean changePassword(Long userId, PasswordChangeForm data) {

        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String oldPassword = data.getOldPassword();

        // 校验原密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        // 新旧密码不能相同
        if (passwordEncoder.matches(data.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        String newPassword = data.getNewPassword();
        return this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(newPassword))
        );
    }

    /**
     * 重置密码
     *
     * @param userId   用户ID
     * @param password 密码重置表单数据
     * @return
     */
    @Override
    public boolean resetPassword(Long userId, String password) {
        return this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(password))
        );
    }

    /**
     * 发送验证码
     *
     * @param contact 联系方式 手机号/邮箱
     * @param type    联系方式类型 {@link ContactType}
     * @return
     */
    @Override
    public boolean sendVerificationCode(String contact, ContactType type) {

        // 随机生成4位验证码
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 1000));
        // 发送验证码

        String verificationCodePrefix = null;
        switch (type) {
            case MOBILE:
                // 获取修改密码的模板code
                String changePasswordSmsTemplateCode = aliyunSmsProperties.getTemplateCodes().get("changePassword");
                smsService.sendSms(contact, changePasswordSmsTemplateCode, "[{\"code\":\"" + code + "\"}]");
                verificationCodePrefix = RedisConstants.MOBILE_VERIFICATION_CODE_PREFIX;
                break;
            case EMAIL:
                mailService.sendMail(contact, "验证码", "您的验证码是：" + code);
                verificationCodePrefix = RedisConstants.EMAIL_VERIFICATION_CODE_PREFIX;
                break;
            default:
                throw new BusinessException("不支持的联系方式类型");
        }
        // 存入 redis 用于校验, 5分钟有效
        redisTemplate.opsForValue().set(verificationCodePrefix + contact, code, 5, TimeUnit.MINUTES );
        return true;
    }

    /**
     * 修改当前用户手机号码
     *
     * @param data 表单数据
     * @return
     */
    @Override
    public boolean bindMobile(MobileBindingForm data) {
        Long userId = SecurityUtils.getUserId();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验验证码
        String verificationCode = data.getCode();
        String contact = data.getMobile();
        String verificationCodeKey = RedisConstants.MOBILE_VERIFICATION_CODE_PREFIX + contact;
        String code = redisTemplate.opsForValue().get(verificationCodeKey);
        if (!verificationCode.equals(code)) {
            throw new BusinessException("验证码错误");
        }
        // 更新手机号码
        return this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getMobile, contact)
        );
    }

    /**
     * 修改当前用户邮箱
     *
     * @param data 表单数据
     * @return
     */
    @Override
    public boolean bindEmail(EmailChangeForm data) {
        Long userId = SecurityUtils.getUserId();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验验证码
        String verificationCode = data.getCode();
        String email = data.getEmail();
        String verificationCodeKey = RedisConstants.EMAIL_VERIFICATION_CODE_PREFIX + email;
        String code = redisTemplate.opsForValue().get(verificationCodeKey);
        if (!verificationCode.equals(code)) {
            throw new BusinessException("验证码错误");
        }
        // 更新邮箱
        return this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getEmail, email)
        );
    }
}
