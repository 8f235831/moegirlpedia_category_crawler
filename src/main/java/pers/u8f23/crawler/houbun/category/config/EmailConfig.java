package pers.u8f23.crawler.houbun.category.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author 8f23
 * @create 2023/8/5-11:34
 */
@Getter
@Setter
public class EmailConfig
{
	private String emailHost;
	private String transportType;
	private String fromUser;
	private String fromEmail;
	private String authCode;
	private String toEmail;
	private List<String> copyToEmail;
	private String subject;

}
