package pers.u8f23.crawler.houbun.category.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author 8f23
 * @create 2023/9/5-12:52
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailReceiverConfig
{
	/** 【必需】收件人地址。 */
	private String address;
	/** 邮件主题。 */
	private String mailSubject;
	/** 是否通过邮件发送备份清单。 */
	private boolean sendAttachment;
	/** 是否通过邮件发送错误的堆栈追踪。 */
	private boolean sendErrorStackTrace;
}
