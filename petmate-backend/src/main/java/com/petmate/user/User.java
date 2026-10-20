package com.petmate.user;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private Integer vipLevel;
    private String bio;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private String password;     // BCrypt hash, 演示场景为 null
    private String openId;
    private LocalDateTime vipExpireAt;
    // 实名认证相关
    private String realName;     // 真实姓名
    private String idCard;       // 身份证号(18位)
    private Integer verified;    // 0未认证 / 1已认证
    private String region;       // 所在地区(省/市/区 拼接字符串)
    private String city;          // 所在城市(如 北京)，用于社区「附近/市区」分类

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }
    public Integer getVipLevel() { return vipLevel; }
    public void setVipLevel(Integer vipLevel) { this.vipLevel = vipLevel; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public LocalDateTime getVipExpireAt() { return vipExpireAt; }
    public void setVipExpireAt(LocalDateTime vipExpireAt) { this.vipExpireAt = vipExpireAt; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public Integer getVerified() { return verified; }
    public void setVerified(Integer verified) { this.verified = verified; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
