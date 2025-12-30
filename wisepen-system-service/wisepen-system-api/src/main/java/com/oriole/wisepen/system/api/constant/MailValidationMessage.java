package com.oriole.wisepen.system.api.constant;

public interface MailValidationMessage{
/**
 * 用户校验消息常量接口
 * 用于统一存储校验相关的消息常量，避免硬编码
 */
    String  TO_EMAIL_EMPTY = "收件人邮箱不能为空";
    String  TO_EMAIL_INVALID = "邮箱格式不正确";
    String  SUBJECT_EMPTY = "邮件主题不能为空";
    String  CONTENT_EMPTY = "邮件内容不能为空";
}

